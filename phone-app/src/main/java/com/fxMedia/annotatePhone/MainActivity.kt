package com.fxMedia.annotatePhone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.annotatePhone.data.ApiSettings
import com.fxMedia.annotatePhone.data.SettingsRepository
import com.fxMedia.annotatePhone.data.validateForChat
import com.fxMedia.annotatePhone.data.validateForSpeech
import com.fxMedia.annotatePhone.service.PhoneAIService
import com.fxMedia.annotatePhone.ui.LlmParametersScreen
import com.fxMedia.annotatePhone.ui.TtsSettingsScreen
import com.fxMedia.annotatePhone.ui.SettingsScreen
import com.fxMedia.annotatePhone.ui.conversation.ChatScreen
import com.fxMedia.annotatePhone.ui.conversation.ConversationHistoryScreen
import com.fxMedia.annotatePhone.ui.gallery.ClearAllConfirmDialog
import com.fxMedia.annotatePhone.ui.gallery.DeleteConfirmDialog
import com.fxMedia.annotatePhone.ui.gallery.PhotoDetailScreen
import com.fxMedia.annotatePhone.ui.gallery.PhotoGalleryScreen
import com.fxMedia.annotatePhone.ui.home.HomeScreen
import com.fxMedia.annotatePhone.ui.logs.LogViewerScreen
import com.fxMedia.annotatePhone.ui.navigation.BottomNavDestination
import com.fxMedia.annotatePhone.ui.navigation.NavRoutes
import com.fxMedia.annotatePhone.ui.theme.RokidPhoneTheme
import com.fxMedia.annotatePhone.viewmodel.ConversationViewModel
import com.fxMedia.annotatePhone.viewmodel.PhotoGalleryViewModel
import com.fxMedia.annotatePhone.viewmodel.PhoneUiState
import com.fxMedia.annotatePhone.viewmodel.PhoneViewModel

class MainActivity : AppCompatActivity() {
    private var testIndex = 1

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startAIService()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Auto start service
        checkPermissionsAndStart()
        
