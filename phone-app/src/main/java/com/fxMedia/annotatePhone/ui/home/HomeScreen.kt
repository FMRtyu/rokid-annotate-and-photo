    package com.fxMedia.annotatePhone.ui.home

    import androidx.compose.foundation.Image
    import androidx.compose.foundation.background
    import androidx.compose.foundation.border
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.filled.ArrowBack
    import androidx.compose.material.icons.automirrored.filled.ArrowForward
    import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
    import androidx.compose.material.icons.filled.*
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.vector.ImageVector
    import androidx.compose.ui.layout.*
    import androidx.compose.ui.res.painterResource
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.tooling.preview.Preview
    import androidx.compose.ui.unit.dp
    import coil.compose.rememberAsyncImagePainter
    import com.fxMedia.rokidcommon.protocol.ConnectionState
    import com.fxMedia.annotatePhone.ConversationItem
    import com.fxMedia.annotatePhone.BuildConfig
    import com.fxMedia.annotatePhone.R
    import com.fxMedia.annotatePhone.data.AvailableModels
    import com.fxMedia.annotatePhone.data.db.RecordingSource
    import com.fxMedia.annotatePhone.data.db.RecordingState
    import com.fxMedia.annotatePhone.ui.components.*
    import com.fxMedia.annotatePhone.ui.theme.AppShapeTokens
    import com.fxMedia.annotatePhone.ui.theme.ExtendedTheme
    import com.fxMedia.annotatePhone.ui.theme.RokidPhoneTheme
    import com.fxMedia.annotatePhone.viewmodel.PhoneViewModel
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import java.io.File
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.foundation.text.BasicTextField
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.setValue
    import androidx.compose.ui.graphics.SolidColor
    import androidx.compose.ui.graphics.vector.rememberVectorPainter
    import androidx.compose.ui.text.TextStyle
    import androidx.compose.ui.unit.sp
    import androidx.lifecycle.Lifecycle
    import androidx.lifecycle.LifecycleEventObserver
    import androidx.compose.ui.platform.LocalLifecycleOwner

    /**
     * Redesigned Home Screen with Material Design 3 patterns
     */
    private val _message = MutableStateFlow<String?>(null)
    val messageFlow: StateFlow<String?> = _message

    fun updateMessage(text: String) {
        _message.value = text
    }
    @OptIn(ExperimentalMaterial3Api::class)

    @Composable
    fun HomeScreen(
        connectionState: ConnectionState,
        connectedGlassesName: String?,
        isServiceRunning: Boolean,
        latestPhotoPath: String?,
        processingStatus: String?,
        currentModelId: String,
        conversations: List<ConversationItem>,
        recordingState: RecordingState = RecordingState.Idle,
        onConnect: () -> Unit,
        onDisconnect: () -> Unit,
        onStartService: () -> Unit,
        onStopService: () -> Unit,
        onCapturePhoto: () -> Unit,
        onStartPhoneRecording: () -> Unit = {},
        onStartGlassesRecording: () -> Unit = {},
        onPauseRecording: () -> Unit = {},
        onStopRecording: () -> Unit = {},
        onViewConversationHistory: () -> Unit = {},
        onViewGallery: () -> Unit = {},
        onViewRecordings: () -> Unit = {},
        onNextTranscript: () -> Unit = {},
        onPreviousTranscript: () -> Unit = {},
        onRefresh: () -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    onRefresh()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        val currentModel = AvailableModels.findModel(currentModelId)
        val transcriptGroup by PhoneViewModel.uiState.collectAsState() // This is a List<String> from update
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            /*item {
                Image(
                    painter = rememberAsyncImagePainter(R.drawable.image_background2),
                    contentDescription = "Frame Background",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentScale = ContentScale.FillBounds // Penting untuk 9-patch
                )
            }*/
            // Welcome header
            item {
                WelcomeHeader()
            }

            // Status overview cards
//            item {
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.spacedBy(12.dp)
//                ) {
//                    // Glasses connection status
//                    InfoCard(
//                        icon = when (connectionState) {
//                            ConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
//                            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Icons.AutoMirrored.Filled.BluetoothSearching
//                            else -> Icons.Default.BluetoothDisabled
//                        },
//                        title = stringResource(R.string.glasses_status),
//                        value = when (connectionState) {
//                            ConnectionState.CONNECTED -> connectedGlassesName ?: stringResource(R.string.connected)
//                            ConnectionState.CONNECTING -> stringResource(R.string.connecting)
//                            ConnectionState.RECONNECTING -> stringResource(R.string.reconnecting)
//                            ConnectionState.ERROR -> stringResource(R.string.connection_error)
//                            else -> stringResource(R.string.disconnected)
//                        },
//                        valueColor = when (connectionState) {
//                            ConnectionState.CONNECTED -> ExtendedTheme.colors.success
//                            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.tertiary
//                            ConnectionState.ERROR -> MaterialTheme.colorScheme.error
//                            else -> MaterialTheme.colorScheme.onSurfaceVariant
//                        },
//                        modifier = Modifier.weight(1f)
//                    )
//
//                     Current AI model
//                    InfoCard(
//                        icon = Icons.Default.Psychology,
//                        title = stringResource(R.string.current_model),
//                        value = currentModel?.displayName ?: currentModelId,
//                        modifier = Modifier.weight(1f)
//                    )
//                }
//            }
            item {
                GlassesConnectionCard(
                    connectionState = connectionState,
                    glassesName = connectedGlassesName,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect
                )
            }
            item {
                var manualText by remember { mutableStateOf("") }
                ManualAnnotationCard(
                    annotationText = manualText,
                    onAnnotationTextChange = { manualText = it },
                    onFinish = {
                        if (manualText.isNotBlank()) {
                            // 2. Panggil fungsi statis dari PhoneViewModel
                            PhoneViewModel.updateTranscript(manualText)

                            // 3. Kosongkan textbox setelah berhasil dikirim
                            manualText = ""
                        }
                    }
                )
            }
            // Latest photo (if available)
            item {
                // Note: In a real implementation, we should use a proper Paging state
                // But since HomeScreen is stateless, we pass the data and callbacks
                // At the top level where you use transcripts
                // Read from ViewModel's official state
                val uiState by PhoneViewModel.uiState.collectAsState()
                val transcripts = uiState.transcripts

                //val photoToShow = uiState.latestPhotoPath

                CapturedPhoto(
                    photoPath = latestPhotoPath ?: "null",
                    message = if (transcripts.isNotEmpty() && uiState.currentTranscriptIndex < transcripts.size) {
                        transcripts[uiState.currentTranscriptIndex]
                    } else {
                        "Start record the annotate"
                    },
                    onNext = onNextTranscript,
                    onPrevious = onPreviousTranscript,
                    currentIndex = uiState.currentTranscriptIndex,
                    totalCount = transcripts.size
                )

            }

            // Connection control card
            /*item {
                GlassesConnectionCard(
                    connectionState = connectionState,
                    glassesName = connectedGlassesName,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect
                )
            }*/

            // Camera capture card (only when connected)
            item {
                AnimatedSection(visible = connectionState == ConnectionState.CONNECTED) {
                    //CameraCaptureCard(onCapturePhoto = onCapturePhoto)
                    //CapturedPhoto(photoPath = photoPath)
                }
            }

            // Recording control card
    //        item {
    //            RecordingControlCard(
    //                recordingState = recordingState,
    //                isGlassesConnected = connectionState == ConnectionState.CONNECTED,
    //                onStartPhoneRecording = onStartPhoneRecording,
    //                onStartGlassesRecording = onStartGlassesRecording,
    //                onPauseRecording = onPauseRecording,
    //                onStopRecording = onStopRecording,
    //                onSend = { text ->
    //                    updateMessage(text)
    //                }
    //            )
    //        }

            // Service control card
    //        item {
    //            ServiceCard(
    //                isRunning = isServiceRunning,
    //                onStart = onStartService,
    //                onStop = onStopService
    //            )
    //        }

            // Quick access cards row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Chat history quick access
    //                QuickAccessCard(
    //                    icon = Icons.Default.History,
    //                    title = stringResource(R.string.conversation_history),
    //                    onClick = onViewConversationHistory,
    //                    modifier = Modifier.weight(1f)
    //                )

                    // Gallery quick access
                    QuickAccessCard(
                        icon = painterResource(id =R.drawable.gallery_icon),
                        title = stringResource(R.string.nav_gallery),
                        onClick = onViewGallery,
                        modifier = Modifier.weight(1f)
                    )
                    //last recording
    //                QuickAccessCard(
    //                    icon = Icons.Default.Mic,
    //                    title = stringResource(R.string.recordings),
    //                    onClick = onViewRecordings,
    //                    modifier = Modifier.weight(1f)
    //                )
                }
            }

            // Second row of quick access cards
    //        item {
    //            Row(
    //                modifier = Modifier.fillMaxWidth(),
    //                horizontalArrangement = Arrangement.spacedBy(12.dp)
    //            ) {
    //                // Recordings quick access
    //                QuickAccessCard(
    //                    icon = Icons.Default.Mic,
    //                    title = stringResource(R.string.recordings),
    //                    onClick = onViewRecordings,
    //                    modifier = Modifier.weight(1f)
    //                )
    //
    //                // Placeholder for future feature
    //                Spacer(modifier = Modifier.weight(1f))
    //            }
    //        }

            // Recent voice conversations section (current session)
