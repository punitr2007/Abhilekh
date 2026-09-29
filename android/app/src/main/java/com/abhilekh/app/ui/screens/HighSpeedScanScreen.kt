package com.abhilekh.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "HighSpeedScan"
/** ms the document must remain stable before auto-capture fires */
private const val STABLE_MS = 600L
/** Normalised brightness-variance threshold → document is in frame */
private const val MIN_CONFIDENCE = 0.45f

/**
 * Pixel-variance heuristic: a document in frame produces high local contrast.
 * We down-scale the frame to a tiny thumbnail then measure luminance variance.
 */
private fun computeDocumentConfidence(bitmap: Bitmap): Float {
    val small = Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * 0.12f).toInt().coerceAtLeast(1),
        (bitmap.height * 0.12f).toInt().coerceAtLeast(1),
        false
    )
    val pixels = IntArray(small.width * small.height)
    small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
    small.recycle()
    val mean = pixels.map { p ->
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        0.299 * r + 0.587 * g + 0.114 * b
    }.average()
    val variance = pixels.map { p ->
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val d = (0.299 * r + 0.587 * g + 0.114 * b) - mean; d * d
    }.average()
    return (variance / 6000.0).toFloat().coerceIn(0f, 1f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighSpeedScanScreen(
    /** Called for each captured page bitmap — caller appends to ViewModel queue */
    onCapture: (Bitmap) -> Unit,
    /** Called when user taps "Done" — navigates to DocumentEditorScreen */
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // ── Camera permission ─────────────────────────────────────────────────────
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA) }

    // ── Local capture state ───────────────────────────────────────────────────
    var isPaused by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    val capturedThumbs = remember { mutableStateListOf<Bitmap>() }
    var confidence by remember { mutableStateOf(0f) }
    var stableStartMs by remember { mutableStateOf(0L) }
    var lastCaptureMs by remember { mutableStateOf(0L) }
    var showCoachmark by remember { mutableStateOf(true) }
    var coachStep by remember { mutableIntStateOf(1) }
    val stripState = rememberLazyListState()

    // ── CameraX use-cases ─────────────────────────────────────────────────────
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    /** Fires shutter — debounced for auto, immediate for manual */
    fun doCapture(manual: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!manual && now - lastCaptureMs < 1200L) return
        lastCaptureMs = now
        stableStartMs = 0L

        imageCapture.takePicture(cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(proxy: ImageProxy) {
                    val buf = proxy.planes[0].buffer
                    val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                    proxy.close()
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?.copy(Bitmap.Config.ARGB_8888, true) ?: return
                    scope.launch {
                        onCapture(bmp)
                        capturedThumbs.add(bmp)
                        vibrate(context)
                        delay(60)
                        if (capturedThumbs.isNotEmpty())
                            stripState.animateScrollToItem(capturedThumbs.lastIndex)
                    }
                }
                override fun onError(e: ImageCaptureException) { Log.e(TAG, "Capture error: $e") }
            })
    }

    // ── Image analyser (auto-detect document edge → auto-capture) ────────────
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
    }

    DisposableEffect(isPaused) {
        if (!isPaused) {
            imageAnalysis.setAnalyzer(cameraExecutor) { proxy ->
                val bmp = proxy.toBitmap()
                val score = computeDocumentConfidence(bmp)
                bmp.recycle()
                proxy.close()
                scope.launch {
                    confidence = score
                    val now = System.currentTimeMillis()
                    if (score >= MIN_CONFIDENCE) {
                        if (stableStartMs == 0L) stableStartMs = now
                        else if (now - stableStartMs >= STABLE_MS) doCapture()
                    } else stableStartMs = 0L
                }
            }
        } else {
            imageAnalysis.clearAnalyzer()
        }
        onDispose { imageAnalysis.clearAnalyzer() }
    }

    // ── Flash ─────────────────────────────────────────────────────────────────
    LaunchedEffect(flashEnabled) { cameraControl?.enableTorch(flashEnabled) }

    // ── Cleanup on exit ───────────────────────────────────────────────────────
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────
    val ringColor = if (confidence >= MIN_CONFIDENCE) Color(0xFF00C853) else Color.White.copy(0.6f)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!hasPermission) {
            // Permission required state
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Camera permission required", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant Permission") }
                }
            }
        } else {

            // ── Camera preview ──────────────────────────────────────────────
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    ProcessCameraProvider.getInstance(ctx).addListener({
                        val provider = ProcessCameraProvider.getInstance(ctx).get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        try {
                            provider.unbindAll()
                            val cam = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview, imageCapture, imageAnalysis
                            )
                            cameraControl = cam.cameraControl
                        } catch (e: Exception) { Log.e(TAG, "Bind failed: $e") }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    "High-Speed Scan",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { flashEnabled = !flashEnabled }) {
                    Icon(
                        if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        "Flash",
                        tint = if (flashEnabled) Color.Yellow else Color.White
                    )
                }
            }

            // ── Animated page counter badge ──────────────────────────────────
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
            ) {
                AnimatedContent(
                    targetState = capturedThumbs.size,
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    },
                    label = "counter"
                ) { n ->
                    if (n > 0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1565C0).copy(alpha = 0.88f)
                        ) {
                            Text(
                                "$n page${if (n == 1) "" else "s"} captured",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // ── Bottom strip + controls ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Thumbnail strip
                if (capturedThumbs.isNotEmpty()) {
                    LazyRow(
                        state = stripState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(capturedThumbs.toList()) { bmp ->
                            Image(
                                painter = BitmapPainter(bmp.asImageBitmap()),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(54.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, Color.White.copy(0.4f), RoundedCornerShape(6.dp))
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Pause | Shutter | Done
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pause / Resume toggle
                    IconButton(
                        onClick = { isPaused = !isPaused },
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.White.copy(0.15f), CircleShape)
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Manual shutter with confidence ring
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(4.dp, ringColor, CircleShape)
                            .padding(7.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.9f))
                            .clickable { doCapture(manual = true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, "Capture", tint = Color(0xFF1565C0), modifier = Modifier.size(30.dp))
                    }

                    // Done button
                    Button(
                        onClick = { if (capturedThumbs.isNotEmpty()) onFinish() },
                        enabled = capturedThumbs.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1565C0),
                            disabledContainerColor = Color.White.copy(0.18f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .widthIn(min = 72.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (capturedThumbs.isNotEmpty())
                                Text("${capturedThumbs.size}p", color = Color.White.copy(0.75f), fontSize = 11.sp)
                        }
                    }
                }
            }

            // ── Paused overlay ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = isPaused,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Pause, null, tint = Color.White, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Auto-capture paused", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap ▶ to resume", color = Color.White.copy(0.7f), fontSize = 14.sp)
                    }
                }
            }

            // ── Onboarding coachmark (one-time, 3 steps) ─────────────────────
            if (showCoachmark) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.72f))
                        .padding(24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, null, tint = Color(0xFF4A90D9), modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("High-speed scan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = when (coachStep) {
                                    1 -> "Point the camera at a page. It captures automatically when the page is stable for 0.6 seconds."
                                    2 -> "Flip to the next page immediately. You can pause auto-capture at any time with the ⏸ button."
                                    else -> "Tap Done when finished — all pages will be loaded for editing at once."
                                },
                                color = Color.White.copy(0.85f), fontSize = 14.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$coachStep of 3", color = Color.White.copy(0.45f), fontSize = 12.sp)
                                Row {
                                    TextButton(onClick = { showCoachmark = false }) {
                                        Text("Skip", color = Color.White.copy(0.55f))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Button(onClick = { if (coachStep < 3) coachStep++ else showCoachmark = false }) {
                                        Text(if (coachStep < 3) "Next" else "Got it")
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

// ─── Haptic feedback helper ───────────────────────────────────────────────────
private fun vibrate(context: Context) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)
                ?.defaultVibrator
                ?.vibrate(VibrationEffect.createOneShot(38, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(38)
        }
    } catch (_: Exception) {}
}
