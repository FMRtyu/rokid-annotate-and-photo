package com.fxMedia.AnnotateRokid

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fxMedia.AnnotateRokid.service.WakeWordService
import com.fxMedia.AnnotateRokid.service.photo.CameraService
import com.fxMedia.AnnotateRokid.ui.theme.RokidGlassesTheme
import com.fxMedia.AnnotateRokid.viewmodel.GlassesViewModel
import com.fxMedia.AnnotateRokid.viewmodel.GlassesUIState
import android.view.KeyEvent

sealed class CameraAppScreen {
    object Prompt : CameraAppScreen()
    object Capturing : CameraAppScreen()
    object Review : CameraAppScreen()
    object Annotating : CameraAppScreen()
}

// Selection state for Review screen
enum class ReviewSelection {
    NONE,
    RETAKE,
    ANNOTATE
}

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var glassesViewModel: GlassesViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) startServices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        }

        checkPermissions()
        handleWakeUpIntent(intent)

        setContent {
            RokidGlassesTheme {
                val viewModel: GlassesViewModel = viewModel(
                    factory = GlassesViewModel.Factory(this)
                )
                glassesViewModel = viewModel

                GlassesMainWithCameraScreen(viewModel = viewModel)
            }
        }
    }

//    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
//        Log.d(
//            TAG,
//            "onKeyDown: keyCode=$keyCode (${KeyEvent.keyCodeToString(keyCode)}), scanCode=${event?.scanCode}, repeat=${event?.repeatCount}"
//        )
//
//        val viewModel = glassesViewModel ?: return super.onKeyDown(keyCode, event)
//
//        return when (keyCode) {
//            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
//                if (event?.repeatCount == 1) {
//                    Log.d(TAG, "Long press center = capture photo")
//                    viewModel.captureAndSendPhoto()
//                    true
//                } else if (event?.repeatCount == 0) {
//                    true
//                } else {
//                    true
//                }
//            }
//
//            KeyEvent.KEYCODE_CAMERA,
//            KeyEvent.KEYCODE_FOCUS,
//            27, 260, 261, 262, 263 -> {
//                Log.d(TAG, "Camera/Focus key pressed: $keyCode")
//                viewModel.captureAndSendPhoto()
//                true
//            }
//
//            KeyEvent.KEYCODE_BACK -> {
//                if (event?.repeatCount == 1) {
//                    Log.d(TAG, "Long press back = capture photo")
//                    viewModel.captureAndSendPhoto()
//                    true
//                } else {
//                    super.onKeyDown(keyCode, event)
//                }
//            }
//
//            else -> super.onKeyDown(keyCode, event)
//        }
//    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyUp: keyCode=$keyCode (${KeyEvent.keyCodeToString(keyCode)})")

        val viewModel = glassesViewModel ?: return super.onKeyUp(keyCode, event)

        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                val holdMs = (event?.eventTime ?: 0L) - (event?.downTime ?: 0L)
                if (holdMs < 500L) {
                    viewModel.onPrimaryTap()
                }
                true
            }

            // Temple back → Select Retake
            KeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.onNavigateUp()
                true
            }

            // Temple forward / Volume Down → Select Annotate
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.onNavigateDown()
                true
            }

            else -> super.onKeyUp(keyCode, event)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWakeUpIntent(intent)
    }

    private fun handleWakeUpIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("wake_up", false) == true) {
            Log.d(TAG, "Woke up by voice command")
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            Log.w(TAG, "Missing permissions: ${notGranted.joinToString(", ")}")
            permissionLauncher.launch(notGranted.toTypedArray())
        } else {
            Log.d(TAG, "All permissions granted")
            startServices()
        }
    }

    private fun startServices() {
        startWakeWordService()
        startCameraService()
    }

    private fun startCameraService() {
        if (!CameraService.isRunning) {
            val serviceIntent = Intent(this, CameraService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private fun startWakeWordService() {
        if (!WakeWordService.isRunning) {
            val serviceIntent = Intent(this, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
fun GlassesMainWithCameraScreen(viewModel: GlassesViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraScreen by viewModel.appScreen.collectAsState()
    val annotationText by viewModel.liveAnnotation.collectAsState()
    val reviewSelection by viewModel.reviewSelection.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        //main UI
        when {
            !uiState.isConnected -> {
                ConnectionScreen(
                    uiState = uiState,
                    onScreenTap = {
                        viewModel.onPrimaryTap()
                    },
                    onDeviceSelected = { device ->
                        viewModel.connectToDevice(device)
                    },
                    showDeviceSelector = uiState.showDeviceSelector,
                    onDismissDeviceSelector = { viewModel.dismissDeviceSelector() }
                )
            }
            else -> {
                CameraAnnotationScreen(
                    cameraScreen = cameraScreen,
                    annotationText = uiState.displayText,
                    connectedDeviceName = uiState.connectedDeviceName,
                    reviewSelection = reviewSelection,
                    onCapture = { viewModel.captureAndSendPhoto() },
                    onRetake = { viewModel.retakePhoto() },
                    onAnnotate = { viewModel.startVerbalAnnotation() },
                    onDone = { viewModel.finishAnnotation() },
                    onDisconnect = { viewModel.disconnectDevice() }
                )
            }
        }
        //TRY UI
//        CameraAnnotationScreen(
//            cameraScreen = cameraScreen,
//            annotationText = uiState.displayText,
//            connectedDeviceName = uiState.connectedDeviceName,
//            reviewSelection = reviewSelection,
//            onCapture = { viewModel.captureAndSendPhoto() },
//            onRetake = { viewModel.retakePhoto() },
//            onAnnotate = { viewModel.startVerbalAnnotation() },
//            onDone = { viewModel.finishAnnotation() },
//            onDisconnect = { viewModel.disconnectDevice() }
//        )
        AppVersionDisplay(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        )
    }
}

@Composable
fun AppVersionDisplay(modifier: Modifier = Modifier) {
    Text(
        text = "v${BuildConfig.VERSION_NAME}",
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 10.sp,
        modifier = modifier
    )
}

@Composable
fun ConnectionScreen(
    uiState: GlassesUIState,
    onScreenTap: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    showDeviceSelector: Boolean,
    onDismissDeviceSelector: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onScreenTap() }
    ) {
        StatusIndicator(
            isConnected = uiState.isConnected,
            isListening = uiState.isListening,
            deviceName = uiState.connectedDeviceName,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        MainDisplayArea(
            displayText = uiState.displayText,
            isProcessing = uiState.isProcessing,
            isPaginated = uiState.isPaginated,
            currentPage = uiState.currentPage,
            totalPages = uiState.totalPages,
            modifier = Modifier.align(Alignment.Center)
        )

        HintText(
            hint = uiState.hintText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )

        if (showDeviceSelector) {
            DeviceSelectorDialog(
                devices = uiState.availableDevices,
                selectedDeviceIndex = uiState.selectedDeviceIndex,
                lastConnectedAddress = uiState.lastConnectedAddress,
                cxrConnectedPhoneName = uiState.cxrConnectedPhoneName,
                onDeviceSelected = onDeviceSelected,
                onDismiss = onDismissDeviceSelector
            )
        }
    }
}

@Composable
fun CameraAnnotationScreen(
    cameraScreen: CameraAppScreen,
    annotationText: String,
    connectedDeviceName: String? = null,
    reviewSelection: ReviewSelection = ReviewSelection.NONE,
    onCapture: () -> Unit,
    onRetake: () -> Unit,
    onAnnotate: () -> Unit,
    onDone: () -> Unit,
    onDisconnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AnimatedContent(
            targetState = cameraScreen,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "camera_screen_transition"
        ) { currentScreen ->
            when (currentScreen) {
                is CameraAppScreen.Prompt -> CameraPromptScreen(onTap = onCapture)
                is CameraAppScreen.Capturing -> CameraCapturingScreen()
                is CameraAppScreen.Review -> CameraReviewScreenWithSelection(
                    reviewSelection = reviewSelection,
                    onRetake = onRetake,
                    onAnnotate = onAnnotate,
                    connectedDeviceName = connectedDeviceName
                )
                is CameraAppScreen.Annotating -> CameraAnnotatingScreen(
                    overlayText = annotationText,
                    onDone = onDone
                )
            }
        }
    }
}

@Composable
fun StatusIndicator(
    isConnected: Boolean,
    isListening: Boolean,
    deviceName: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(
                color = if (isConnected) Color(0xFF64B5F6) else Color(0xFFFF5722),
                label = if (isConnected) "Connected" else "Tap to connect"
            )

            AnimatedVisibility(
                visible = isListening,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                StatusDot(
                    color = Color(0xFFF44336),
                    label = "Recording"
                )
            }
        }

        if (isConnected && deviceName != null) {
            Text(
                text = deviceName,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun StatusDot(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = RoundedCornerShape(50))
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
    }
}

@Composable
fun MainDisplayArea(
    displayText: String,
    isProcessing: Boolean,
    isPaginated: Boolean = false,
    currentPage: Int = 0,
    totalPages: Int = 1,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = isProcessing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFF64B5F6),
                strokeWidth = 3.dp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = displayText,
            transitionSpec = {
                if (isPaginated) {
                    slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "display_text"
        ) { text ->
            Text(
                text = text,
                color = Color.White,
                fontSize = if (isPaginated) 20.sp else 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = if (isPaginated) 28.sp else 32.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isPaginated) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage > 0) {
                    Text(text = "▲", color = Color(0xFF64B5F6), fontSize = 16.sp)
                }
                if (currentPage < totalPages - 1) {
                    Text(text = "▼", color = Color(0xFF64B5F6), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun HintText(
    hint: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = hint,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
fun DeviceSelectorDialog(
    devices: List<BluetoothDevice>,
    selectedDeviceIndex: Int,
    lastConnectedAddress: String? = null,
    cxrConnectedPhoneName: String? = null,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedDeviceIndex) {
        if (devices.isNotEmpty()) {
            listState.animateScrollToItem(selectedDeviceIndex)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text(
                text = "Select phone",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (devices.isEmpty()) {
                Text(
                    text = "No paired devices\nPair a device in Bluetooth settings",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    itemsIndexed(devices) { index: Int, device: BluetoothDevice ->
                        @Suppress("MissingPermission")
                        val deviceName = device.name ?: "Unknown device"
                        @Suppress("MissingPermission")
                        val deviceAddress = device.address
                        val isSelected = index == selectedDeviceIndex
                        val isLastConnected = deviceAddress == lastConnectedAddress
                        val isRecommended = cxrConnectedPhoneName != null &&
                                deviceName.equals(cxrConnectedPhoneName, ignoreCase = true)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDeviceSelected(device) }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, Color.White, MaterialTheme.shapes.small)
                                    } else Modifier
                                ),
                            color = when {
                                isSelected -> Color(0xFF3D3D3D)
                                isRecommended -> Color(0xFF1E3A5F)
                                else -> Color(0xFF2A2A2A)
                            },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = deviceName, color = Color.White, fontSize = 16.sp)
                                    if (isRecommended) {
                                        Text(
                                            text = "Recommended",
                                            color = Color(0xFF64B5F6),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                if (isLastConnected) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Last connected",
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64B5F6))
            }
        }
    )
}

@Composable
fun ConnectionStatusBadge(
    connectedDeviceName: String? = null,
    //onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1E3A5F),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFF64B5F6), shape = RoundedCornerShape(50))
            )
            Text(
                text = connectedDeviceName ?: "Connected",
                color = Color.White,
                fontSize = 11.sp
            )
//            Text(
//                text = "✕",
//                color = Color.White.copy(alpha = 0.6f),
//                fontSize = 12.sp,
//                modifier = Modifier.clickable(onClick = onDisconnect)
//            )
        }
    }
}

@Composable
fun CameraPromptScreen(onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onTap
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tap once at the side of glasses\nto take a picture",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "or press the camera button",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CameraCapturingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Capturing…",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}

@Composable
fun CameraReviewScreenWithSelection(
    reviewSelection: ReviewSelection = ReviewSelection.NONE,
    onRetake: () -> Unit,
    onAnnotate: () -> Unit,
    connectedDeviceName: String? = null,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
//            if(isPhotoCaptured) {
//                Text(text = "📸 Image captured", color = Color.White, fontSize = 18.sp)
//            }

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Retake box
                    Box(
                        modifier = Modifier
                            .border(
                                width = 2.dp,
                                color = if (reviewSelection == ReviewSelection.RETAKE) Color(0xFF64B5F6) else Color.White,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                color = if (reviewSelection == ReviewSelection.RETAKE) Color(0xFF1A2A3A) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ).padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Take Photo", color = Color.White, fontWeight = if (reviewSelection == ReviewSelection.RETAKE) FontWeight.Bold else FontWeight.Normal)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Swipe Back", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    // Annotate box
                    Box(
                        modifier = Modifier
                            .border(
                                width = 2.dp,
                                color = if (reviewSelection == ReviewSelection.ANNOTATE) Color(0xFF64B5F6) else Color.White,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                color = if (reviewSelection == ReviewSelection.ANNOTATE) Color(0xFF1A2A3A) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ).padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Annotate", color = Color.White, fontWeight = if (reviewSelection == ReviewSelection.ANNOTATE) FontWeight.Bold else FontWeight.Normal)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Swipe Forward", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                if(reviewSelection != ReviewSelection.NONE)
                    Text(
                        text = "Tap glasses to confirm",
                        color = Color(0xFF64B5F6),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
            }
        }

        ConnectionStatusBadge(
            connectedDeviceName = connectedDeviceName,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
    }
}

@Composable
fun CameraAnnotatingScreen(overlayText: String, onDone: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xCC000000))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (overlayText.isNotBlank()) {
                Text(
                    text = overlayText,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp,
                    modifier = Modifier
                        .background(Color(0x66000000), shape = RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            RecordingIndicator()
        }

        TextButton(
            onClick = onDone,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text(
                text = "Tap right glasses when done",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Color(0xFFF44336).copy(alpha = alpha), RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Recording annotation…",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  PREVIEWS
// ═══════════════════════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewConnectionScreen() {
    RokidGlassesTheme {
        ConnectionScreen(
            uiState = GlassesUIState(
                isConnected = false,
                displayText = "Not connected",
                hintText = "Please connect phone"
            ),
            onScreenTap = {},
            onDeviceSelected = {},
            showDeviceSelector = false,
            onDismissDeviceSelector = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewCameraPromptScreen() {
    RokidGlassesTheme {
        CameraPromptScreen(onTap = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewCameraCapturingScreen() {
    RokidGlassesTheme {
        CameraCapturingScreen()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewCameraReviewScreen() {
    RokidGlassesTheme {
        CameraReviewScreenWithSelection(
            reviewSelection = ReviewSelection.NONE,
            onRetake = {},
            onAnnotate = {},
            connectedDeviceName = "Xiaomi"
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewCameraAnnotatingScreen() {
    RokidGlassesTheme {
        CameraAnnotatingScreen(
            overlayText = "This is a sample annotation from the AI assistant.",
            onDone = {}
        )
    }
}
