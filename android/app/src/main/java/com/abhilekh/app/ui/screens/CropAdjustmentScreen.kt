package com.abhilekh.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abhilekh.app.core.cv.OpenCVNativeBridge
import com.abhilekh.app.ui.theme.RoyalBlue
import com.abhilekh.app.ui.theme.Slate900
import kotlin.math.roundToInt

private enum class DragCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, NONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropAdjustmentScreen(
    rawBitmap: Bitmap,
    initialCrop: Rect? = null,
    onComplete: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var workingBitmap by remember { mutableStateOf(rawBitmap) }

    // Normalized coordinates (0.0f .. 1.0f)
    var topLeft by remember {
        mutableStateOf(
            if (initialCrop != null) {
                Offset(initialCrop.left.toFloat() / rawBitmap.width, initialCrop.top.toFloat() / rawBitmap.height)
            } else {
                Offset(0.05f, 0.05f)
            }
        )
    }
    var topRight by remember {
        mutableStateOf(
            if (initialCrop != null) {
                Offset(initialCrop.right.toFloat() / rawBitmap.width, initialCrop.top.toFloat() / rawBitmap.height)
            } else {
                Offset(0.95f, 0.05f)
            }
        )
    }
    var bottomLeft by remember {
        mutableStateOf(
            if (initialCrop != null) {
                Offset(initialCrop.left.toFloat() / rawBitmap.width, initialCrop.bottom.toFloat() / rawBitmap.height)
            } else {
                Offset(0.05f, 0.95f)
            }
        )
    }
    var bottomRight by remember {
        mutableStateOf(
            if (initialCrop != null) {
                Offset(initialCrop.right.toFloat() / rawBitmap.width, initialCrop.bottom.toFloat() / rawBitmap.height)
            } else {
                Offset(0.95f, 0.95f)
            }
        )
    }

    var activeCorner by remember { mutableStateOf(DragCorner.NONE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adjust Crop & Borders", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Reset to default auto borders
                        topLeft = Offset(0.05f, 0.05f)
                        topRight = Offset(0.95f, 0.05f)
                        bottomLeft = Offset(0.05f, 0.95f)
                        bottomRight = Offset(0.95f, 0.95f)
                    }) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Auto Detect", tint = RoyalBlue)
                    }
                    IconButton(onClick = {
                        workingBitmap = OpenCVNativeBridge.rotateBitmap(workingBitmap, 90f)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90")
                    }
                    IconButton(onClick = {
                        val minX = minOf(topLeft.x, bottomLeft.x).coerceIn(0f, 1f)
                        val maxX = maxOf(topRight.x, bottomRight.x).coerceIn(0f, 1f)
                        val minY = minOf(topLeft.y, topRight.y).coerceIn(0f, 1f)
                        val maxY = maxOf(bottomLeft.y, bottomRight.y).coerceIn(0f, 1f)

                        val pixelRect = Rect(
                            (minX * workingBitmap.width).roundToInt(),
                            (minY * workingBitmap.height).roundToInt(),
                            (maxX * workingBitmap.width).roundToInt(),
                            (maxY * workingBitmap.height).roundToInt()
                        )
                        val cropped = OpenCVNativeBridge.cropBitmap(workingBitmap, pixelRect)
                        onComplete(cropped)
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Apply Crop", tint = RoyalBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Slate900
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val containerWidth = constraints.maxWidth.toFloat()
                val containerHeight = constraints.maxHeight.toFloat()

                val imgAspect = workingBitmap.width.toFloat() / workingBitmap.height.toFloat()
                val viewAspect = containerWidth / containerHeight

                val displayedWidth: Float
                val displayedHeight: Float

                if (imgAspect > viewAspect) {
                    displayedWidth = containerWidth
                    displayedHeight = containerWidth / imgAspect
                } else {
                    displayedHeight = containerHeight
                    displayedWidth = containerHeight * imgAspect
                }

                val leftOffset = (containerWidth - displayedWidth) / 2f
                val topOffset = (containerHeight - displayedHeight) / 2f

                val imageBitmap = remember(workingBitmap) { workingBitmap.asImageBitmap() }

                val density = androidx.compose.ui.platform.LocalDensity.current

                Canvas(
                    modifier = Modifier
                        .size(
                            width = with(density) { displayedWidth.toDp() },
                            height = with(density) { displayedHeight.toDp() }
                        )
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val normX = offset.x / displayedWidth
                                    val normY = offset.y / displayedHeight
                                    val clickOffset = Offset(normX, normY)

                                    val radius = 0.12f // touch slop radius in normalized space
                                    activeCorner = when {
                                        (clickOffset - topLeft).getDistance() < radius -> DragCorner.TOP_LEFT
                                        (clickOffset - topRight).getDistance() < radius -> DragCorner.TOP_RIGHT
                                        (clickOffset - bottomLeft).getDistance() < radius -> DragCorner.BOTTOM_LEFT
                                        (clickOffset - bottomRight).getDistance() < radius -> DragCorner.BOTTOM_RIGHT
                                        else -> DragCorner.NONE
                                    }
                                },
                                onDragEnd = {
                                    activeCorner = DragCorner.NONE
                                },
                                onDragCancel = {
                                    activeCorner = DragCorner.NONE
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dx = dragAmount.x / displayedWidth
                                    val dy = dragAmount.y / displayedHeight

                                    when (activeCorner) {
                                        DragCorner.TOP_LEFT -> {
                                            topLeft = Offset(
                                                (topLeft.x + dx).coerceIn(0f, topRight.x - 0.1f),
                                                (topLeft.y + dy).coerceIn(0f, bottomLeft.y - 0.1f)
                                            )
                                        }
                                        DragCorner.TOP_RIGHT -> {
                                            topRight = Offset(
                                                (topRight.x + dx).coerceIn(topLeft.x + 0.1f, 1f),
                                                (topRight.y + dy).coerceIn(0f, bottomRight.y - 0.1f)
                                            )
                                        }
                                        DragCorner.BOTTOM_LEFT -> {
                                            bottomLeft = Offset(
                                                (bottomLeft.x + dx).coerceIn(0f, bottomRight.x - 0.1f),
                                                (bottomLeft.y + dy).coerceIn(topLeft.y + 0.1f, 1f)
                                            )
                                        }
                                        DragCorner.BOTTOM_RIGHT -> {
                                            bottomRight = Offset(
                                                (bottomRight.x + dx).coerceIn(bottomLeft.x + 0.1f, 1f),
                                                (bottomRight.y + dy).coerceIn(topRight.y + 0.1f, 1f)
                                            )
                                        }
                                        DragCorner.NONE -> {}
                                    }
                                }
                            )
                        }
                ) {
                    // 1. Draw source image
                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(displayedWidth.roundToInt(), displayedHeight.roundToInt())
                    )

                    // 2. Dim outside area
                    val path = Path().apply {
                        moveTo(topLeft.x * displayedWidth, topLeft.y * displayedHeight)
                        lineTo(topRight.x * displayedWidth, topRight.y * displayedHeight)
                        lineTo(bottomRight.x * displayedWidth, bottomRight.y * displayedHeight)
                        lineTo(bottomLeft.x * displayedWidth, bottomLeft.y * displayedHeight)
                        close()
                    }

                    // 3. Draw crop quadrilateral
                    drawPath(
                        path = path,
                        color = RoyalBlue,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // 4. Draw corner handles
                    val handleRadius = 14.dp.toPx()
                    val corners = listOf(
                        topLeft to (activeCorner == DragCorner.TOP_LEFT),
                        topRight to (activeCorner == DragCorner.TOP_RIGHT),
                        bottomLeft to (activeCorner == DragCorner.BOTTOM_LEFT),
                        bottomRight to (activeCorner == DragCorner.BOTTOM_RIGHT)
                    )

                    for ((corner, isActive) in corners) {
                        val center = Offset(corner.x * displayedWidth, corner.y * displayedHeight)
                        // Outer ring
                        drawCircle(
                            color = Color.White,
                            radius = handleRadius + 2.dp.toPx(),
                            center = center
                        )
                        // Inner fill
                        drawCircle(
                            color = if (isActive) Color(0xFF2563EB) else Color(0xFF1D4ED8),
                            radius = handleRadius,
                            center = center
                        )
                    }
                }
            }
        }
    }
}
