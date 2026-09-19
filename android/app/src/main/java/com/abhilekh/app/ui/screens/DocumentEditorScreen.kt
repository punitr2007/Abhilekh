package com.abhilekh.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abhilekh.app.core.cv.FilterMode
import com.abhilekh.app.core.cv.OpenCVNativeBridge
import com.abhilekh.app.ui.theme.*
import kotlinx.coroutines.launch

data class EditablePage(
    val id: String,
    val rawBitmap: Bitmap,
    var displayBitmap: Bitmap,
    var activeFilter: FilterMode = FilterMode.ILLUMINATION_DIVISION,
    var isAadhaarDetected: Boolean = false,
    var isMasked: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    initialPages: List<Bitmap>,
    initialTitle: String,
    onSavePdf: (title: String, pages: List<EditablePage>) -> Unit,
    onAddMorePages: () -> Unit,
    onCancel: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var documentTitle by remember { mutableStateOf(initialTitle) }
    var isRenaming by remember { mutableStateOf(false) }

    // Initialize editable pages with default Magic Clean filter applied
    val pages = remember {
        mutableStateListOf<EditablePage>().apply {
            addAll(
                initialPages.mapIndexed { index, bitmap ->
                    val filtered = OpenCVNativeBridge.applyFilter(bitmap, FilterMode.ILLUMINATION_DIVISION)
                    EditablePage(
                        id = "page_$index",
                        rawBitmap = bitmap,
                        displayBitmap = filtered,
                        activeFilter = FilterMode.ILLUMINATION_DIVISION
                    )
                }
            )
        }
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val currentPageIndex = pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

    // Active sub-screens
    var croppingPageIndex by remember { mutableStateOf<Int?>(null) }
    var redactingPageIndex by remember { mutableStateOf<Int?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var applyFilterToAll by remember { mutableStateOf(false) }

    // Sub-Screen: Interactive Crop & Border Adjustment
    if (croppingPageIndex != null && croppingPageIndex!! < pages.size) {
        val targetIndex = croppingPageIndex!!
        val page = pages[targetIndex]
        CropAdjustmentScreen(
            rawBitmap = page.rawBitmap,
            onComplete = { croppedBitmap ->
                val newFiltered = OpenCVNativeBridge.applyFilter(croppedBitmap, page.activeFilter)
                pages[targetIndex] = page.copy(
                    rawBitmap = croppedBitmap,
                    displayBitmap = newFiltered
                )
                croppingPageIndex = null
            },
            onCancel = { croppingPageIndex = null }
        )
        return
    }

    // Sub-Screen: Manual Touch Redaction
    if (redactingPageIndex != null && redactingPageIndex!! < pages.size) {
        val targetIndex = redactingPageIndex!!
        val page = pages[targetIndex]
        ManualRedactionScreen(
            bitmap = page.displayBitmap,
            onComplete = { redactedBitmap ->
                pages[targetIndex] = page.copy(
                    displayBitmap = redactedBitmap,
                    isMasked = true
                )
                redactingPageIndex = null
            },
            onCancel = { redactingPageIndex = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isRenaming = true }
                    ) {
                        Text(
                            text = documentTitle,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = Slate500,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Discard Scan")
                    }
                },
                actions = {
                    Button(
                        onClick = { onSavePdf(documentTitle, pages.toList()) },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save PDF", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // ─── Thumbnails Strip ──────────────────────────────────────
                    if (pages.size > 1) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(pages) { index, item ->
                                val isSelected = index == currentPageIndex
                                Box(
                                    modifier = Modifier
                                        .size(54.dp, 72.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) RoyalBlue else Slate500.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                ) {
                                    Image(
                                        bitmap = item.displayBitmap.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .background(Slate900.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("${index + 1}", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }

                            item {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp, 72.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Slate100)
                                        .clickable { onAddMorePages() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Page", tint = RoyalBlue)
                                        Text("Add", fontSize = 10.sp, color = Slate700)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = Slate100)
                    }

                    // ─── Adobe Scan Style Bottom Actions Bar ───────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EditorActionItem(
                            icon = Icons.Default.Crop,
                            label = "Crop",
                            onClick = { croppingPageIndex = currentPageIndex }
                        )

                        EditorActionItem(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Filters",
                            onClick = { showFilterSheet = true }
                        )

                        EditorActionItem(
                            icon = Icons.AutoMirrored.Filled.RotateRight,
                            label = "Rotate",
                            onClick = {
                                if (currentPageIndex < pages.size) {
                                    val current = pages[currentPageIndex]
                                    val rotatedRaw = OpenCVNativeBridge.rotateBitmap(current.rawBitmap, 90f)
                                    val rotatedDisplay = OpenCVNativeBridge.applyFilter(rotatedRaw, current.activeFilter)
                                    pages[currentPageIndex] = current.copy(
                                        rawBitmap = rotatedRaw,
                                        displayBitmap = rotatedDisplay
                                    )
                                }
                            }
                        )

                        EditorActionItem(
                            icon = Icons.Default.Shield,
                            label = "Redact",
                            onClick = { redactingPageIndex = currentPageIndex }
                        )

                        if (pages.size > 1) {
                            EditorActionItem(
                                icon = Icons.Default.Delete,
                                label = "Delete",
                                tint = Color(0xFFDC2626),
                                onClick = {
                                    if (currentPageIndex < pages.size) {
                                        pages.removeAt(currentPageIndex)
                                    }
                                }
                            )
                        }

                        EditorActionItem(
                            icon = Icons.Default.AddAPhoto,
                            label = "Add",
                            onClick = onAddMorePages
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Slate900),
            contentAlignment = Alignment.Center
        ) {
            if (pages.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIdx ->
                    val page = pages[pageIdx]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black)
                        ) {
                            Image(
                                bitmap = page.displayBitmap.asImageBitmap(),
                                contentDescription = "Page ${pageIdx + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Page Counter Badge
                        Surface(
                            shape = CircleShape,
                            color = Slate900.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                        ) {
                            Text(
                                text = "${pageIdx + 1} of ${pages.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }

                        // Filter Tag
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = RoyalBlue.copy(alpha = 0.9f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = page.activeFilter.displayName,
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // ─── Rename Document Dialog ───────────────────────────────────────────────
    if (isRenaming) {
        var tempTitle by remember { mutableStateOf(documentTitle) }
        AlertDialog(
            onDismissRequest = { isRenaming = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = tempTitle,
                    onValueChange = { tempTitle = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tempTitle.isNotBlank()) documentTitle = tempTitle.trim()
                    isRenaming = false
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { isRenaming = false }) { Text("Cancel") }
            }
        )
    }

    // ─── Filter Selection Bottom Sheet ─────────────────────────────────────────
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Select Document Filter", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Apply to all pages", fontSize = 14.sp, color = Slate700)
                    Switch(
                        checked = applyFilterToAll,
                        onCheckedChange = { applyFilterToAll = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(FilterMode.values().toList()) { filter ->
                        val isSelected = if (currentPageIndex < pages.size) {
                            pages[currentPageIndex].activeFilter == filter
                        } else false

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                if (applyFilterToAll) {
                                    for (i in pages.indices) {
                                        val p = pages[i]
                                        val filtered = OpenCVNativeBridge.applyFilter(p.rawBitmap, filter)
                                        pages[i] = p.copy(
                                            displayBitmap = filtered,
                                            activeFilter = filter
                                        )
                                    }
                                } else if (currentPageIndex < pages.size) {
                                    val p = pages[currentPageIndex]
                                    val filtered = OpenCVNativeBridge.applyFilter(p.rawBitmap, filter)
                                    pages[currentPageIndex] = p.copy(
                                        displayBitmap = filtered,
                                        activeFilter = filter
                                    )
                                }
                                showFilterSheet = false
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp, 80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate100)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) RoyalBlue else Slate500.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentPageIndex < pages.size) {
                                    Image(
                                        bitmap = pages[currentPageIndex].rawBitmap.asImageBitmap(),
                                        contentDescription = filter.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = filter.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) RoyalBlue else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EditorActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}
