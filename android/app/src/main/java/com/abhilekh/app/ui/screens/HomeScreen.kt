package com.abhilekh.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.abhilekh.app.data.db.DocumentEntity
import com.abhilekh.app.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    documents: List<DocumentEntity>,
    onLaunchScanner: () -> Unit,
    onHighSpeedScan: () -> Unit,
    onImportPhotos: () -> Unit,
    onCombineFiles: () -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val filteredDocs = documents.filter {
        it.title.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Abhilekh (अभिलेख)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Sovereign Document Scanner",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCombineFiles) {
                        Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = "Combine Files", tint = RoyalBlue)
                    }
                    IconButton(onClick = { /* Settings / DPDP */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            SpeedDialFab(
                onStandardScan = onLaunchScanner,
                onHighSpeedScan = onHighSpeedScan,
                onImportPhotos = onImportPhotos,
                onCombineFiles = onCombineFiles
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by title or OCR text...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "ID Cards", "GST Bills", "Notes").forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onImportPhotos) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Import from Photos", tint = Slate700)
                }
            }

            // Documents List
            if (filteredDocs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Slate500
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No documents scanned yet",
                            color = Slate500,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onLaunchScanner) {
                            Text("Start Scanning")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDocs, key = { it.id }) { doc ->
                        DocumentCard(
                            document = doc,
                            onShare = { shareDocument(context, doc.pdfPath, "Share ${doc.title}") },
                            onClick = { openDocument(context, doc.pdfPath) },
                            onDelete = { onDeleteDocument(doc) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentCard(
    document: DocumentEntity,
    onShare: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(document.updatedAt))
    val sizeKb = (document.fileSizeBytes / 1024).coerceAtLeast(1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Icon Box
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate100),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = RoyalBlue,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Document Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${document.pageCount} pages • ${sizeKb} KB • $dateStr",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }
                if (document.hasMaskedAadhaar) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(EmeraldLight)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Masked",
                            tint = EmeraldTrust,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Aadhaar Masked",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldTrust
                        )
                    }
                }
            }

            // Universal Share Action
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    tint = RoyalBlue
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Slate500
                )
            }
        }
    }
}

fun shareDocument(context: Context, pdfPath: String, chooserTitle: String = "Share Document") {
    val file = File(pdfPath)
    if (!file.exists()) return

    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun openDocument(context: Context, pdfPath: String) {
    val file = File(pdfPath)
    if (!file.exists()) return

    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(Intent.createChooser(viewIntent, "Open PDF"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ─── Speed Dial FAB ───────────────────────────────────────────────────────────

@Composable
fun SpeedDialFab(
    onStandardScan: () -> Unit,
    onHighSpeedScan: () -> Unit,
    onImportPhotos: () -> Unit,
    onCombineFiles: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(250),
        label = "fab_rotation"
    )

    Column(horizontalAlignment = Alignment.End) {
        // Speed-dial items (visible when expanded)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 }
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SpeedDialItem(
                    label = "Combine files",
                    icon = Icons.AutoMirrored.Filled.MergeType,
                    onClick = { expanded = false; onCombineFiles() }
                )
                SpeedDialItem(
                    label = "Import from photos",
                    icon = Icons.Default.AddPhotoAlternate,
                    onClick = { expanded = false; onImportPhotos() }
                )
                SpeedDialItem(
                    label = "High-speed scan",
                    icon = Icons.Default.Speed,
                    onClick = { expanded = false; onHighSpeedScan() },
                    highlight = true
                )
                SpeedDialItem(
                    label = "Standard scan",
                    icon = Icons.Default.CameraAlt,
                    onClick = { expanded = false; onStandardScan() }
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        // Primary FAB (+ → ×)
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = RoyalBlue,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (expanded) "Close" else "Scan actions",
                modifier = Modifier.rotate(rotation)
            )
        }
    }

    // Dismiss overlay when expanded
    if (expanded) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(onClick = { expanded = false })
        )
    }
}

@Composable
private fun SpeedDialItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    highlight: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        // Label chip
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (highlight) RoyalBlue.copy(alpha = 0.95f) else Color(0xFF1A1A2E).copy(alpha = 0.92f),
            shadowElevation = 4.dp
        ) {
            Text(
                text = label,
                color = Color.White,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        // Mini FAB
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = if (highlight) RoyalBlue else Color(0xFF2C2C3E),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}
