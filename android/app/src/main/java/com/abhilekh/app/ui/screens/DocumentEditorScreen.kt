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
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import com.abhilekh.app.ui.designsystem.AbhilekhTokens
import com.abhilekh.app.ui.designsystem.AmberWarningHUD
import com.abhilekh.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditablePage(
    val id: String,
    val rawBitmap: Bitmap,
    var displayBitmap: Bitmap,
    var activeFilter: FilterMode = FilterMode.ILLUMINATION_DIVISION,
    var isAadhaarDetected: Boolean = false,
    var isAutoMasked: Boolean = false,
    var requiresManualReview: Boolean = false,
    var aadhaarSnippet: String? = null,
    var dismissedWarning: Boolean = false,
    var isMasked: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    // ─── pages is now owned by the ViewModel (SnapshotStateList) ─────────────
    // This means edits survive recomposition, screen rotation, and nav changes.
    pages: SnapshotStateList<EditablePage>,
    initialTitle: String,
    isApplyingFilter: Boolean,
    onApplyFilter: (pageIndex: Int, filter: FilterMode, applyToAll: Boolean) -> Unit,
    onRotatePage: (pageIndex: Int) -> Unit,
    onUpdatePage: (pageIndex: Int, updatedPage: EditablePage) -> Unit,
    onRemovePage: (pageIndex: Int) -> Unit,
    onSavePdf: (title: String) -> Unit,
    onAddMorePages: () -> Unit,
    onCancel: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var documentTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var isRenaming by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var applyFilterToAll by remember { mutableStateOf(false) }

    // Active sub-screens
    var croppingPageIndex by remember { mutableStateOf<Int?>(null) }
    var redactingPageIndex by remember { mutableStateOf<Int?>(null) }

    // Sub-Screen: Interactive Crop & Border Adjustment
    if (croppingPageIndex != null && croppingPageIndex!! < pages.size) {
        val targetIndex = croppingPageIndex!!
        val page = pages[targetIndex]
        CropAdjustmentScreen(
            rawBitmap = page.rawBitmap,
            onComplete = { croppedBitmap ->
                coroutineScope.launch {
                    // Apply current filter on the newly cropped bitmap — off-thread
                    val newFiltered = withContext(Dispatchers.Default) {
                        OpenCVNativeBridge.applyFilter(croppedBitmap, page.activeFilter)
                    }
                    onUpdatePage(targetIndex, page.copy(rawBitmap = croppedBitmap, displayBitmap = newFiltered))
                    croppingPageIndex = null
                }
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
                onUpdatePage(targetIndex, page.copy(
                    displayBitmap = redactedBitmap,
                    isMasked = true,
                    requiresManualReview = false
                ))
                redactingPageIndex = null
            },
            onCancel = { redactingPageIndex = null }
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val currentPageIndex = pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

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
                        onClick = { onSavePdf(documentTitle) },
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

                    // ─── Bottom Actions Bar ────────────────────────────────────
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
                            // Disable tap while a filter is being applied
                            enabled = !isApplyingFilter,
                            onClick = { showFilterSheet = true }
                        )

                        EditorActionItem(
                            icon = Icons.AutoMirrored.Filled.RotateRight,
                            label = "Rotate",
                            enabled = !isApplyingFilter,
                            onClick = { onRotatePage(currentPageIndex) }
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
                                        onRemovePage(currentPageIndex)
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

                    // In-progress indicator while filter is being applied
                    if (isApplyingFilter) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = RoyalBlue
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

                        // Masked Badge
                        if (page.isMasked || page.isAutoMasked) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = EmeraldTrust.copy(alpha = 0.95f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = "Masked",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("MASKED", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // ─── Fail-Loud Amber Warning Alert HUD ─────────────────────────
                if (currentPageIndex < pages.size) {
                    val currentPage = pages[currentPageIndex]
                    if (currentPage.requiresManualReview && !currentPage.dismissedWarning) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        ) {
                            AmberWarningHUD(
                                isVisible = true,
                                pageNumber = currentPageIndex + 1,
                                detectedTextSnippet = currentPage.aadhaarSnippet,
                                onReviewClick = {
                                    redactingPageIndex = currentPageIndex
                                },
                                onDismiss = {
                                    onUpdatePage(
                                        currentPageIndex,
                                        currentPage.copy(dismissedWarning = true)
                                    )
                                }
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
        // Precompute scaled filter preview thumbnails — tiny (100×140) so it's fast
        // and does not cause OOM. Computed once and cached in a remember map.
        val filterPreviews = remember(currentPageIndex) {
            if (pages.isEmpty() || currentPageIndex >= pages.size) return@remember mapOf<FilterMode, Bitmap>()
            val rawBitmap = pages[currentPageIndex].rawBitmap
            val thumbW = 100
            val thumbH = 140
            val scaledRaw = Bitmap.createScaledBitmap(rawBitmap, thumbW, thumbH, true)
            FilterMode.values().associateWith { filter ->
                OpenCVNativeBridge.applyFilter(scaledRaw, filter)
            }
        }

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
                            modifier = Modifier.clickable(enabled = !isApplyingFilter) {
                                // Delegate all bitmap work to the ViewModel (off-thread)
                                onApplyFilter(currentPageIndex, filter, applyFilterToAll)
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
                                // Show filtered preview thumbnail (not raw)
                                filterPreviews[filter]?.let { previewBmp ->
                                    Image(
                                        bitmap = previewBmp.asImageBitmap(),
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
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val effectiveTint = if (enabled) tint else tint.copy(alpha = 0.38f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = effectiveTint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = effectiveTint, fontWeight = FontWeight.Medium)
    }
}
