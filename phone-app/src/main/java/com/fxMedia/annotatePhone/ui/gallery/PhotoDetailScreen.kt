package com.fxMedia.annotatePhone.ui.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fxMedia.annotatePhone.R
import com.fxMedia.annotatePhone.service.photo.PhotoData
import com.fxMedia.annotatePhone.ui.theme.RokidPhoneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Photo Detail Screen - full screen photo viewer with zoom and swipe
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photos: List<PhotoData>,
    initialPhoto: PhotoData,
    onBack: () -> Unit,
    onDelete: (PhotoData) -> Unit,
    onShare: (PhotoData) -> Unit,
    onAddAnnotation: (PhotoData, String) -> Unit,
    loadBitmap: suspend (PhotoData, Int?) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val initialIndex = photos.indexOfFirst { it.id == initialPhoto.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    val currentPhoto = photos.getOrNull(pagerState.currentPage) ?: initialPhoto
    var showInfo by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var manualText by remember { mutableStateOf("") }
    var currentAnnotationIndex by remember(pagerState.currentPage) { mutableIntStateOf(0) }
    
    // Reset annotation text when swiping to a different photo
    LaunchedEffect(pagerState.currentPage) {
        manualText = ""
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Photo pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photo = photos.getOrNull(page)
            if (photo != null) {
                ZoomablePhoto(
                    photoData = photo,
                    loadBitmap = loadBitmap,
                    onTap = { showControls = !showControls }
                )
            }
        }
        
        // Existing Annotations Overlay (Top)
        AnimatedVisibility(
            visible = showControls && currentPhoto.analysisResults.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
                .padding(horizontal = 16.dp)
        ) {
            val annotations = currentPhoto.analysisResults
            val canGoPrevious = currentAnnotationIndex > 0
            val canGoNext = currentAnnotationIndex < annotations.size - 1
            
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth(0.9f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { if (canGoPrevious) currentAnnotationIndex-- },
                        enabled = canGoPrevious,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Image(
                            painter = painterResource(
                                id = if (canGoPrevious) R.drawable.l_arrow_default else R.drawable.l_arrow_inactive
                            ),
                            contentDescription = "Previous",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = annotations.getOrNull(currentAnnotationIndex) ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (annotations.size > 1) {
                            Text(
                                text = "${currentAnnotationIndex + 1} / ${annotations.size}",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    IconButton(
                        onClick = { if (canGoNext) currentAnnotationIndex++ },
                        enabled = canGoNext,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Image(
                            painter = painterResource(
                                id = if (canGoNext) R.drawable.r_arrow_default else R.drawable.r_arrow_inactive
                            ),
                            contentDescription = "Next",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Manual Annotation Card Overlay (Bottom)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .padding(horizontal = 16.dp)
        ) {
            ManualAnnotationCard(
                annotationText = manualText,
                onAnnotationTextChange = { manualText = it },
                onFinish = {
                    if (manualText.isNotBlank()) {
                        onAddAnnotation(currentPhoto, manualText)
                        manualText = ""
                        // Optionally update index to show the new one
                    }
                }
            )
        }

        // Top bar with controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${photos.size}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = currentPhoto.formattedTimestamp,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.gallery_info),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            )
        }
        
        // Bottom action bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = { onShare(currentPhoto) }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.share),
                            tint = Color.White
                        )
                    }
                    Text(
                        text = stringResource(R.string.share),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = Color.White
                        )
                    }
                    Text(
                        text = stringResource(R.string.delete),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
        
        // Page indicator dots
        if (photos.size > 1 && showControls) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(minOf(photos.size, 10)) { index ->
                    val actualIndex = if (photos.size > 10) {
                        (pagerState.currentPage / 10) * 10 + index
                    } else index
                    
                    if (actualIndex < photos.size) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (actualIndex == pagerState.currentPage) 8.dp else 6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (actualIndex == pagerState.currentPage) 
                                        Color.White 
                                    else Color.White.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
            }
        }
    }
    
    // Photo info dialog
    if (showInfo) {
        PhotoInfoDialog(
            photoData = currentPhoto,
            onDismiss = { showInfo = false }
        )
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.gallery_delete_title)) },
            text = { Text(stringResource(R.string.gallery_delete_single_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(currentPhoto)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ManualAnnotationCard(
    annotationText: String,
    onAnnotationTextChange: (String) -> Unit,
    onFinish: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp) // Smaller height
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background Utama
        Image(
            painter = painterResource(id = R.drawable.annotate_container),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp), // Less padding
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Area Input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp) // Smaller input box
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(1.dp, Color.Gray.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (annotationText.isEmpty()) {
                    Text(
                        text = "Add Annotation here...",
                        style = MaterialTheme.typography.bodySmall, // Smaller text
                        color = Color.Gray
                    )
                }
                
                BasicTextField(
                    value = annotationText,
                    onValueChange = onAnnotationTextChange,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 12.sp // Smaller font size
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Tombol FINISH
            Button(
                onClick = onFinish,
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5D8233),
                    contentColor = Color.White
                ),
                modifier = Modifier.height(30.dp), // Smaller button
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "FINISH",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(
    photoData: PhotoData,
    loadBitmap: suspend (PhotoData, Int?) -> Bitmap?,
    onTap: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    LaunchedEffect(photoData.id) {
        isLoading = true
        bitmap = withContext(Dispatchers.IO) {
            loadBitmap(photoData, null)  // Full size
        }
        isLoading = false
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        // Toggle zoom on double tap
                        scale = if (scale > 1.5f) 1f else 2.5f
                        offset = Offset.Zero
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (scale > 1f || zoomChange != 1f) {
                            // Consume only if zoomed in or zooming
                            event.changes.forEach {
                                if (it.positionChanged()) {
                                    it.consume()
                                }
                            }

                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                            
                            if (scale > 1f) {
                                offset = Offset(
                                    x = (offset.x + panChange.x).coerceIn(-500f * (scale - 1), 500f * (scale - 1)),
                                    y = (offset.y + panChange.y).coerceIn(-500f * (scale - 1), 500f * (scale - 1))
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                            rotationZ = -90f
                        },
                    contentScale = ContentScale.Fit
                )
            }
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.gallery_load_error),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoInfoDialog(
    photoData: PhotoData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Info, contentDescription = null)
        },
        title = { Text(stringResource(R.string.gallery_photo_info)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow(
                    label = stringResource(R.string.gallery_info_date),
                    value = photoData.formattedTimestamp
                )
                InfoRow(
                    label = stringResource(R.string.gallery_info_dimensions),
                    value = photoData.formattedDimensions
                )
                InfoRow(
                    label = stringResource(R.string.gallery_info_size),
                    value = photoData.formattedSize
                )
                if (photoData.transferTimeMs > 0) {
                    InfoRow(
                        label = stringResource(R.string.gallery_info_transfer_time),
                        value = "${photoData.transferTimeMs}ms"
                    )
                }
                if (photoData.analysisResults != null) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.gallery_info_analysis),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
//                    Text(
//                        text = photoData.analysisResults.firstOrNull() ?: "no annotate yet",
//                        style = MaterialTheme.typography.bodySmall,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
                    if (photoData.analysisResults.isEmpty()) {
                        Text(
                            text = "no annotate yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        photoData.analysisResults.forEachIndexed { index, result ->
                            Text(
                                text = "${index + 1}. $result",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PhotoDetailScreenPreview() {
    val context = LocalContext.current
    val samplePhotos = listOf(
        PhotoData(
            id = "1",
            filePath = "path1",
            timestamp = System.currentTimeMillis(),
            width = 1920,
            height = 1080,
            sizeBytes = 1024 * 500,
            transferTimeMs = 100,
            analysisResults = listOf("A sample image analysis result showing what's in the photo.")
        ),
        PhotoData(
            id = "2",
            filePath = "path2",
            timestamp = System.currentTimeMillis() - 3600000,
            width = 1080,
            height = 1920,
            sizeBytes = 1024 * 300,
            transferTimeMs = 80
        )
    )
    
    RokidPhoneTheme {
        Surface {
            PhotoDetailScreen(
                photos = samplePhotos,
                initialPhoto = samplePhotos[0],
                onBack = {},
                onDelete = {},
                onShare = {},
                onAddAnnotation = { _, _ -> },
                loadBitmap = { _, _ -> 
                    BitmapFactory.decodeResource(context.resources, R.drawable.defaultphoto_2)
                }
            )
        }
    }
}