        setContent {
            RokidPhoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PhoneMainScreen(
                        onStartService = { checkPermissionsAndStart() },
                        onStopService = { stopAIService() }
                    )
                }
            }
        }
    }
    
    private fun checkPermissionsAndStart() {
        val requiredPermissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ))
        }
        
        requiredPermissions.add(Manifest.permission.RECORD_AUDIO)
        
        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (notGranted.isEmpty()) {
            startAIService()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }
    
    private fun startAIService() {
        val intent = Intent(this, PhoneAIService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun stopAIService() {
        stopService(Intent(this, PhoneAIService::class.java))
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                val holdMs = (event?.eventTime ?: 0L) - (event?.downTime ?: 0L)
                // if not long press
                if (holdMs < 500L) {
                    PhoneViewModel.updateTranscript("this is the transcript 07 test add no.${testIndex++}, this should be verrrrryyyyyyyy loooooooooonnnnggggggg text")
                }
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }
}


/**
 * Main Screen with Bottom Navigation following Material Design 3
 */
@Composable
fun PhoneMainScreen(
    viewModel: PhoneViewModel = viewModel(),
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository.getInstance(context) }
    val settings by settingsRepository.settingsFlow.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    PhoneMainScreenContent(
        uiState = uiState,
        settings = settings,
        onStartService = onStartService,
        onStopService = onStopService,
        onConnect = { viewModel.startScanning() },
        onDisconnect = { viewModel.disconnect() },
        onCapturePhoto = { viewModel.requestCapturePhoto() },
        onStartPhoneRecording = { viewModel.startPhoneRecording() },
        onStartGlassesRecording = { viewModel.startGlassesRecording() },
        onPauseRecording = { viewModel.pauseRecording() },
        onStopRecording = { viewModel.stopRecording() },
        onDismissInitialSetup = { viewModel.dismissInitialSetup() },
        onDismissApiKeyWarning = { viewModel.dismissApiKeyWarning() },
        onCheckInitialSetup = { viewModel.checkInitialSetup(it) },
        onSettingsChange = { settingsRepository.saveSettings(it) },
        onNextTranscript = { viewModel.nextTranscript() },
        onPreviousTranscript = { viewModel.previousTranscript() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneMainScreenContent(
    uiState: PhoneUiState,
    settings: ApiSettings,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCapturePhoto: () -> Unit,
    onStartPhoneRecording: () -> Unit,
    onStartGlassesRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDismissInitialSetup: () -> Unit,
    onDismissApiKeyWarning: () -> Unit,
    onCheckInitialSetup: (Boolean) -> Unit,
    onSettingsChange: (ApiSettings) -> Unit,
    onNextTranscript: () -> Unit = {},
    onPreviousTranscript: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Box(modifier = Modifier.fillMaxSize()){
        Image(
            painter = painterResource(R.drawable.image_background),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Only show top bar on Home screen
//            if (currentDestination?.route == NavRoutes.HOME) {
//                TopAppBar(
//                    title = { Text(stringResource(R.string.app_title)) },
//                    colors = TopAppBarDefaults.topAppBarColors(
//                        containerColor = MaterialTheme.colorScheme.surface
//                    )
//                )
//            }
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Captured photo",
                    modifier = Modifier
                        .
                        //width(100.dp),
                        fillMaxWidth(0.5f)
                        .padding(start = 16.dp, top = 5.dp),
                    contentScale = ContentScale.FillWidth
                )
            },
//        bottomBar = {
//            NavigationBar(
//                containerColor = MaterialTheme.colorScheme.surface,
//                tonalElevation = 0.dp
//            ) {
//                BottomNavDestination.entries.forEach { destination ->
//                    if (destination.route in listOf(NavRoutes.GALLERY, NavRoutes.CHAT)) return@forEach
//                    val selected = currentDestination?.route == destination.route
//                    NavigationBarItem(
//                        icon = {
//                            Icon(
//                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
//                                contentDescription = stringResource(destination.labelResId)
//                            )
//                        },
//                        label = { Text(stringResource(destination.labelResId)) },
//                        selected = selected,
//                        onClick = {
//                            // Navigate to the selected destination
//                            if (currentDestination?.route != destination.route) {
//                                if (destination.route == NavRoutes.HOME) {
//                                    // For HOME: clear entire back stack and go to HOME
//                                    navController.navigate(destination.route) {
//                                        popUpTo(navController.graph.startDestinationId) {
//                                            inclusive = true
//                                        }
//                                        launchSingleTop = true
//                                    }
//                                } else {
//                                    // For other bottom nav items: navigate normally
//                                    navController.navigate(destination.route) {
//                                        // Pop back to HOME but keep HOME in stack
//                                        popUpTo(NavRoutes.HOME) {
//                                            saveState = false
//                                            inclusive = false
//                                        }
//                                        launchSingleTop = true
//                                        restoreState = false
//                                    }
//                                }
//                            }
//                        }
//                    )
//                }
//            }
//        }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = NavRoutes.HOME,
                modifier = Modifier.padding(padding),
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                composable(NavRoutes.HOME) {
                    HomeScreen(
                        connectionState = uiState.connectionState,
                        connectedGlassesName = uiState.connectedGlassesName,
                        isServiceRunning = uiState.isServiceRunning,
                        latestPhotoPath = uiState.latestPhotoPath,
                        processingStatus = uiState.processingStatus,
                        currentModelId = settings.aiModelId,
                        conversations = uiState.conversations,
                        recordingState = uiState.recordingState,
                        onConnect = onConnect,
                        onDisconnect = onDisconnect,
                        onStartService = onStartService,
                        onStopService = onStopService,
                        onCapturePhoto = onCapturePhoto,
                        onStartPhoneRecording = onStartPhoneRecording,
                        onStartGlassesRecording = onStartGlassesRecording,
                        onPauseRecording = onPauseRecording,
                        onStopRecording = onStopRecording,
                        onViewConversationHistory = { navController.navigate(NavRoutes.CHAT) },
                        onViewGallery = { navController.navigate(NavRoutes.GALLERY) },
                        onViewRecordings = { navController.navigate(NavRoutes.RECORDINGS) },
                        onNextTranscript = onNextTranscript, // Hubungkan ke ViewModel
                        onPreviousTranscript = onPreviousTranscript,
                    )
                }

                composable(NavRoutes.RECORDINGS) {
                    com.fxMedia.annotatePhone.ui.recording.RecordingsScreen(
                        onBack = { navController.popBackStack() },
                        onRecordingDetail = { recordingId ->
                            navController.navigate(NavRoutes.recordingDetail(recordingId))
                        }
                    )
                }

                composable(
                    route = NavRoutes.RECORDING_DETAIL,
                    arguments = listOf(
                        androidx.navigation.navArgument("recordingId") {
                            type = androidx.navigation.NavType.StringType
                        }
                    )
                ) { backStackEntry ->
                    val recordingId = backStackEntry.arguments?.getString("recordingId") ?: ""
                    com.fxMedia.annotatePhone.ui.recording.RecordingDetailScreen(
                        recordingId = recordingId,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(NavRoutes.GALLERY) {
                    // Photo Gallery screen
                    val galleryViewModel: PhotoGalleryViewModel = viewModel()
                    val groupedPhotos by galleryViewModel.groupedPhotos.collectAsState()
                    val photos by galleryViewModel.photos.collectAsState()
                    val photoCount by galleryViewModel.photoCount.collectAsState()
                    val galleryUiState by galleryViewModel.uiState.collectAsState()
                    val context = LocalContext.current

                    // Reset detail view when entering gallery tab
                    DisposableEffect(Unit) {
                        onDispose {
                            galleryViewModel.closePhotoDetail()
                            galleryViewModel.clearSelection()
                        }
                    }

                    // Show detail view if a photo is selected
                    val currentDetailPhoto = galleryUiState.currentDetailPhoto
                    if (currentDetailPhoto != null) {
                        PhotoDetailScreen(
                            photos = photos,
                            initialPhoto = currentDetailPhoto,
                            onBack = { galleryViewModel.closePhotoDetail() },
                            onDelete = { galleryViewModel.deletePhoto(it) },
                            onShare = { photo ->
                                galleryViewModel.sharePhoto(photo) { intent ->
                                    context.startActivity(intent)
                                }
                            },
                            loadBitmap = { photoData, maxSize ->
                                galleryViewModel.loadBitmap(photoData, maxSize)
                            }
                        )
                    } else {
                        PhotoGalleryScreen(
                            groupedPhotos = groupedPhotos,
                            photoCount = photoCount,
                            uiState = galleryUiState,
                            onPhotoClick = { galleryViewModel.openPhotoDetail(it) },
                            onPhotoLongClick = { galleryViewModel.togglePhotoSelection(it.id) },
                            onToggleSelection = { galleryViewModel.togglePhotoSelection(it) },
                            onSelectAll = { galleryViewModel.selectAll() },
                            onClearSelection = { galleryViewModel.clearSelection() },
                            onDeleteSelected = { galleryViewModel.showDeleteConfirmDialog() },
                            onShareSelected = {
                                galleryViewModel.shareSelectedPhotos { intent ->
                                    context.startActivity(intent)
                                }
                            },
                            onClearAll = { galleryViewModel.showClearAllDialog() },
                            onBack = { navController.popBackStack() },
                            loadBitmap = { photoData, maxSize ->
                                galleryViewModel.loadBitmap(photoData, maxSize)
                            }
                        )
                    }

                    // Delete confirmation dialog
                    if (galleryUiState.showDeleteConfirmDialog) {
                        DeleteConfirmDialog(
                            count = galleryUiState.selectedPhotos.size,
                            onConfirm = { galleryViewModel.deleteSelectedPhotos() },
                            onDismiss = { galleryViewModel.hideDeleteConfirmDialog() }
                        )
                    }

                    // Clear all confirmation dialog
                    if (galleryUiState.showClearAllDialog) {
                        ClearAllConfirmDialog(
                            onConfirm = { galleryViewModel.clearAllPhotos() },
                            onDismiss = { galleryViewModel.hideClearAllDialog() }
                        )
                    }
                }

                composable(NavRoutes.CHAT) {
                    // Full Chat screen with conversation management
                    val conversationViewModel: ConversationViewModel = viewModel()
                    val conversations by conversationViewModel.conversations.collectAsState()
                    val currentConversationId by conversationViewModel.currentConversationId.collectAsState()
                    val currentMessages by conversationViewModel.currentMessages.collectAsState()
                    val currentConversation by conversationViewModel.currentConversation.collectAsState()
                    val chatUiState by conversationViewModel.uiState.collectAsState()
                    val inputText by conversationViewModel.inputText.collectAsState()
                    val context = LocalContext.current

                    // Reset conversation state when leaving chat tab
                    DisposableEffect(Unit) {
                        onDispose {
                            conversationViewModel.closeCurrentConversation()
                        }
                    }

                    if (currentConversationId != null) {
                        // Show chat screen
                        ChatScreen(
                            conversationTitle = currentConversation?.title ?: stringResource(R.string.new_conversation),
                            messages = currentMessages,
                            isLoading = chatUiState.isLoading,
                            error = chatUiState.error,
                            inputText = inputText,
                            onInputChange = { conversationViewModel.updateInputText(it) },
                            onSendMessage = { conversationViewModel.sendMessage() },
                            onClearError = { conversationViewModel.clearError() },
                            onBack = { conversationViewModel.closeCurrentConversation() },
                            onClearHistory = { conversationViewModel.clearCurrentConversation() },
                            onExport = {
                                conversationViewModel.exportCurrentConversation { intent ->
                                    context.startActivity(intent)
                                }
                            }
                        )
                    } else {
                        // Show conversation history
                        ConversationHistoryScreen(
                            conversations = conversations,
                            onConversationClick = { conversationViewModel.selectConversation(it) },
                            onNewConversation = { conversationViewModel.createNewConversation() },
                            onDeleteConversation = { conversationViewModel.deleteConversation(it) },
                            onArchiveConversation = { conversationViewModel.archiveConversation(it) },
                            onPinConversation = { conversationViewModel.pinConversation(it) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable(
                    route = NavRoutes.CONVERSATION_DETAIL,
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
                    val conversationViewModel: ConversationViewModel = viewModel()
                    val context = LocalContext.current

                    // Select the conversation
                    LaunchedEffect(conversationId) {
                        conversationViewModel.selectConversation(conversationId)
                    }

                    val currentMessages by conversationViewModel.currentMessages.collectAsState()
                    val currentConversation by conversationViewModel.currentConversation.collectAsState()
                    val chatUiState by conversationViewModel.uiState.collectAsState()
                    val inputText by conversationViewModel.inputText.collectAsState()

                    ChatScreen(
                        conversationTitle = currentConversation?.title ?: "",
                        messages = currentMessages,
                        isLoading = chatUiState.isLoading,
                        error = chatUiState.error,
                        inputText = inputText,
                        onInputChange = { conversationViewModel.updateInputText(it) },
                        onSendMessage = { conversationViewModel.sendMessage() },
                        onClearError = { conversationViewModel.clearError() },
                        onBack = { navController.popBackStack() },
                        onClearHistory = { conversationViewModel.clearCurrentConversation() },
                        onExport = {
                            conversationViewModel.exportCurrentConversation { intent ->
                                context.startActivity(intent)
                            }
                        }
                    )
                }

                composable(NavRoutes.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        onBack = { navController.popBackStack() },
                        onNavigateToLogViewer = { navController.navigate(NavRoutes.LOG_VIEWER) },
                        onNavigateToLlmParameters = { navController.navigate(NavRoutes.LLM_PARAMETERS) },
                        onNavigateToTtsSettings = { navController.navigate(NavRoutes.TTS_SETTINGS) }
                    )
                }

                composable(NavRoutes.LLM_PARAMETERS) {
                    LlmParametersScreen(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(NavRoutes.TTS_SETTINGS) {
                    TtsSettingsScreen(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(NavRoutes.LOG_VIEWER) {
                    LogViewerScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    // Check if initial setup is needed when settings are loaded
    LaunchedEffect(settings) {
        onCheckInitialSetup(settings.hasAnyApiKeyConfigured())
    }

    // Show initial setup dialog when no API key is configured
    if (uiState.showInitialSetup) {
        InitialSetupDialog(
            onGoToSettings = {
                onDismissInitialSetup()
                navController.navigate(NavRoutes.SETTINGS)
            },
            onDismiss = {
                onDismissInitialSetup()
            }
        )
    }

    // Show API key warning dialog when triggered by service
    if (uiState.showApiKeyWarning) {
        ApiKeyMissingDialog(
            settings = settings,
            onGoToSettings = {
                onDismissApiKeyWarning()
                navController.navigate(NavRoutes.SETTINGS)
            },
            onDismiss = {
                onDismissApiKeyWarning()
            }
        )
    }
}

/**
 * Conversation item data class
 */
data class ConversationItem(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Dialog shown when API keys are not configured
 */
@Composable
fun ApiKeyMissingDialog(
    settings: com.fxMedia.annotatePhone.data.ApiSettings,
    onGoToSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val chatValidation = settings.validateForChat()
    val speechValidation = settings.validateForSpeech()
    
    val hasChatIssue = chatValidation !is com.fxMedia.annotatePhone.data.SettingsValidationResult.Valid
    val hasSpeechIssue = speechValidation !is com.fxMedia.annotatePhone.data.SettingsValidationResult.Valid
    
    // Build the message based on issues
    val message = buildString {
        if (hasChatIssue) {
            when (chatValidation) {
                is com.fxMedia.annotatePhone.data.SettingsValidationResult.MissingApiKey -> {
                    append("• ")
                    append(stringResource(chatValidation.provider.displayNameResId))
                    append(" API Key ")
                    append(stringResource(R.string.api_key_not_set))
                    append("\n")
                }
                is com.fxMedia.annotatePhone.data.SettingsValidationResult.InvalidConfiguration -> {
                    append("• ")
                    append(chatValidation.message)
                    append("\n")
                }
                else -> {}
            }
        }
        if (hasSpeechIssue) {
            when (speechValidation) {
                is com.fxMedia.annotatePhone.data.SettingsValidationResult.MissingSpeechService -> {
                    append("• ")
                    append(stringResource(R.string.speech_service_not_configured))
                }
                else -> {}
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(stringResource(R.string.api_key_required))
        },
        text = {
            Column {
                Text(message)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.api_key_setup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onGoToSettings) {
                Text(stringResource(R.string.go_to_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.later))
            }
        }
    )
}

/**
 * Initial setup dialog shown when no API key is configured at all
 */
@Composable
fun InitialSetupDialog(
    onGoToSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(stringResource(R.string.initial_setup_title))
        },
        text = {
            Column {
                Text(stringResource(R.string.initial_setup_message))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.initial_setup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onGoToSettings) {
                Text(stringResource(R.string.setup_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.later))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PhoneMainScreenPreview() {
    RokidPhoneTheme {
        PhoneMainScreenContent(
            uiState = PhoneUiState(
                connectionState = ConnectionState.CONNECTED,
                connectedGlassesName = "Rokid Max",
                isServiceRunning = true,
                processingStatus = "Ready",
                conversations = listOf(
                    ConversationItem("user", "Hello!"),
                    ConversationItem("assistant", "Hi there, how can I help you?")
                )
            ),
            settings = ApiSettings(),
            onStartService = {},
            onStopService = {},
            onConnect = {},
            onDisconnect = {},
            onCapturePhoto = {},
            onStartPhoneRecording = {},
            onStartGlassesRecording = {},
            onPauseRecording = {},
            onStopRecording = {},
            onDismissInitialSetup = {},
            onDismissApiKeyWarning = {},
            onCheckInitialSetup = {},
            onSettingsChange = {}
        )
    }
}
