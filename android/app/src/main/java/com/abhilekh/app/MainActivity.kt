package com.abhilekh.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abhilekh.app.core.cv.OpenCVNativeBridge
import com.abhilekh.app.core.masking.AadhaarMaskingEngine
import com.abhilekh.app.core.ocr.OcrManager
import com.abhilekh.app.core.pdf.PdfBoxEngine
import com.abhilekh.app.core.pdf.PdfPageInput
import com.abhilekh.app.core.storage.EphemeralSessionStorage
import com.abhilekh.app.core.thermal.AndroidThermalMonitor
import com.abhilekh.app.core.thermal.ThermalTier
import com.abhilekh.app.data.db.DocumentEntity
import com.abhilekh.app.data.db.PageEntity
import com.abhilekh.app.ui.designsystem.DynamicProgressCapsule
import com.abhilekh.app.ui.screens.DocumentEditorScreen
import com.abhilekh.app.ui.screens.EditablePage
import com.abhilekh.app.ui.screens.HomeScreen
import com.abhilekh.app.ui.theme.AbhilekhTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(private val app: AbhilekhApplication) : ViewModel() {
    private val dao = app.database.documentDao()
    private val thermalMonitor = AndroidThermalMonitor(app)

    val documents = dao.getAllDocuments().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    var isProcessing by mutableStateOf(false)
    var isEditingSession by mutableStateOf(false)
    var sessionBitmaps = mutableStateListOf<Bitmap>()
    var defaultSessionTitle by mutableStateOf("")

    // Real-Time Progress & Thermal State Tracking
    var progressStep by mutableIntStateOf(0)
    var totalProgressSteps by mutableIntStateOf(0)
    var progressMessage by mutableStateOf("")
    var currentThermalTier by mutableStateOf(ThermalTier.NOMINAL)

    // Ephemeral Session Storage with AES-256-GCM Crypto-Shredding
    private var currentSessionStorage: EphemeralSessionStorage? = null

    /**
     * Ingests scanned or picked URIs into the interactive editing studio session.
     */
    fun startEditingSessionFromUris(uris: List<Uri>, isAppend: Boolean = false) {
        viewModelScope.launch {
            isProcessing = true
            progressMessage = "Ingesting scan pages..."
            progressStep = 0
            totalProgressSteps = uris.size

            try {
                if (!isAppend) {
                    val sessionDir = File(app.cacheDir, "session_${UUID.randomUUID()}").apply { mkdirs() }
                    currentSessionStorage = EphemeralSessionStorage(sessionDir)
                }

                val loadedBitmaps = withContext(Dispatchers.IO) {
                    uris.mapIndexedNotNull { index, uri ->
                        progressStep = index + 1
                        app.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }?.copy(Bitmap.Config.ARGB_8888, true)?.also { bmp ->
                            // Cache encrypted page
                            val byteStream = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, byteStream)
                            currentSessionStorage?.writeEncryptedPage(index, byteStream.toByteArray())
                        }
                    }
                }

                if (isAppend) {
                    sessionBitmaps.addAll(loadedBitmaps)
                } else {
                    sessionBitmaps.clear()
                    sessionBitmaps.addAll(loadedBitmaps)
                    val timeStamp = SimpleDateFormat("ddMMM_HHmm", Locale.getDefault()).format(Date())
                    defaultSessionTitle = "Scan_$timeStamp"
                    isEditingSession = true
                }
            } finally {
                isProcessing = false
            }
        }
    }

    /**
     * Compiles edited pages into dual-layer searchable PDF and saves to Room database.
     * Incorporates Thermal Throttling Governor and Ephemeral Crypto-Shredding.
     */
    fun saveEditedSession(docTitle: String, pages: List<EditablePage>, onComplete: () -> Unit) {
        viewModelScope.launch {
            isProcessing = true
            totalProgressSteps = pages.size
            progressStep = 0

            try {
                val docId = UUID.randomUUID().toString()
                val docDir = File(app.filesDir, "documents/$docId").apply { mkdirs() }

                val pdfInputs = mutableListOf<PdfPageInput>()
                val pageEntities = mutableListOf<PageEntity>()
                var hasMaskedAadhaar = false

                for ((index, page) in pages.withIndex()) {
                    progressStep = index + 1
                    progressMessage = "Enhancing & OCR Page ${index + 1} of ${pages.size}..."

                    // 1. Evaluate Thermal State Governor
                    currentThermalTier = thermalMonitor.getCurrentThermalTier()
                    if (currentThermalTier == ThermalTier.SEVERE) {
                        delay(150) // Yield coroutine on high thermal headroom
                    } else if (currentThermalTier == ThermalTier.MODERATE) {
                        delay(50)
                    }

                    val finalBitmap = page.displayBitmap

                    // 2. Run On-Device OCR on the final edited bitmap
                    val ocrResult = withContext(Dispatchers.Default) {
                        try {
                            OcrManager.recognizeText(finalBitmap)
                        } catch (_: Exception) {
                            null
                        }
                    }

                    // 3. Global Aadhaar Multi-Signal Check
                    var maskedPlaceholder: String? = null
                    if (ocrResult != null) {
                        val aadhaarEval = AadhaarMaskingEngine.evaluateAndMask(finalBitmap, ocrResult)
                        if (aadhaarEval.isAutoMasked || page.isMasked) {
                            hasMaskedAadhaar = true
                            maskedPlaceholder = aadhaarEval.maskedText
                        }
                    }

                    // 4. Save enhanced page bitmap to sandboxed storage
                    val pageFile = File(docDir, "page_${index + 1}.jpg")
                    withContext(Dispatchers.IO) {
                        FileOutputStream(pageFile).use { out ->
                            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                    }

                    pdfInputs.add(PdfPageInput(finalBitmap, ocrResult, maskedPlaceholder))
                    pageEntities.add(
                        PageEntity(
                            id = UUID.randomUUID().toString(),
                            documentId = docId,
                            pageNumber = index + 1,
                            imagePath = pageFile.absolutePath,
                            thumbPath = pageFile.absolutePath,
                            width = finalBitmap.width,
                            height = finalBitmap.height,
                            filterType = page.activeFilter.name.lowercase(),
                            ocrText = ocrResult?.text,
                            isMasked = hasMaskedAadhaar || page.isMasked
                        )
                    )
                }

                if (pdfInputs.isNotEmpty()) {
                    progressMessage = "Assembling Searchable PDF ('3 Tr')..."
                    // Assemble Dual-Layer Searchable PDF via PDFBox ('3 Tr')
                    val pdfFile = File(docDir, "$docTitle.pdf")
                    PdfBoxEngine.createSearchablePdf(pdfInputs, pdfFile)

                    val docEntity = DocumentEntity(
                        id = docId,
                        title = docTitle,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        pageCount = pdfInputs.size,
                        fileSizeBytes = pdfFile.length(),
                        thumbnailPath = pageEntities.first().thumbPath,
                        pdfPath = pdfFile.absolutePath,
                        hasMaskedAadhaar = hasMaskedAadhaar
                    )

                    dao.insertDocument(docEntity)
                    dao.insertPages(pageEntities)
                }

                // 5. Ephemeral Crypto-Shredding: Shred temporary cache
                currentSessionStorage?.cryptoShred()
                currentSessionStorage = null

                isEditingSession = false
                sessionBitmaps.clear()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessing = false
                onComplete()
            }
        }
    }

    fun cancelEditingSession() {
        currentSessionStorage?.cryptoShred()
        currentSessionStorage = null
        sessionBitmaps.clear()
        isEditingSession = false
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteDocument(doc)
            File(doc.pdfPath).parentFile?.deleteRecursively()
        }
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var photoPickerLauncher: ActivityResultLauncher<String>
    private var isAppendingPages = false

    private val viewModel: MainViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(application as AbhilekhApplication) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Configure ML Kit High-Speed & Full Document Scanner
        val scannerOptions = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE) // High-speed continuous auto-capture
            .build()

        val scannerClient = GmsDocumentScanning.getClient(scannerOptions)

        scannerLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val pages = scanResult?.pages?.map { it.imageUri } ?: emptyList()
                if (pages.isNotEmpty()) {
                    viewModel.startEditingSessionFromUris(pages, isAppend = isAppendingPages)
                }
            }
            isAppendingPages = false
        }

        // 2. Configure Photo Picker Launcher ("Create from photos")
        photoPickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.startEditingSessionFromUris(uris, isAppend = isAppendingPages)
            }
            isAppendingPages = false
        }

        setContent {
            AbhilekhTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val docs by viewModel.documents.collectAsState()

                    if (viewModel.isEditingSession && viewModel.sessionBitmaps.isNotEmpty()) {
                        DocumentEditorScreen(
                            initialPages = viewModel.sessionBitmaps.toList(),
                            initialTitle = viewModel.defaultSessionTitle,
                            onSavePdf = { title, editedPages ->
                                viewModel.saveEditedSession(title, editedPages) {
                                    Toast.makeText(this@MainActivity, "PDF Saved & Indexed!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAddMorePages = {
                                isAppendingPages = true
                                launchScanner(scannerClient)
                            },
                            onCancel = {
                                viewModel.cancelEditingSession()
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            HomeScreen(
                                documents = docs,
                                onLaunchScanner = {
                                    isAppendingPages = false
                                    launchScanner(scannerClient)
                                },
                                onImportPhotos = {
                                    isAppendingPages = false
                                    photoPickerLauncher.launch("image/*")
                                },
                                onCombineFiles = {
                                    Toast.makeText(this@MainActivity, "Select documents to merge into one PDF", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteDocument = { doc ->
                                    viewModel.deleteDocument(doc)
                                }
                            )

                            // Dynamic Island / Live Updates Progress Capsule Overlay
                            if (viewModel.isProcessing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 36.dp),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    DynamicProgressCapsule(
                                        isVisible = true,
                                        currentStep = viewModel.progressStep,
                                        totalSteps = viewModel.totalProgressSteps,
                                        statusMessage = viewModel.progressMessage,
                                        thermalTier = viewModel.currentThermalTier
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchScanner(scannerClient: com.google.mlkit.vision.documentscanner.GmsDocumentScanner) {
        scannerClient.getStartScanIntent(this@MainActivity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this@MainActivity, "Scanner Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
