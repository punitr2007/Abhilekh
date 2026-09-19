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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abhilekh.app.core.cv.OpenCVNativeBridge
import com.abhilekh.app.core.masking.AadhaarMaskingEngine
import com.abhilekh.app.core.ocr.OcrManager
import com.abhilekh.app.core.pdf.PdfBoxEngine
import com.abhilekh.app.core.pdf.PdfPageInput
import com.abhilekh.app.data.db.DocumentEntity
import com.abhilekh.app.data.db.PageEntity
import com.abhilekh.app.ui.screens.HomeScreen
import com.abhilekh.app.ui.screens.ManualRedactionScreen
import com.abhilekh.app.ui.theme.AbhilekhTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(private val app: AbhilekhApplication) : ViewModel() {
    private val dao = app.database.documentDao()

    val documents = dao.getAllDocuments().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    var isProcessing by mutableStateOf(false)
    var activeRedactionBitmap by mutableStateOf<Bitmap?>(null)

    fun processScannedPages(pageUris: List<Uri>, onComplete: () -> Unit) {
        viewModelScope.launch {
            isProcessing = true
            try {
                val docId = UUID.randomUUID().toString()
                val docDir = File(app.filesDir, "documents/$docId").apply { mkdirs() }
                val timeStamp = SimpleDateFormat("ddMMM_HHmm", Locale.getDefault()).format(Date())
                val docTitle = "Scan_$timeStamp"

                val pdfInputs = mutableListOf<PdfPageInput>()
                val pageEntities = mutableListOf<PageEntity>()
                var hasMaskedAadhaar = false

                for ((index, uri) in pageUris.withIndex()) {
                    val bitmap = withContext(Dispatchers.IO) {
                        app.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        }?.copy(Bitmap.Config.ARGB_8888, true)
                    } ?: continue

                    // 1. Apply Illumination Division Filter
                    withContext(Dispatchers.Default) {
                        OpenCVNativeBridge.nativeApplyIlluminationDivision(bitmap)
                    }

                    // 2. Run On-Device OCR
                    val ocrResult = withContext(Dispatchers.Default) {
                        try {
                            OcrManager.recognizeText(bitmap)
                        } catch (_: Exception) {
                            null
                        }
                    }

                    // 3. Global Aadhaar Multi-Signal Check & Auto-Masking
                    var maskedPlaceholder: String? = null
                    if (ocrResult != null) {
                        val aadhaarEval = AadhaarMaskingEngine.evaluateAndMask(bitmap, ocrResult)
                        if (aadhaarEval.isAutoMasked) {
                            hasMaskedAadhaar = true
                            maskedPlaceholder = aadhaarEval.maskedText
                        } else if (aadhaarEval.requiresManualReview) {
                            // Surface amber warning & allow manual redaction
                            activeRedactionBitmap = bitmap
                        }
                    }

                    // Save enhanced page bitmap to sandboxed storage
                    val pageFile = File(docDir, "page_${index + 1}.jpg")
                    withContext(Dispatchers.IO) {
                        FileOutputStream(pageFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                    }

                    pdfInputs.add(PdfPageInput(bitmap, ocrResult, maskedPlaceholder))
                    pageEntities.add(
                        PageEntity(
                            id = UUID.randomUUID().toString(),
                            documentId = docId,
                            pageNumber = index + 1,
                            imagePath = pageFile.absolutePath,
                            thumbPath = pageFile.absolutePath,
                            width = bitmap.width,
                            height = bitmap.height,
                            filterType = "illumination_division",
                            ocrText = ocrResult?.text,
                            isMasked = maskedPlaceholder != null
                        )
                    )
                }

                if (pdfInputs.isNotEmpty()) {
                    // 4. Assemble Dual-Layer Searchable PDF via PDFBox ('3 Tr')
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
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessing = false
                onComplete()
            }
        }
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

        // 1. Configure ML Kit Document Scanner Intent Launcher
        val scannerOptions = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        val scannerClient = GmsDocumentScanning.getClient(scannerOptions)

        scannerLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val pages = scanResult?.pages?.map { it.imageUri } ?: emptyList()
                if (pages.isNotEmpty()) {
                    viewModel.processScannedPages(pages) {
                        Toast.makeText(this, "Scan processed & searchable PDF created!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 2. Configure Photo Picker Launcher ("Create from photos")
        photoPickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.processScannedPages(uris) {
                    Toast.makeText(this, "Imported ${uris.size} photos to PDF!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            AbhilekhTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val docs by viewModel.documents.collectAsState()
                    val redactionBitmap = viewModel.activeRedactionBitmap

                    if (redactionBitmap != null) {
                        ManualRedactionScreen(
                            bitmap = redactionBitmap,
                            onComplete = { _ ->
                                viewModel.activeRedactionBitmap = null
                            },
                            onCancel = {
                                viewModel.activeRedactionBitmap = null
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            HomeScreen(
                                documents = docs,
                                onLaunchScanner = {
                                    scannerClient.getStartScanIntent(this@MainActivity)
                                        .addOnSuccessListener { intentSender ->
                                            scannerLauncher.launch(
                                                IntentSenderRequest.Builder(intentSender).build()
                                            )
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(this@MainActivity, "Scanner Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                },
                                onImportPhotos = {
                                    photoPickerLauncher.launch("image/*")
                                },
                                onCombineFiles = {
                                    Toast.makeText(this@MainActivity, "Select documents to merge into one PDF", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteDocument = { doc ->
                                    viewModel.deleteDocument(doc)
                                }
                            )

                            if (viewModel.isProcessing) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