//            if (conversations.isNotEmpty()) {
//                item {
//                    SectionHeaderWithAction(
//                        title = stringResource(R.string.home_current_session),
//                        actionLabel = stringResource(R.string.home_view_all),
//                        onAction = onViewConversationHistory
//                    )
//                }
//
//                item {
//                    // Display conversations in a card
//                    Card(
//                        modifier = Modifier.fillMaxWidth(),
//                        shape = MaterialTheme.shapes.medium,
//                        colors = CardDefaults.cardColors(
//                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
//                        )
//                    ) {
//                        Column(
//                            modifier = Modifier.padding(12.dp),
//                            verticalArrangement = Arrangement.spacedBy(8.dp)
//                        ) {
//                            conversations.takeLast(6).forEach { item ->
//                                ConversationBubbleCard(item = item)
//                            }
//                        }
//                    }
//                }
//            }

            // App Version Info Card at the very bottom
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp), // Tambah bottom padding agar tidak mepet layar
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(35.dp),
                        shape = RoundedCornerShape(0.dp), // Konsisten kotak
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = painterResource(id = R.drawable.gallery_container),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                            Text(
                                text = "Version ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center, // Teks di tengah
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            // Status bar
    //        item {
    //            StatusFooter(
    //                processingStatus = processingStatus,
    //                modelName = currentModel?.displayName ?: currentModelId
    //            )
    //        }
        }
    }

    @Composable
    private fun QuickAccessCard(
        icon: androidx.compose.ui.graphics.painter.Painter,
        title: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Card(
            onClick = onClick,
            modifier = modifier.height(85.dp),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.gallery_container),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }

    @Composable
    private fun SectionHeaderWithAction(
        title: String,
        actionLabel: String,
        onAction: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
    @Composable
    private fun WelcomeHeader() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
//            Text(
//                text = stringResource(R.string.home_welcome),
//                style = MaterialTheme.typography.headlineMedium,
//                fontWeight = FontWeight.Bold,
//                color = MaterialTheme.colorScheme.onSurface
//            )
//            Spacer(modifier = Modifier.height(4.dp))
//            Text(
//                text = stringResource(R.string.home_subtitle),
//                style = MaterialTheme.typography.bodyLarge,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
//            Image(
//                painter = painterResource(id = R.drawable.logo),
//                contentDescription = "Captured photo",
//                modifier = Modifier.
//                //width(100.dp),
//                fillMaxWidth(0.5f),
//                contentScale = ContentScale.FillWidth
//            )
            Text(
                text = stringResource(R.string.home_welcome),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(10.dp))
            Image(
                painter = painterResource(id = R.drawable.line_seperator),
                contentDescription = "Captured photo",
                modifier = Modifier
                    .fillMaxSize()
                    .height(2.dp),
                contentScale = ContentScale.FillWidth
            )
        }
    }

    @Composable
    private fun GlassesConnectionCard(
        connectionState: ConnectionState,
        glassesName: String?,
        onConnect: () -> Unit,
        onDisconnect: () -> Unit
    ) {
        val isConnected = connectionState == ConnectionState.CONNECTED
        val isConnecting = connectionState == ConnectionState.CONNECTING ||
                           connectionState == ConnectionState.RECONNECTING

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(75.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Vertical Separator
                    Image(
                        painter = painterResource(id = R.drawable.line_seperator_wifi),
                        contentDescription = null,
                        modifier = Modifier.height(75.dp),
                        contentScale = ContentScale.FillHeight
                    )

                    // --- BLUETOOTH SECTION ---
                    Box(
                        modifier = Modifier.padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.bluetooth_container),
                            contentDescription = null,
                            modifier = Modifier.size(75.dp, 75.dp),
                            contentScale = ContentScale.FillBounds
                        )

                        if (isConnecting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFB5FF7D))
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.bluetooth_icon),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = Color(0xFFB5FF7D)
                            )
                        }
                    }

                    // Middle Vertical Separator
                    Image(
                        painter = painterResource(id = R.drawable.line_seperator_wifi),
                        contentDescription = null,
                        modifier = Modifier.height(75.dp),
                        contentScale = ContentScale.FillHeight
                    )

                    // --- MAIN INFO SECTION ---
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(75.dp)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.wifi_container),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )

                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.icon_signal),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = (glassesName ?: stringResource(R.string.disconnected)).uppercase(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = if (isConnected) onDisconnect else onConnect,
                                shape = RoundedCornerShape(0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF5D8233),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = if (isConnected) "DISCONNECT" else "CONNECT",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }

                    // Right Vertical Separator
                    Image(
                        painter = painterResource(id = R.drawable.line_seperator_wifi),
                        contentDescription = null,
                        modifier = Modifier.height(75.dp),
                        contentScale = ContentScale.FillHeight
                    )
                }
            }
        }
    }

    @Composable
    private fun ManualAnnotationCard(
        annotationText: String,
        onAnnotationTextChange: (String) -> Unit,
        onFinish: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp), // Sesuaikan tinggi agar proporsional dengan gambar
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Garis Vertikal Kiri
            Image(
                painter = painterResource(id = R.drawable.line_seperator_anonate),
                contentDescription = null,
                modifier = Modifier.fillMaxHeight(),
                contentScale = ContentScale.FillHeight
            )

            // 2. Kontainer Utama Annotation
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
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

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Annotation Text",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Area Input
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(35.dp)
                                .background(Color.Black.copy(alpha = 0.3f))
                                .border(1.dp, Color.Gray.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (annotationText.isEmpty()) {
                                Text(
                                    text = "Add here...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                            
                            BasicTextField(
                                value = annotationText,
                                onValueChange = onAnnotationTextChange,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp
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
                                containerColor = Color(0xFF5D8233), // Hijau Neon/Gelap sesuai tema
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(35.dp)
                        ) {
                            Text(
                                text = "FINISH",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 3. Garis Vertikal Kanan
            Image(
                painter = painterResource(id = R.drawable.line_seperator_anonate),
                contentDescription = null,
                modifier = Modifier.fillMaxHeight(),
                contentScale = ContentScale.FillHeight
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Image(
            painter = painterResource(id = R.drawable.line_seperator),
            contentDescription = "Captured photo",
            modifier = Modifier
                .fillMaxSize()
                .height(2.dp),
            contentScale = ContentScale.FillWidth
        )

    }

    @Composable
    private fun CameraCaptureCard(
        onCapturePhoto: () -> Unit
    ) {
        ActionCard(
         icon = Icons.Default.CameraAlt,
          title = stringResource(R.string.camera_capture),
          subtitle = stringResource(R.string.camera_capture_hint),
         actionLabel = stringResource(R.string.capture),
         onAction = onCapturePhoto,
         iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
         iconColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

    @Composable
    private fun LatestPhotoCard(photoPath: String) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.latest_photo),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val file = remember(photoPath) { File(photoPath) }
                if (file.exists()) {
                    Image(
                        painter = rememberAsyncImagePainter(file),
                        contentDescription = "Captured photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(AppShapeTokens.ImageContainer),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.photo_not_found),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ServiceCard(
        isRunning: Boolean,
        onStart: () -> Unit,
        onStop: () -> Unit
    ) {
        ActionCard(
            icon = if (isRunning) Icons.Default.PlayCircle else Icons.Default.StopCircle,
            title = stringResource(R.string.ai_service),
            subtitle = if (isRunning) stringResource(R.string.service_running) else stringResource(R.string.service_stopped),
            actionLabel = if (isRunning) stringResource(R.string.stop) else stringResource(R.string.start),
            onAction = if (isRunning) onStop else onStart,
            iconContainerColor = if (isRunning)
                ExtendedTheme.colors.successContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            iconColor = if (isRunning)
                ExtendedTheme.colors.success
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    @Composable
    private fun ConversationBubbleCard(item: ConversationItem) {
        val isUser = item.role == "user"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                shape = if (isUser) AppShapeTokens.MessageBubbleUser else AppShapeTokens.MessageBubbleAssistant,
                color = if (isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text = item.content,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    @Composable
    private fun StatusFooter(
        processingStatus: String?,
        modelName: String
    ) {
        val ready = stringResource(R.string.ready)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = processingStatus ?: ready,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    @Composable
    private fun CapturedPhoto(
        photoPath: String,
        message: String?,
        onNext: () -> Unit = {},
        onPrevious: () -> Unit = {},
        currentIndex: Int = 0,
        totalCount: Int = 1
    ) {
        // Calculate whether buttons should be enabled
        val canGoPrevious = currentIndex > 0
        val canGoNext = currentIndex < totalCount - 1

        //get photo file
        val file = remember(photoPath) { File(photoPath) }

        // Use the message parameter
        val displayMsg = if (!message.isNullOrEmpty()) {
            message
        } else {
            "Start record the annotate"
        }

        val painter = if (file.exists()) {
            rememberAsyncImagePainter(file)
        } else {
            painterResource(id = R.drawable.defaultphoto)
        }

        val imageRatio = remember(painter.intrinsicSize) {
            val size = painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) {
                // Formula: Height / Width (flipped for rotation)
                size.height / size.width
            } else {
                0.6f // Fallback while loading
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                //.height(700.dp),
                .aspectRatio(imageRatio),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                    //.clip(MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.frame_without_bg),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                Image(
                    painter = painter,
                    contentDescription = "Captured photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 13.dp, vertical = 1.dp)
                        .layout { measurable, constraints ->
                            // Measure the image with swapped constraints to simulate landscape
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = constraints.minHeight,
                                    maxWidth = constraints.maxHeight,
                                    minHeight = constraints.minWidth,
                                    maxHeight = constraints.maxWidth
                                )
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.placeWithLayer(
                                    x = (constraints.maxWidth - placeable.width) / 2,
                                    y = (constraints.maxHeight - placeable.height) / 2
                                ) {
                                    rotationZ = -90f
                                    //scaleX = -1f
                                }
                            }
                        },
                    contentScale = ContentScale.Fit
                )

                // 🟢 Text overlay with Paging
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 35.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
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
                            // Left Arrow
                            // Previous button (disabled at position 0)
                            IconButton(onClick = onPrevious,
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

                            // Text content and counter
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = displayMsg,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (totalCount > 1) {
                                    Text(
                                        text = "${currentIndex + 1} / $totalCount",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            // Right Arrow
                            // Next button (disabled at last position)
                            IconButton(
                                onClick = onNext,
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
            }
        }
    }

    /**
     * Recording control card with start/pause/stop functionality
     */
    @Composable
    private fun RecordingControlCard(
        recordingState: RecordingState,
        isGlassesConnected: Boolean,
        onStartPhoneRecording: () -> Unit,
        onStartGlassesRecording: () -> Unit,
        onPauseRecording: () -> Unit,
        onStopRecording: () -> Unit,
        onSend: (String) -> Unit,
    ) {
        val isRecording = recordingState is RecordingState.Recording
        val isStopping = recordingState is RecordingState.Stopping

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isRecording -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
    //                //textbox and button
    //                var annotationText by remember { mutableStateOf("") }
    //
    //                OutlinedTextField(
    //                    value = annotationText,
    //                    onValueChange = { annotationText = it },
    //                    modifier = Modifier.weight(1f),
    //                    placeholder = { Text("Annotation Text") },
    //                    singleLine = true,
    //                    keyboardOptions = KeyboardOptions(
    //                                imeAction = ImeAction.Send
    //                            )
    //                )
    //                Spacer(modifier = Modifier.width(8.dp))
    //
    //                Button(
    //                    onClick = {
    //                        onSend(annotationText)
    //                        annotationText = "" // optional reset
    //                    },
    //                    enabled = annotationText.isNotBlank()
    //                ) {
    //                    Text("Send")
    //                }

                    //Mic Icon
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isRecording) {
                                // Pulsing recording indicator
                                Icon(
                                    imageVector = Icons.Default.FiberManualRecord,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    //title Mic
                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Annotation Text",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (isRecording && recordingState is RecordingState.Recording) {
                            val durationText = formatRecordingDuration(recordingState.durationMs)
                            val sourceText = when (recordingState.source) {
                                RecordingSource.PHONE -> stringResource(R.string.recording_source_phone)
                                RecordingSource.GLASSES -> stringResource(R.string.recording_source_glasses)
                            }
                            Text(
                                text = "$sourceText • $durationText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.recording_ready),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Control buttons
                if (isRecording || isStopping) {
                    // Recording controls: Pause & Stop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Pause button (disabled for now - MediaRecorder pause requires API 24+)
                        // OutlinedButton(
                        //     onClick = onPauseRecording,
                        //     modifier = Modifier.weight(1f),
                        //     enabled = !isStopping
                        // ) {
                        //     Icon(Icons.Default.Pause, contentDescription = null)
                        //     Spacer(modifier = Modifier.width(8.dp))
                        //     Text(stringResource(R.string.pause_recording))
                        // }

                        // Stop button
                        Button(
                            onClick = onStopRecording,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isStopping,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            if (isStopping) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            } else {
                                Icon(Icons.Default.Stop, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isStopping)
                                    stringResource(R.string.stopping)
                                else
                                    stringResource(R.string.stop_and_send)
                            )
                        }
                    }
                } else {
                    // Start recording options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Phone recording button
                        Button(
                            onClick = onStartPhoneRecording,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Smartphone, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.record_phone))
                        }

                        // Glasses recording button (only when connected)
                        Button(
                            onClick = onStartGlassesRecording,
                            modifier = Modifier.weight(1f),
                            enabled = isGlassesConnected,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.record_glasses))
                        }
                    }
                }

                // Error message
                if (recordingState is RecordingState.Error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = recordingState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    /**
     * Format recording duration in MM:SS format
     */
    private fun formatRecordingDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        return "%02d:%02d".format(minutes, seconds)
    }

    @Preview(showBackground = true, backgroundColor = 0xFF000000)
    @Composable
    fun HomeScreenPreview(
        modifier: Modifier = Modifier,
    ) {
        RokidPhoneTheme {
            HomeScreen(
                connectionState = ConnectionState.DISCONNECTED,
                connectedGlassesName = "Rokid Max",
                isServiceRunning = true,
                latestPhotoPath = null,
                processingStatus = "Ready",
                currentModelId = "gemini-2.5-flash",
                conversations = listOf(
                    ConversationItem("user", "Hello, how are you?"),
                    ConversationItem("assistant", "I'm doing great, thank you! How can I help you today?")
                ),
                recordingState = RecordingState.Idle,
                onConnect = {},
                onDisconnect = {},
                onStartService = {},
                onStopService = {},
                onCapturePhoto = {},
                onStartPhoneRecording = {},
                onStartGlassesRecording = {},
                onPauseRecording = {},
                onStopRecording = {},
                onViewConversationHistory = {},
                onViewGallery = {},
                onViewRecordings = {}
            )
        }
    }
/*
    @Preview(showBackground = true, backgroundColor = 0xFF000000)
    @Composable
    fun GreetingPreview() {
        Image(
            painter = painterResource(R.drawable.photo_frame),
            contentDescription = "Frame Background",
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentScale = ContentScale.FillBounds // Penting untuk 9-patch
        )
    }*/
