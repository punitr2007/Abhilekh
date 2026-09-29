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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abhilekh.app.core.cv.FilterMode
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

// ─── SavedStateHandle keys ────────────────────────────────────────────────────
private const val KEY_IS_EDITING = "is_editing_session"
private const val KEY_SESSION_TITLE = "session_title"

// ─── App navigation screen sealed class ──────────────────────────────────────
sealed class AppScreen {
    object Home : AppScreen()
    object HighSpeedCapture : AppScreen()
    object DocumentEditor : AppScreen()
}

class MainViewModel(
    private val app: AbhilekhApplication,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val dao = app.database.documentDao()
    private val thermalMonitor = AndroidThermalMonitor(app)

    val documents = dao.getAllDocuments().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // ── Processing state ──────────────────────────────────────────────────────
    var isProcessing by mutableStateOf(false)
    var progressStep by mutableIntStateOf(0)
    var totalProgressSteps by mutableIntStateOf(0)
    var progressMessage by mutableStateOf("")
    var currentThermalTier by mutableStateOf(ThermalTier.NOMINAL)

    // ── Session flags — persisted via SavedStateHandle across process death ───
    var isEditingSession by mutableStateOf(savedState.get<Boolean>(KEY_IS_EDITING) ?: false)
        private set

    var defaultSessionTitle by mutableStateOf(savedState.get<String>(KEY_SESSION_TITLE) ?: "")
        private set

    /**
     * Canonical in-session editable pages.
     * Hoisted here (out of the Composable) so they survive recomposition,
     * screen rotations, and navigation back-stack changes.
     */
    val editablePages = mutableStateListOf<EditablePage>()

    // Raw bitmaps used to build pages on first open — kept separate so that
    // the ViewModel can rebuild pages if the session was restored from disk.
    private val sessionRawBitmaps = mutableListOf<Bitmap>()

    // Ephemeral AES-256-GCM encrypted cache dir
    private var currentSessionStorage: EphemeralSessionStorage? = null

    // ── Filter / Rotate (background, not UI thread) ───────────────────────────

    /** isApplyingFilter prevents double-tap races while heavy work is in flight. */
    var isApplyingFilter by mutableStateOf(false)
        private set

    /**
     * Applies [filter] to [pageIndex] (or all pages) on [Dispatchers.Default].
     * This is the fix for the OOM/ANR filter crash: all bitmap work is now
     * safely off the main thread.
     */
    fun applyFilter(pageIndex: Int, filter: FilterMode, applyToAll: Boolean) {
        if (isApplyingFilter) return
        viewModelScope.launch {
            isApplyingFilter = true
            try {
                if (applyToAll) {
                    for (i in editablePages.indices) {
                        val p = editablePages[i]
                        val filtered = withContext(Dispatchers.Default) {
                            OpenCVNativeBridge.applyFilter(p.rawBitmap, filter)
                        }
                        // Eagerly recycle the old display bitmap to help the GC
                        // reclaim native heap before allocating the next one.
                        if (p.displayBitmap !== p.rawBitmap) p.displayBitmap.recycle()
                        editablePages[i] = p.copy(displayBitmap = filtered, activeFilter = filter)
                    }
                } else if (pageIndex < editablePages.size) {
                    val p = editablePages[pageIndex]
                    val filtered = withContext(Dispatchers.Default) {
                        OpenCVNativeBridge.applyFilter(p.rawBitmap, filter)
                    }
                    if (p.displayBitmap !== p.rawBitmap) p.displayBitmap.recycle()
                    editablePages[pageIndex] = p.copy(displayBitmap = filtered, activeFilter = filter)
                }
            } finally {
                isApplyingFilter = false
            }
        }
    }

    /**
     * Rotates the page at [pageIndex] by 90° clockwise on [Dispatchers.Default].
     * Fixes same OOM risk as the filter path.
     */
    fun rotatePage(pageIndex: Int) {
        if (isApplyingFilter || pageIndex >= editablePages.size) return
        viewModelScope.launch {
            isApplyingFilter = true
            try {
                val p = editablePages[pageIndex]
                val rotatedRaw = withContext(Dispatchers.Default) {
                    OpenCVNativeBridge.rotateBitmap(p.rawBitmap, 90f)
                }
                val rotatedDisplay = withContext(Dispatchers.Default) {
                    OpenCVNativeBridge.applyFilter(rotatedRaw, p.activeFilter)
                }
                if (p.displayBitmap !== p.rawBitmap) p.displayBitmap.recycle()
                editablePages[pageIndex] = p.copy(rawBitmap = rotatedRaw, displayBitmap = rotatedDisplay)
            } finally {
                isApplyingFilter = false
            }
        }
    }

    /**
     * Updates a page after crop or redaction (called from sub-screens).
     */
    fun updatePage(pageIndex: Int, updatedPage: EditablePage) {
        if (pageIndex < editablePages.size) {
            editablePages[pageIndex] = updatedPage
        }
    }

    /**
     * Removes page at [pageIndex].
     */
    fun removePage(pageIndex: Int) {
        if (pageIndex < editablePages.size) {
            editablePages.removeAt(pageIndex)
        }
    }

    /**
     * Appends additional pages to the current editing session.
     */
    fun appendPages(newPages: List<EditablePage>) {
        editablePages.addAll(newPages)
    }

    // ── High-Speed Capture queue ──────────────────────────────────────────────

    /** Bitmaps captured during HighSpeedScanScreen — not yet processed */
    val capturedRawQueue = mutableStateListOf<Bitmap>()

    /**
     * Called by HighSpeedScanScreen for every auto/manual captured page.
     * Keeps the raw bitmap in queue until the user taps "Done".
     */
    fun appendRawCapture(bitmap: Bitmap) {
        capturedRawQueue.add(bitmap)
    }

    /**
     * Converts the captured bitmap queue into a full editing session.
     * Called when the user taps "Done" in HighSpeedScanScreen.
     * Bitmaps are ingested directly (no URI decoding needed — already in memory).
     */
    fun beginEditPhaseFromQueue() {
        if (capturedRawQueue.isEmpty()) return
        viewModelScope.launch {
            isProcessing = true
            progressMessage = "Preparing pages..."
            progressStep = 0
            totalProgressSteps = capturedRawQueue.size

            try {
                val sessionDir = File(app.cacheDir, "session_${UUID.randomUUID()}").apply { mkdirs() }
                currentSessionStorage = EphemeralSessionStorage(sessionDir)
                sessionRawBitmaps.clear()
                editablePages.clear()

                // Process each captured bitmap in the background
                progressMessage = "Applying filter to ${capturedRawQueue.size} pages..."
                val newPages = withContext(Dispatchers.Default) {
                    capturedRawQueue.mapIndexed { idx, bitmap ->
                        progressStep = idx + 1
                        val filtered = OpenCVNativeBridge.applyFilter(bitmap, FilterMode.ILLUMINATION_DIVISION)
                        sessionRawBitmaps.add(bitmap)
                        // Encrypt + cache to ephemeral session
                        val byteStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteStream)
                        currentSessionStorage?.writeEncryptedPage(idx, byteStream.toByteArray())
                        EditablePage(
                            id = "page_$idx",
                            rawBitmap = bitmap,
                            displayBitmap = filtered,
                            activeFilter = FilterMode.ILLUMINATION_DIVISION
                        )
                    }
                }

                editablePages.addAll(newPages)
                capturedRawQueue.clear()

                val timeStamp = SimpleDateFormat("ddMMM_HHmm", Locale.getDefault()).format(Date())
                defaultSessionTitle = "HighSpeed_$timeStamp"
                savedState[KEY_SESSION_TITLE] = defaultSessionTitle
                isEditingSession = true
                savedState[KEY_IS_EDITING] = true

                // Background OCR + Aadhaar detection
                viewModelScope.launch {
                    withContext(Dispatchers.Default) {
                        for (i in newPages.indices) {
                            if (i >= editablePages.size) break
                            val page = editablePages[i]
                            try {
                                val ocr = OcrManager.recognizeText(page.displayBitmap)
                                val eval = AadhaarMaskingEngine.evaluateAndMask(page.displayBitmap, ocr)
                                editablePages[i] = page.copy(
                                    isAadhaarDetected = eval.hasAadhaar,
                                    isAutoMasked = eval.isAutoMasked,
                                    requiresManualReview = eval.requiresManualReview,
                                    aadhaarSnippet = eval.rawMatchedText,
                                    isMasked = eval.isAutoMasked || page.isMasked
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }
            } finally {
                isProcessing = false
            }
        }
    }

    // ── Session Lifecycle ─────────────────────────────────────────────────────

    /**
     * Ingests scanned or picked URIs into the interactive editing studio session.
     * After loading, immediately runs OCR + Aadhaar detection in the background
     * and populates [editablePages].
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
                    sessionRawBitmaps.clear()
                    editablePages.clear()
                }

                val loadedBitmaps = withContext(Dispatchers.IO) {
                    uris.mapIndexedNotNull { index, uri ->
                        progressStep = index + 1
                        app.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }?.copy(Bitmap.Config.ARGB_8888, true)?.also { bmp ->
                            val byteStream = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 90, byteStream)
                            currentSessionStorage?.writeEncryptedPage(
                                sessionRawBitmaps.size + index,
                                byteStream.toByteArray()
                            )
                        }
                    }
                }

                sessionRawBitmaps.addAll(loadedBitmaps)

                // Build EditablePages with the default filter applied (background)
                progressMessage = "Applying initial filter..."
                val newPages = withContext(Dispatchers.Default) {
                    loadedBitmaps.mapIndexed { idx, bitmap ->
                        val offset = if (isAppend) editablePages.size else 0
                        val filtered = OpenCVNativeBridge.applyFilter(bitmap, FilterMode.ILLUMINATION_DIVISION)
                        EditablePage(
                            id = "page_${offset + idx}",
                            rawBitmap = bitmap,
                            displayBitmap = filtered,
                            activeFilter = FilterMode.ILLUMINATION_DIVISION
                        )
                    }
                }

                if (isAppend) {
                    editablePages.addAll(newPages)
                } else {
                    editablePages.addAll(newPages)
                    val timeStamp = SimpleDateFormat("ddMMM_HHmm", Locale.getDefault()).format(Date())
                    defaultSessionTitle = "Scan_$timeStamp"
                    savedState[KEY_SESSION_TITLE] = defaultSessionTitle
                    isEditingSession = true
                    savedState[KEY_IS_EDITING] = true
                }

                // Background OCR + Aadhaar detection (non-blocking)
                viewModelScope.launch {
                    val startOffset = if (isAppend) editablePages.size - newPages.size else 0
                    withContext(Dispatchers.Default) {
                        for (i in newPages.indices) {
                            val idx = startOffset + i
                            if (idx >= editablePages.size) break
                            val page = editablePages[idx]
                            try {
                                val ocr = OcrManager.recognizeText(page.displayBitmap)
                                val eval = AadhaarMaskingEngine.evaluateAndMask(page.displayBitmap, ocr)
                                editablePages[idx] = page.copy(
                                    isAadhaarDetected = eval.hasAadhaar,
                                    isAutoMasked = eval.isAutoMasked,
                                    requiresManualReview = eval.requiresManualReview,
                                    aadhaarSnippet = eval.rawMatchedText,
                                    isMasked = eval.isAutoMasked || page.isMasked
                                )
                            } catch (_: Exception) {}
                        }
                    }
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
    fun saveEditedSession(docTitle: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            isProcessing = true
            totalProgressSteps = editablePages.size
            progressStep = 0

            try {
                val docId = UUID.randomUUID().toString()
                val docDir = File(app.filesDir, "documents/$docId").apply { mkdirs() }

                val pdfInputs = mutableListOf<PdfPageInput>()
                val pageEntities = mutableListOf<PageEntity>()
                var hasMaskedAadhaar = false

                for ((index, page) in editablePages.withIndex()) {
                    progressStep = index + 1
                    progressMessage = "Enhancing & OCR Page ${index + 1} of ${editablePages.size}..."

                    currentThermalTier = thermalMonitor.getCurrentThermalTier()
                    when (currentThermalTier) {
                        ThermalTier.SEVERE -> delay(150)
                        ThermalTier.MODERATE -> delay(50)
                        else -> Unit
                    }

                    val finalBitmap = page.displayBitmap

                    val ocrResult = withContext(Dispatchers.Default) {
                        try { OcrManager.recognizeText(finalBitmap) } catch (_: Exception) { null }
                    }

                    var maskedPlaceholder: String? = null
                    if (ocrResult != null) {
                        val aadhaarEval = AadhaarMaskingEngine.evaluateAndMask(finalBitmap, ocrResult)
                        if (aadhaarEval.isAutoMasked || page.isMasked) {
                            hasMaskedAadhaar = true
                            maskedPlaceholder = aadhaarEval.maskedText
                        }
                    }

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

                // Ephemeral Crypto-Shredding
                currentSessionStorage?.cryptoShred()
                currentSessionStorage = null

                clearSession()
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
        clearSession()
    }

    private fun clearSession() {
        editablePages.clear()
        sessionRawBitmaps.clear()
        isEditingSession = false
        defaultSessionTitle = ""
        savedState[KEY_IS_EDITING] = false
        savedState[KEY_SESSION_TITLE] = ""
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
        object : androidx.lifecycle.AbstractSavedStateViewModelFactory(this, null) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle
            ): T = MainViewModel(application as AbhilekhApplication, handle) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scannerOptions = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
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
                    // Screen state — drives navigation
                    var currentScreen by remember {
                        mutableStateOf<AppScreen>(
                            if (viewModel.isEditingSession && viewModel.editablePages.isNotEmpty())
                                AppScreen.DocumentEditor
                            else AppScreen.Home
                        )
                    }

                    when (currentScreen) {
                        // ── Document Editor ───────────────────────────────────
                        AppScreen.DocumentEditor -> {
                            DocumentEditorScreen(
                                pages = viewModel.editablePages,
                                initialTitle = viewModel.defaultSessionTitle,
                                isApplyingFilter = viewModel.isApplyingFilter,
                                onApplyFilter = { pageIndex, filter, applyToAll ->
                                    viewModel.applyFilter(pageIndex, filter, applyToAll)
                                },
                                onRotatePage = { pageIndex ->
                                    viewModel.rotatePage(pageIndex)
                                },
                                onUpdatePage = { pageIndex, updatedPage ->
                                    viewModel.updatePage(pageIndex, updatedPage)
                                },
                                onRemovePage = { pageIndex ->
                                    viewModel.removePage(pageIndex)
                                },
                                onSavePdf = { title ->
                                    viewModel.saveEditedSession(title) {
                                        Toast.makeText(this@MainActivity, "PDF Saved & Indexed!", Toast.LENGTH_SHORT).show()
                                        currentScreen = AppScreen.Home
                                    }
                                },
                                onAddMorePages = {
                                    isAppendingPages = true
                                    launchScanner(scannerClient)
                                },
                                onCancel = {
                                    viewModel.cancelEditingSession()
                                    currentScreen = AppScreen.Home
                                }
                            )
                        }

                        // ── High-Speed Capture ────────────────────────────────
                        AppScreen.HighSpeedCapture -> {
                            com.abhilekh.app.ui.screens.HighSpeedScanScreen(
                                onCapture = { bmp -> viewModel.appendRawCapture(bmp) },
                                onFinish = {
                                    viewModel.beginEditPhaseFromQueue()
                                    currentScreen = AppScreen.DocumentEditor
                                },
                                onBack = { currentScreen = AppScreen.Home }
                            )
                        }

                        // ── Home ──────────────────────────────────────────────
                        AppScreen.Home -> {
                            // Sync: if ViewModel restores an editing session on process restart, jump to editor
                            LaunchedEffect(viewModel.isEditingSession, viewModel.editablePages.size) {
                                if (viewModel.isEditingSession && viewModel.editablePages.isNotEmpty()) {
                                    currentScreen = AppScreen.DocumentEditor
                                }
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                                HomeScreen(
                                    documents = docs,
                                    onLaunchScanner = {
                                        isAppendingPages = false
                                        launchScanner(scannerClient)
                                    },
                                    onHighSpeedScan = {
                                        currentScreen = AppScreen.HighSpeedCapture
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

