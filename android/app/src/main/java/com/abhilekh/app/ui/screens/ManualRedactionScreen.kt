package com.abhilekh.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.abhilekh.app.core.cv.OpenCVNativeBridge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRedactionScreen(
    bitmap: Bitmap,
    onComplete: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val currentBitmap = remember { bitmap.copy(Bitmap.Config.ARGB_8888, true) }
    val redactions = remember { mutableStateListOf<Rect>() }
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var currentOffset by remember { mutableStateOf<Offset?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manual Redaction Brush") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (redactions.isNotEmpty()) {
                        IconButton(onClick = { redactions.removeLastOrNull() }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                    }
                    IconButton(onClick = {
                        // Apply all user redactions to bitmap
                        for (rect in redactions) {
                            OpenCVNativeBridge.maskRect(currentBitmap, rect)
                        }
                        onComplete(currentBitmap)
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Done")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                startOffset = offset
                                currentOffset = offset
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentOffset = change.position
                            },
                            onDragEnd = {
                                val s = startOffset
                                val e = currentOffset
                                if (s != null && e != null) {
                                    val left = minOf(s.x, e.x).toInt()
                                    val top = minOf(s.y, e.y).toInt()
                                    val right = maxOf(s.x, e.x).toInt()
                                    val bottom = maxOf(s.y, e.y).toInt()
                                    if (right - left > 10 && bottom - top > 10) {
                                        redactions.add(Rect(left, top, right, bottom))
                                    }
                                }
                                startOffset = null
                                currentOffset = null
                            }
                        )
                    }
            ) {
                // Draw base bitmap
                drawImage(currentBitmap.asImageBitmap())

                // Draw existing black redaction boxes
                for (rect in redactions) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                        size = Size((rect.right - rect.left).toFloat(), (rect.bottom - rect.top).toFloat())
                    )
                }

                // Draw in-progress swipe rectangle
                val s = startOffset
                val e = currentOffset
                if (s != null && e != null) {
                    val left = minOf(s.x, e.x)
                    val top = minOf(s.y, e.y)
                    val width = kotlin.math.abs(e.x - s.x)
                    val height = kotlin.math.abs(e.y - s.y)
                    drawRect(
                        color = Color.Black.copy(alpha = 0.7f),
                        topLeft = Offset(left, top),
                        size = Size(width, height)
                    )
                }
            }
        }
    }
}
