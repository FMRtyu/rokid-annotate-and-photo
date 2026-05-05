package com.fxMedia.AnnotateRokid.viewmodel

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fxMedia.AnnotateRokid.CameraAppScreen
import com.fxMedia.AnnotateRokid.ReviewSelection
import com.fxMedia.AnnotateRokid.R
import com.fxMedia.AnnotateRokid.sdk.CameraMode
import com.fxMedia.AnnotateRokid.sdk.CxrServiceManager
import com.fxMedia.AnnotateRokid.sdk.UnifiedCameraManager
import com.fxMedia.AnnotateRokid.service.BluetoothClientState
import com.fxMedia.AnnotateRokid.service.BluetoothSppClient
import com.fxMedia.AnnotateRokid.service.photo.ImageCompressor
import com.fxMedia.AnnotateRokid.service.photo.PhotoTransferProtocol
import com.fxMedia.AnnotateRokid.service.photo.createPhotoTransferProtocol
import com.fxMedia.rokidcommon.Constants
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.rokidcommon.protocol.Message
import com.fxMedia.rokidcommon.protocol.MessageType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream

data class GlassesUIState(
    // Connection state
    val isConnected: Boolean = false,
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val bluetoothState: BluetoothClientState = BluetoothClientState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val availableDevices: List<BluetoothDevice> = emptyList(),
    val cxrConnectedPhoneName: String? = null,

    // Display state
    val displayText: String = "",
    val hintText: String = "",
    val userTranscript: String = "",
    val aiResponse: String = "",

    // Pagination
    val currentPage: Int = 0,
    val totalPages: Int = 1,
    val isPaginated: Boolean = false,

    // Photo capture
    val isCapturingPhoto: Boolean = false,
    val photoTransferProgress: Float = 0f,

    // Live mode
    val isLiveModeActive: Boolean = false,
    val liveTranscription: String = ""
)

