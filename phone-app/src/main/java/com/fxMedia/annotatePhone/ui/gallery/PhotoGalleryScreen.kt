package com.fxMedia.annotatePhone.ui.gallery

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fxMedia.annotatePhone.R
import com.fxMedia.annotatePhone.service.photo.PhotoData
import com.fxMedia.annotatePhone.ui.theme.RokidPhoneTheme
import com.fxMedia.annotatePhone.viewmodel.PhotoGalleryUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Photo Gallery Screen - displays photos in a grid layout grouped by date
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGalleryScreen(
    groupedPhotos: Map<String, List<PhotoData>>,
    photoCount: Int,
    uiState: PhotoGalleryUiState,
    onPhotoClick: (PhotoData) -> Unit,
    onPhotoLongClick: (PhotoData) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    loadBitmap: suspend (PhotoData, Int?) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                // Selection mode top bar
                TopAppBar(
                    title = { 
                        Text(
                            text = stringResource(
                                R.string.gallery_selected_count,
                                uiState.selectedPhotos.size
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(
                                Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.gallery_select_all)
                            )
                        }
                        IconButton(onClick = onShareSelected) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.share)
                            )
                        }
                        IconButton(onClick = onDeleteSelected) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                // Normal mode top bar
                TopAppBar(
                    title = { 
                        Column {
                            Text(stringResource(R.string.nav_gallery))
                            if (photoCount > 0) {
                                Text(
                                    text = stringResource(R.string.gallery_photo_count, photoCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        if (photoCount > 0) {
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.more_options)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.gallery_clear_all)) },
                                        onClick = {
                                            showMenu = false
                                            onClearAll()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        if (groupedPhotos.isEmpty()) {
            // Empty state
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.gallery_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.gallery_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groupedPhotos.forEach { (date, photos) ->
                    // Date header
                    item(span = { GridItemSpan(3) }) {
                        DateHeader(date = date)
                    }
                    
                    // Photos for this date
                    items(
                        items = photos,
                        key = { it.id }
                    ) { photo ->
                        PhotoGridItem(
                            photoData = photo,
                            isSelected = uiState.selectedPhotos.contains(photo.id),
                            isSelectionMode = uiState.isSelectionMode,
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    onToggleSelection(photo.id)
                                } else {
                                    onPhotoClick(photo)
                                }
                            },
                            onLongClick = { onPhotoLongClick(photo) },
                            loadBitmap = loadBitmap
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(date: String) {
    val displayDate = remember(date) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val parsedDate = sdf.parse(date)
            val displaySdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            parsedDate?.let { displaySdf.format(it) } ?: date
        } catch (_: Exception) {
            date
        }
    }
    
    Text(
        text = displayDate,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridItem(
    photoData: PhotoData,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    loadBitmap: suspend (PhotoData, Int?) -> Bitmap?
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(photoData.id) {
        isLoading = true
        bitmap = withContext(Dispatchers.IO) {
            loadBitmap(photoData, 300)  // Thumbnail size
        }
        isLoading = false
    }
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        
        // Selection indicator
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    )
                    .border(
                        width = 2.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        
        // Analysis result indicator
        if (photoData.analysisResult != null && !isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

/**
 * Delete confirmation dialog
 */
@Composable
fun DeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.gallery_delete_title)) },
        text = { 
            Text(
                stringResource(R.string.gallery_delete_message, count)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Clear all confirmation dialog
 */
@Composable
fun ClearAllConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.gallery_clear_all_title)) },
        text = { 
            Text(stringResource(R.string.gallery_clear_all_message))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.gallery_clear_all))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PhotoGalleryScreenPreview() {
    val samplePhotos = listOf(
        PhotoData("1", "path1", System.currentTimeMillis(), 1000, 1000, 1024 * 100, 100),
        PhotoData("2", "path2", System.currentTimeMillis(), 1000, 1000, 1024 * 200, 150, "Analysis result"),
        PhotoData("3", "path3", System.currentTimeMillis() - 86400000, 1000, 1000, 1024 * 300, 200)
    )
    val groupedPhotos = mapOf(
        "2023-10-27" to listOf(samplePhotos[0], samplePhotos[1]),
        "2023-10-26" to listOf(samplePhotos[2])
    )
    
    RokidPhoneTheme {
        Surface {
            PhotoGalleryScreen(
                groupedPhotos = groupedPhotos,
                photoCount = 3,
                uiState = PhotoGalleryUiState(),
                onPhotoClick = {},
                onPhotoLongClick = {},
                onToggleSelection = {},
                onSelectAll = {},
                onClearSelection = {},
                onDeleteSelected = {},
                onShareSelected = {},
                onClearAll = {},
                onBack = {},
                loadBitmap = { _, _ -> null }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PhotoGalleryScreenEmptyPreview() {
    RokidPhoneTheme {
        Surface {
            PhotoGalleryScreen(
                groupedPhotos = emptyMap(),
                photoCount = 0,
                uiState = PhotoGalleryUiState(),
                onPhotoClick = {},
                onPhotoLongClick = {},
                onToggleSelection = {},
                onSelectAll = {},
                onClearSelection = {},
                onDeleteSelected = {},
                onShareSelected = {},
                onClearAll = {},
                onBack = {},
                loadBitmap = { _, _ -> null }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PhotoGalleryScreenSelectionPreview() {
    val samplePhotos = listOf(
        PhotoData("1", "path1", System.currentTimeMillis(), 1000, 1000, 1024 * 100, 100),
        PhotoData("2", "path2", System.currentTimeMillis(), 1000, 1000, 1024 * 200, 150)
    )
    val groupedPhotos = mapOf(
        "2023-10-27" to samplePhotos
    )
    
    RokidPhoneTheme {
        Surface {
            PhotoGalleryScreen(
                groupedPhotos = groupedPhotos,
                photoCount = 2,
                uiState = PhotoGalleryUiState(
                    isSelectionMode = true,
                    selectedPhotos = setOf("1")
                ),
                onPhotoClick = {},
                onPhotoLongClick = {},
                onToggleSelection = {},
                onSelectAll = {},
                onClearSelection = {},
                onDeleteSelected = {},
                onShareSelected = {},
                onClearAll = {},
                onBack = {},
                loadBitmap = { _, _ -> null }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeleteConfirmDialogPreview() {
    RokidPhoneTheme {
        DeleteConfirmDialog(
            count = 5,
            onConfirm = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ClearAllConfirmDialogPreview() {
    RokidPhoneTheme {
        ClearAllConfirmDialog(
            onConfirm = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DateHeaderPreview() {
    RokidPhoneTheme {
        DateHeader(date = "2023-10-27")
    }
}

@Preview(showBackground = true)
@Composable
fun PhotoGridItemPreview() {
    val samplePhoto = PhotoData("1", "path1", System.currentTimeMillis(), 1000, 1000, 1024 * 100, 100)
    RokidPhoneTheme {
        PhotoGridItem(
            photoData = samplePhoto,
            isSelected = false,
            isSelectionMode = false,
            onClick = {},
            onLongClick = {},
            loadBitmap = { _, _ -> null }
        )
    }
}