class GlassesViewModel(
    private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "GlassesViewModel"
        private const val MAX_CHARS_PER_PAGE = 120
        private const val MAX_LINES_PER_PAGE = 4
    }

    // ── Screen state (camera annotation) ─────────────────────────────────────

    private val _appScreen = MutableStateFlow<CameraAppScreen>(CameraAppScreen.Review)
    val appScreen: StateFlow<CameraAppScreen> = _appScreen.asStateFlow()

    /** Live transcription / AI reply text shown as overlay on the Annotating screen. */
    private val _liveAnnotation = MutableStateFlow("")
    val liveAnnotation: StateFlow<String> = _liveAnnotation.asStateFlow()

    /** Review screen selection (Retake vs Annotate) */
    private val _reviewSelection = MutableStateFlow(ReviewSelection.NONE)
    val reviewSelection: StateFlow<ReviewSelection> = _reviewSelection.asStateFlow()

    // ── UI state (connection, pagination, live mode) ─────────────────────────

    private val _uiState = MutableStateFlow(GlassesUIState())
    val uiState: StateFlow<GlassesUIState> = _uiState.asStateFlow()

    // ── Internal ─────────────────────────────────────────────────────────────

    private var fullAiResponse: String = ""
    private var responsePages: List<String> = emptyList()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    /** Retained between Review and Annotating screens.
     *  FIX: Use a dedicated field that tracks whether bitmap is available
     */
    private var lastCapturedBitmap: Bitmap? = null
    private var hasCapturedBitmap: Boolean = false  // Track if bitmap exists

    private val bluetoothClient = BluetoothSppClient(context, viewModelScope)
    private var cameraManager: UnifiedCameraManager? = null
    private var cxrServiceManager: CxrServiceManager? = null
    private var photoTransferProtocol: PhotoTransferProtocol? = null

    private val audioBuffer = ByteArrayOutputStream()

    // Live-mode
    private var isLiveModeActive = false
    private var videoStreamingJob: Job? = null
    private val videoFrameIntervalMs = 1000L
    private val videoFrameQuality = 50

    init {
        initializeBluetooth()
        initializeCamera()
        initializeCxrService()
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Review Screen Selection (using highlightBox pattern from quiz)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Select an option on the Review screen
     * Temple back → RETAKE
     * Temple forward → ANNOTATE
     * Temple tap → Confirm selection
     */
    fun selectReviewOption(selection: ReviewSelection) {
        _reviewSelection.value = selection
        Log.d(TAG, "Review selection: $selection")
    }

    /**
     * Short tap on Review screen → confirm selection and execute
     */
    fun confirmReviewSelection() {
        Log.d(TAG, "Confirming review selection: ${_reviewSelection.value}")
        when (_reviewSelection.value) {
            ReviewSelection.RETAKE -> retakePhoto()
            ReviewSelection.ANNOTATE -> startVerbalAnnotation()
            ReviewSelection.NONE -> {
                Log.w(TAG, "No selection made yet")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Screen-flow actions (camera annotation)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Short physical tap → context-sensitive primary action.
     *   Prompt     → capture photo
     *   Review     → confirm selection (retake or annotate)
     *   Annotating → finish annotation
     *   Capturing  → no-op (busy)
     */
    fun onPrimaryTap() {
        when (_appScreen.value) {
            is CameraAppScreen.Prompt -> retakePhoto()
            is CameraAppScreen.Review -> confirmReviewSelection()
            is CameraAppScreen.Annotating -> finishAnnotation()
            is CameraAppScreen.Capturing -> { /* busy — ignore */ }
        }
    }

    fun onNavigateUp() {
        if (_appScreen.value is CameraAppScreen.Review || _appScreen.value is CameraAppScreen.Prompt) {
            selectReviewOption(ReviewSelection.RETAKE)
        }
    }

    fun onNavigateDown() {
        if (_appScreen.value is CameraAppScreen.Review || _appScreen.value is CameraAppScreen.Prompt) {
            selectReviewOption(ReviewSelection.ANNOTATE)
        }
    }

    /** Return to Prompt and discard the last photo. */
    fun retakePhoto() {
        clearBitmap()
        _reviewSelection.value = ReviewSelection.NONE
        _appScreen.value = CameraAppScreen.Prompt
        //captureAndSendPhoto()
        Log.d(TAG, "capturing: capture")
    }

    /**
     * Switch to Annotating screen and begin recording.
     * The annotation overlay (liveAnnotation) is updated when the phone
     * replies with AI_RESPONSE_TEXT or LIVE_TRANSCRIPTION.
     *
     * FIX: Properly validate that we have a bitmap before proceeding
     */
    fun startVerbalAnnotation() {
        // FIX: Check if bitmap is actually available
//        if (!hasCapturedBitmap || lastCapturedBitmap == null) {
//            Log.w(TAG, "startVerbalAnnotation: no bitmap available, returning to Prompt")
//            _appScreen.value = CameraAppScreen.Prompt
//            return
//        }

        _liveAnnotation.value = ""
        _reviewSelection.value = ReviewSelection.NONE
        _appScreen.value = CameraAppScreen.Annotating
        startRecording()
        Log.d(TAG, "Starting verbal annotation…")
    }

    /**
     * Stop recording (sends audio to phone) or, if already stopped, return to Prompt.
     * The Annotating screen stays visible while the phone processes the audio;
     * the AI reply appears as the overlay text. The user then presses Done again
     * (or the physical tap) to go back to Prompt.
     *
     * FIX: Only clear bitmap when fully dismissing the annotation, not before sending audio
     */
    fun finishAnnotation() {
        if (_uiState.value.isListening) {
            stopRecording()
        } else {
            // Only clear bitmap when fully exiting the annotation flow
            clearBitmap()
            _liveAnnotation.value = ""
            _appScreen.value = CameraAppScreen.Review
            //_appScreen.value = CameraAppScreen.Prompt
//            _appScreen.value = if (hasCapturedBitmap && lastCapturedBitmap != null) {
//                CameraAppScreen.Review(lastCapturedBitmap!!)
//            } else {
//                CameraAppScreen.Prompt
//            }
            Log.d(TAG, "Annotation finished, returning to Prompt")
        }
    }

    /**
     * FIX: Centralized bitmap cleanup method
     */
    private fun clearBitmap() {
        lastCapturedBitmap?.recycle()
        lastCapturedBitmap = null
        hasCapturedBitmap = false
        Log.d(TAG, "Bitmap cleared")
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Photo capture
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Capture a photo, show local preview immediately, then send to phone for AI.
     *
     * Transitions:
     *   Prompt / any screen → Capturing → Review(bitmap)  [on success]
     *                                   → Prompt          [on failure]
     *
     * FIX: Ensure bitmap is properly retained after capture
     */
    fun captureAndSendPhoto() {
        if (_uiState.value.isCapturingPhoto) {
            Log.w(TAG, "Photo capture already in progress")
            return
        }

        val btConnected = _uiState.value.bluetoothState == BluetoothClientState.CONNECTED

        viewModelScope.launch {
            try {
                // ── Go to Capturing screen ──
                _appScreen.value = CameraAppScreen.Capturing
                _uiState.update {
                    it.copy(
                        isCapturingPhoto = true,
                        isProcessing = true,
                        displayText = "Capturing photo…",
                        hintText = "Please wait…"
                    )
                }

                // ── Capture raw bytes ──
                val rawImageData = cameraManager?.capturePhoto()

                if (rawImageData == null) {
                    Log.e(TAG, "capturePhoto returned null. Camera state: ${cameraManager?.cameraState?.value}")
                    _uiState.update {
                        it.copy(
                            isCapturingPhoto = false,
                            isProcessing = false,
                            displayText = "Capture failed",
                            hintText = "Please try again"
                        )
                    }
                    _appScreen.value = CameraAppScreen.Prompt
                    return@launch
                }

                // ── Decode to Bitmap for local display ──
                val bitmap = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(rawImageData, 0, rawImageData.size)
                }

                if (bitmap == null) {
                    Log.e(TAG, "Failed to decode bitmap")
                    _uiState.update { it.copy(isCapturingPhoto = false, isProcessing = false) }
                    _appScreen.value = CameraAppScreen.Prompt
                    return@launch
                }

                // FIX: Store bitmap and mark as available
                lastCapturedBitmap = bitmap
                hasCapturedBitmap = true
                Log.d(TAG, "Bitmap captured and stored: ${bitmap.width}x${bitmap.height}")

                // ── Show Review immediately ──
                _reviewSelection.value = ReviewSelection.NONE  // Reset selection
                _appScreen.value = CameraAppScreen.Review
                _uiState.update { it.copy(isCapturingPhoto = false) }

                // ── Send to phone in background (if connected) ──
                if (!btConnected) {
                    Log.w(TAG, "Bluetooth not connected — skipping transfer")
                    _uiState.update { it.copy(isProcessing = false) }
                    return@launch
                }

                _uiState.update { it.copy(displayText = "Compressing photo…") }

                val compressedData = withContext(Dispatchers.Default) {
                    ImageCompressor.compressForTransfer(rawImageData)
                }
                Log.d(TAG, "Compressed: ${rawImageData.size} → ${compressedData.size} bytes")

                _uiState.update {
                    it.copy(
                        displayText = "Transferring photo…",
                        photoTransferProgress = 0f
                    )
                }

                val socket = bluetoothClient.connectedSocket
                if (socket == null || !socket.isConnected) {
                    throw IllegalStateException("Bluetooth socket not connected")
                }

                photoTransferProtocol = socket.createPhotoTransferProtocol { current, total ->
                    _uiState.update { it.copy(photoTransferProgress = current.toFloat() / total) }
                }

                val result = photoTransferProtocol?.sendPhoto(compressedData)

                result?.fold(
                    onSuccess = { stats ->
                        Log.d(TAG, "Transfer complete: $stats")
                        _uiState.update {
                            it.copy(
                                displayText = "Photo sent, waiting for AI…",
                                hintText = "Please wait…"
                            )
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Transfer failed", error)
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                displayText = "Transfer failed: ${error.message ?: ""}",
                                hintText = "Please retry"
                            )
                        }
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Photo capture error", e)
                _uiState.update {
                    it.copy(
                        isCapturingPhoto = false,
                        isProcessing = false,
                        displayText = "Error: ${e.message ?: ""}",
                        hintText = "Please retry"
                    )
                }
                if (_appScreen.value is CameraAppScreen.Capturing) {
                    _appScreen.value = CameraAppScreen.Prompt
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Audio recording
    // ════════════════════════════════════════════════════════════════════════


    fun startRecording() {
        if (_uiState.value.bluetoothState != BluetoothClientState.CONNECTED) {
            _uiState.update {
                it.copy(
                    displayText = "Please connect phone",
                    hintText = "Select paired device"
                )
            }
            return
        }

        if (_uiState.value.isListening) return

        // Explicit permission check
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            _uiState.update {
                it.copy(
                    displayText = "Microphone permission required",
                    isListening = false
                )
            }
            return
        }

        audioBuffer.reset()
        resetPagination()

        _uiState.update {
            it.copy(
                isListening = true,
                displayText = "Listening…",
                hintText = "Tap to stop recording",
                userTranscript = "",
                aiResponse = ""
            )
        }

        viewModelScope.launch {
            bluetoothClient.sendVoiceStart()
        }

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    Constants.AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                // Re-check permission inside IO coroutine (lint requirement)
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "RECORD_AUDIO permission lost before AudioRecord init")
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                displayText = "Microphone permission required",
                                isListening = false
                            )
                        }
                    }
                    return@launch
                }

                // Suppress: permission is verified two lines above
                @Suppress("MissingPermission")
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    Constants.AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                audioRecord = recorder

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize")
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                displayText = "Failed to initialize microphone",
                                isListening = false
                            )
                        }
                    }
                    recorder.release()
                    audioRecord = null
                    return@launch
                }

                recorder.startRecording()
                Log.d(TAG, "AudioRecord started")

                val buffer = ByteArray(Constants.AUDIO_BUFFER_SIZE)
                while (isActive && _uiState.value.isListening) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        synchronized(audioBuffer) { audioBuffer.write(buffer, 0, read) }
                    }
                }

                Log.d(TAG, "Recording loop ended — ${audioBuffer.size()} bytes collected")

            } catch (e: SecurityException) {
                Log.e(TAG, "Microphone SecurityException", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            displayText = "Microphone permission required",
                            isListening = false
                        )
                    }
                }
            } finally {
                try {
                    if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord?.stop()
                    }
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "AudioRecord.stop() failed", e)
                }
                try {
                    audioRecord?.release()
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord.release() failed", e)
                }
                audioRecord = null
            }
        }
    }

    fun stopRecording() {
        _uiState.update {
            it.copy(
                isListening = false,
                isProcessing = true,
                displayText = "Sending audio…",
                hintText = "Please wait…"
            )
        }

        recordingJob?.cancel()
        recordingJob = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val audioData: ByteArray
                synchronized(audioBuffer) { audioData = audioBuffer.toByteArray() }

                Log.d(TAG, "Audio size: ${audioData.size} bytes")

                if (audioData.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                displayText = "No voice detected",
                                hintText = "Please try again"
                            )
                        }
                        // FIX: Go back to Review if we still have the photo (don't clear bitmap yet)
                        _appScreen.value = if (hasCapturedBitmap && lastCapturedBitmap != null) {
                            CameraAppScreen.Review
                        } else {
                            CameraAppScreen.Prompt
                        }
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(displayText = "Sending…") }
                }

                val success = bluetoothClient.sendVoiceEnd(audioData)

                if (!success) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                displayText = "Send failed",
                                hintText = "Reconnect and try again"
                            )
                        }
                    }
                    return@launch
                }

                Log.d(TAG, "Audio sent — awaiting AI response")

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            displayText = "Waiting for phone…",
                            hintText = "AI is thinking…"
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending audio", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            displayText = "Error: ${e.message ?: ""}",
                            hintText = "Please try again"
                        )
                    }
                }
            }
        }
    }

    fun toggleRecording() {
        if (_uiState.value.isListening) stopRecording() else startRecording()
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Phone message handler
    // ════════════════════════════════════════════════════════════════════════

    private fun handlePhoneMessage(message: Message) {
        Log.d(TAG, "From phone: ${message.type}")

        when (message.type) {

            MessageType.AI_PROCESSING -> {
                _uiState.update {
                    it.copy(
                        isProcessing = true,
                        displayText = message.payload ?: "Processing…"
                    )
                }
            }

            MessageType.USER_TRANSCRIPT -> {
                _uiState.update {
                    it.copy(
                        userTranscript = message.payload ?: "",
                        displayText = "You said: ${message.payload ?: ""}"
                    )
                }
            }

            MessageType.AI_RESPONSE_TEXT -> {
                val aiText = message.payload ?: ""
                if (_appScreen.value is CameraAppScreen.Annotating) {
                    // Route reply as annotation overlay; stay on Annotating screen
                    _liveAnnotation.value = aiText
                    _uiState.update { it.copy(isProcessing = false, aiResponse = aiText) }
                } else {
                    handleAiResponseText(message)
                }
            }

            MessageType.AI_RESPONSE_TTS -> {
                message.binaryData?.let { playAudio(it) }
            }

            MessageType.AI_ERROR -> {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        //displayText = "Error: ${message.payload ?: ""}",
                        displayText = message.payload ?: "",
                        hintText = "Please try again"
                    )
                }
            }

            MessageType.DISPLAY_TEXT -> {
                _uiState.update { it.copy(displayText = message.payload ?: "") }
            }

            MessageType.DISPLAY_CLEAR -> {
                _uiState.update {
                    it.copy(
                        displayText = "",
                        hintText = "Tap to start"
                    )
                }
            }

            MessageType.HEARTBEAT -> {
                viewModelScope.launch {
                    bluetoothClient.sendMessage(Message(type = MessageType.HEARTBEAT_ACK))
                }
            }

            MessageType.CAPTURE_PHOTO -> {
                Log.d(TAG, "Phone requested photo capture")
                captureAndSendPhoto()
            }

            MessageType.LIVE_SESSION_START -> {
                isLiveModeActive = true
                _uiState.update {
                    it.copy(
                        isLiveModeActive = true,
                        displayText = "🎙️ Live mode activated",
                        hintText = "Real-time voice conversation…",
                        liveTranscription = ""
                    )
                }
                startVideoStreaming()
            }

            MessageType.LIVE_SESSION_END -> {
                isLiveModeActive = false
                stopVideoStreaming()
                _uiState.update {
                    it.copy(
                        isLiveModeActive = false,
                        displayText = "Live mode ended",
                        hintText = "Tap to start",
                        liveTranscription = ""
                    )
                }
            }

            MessageType.LIVE_TRANSCRIPTION -> {
                val text = message.payload ?: ""
                _uiState.update { it.copy(liveTranscription = text, displayText = text) }
                if (_appScreen.value is CameraAppScreen.Annotating) {
                    _liveAnnotation.value = text
                }
            }

            MessageType.PHOTO_ANALYSIS_RESULT -> {
                val analysisText = message.payload ?: "No result"
                _uiState.update {
                    it.copy(isCapturingPhoto = false, photoTransferProgress = 0f, isProcessing = false)
                }
                if (_appScreen.value is CameraAppScreen.Annotating) {
                    _liveAnnotation.value = analysisText
                } else {
                    fullAiResponse = analysisText
                    responsePages = paginateText(analysisText)
                    val paginated = responsePages.size > 1
                    _uiState.update {
                        it.copy(
                            displayText = responsePages[0] + if (paginated) " (1/${responsePages.size})" else "",
                            hintText = if (paginated) "Swipe left/right for more pages"
                            else "Tap to start",
                            currentPage = 0,
                            totalPages = responsePages.size,
                            isPaginated = paginated
                        )
                    }
                }
            }

            MessageType.REMOTE_RECORD_START -> {
                if (!_uiState.value.isListening) startRecording()
            }

            MessageType.REMOTE_RECORD_STOP -> {
                if (_uiState.value.isListening) stopRecording()
            }

            MessageType.HEARTBEAT_ACK -> { /* handled by BluetoothSppClient */ }

            else -> Log.d(TAG, "Unhandled: ${message.type}")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Pagination helpers
    // ════════════════════════════════════════════════════════════════════════

    private fun handleAiResponseText(message: Message) {
        val responseText = message.payload ?: ""
        fullAiResponse = responseText
        responsePages = paginateText(responseText)

        val paginated = responsePages.size > 1
        val displayText = responsePages.firstOrNull() ?: responseText
        val hintText = if (paginated) "Swipe for more" else "Tap to continue"

        _uiState.update {
            it.copy(
                isProcessing = false,
                aiResponse = responseText,
                displayText = displayText,
                hintText = hintText,
                currentPage = 0,
                totalPages = responsePages.size,
                isPaginated = paginated
            )
        }
    }

    fun nextPage() {
        val s = _uiState.value
        if (s.isPaginated && s.currentPage < s.totalPages - 1) {
            val p = s.currentPage + 1
            _uiState.update {
                it.copy(
                    currentPage = p,
                    displayText = responsePages.getOrElse(p) { "" },
                    hintText = if (p == s.totalPages - 1) "Tap to continue"
                    else "Swipe for more"
                )
            }
        }
    }

    fun previousPage() {
        val s = _uiState.value
        if (s.isPaginated && s.currentPage > 0) {
            val p = s.currentPage - 1
            _uiState.update {
                it.copy(
                    currentPage = p,
                    displayText = responsePages.getOrElse(p) { "" },
                    hintText = "Swipe for more"
                )
            }
        }
    }

    fun dismissPagination() {
        resetPagination()
        _uiState.update {
            it.copy(
                displayText = "Tap to start",
                hintText = "Say 'Hey Rokid' or tap to record"
            )
        }
    }

    private fun resetPagination() {
        fullAiResponse = ""
        responsePages = emptyList()
        _uiState.update { it.copy(currentPage = 0, totalPages = 1, isPaginated = false) }
    }

    private fun paginateText(text: String): List<String> {
        if (text.length <= MAX_CHARS_PER_PAGE) return listOf(text)

        val pages = mutableListOf<String>()
        val words = text.split(" ", "，", "。", "、", "！", "？")
        var page = StringBuilder()
        var lines = 0
        var chars = 0

        for (word in words) {
            val ws = if (page.isEmpty()) word else " $word"
            if (chars + ws.length > MAX_CHARS_PER_PAGE || lines >= MAX_LINES_PER_PAGE) {
                if (page.isNotEmpty()) {
                    pages.add(page.toString().trim())
                    page = StringBuilder()
                    chars = 0
                    lines = 0
                }
            }
            page.append(ws)
            chars = page.length
            lines += ws.count { it == '\n' }
        }
        if (page.isNotEmpty()) pages.add(page.toString().trim())

        // Fallback: character-based split
        if (pages.isEmpty() || (pages.size == 1 && text.length > MAX_CHARS_PER_PAGE)) {
            pages.clear()
            var i = 0
            while (i < text.length) {
                val end = minOf(i + MAX_CHARS_PER_PAGE, text.length)
                var bp = end
                if (end < text.length) {
                    val nat = maxOf(
                        text.lastIndexOf(' ', end),
                        text.lastIndexOf('。', end),
                        text.lastIndexOf('，', end),
                        text.lastIndexOf('.', end),
                        text.lastIndexOf(',', end)
                    )
                    if (nat > i) bp = nat + 1
                }
                pages.add(text.substring(i, bp).trim())
                i = bp
            }
        }

        return pages
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Bluetooth helpers
    // ════════════════════════════════════════════════════════════════════════

    fun refreshPairedDevices() {
        val devices: List<BluetoothDevice> = if (
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothClient.getPairedDevices()
        } else {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted — cannot list paired devices")
            emptyList()
        }
        _uiState.update { it.copy(availableDevices = devices) }
        Log.d(TAG, "Found ${devices.size} paired devices")
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Cannot connect — BLUETOOTH_CONNECT not granted")
            return
        }
        Log.d(TAG, "Connecting to: ${device.name}")
        bluetoothClient.connect(device)
    }

    fun disconnectDevice() {
        bluetoothClient.disconnect()
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Initializers
    // ════════════════════════════════════════════════════════════════════════

    private fun initializeCxrService() {
        if (!CxrServiceManager.isSdkAvailable()) {
            Log.w(TAG, "CXR-S SDK not available")
            return
        }
        cxrServiceManager = CxrServiceManager.getInstance()
        if (cxrServiceManager?.initialize() != true) return

        viewModelScope.launch {
            cxrServiceManager?.connectionState?.collect { state ->
                when (state) {
                    is CxrServiceManager.ConnectionState.Connected ->
                        _uiState.update { it.copy(cxrConnectedPhoneName = state.deviceName) }
                    is CxrServiceManager.ConnectionState.Disconnected ->
                        _uiState.update { it.copy(cxrConnectedPhoneName = null) }
                }
            }
        }
    }

    private fun initializeCamera() {
        viewModelScope.launch {
            cameraManager = UnifiedCameraManager(context, preferredMode = CameraMode.CAMERA2)
            val result = cameraManager?.initialize()
            if (result?.isSuccess == true) {
                Log.d(TAG, "Camera ready: ${cameraManager?.getCameraTypeName()}")
            } else {
                Log.w(TAG, "Camera init failed: ${result?.exceptionOrNull()?.message}")
            }
        }
    }

    private fun initializeBluetooth() {
        viewModelScope.launch {
            bluetoothClient.connectionState.collect { state ->
                _uiState.update {
                    it.copy(
                        bluetoothState = state,
                        connectionState = when (state) {
                            BluetoothClientState.DISCONNECTED -> ConnectionState.DISCONNECTED
                            BluetoothClientState.CONNECTING -> ConnectionState.CONNECTING
                            BluetoothClientState.CONNECTED -> ConnectionState.CONNECTED
                        },
                        isConnected = state == BluetoothClientState.CONNECTED,
                        displayText = when (state) {
                            BluetoothClientState.DISCONNECTED -> "Not connected"
                            BluetoothClientState.CONNECTING -> "Connecting…"
                            BluetoothClientState.CONNECTED -> "Connected, ready"
                        },
                        hintText = when (state) {
                            BluetoothClientState.DISCONNECTED -> "Please connect phone"
                            BluetoothClientState.CONNECTING -> "Please wait…"
                            BluetoothClientState.CONNECTED -> "Tap to start"
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            bluetoothClient.connectedDeviceName.collect { name ->
                _uiState.update { it.copy(connectedDeviceName = name) }
            }
        }

        viewModelScope.launch {
            bluetoothClient.messageFlow.collect { message ->
                handlePhoneMessage(message)
            }
        }

        refreshPairedDevices()
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Live mode video streaming
    // ════════════════════════════════════════════════════════════════════════

    private fun startVideoStreaming() {
        if (videoStreamingJob?.isActive == true) return
        if (cameraManager == null) {
            Log.w(TAG, "No camera for streaming")
            return
        }

        videoStreamingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && isLiveModeActive) {
                try {
                    val raw = cameraManager?.capturePhoto()
                    if (raw != null) {
                        val frame = withContext(Dispatchers.Default) {
                            ImageCompressor.compressForTransfer(raw, 640, 480, videoFrameQuality)
                        }
                        bluetoothClient.sendMessage(
                            Message(type = MessageType.VIDEO_FRAME, binaryData = frame)
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Video frame error", e)
                }
                delay(videoFrameIntervalMs)
            }
        }
    }

    private fun stopVideoStreaming() {
        videoStreamingJob?.cancel()
        videoStreamingJob = null
    }

    private fun playAudio(audioData: ByteArray) {
        Log.d(TAG, "Playing audio: ${audioData.size} bytes")
        // TODO: AudioTrack playback
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Lifecycle & Factory
    // ════════════════════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        videoStreamingJob?.cancel()
        audioRecord?.release()
        //bluetoothClient.disconnect()
        cameraManager?.release()
        cxrServiceManager?.release()
        clearBitmap()  // FIX: Also clear bitmap on lifecycle cleanup
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GlassesViewModel::class.java)) {
                return GlassesViewModel(context.applicationContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}