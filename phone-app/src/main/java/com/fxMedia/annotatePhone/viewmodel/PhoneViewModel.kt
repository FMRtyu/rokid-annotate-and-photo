package com.fxMedia.annotatePhone.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.rokidcommon.protocol.MessageType
import com.fxMedia.annotatePhone.ConversationItem
import com.fxMedia.annotatePhone.R
import com.fxMedia.annotatePhone.data.db.RecordingRepository
import com.fxMedia.annotatePhone.data.db.RecordingSource
import com.fxMedia.annotatePhone.data.db.RecordingState
import com.fxMedia.annotatePhone.service.BluetoothConnectionState
import com.fxMedia.annotatePhone.service.ServiceBridge
import com.fxMedia.annotatePhone.service.photo.PhotoRepository
import com.fxMedia.annotatePhone.service.photo.ReceivedPhoto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.File



private const val TAG = "PhoneViewModel"

data class PhoneUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val bluetoothState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED,
    val connectedGlassesName: String? = null,
    val isServiceRunning: Boolean = false,
    val processingStatus: String? = null,
    val conversations: List<ConversationItem> = emptyList(),
    val isScanning: Boolean = false,
    val availableDevices: List<String> = emptyList(),
    val showApiKeyWarning: Boolean = false,  // Flag to show API key warning dialog
    val showInitialSetup: Boolean = false,   // Flag to show initial setup dialog (no API key configured)
    val latestPhotoPath: String? = null,     // Path to the latest received photo
    val recordingState: RecordingState = RecordingState.Idle,  // Recording state
    val transcripts: List<String> = emptyList(), // List of last 5 transcripts
    val currentTranscriptIndex: Int = 0      // Current shown transcript index
)

class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    // Move state to companion object to allow static access from Service
    companion object {
        private val _uiState = MutableStateFlow(PhoneUiState())
        val uiState: StateFlow<PhoneUiState> = _uiState.asStateFlow()

        // Flow internal untuk mentrigger penyimpanan ke file
        private val _newTranscriptEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val newTranscriptEvent = _newTranscriptEvent.asSharedFlow()

        // Single function for the Service to call
        fun updateTranscript(text: String) {
            _uiState.update { state ->
                val newList = (listOf(text) + state.transcripts).take(5)
                state.copy(
                    transcripts = newList,
                    // Automatically move to the newest one
                    currentTranscriptIndex = 0
                )
            }
            _newTranscriptEvent.tryEmit(text)
        }

        fun getCurrentTranscript(): String {
            val state = _uiState.value
            return state.transcripts.getOrNull(state.currentTranscriptIndex)
                ?: "Start record the annotate"
        }
    }

    /**
     * Force refresh the photo history and transcripts from disk.
     * Useful when returning from other screens that might have modified data.
     */
    fun refreshPhotos() {
        Log.d(TAG, "Refreshing photos and transcripts from disk")
        _uiState.update { it.copy(currentTranscriptIndex = 0) }
        photoRepository.refresh()
    }

    // Instance getters for UI binding
    val uiState: StateFlow<PhoneUiState> = PhoneViewModel.uiState

    // Recording repository
    private val recordingRepository = RecordingRepository.getInstance(application, viewModelScope)

    private val photoRepository = PhotoRepository(application, viewModelScope)

    fun nextTranscript() {
        Log.d(TAG, "Next transcript")
        _uiState.update { state ->
            if (state.transcripts.isEmpty()) return@update state
            val newIndex = (state.currentTranscriptIndex + 1).coerceAtMost(state.transcripts.size - 1)
            state.copy(currentTranscriptIndex = newIndex)
        }
    }

    fun previousTranscript() {
        Log.d(TAG, "Previous transcript")
        _uiState.update { state ->
            if (state.transcripts.isEmpty()) return@update state
            val newIndex = (state.currentTranscriptIndex - 1).coerceAtLeast(0)
            state.copy(currentTranscriptIndex = newIndex)
        }
    }

    /**
     * Menyimpan daftar transkrip ke dalam satu file JSON (Maksimal 5).
     */
    private fun saveTranscripts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val file = File(context.filesDir, "transcripts.json")
                val transcripts = uiState.value.transcripts

                // Simpan seluruh list (yang sudah di-limit 5 di updateTranscript) ke satu file
                val json = Gson().toJson(transcripts)
                file.writeText(json)
                Log.d(TAG, "Transcripts saved to: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save transcripts", e)
            }
        }
    }

    fun clearTranscriptHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                // 1. Hapus file gabungan
                val file = File(context.filesDir, "transcripts.json")
                if (file.exists()) file.delete()

                // 2. Bersihkan folder lama (transcripts/) dari sampah file sebelumnya (opsional)
                val oldDir = File(context.filesDir, "transcripts")
                if (oldDir.exists()) oldDir.deleteRecursively()

                // 3. Reset UI
                _uiState.update { state ->
                    state.copy(transcripts = emptyList(), currentTranscriptIndex = 0)
                }
                Log.d(TAG, "All transcript history cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear transcript history", e)
            }
        }
    }

    init {
        // Listen to recording state
        viewModelScope.launch {
            recordingRepository.recordingState.collect { state ->
                _uiState.update { it.copy(recordingState = state) }
            }
        }

        // Listen to service state
        viewModelScope.launch {
            ServiceBridge.serviceStateFlow.collect { isRunning ->
                _uiState.update { it.copy(isServiceRunning = isRunning) }
            }
        }

        // Listen to Bluetooth connection state
        viewModelScope.launch {
            ServiceBridge.bluetoothStateFlow.collect { state ->
                Log.d(TAG, "Bluetooth state updated: $state")
                val connectionState = when (state) {
                    BluetoothConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
                    BluetoothConnectionState.LISTENING -> ConnectionState.DISCONNECTED
                    BluetoothConnectionState.CONNECTING -> ConnectionState.CONNECTING
                    BluetoothConnectionState.CONNECTED -> ConnectionState.CONNECTED
                }

                _uiState.update { it.copy(
                    bluetoothState = state,
                    connectionState = connectionState
                ) }
            }
        }

        // Listen to connected device name
        viewModelScope.launch {
            ServiceBridge.connectedDeviceNameFlow.collect { name ->
                Log.d(TAG, "Connected device name updated: $name")
                _uiState.update { it.copy(
                    connectedGlassesName = name
                ) }
            }
        }

        // Listen to API Key missing notifications
        viewModelScope.launch {
            ServiceBridge.apiKeyMissingFlow.collect {
                _uiState.update { it.copy(showApiKeyWarning = true) }
            }
        }

        // Listen to latest photo path for display
        viewModelScope.launch {
            ServiceBridge.latestPhotoPathFlow.collect { path ->
                Log.d(TAG, "New photo received, clearing old transcripts: $path")
                _uiState.update {
                    it.copy(
                        latestPhotoPath = path,
                        transcripts = emptyList(), // BERSIHKAN TRANSKRIP FOTO LAMA
                        currentTranscriptIndex = 0
                    )
                }
            }
        }

        // Listen to conversation messages (voice input from glasses and AI response)
        viewModelScope.launch {
            ServiceBridge.conversationFlow.collect { message ->
                when (message.type) {
                    MessageType.USER_TRANSCRIPT -> {
                        // User's speech
                        message.payload?.let { text ->
                            addConversation("user", text)
                        }
                    }
                    MessageType.AI_RESPONSE_TEXT -> {
                        // AI's response
                        message.payload?.let { text ->
                            addConversation("assistant", text)
                        }
                    }
                    MessageType.AI_PROCESSING -> {
                        // Update processing status
                        _uiState.update { it.copy(processingStatus = message.payload) }
                    }
                    else -> { /* Other types */ }
                }
            }
        }

        viewModelScope.launch {
            photoRepository.photoHistory.collect { history ->
                if (history.isNotEmpty()) {
                    val photo = history.first()
                    // Sinkronkan list transkrip UI agar HANYA berisi milik foto ini
                    _uiState.update {
                        it.copy(
                            latestPhotoPath = photo.filePath,
                            transcripts = photo.analysisResults, // LOAD HANYA MILIK FOTO B
                            currentTranscriptIndex = if (photo.filePath != it.latestPhotoPath) 0 else it.currentTranscriptIndex
                        )
                    }
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val file = File(context.filesDir, "transcripts.json")
                if (file.exists()) {
                    val json = file.readText()
                    val type = object : TypeToken<List<String>>() {}.type
                    val savedList: List<String> = Gson().fromJson(json, type)
                    _uiState.update { it.copy(transcripts = savedList) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load transcripts", e)
            }
        }

//        viewModelScope.launch {
//            newTranscriptEvent.collect {
//                saveTranscripts() // Simpan seluruh list terbaru
//            }
//        }

        viewModelScope.launch {
            newTranscriptEvent.collect { transcript ->
                val latestPath = uiState.value.latestPhotoPath
                if (latestPath != null) {
                    // Cari objek PhotoData yang cocok dengan path foto saat ini di history
                    val photoData = photoRepository.photoHistory.value.find { it.filePath == latestPath }

                    photoData?.let {
                        Log.d(TAG, "Binding transcript to photo ID: ${it.id}")
                        // Panggil fungsi rolling per-ID di Repository
                        photoRepository.saveAnalysisResult(it, transcript)
                    }
                }
            }
        }

        // Initialize with test data (runs once)
        // addSamplePhoto()
        // clearPhotoHistory()
        // clearTranscriptHistory()
//        if (uiState.value.transcripts.isEmpty()) {
//            updateTranscript("this is the transcript 01")
//            updateTranscript("this is the transcript 02")
//            updateTranscript("this is the transcript 03")
//            updateTranscript("this is the transcript 04")
//            updateTranscript("this is the transcript 05")
//            updateTranscript("this is the transcript 06")
//        }
    }

    /**
     * Menambahkan foto default (test) ke dalam repository.
     */
    fun addSamplePhoto() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                // Decode drawable menjadi bitmap
                val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.defaultphoto_2) ?: return@launch

                // Kompres ke ByteArray
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                val byteArray = stream.toByteArray()

                // Simulasikan seolah-olah diterima dari kacamata
                val receivedPhoto = ReceivedPhoto(
                    data = byteArray,
                    timestamp = System.currentTimeMillis(),
                    transferTimeMs = 0
                )

                // Simpan ke repo (ini akan otomatis mentrigger update di UI)
                photoRepository.processReceivedPhoto(receivedPhoto)
                Log.d(TAG, "Sample photo added for testing")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add sample photo", e)
            }
        }
    }

    /**
     * Menghapus semua riwayat foto di folder HP.
     */
    fun clearPhotoHistory() {
        viewModelScope.launch {
            photoRepository.clearAll()
            // Reset state agar tampilan di HomeScreen jadi kosong/default
            _uiState.update { it.copy(latestPhotoPath = null) }
            Log.d(TAG, "All photos cleared")
        }
    }

    /**
     * Request service to start Bluetooth listening
     * This restarts the server socket to accept new connections
     */
    fun startScanning() {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = ConnectionState.CONNECTING) }
            ServiceBridge.requestStartListening()
        }
    }

    /**
     * Request service to disconnect current Bluetooth connection
     * Note: UI state will be updated automatically through the bluetoothStateFlow
     */
    fun disconnect() {
        viewModelScope.launch {
            Log.d(TAG, "Requesting disconnect")
            ServiceBridge.requestDisconnect()
            // Don't manually override state here - let the flow update it naturally
            // This ensures UI stays in sync with actual Bluetooth state
        }
    }

    fun updateServiceStatus(isRunning: Boolean) {
        _uiState.update { it.copy(isServiceRunning = isRunning) }
    }

    fun updateProcessingStatus(status: String?) {
        _uiState.update { it.copy(processingStatus = status) }
    }

    fun addConversation(role: String, content: String) {
        _uiState.update { state ->
            state.copy(
                conversations = state.conversations + ConversationItem(role, content)
            )
        }
    }

    fun clearConversations() {
        _uiState.update { it.copy(conversations = emptyList()) }
    }

    fun dismissApiKeyWarning() {
        _uiState.update { it.copy(showApiKeyWarning = false) }
    }

    /**
     * Check if initial setup is needed (no API key configured at all)
     * Called from UI when settings are loaded
     */
    fun checkInitialSetup(hasAnyApiKey: Boolean) {
        if (!hasAnyApiKey) {
            _uiState.update { it.copy(showInitialSetup = true) }
        }
    }

    /**
     * Dismiss initial setup dialog
     */
    fun dismissInitialSetup() {
        _uiState.update { it.copy(showInitialSetup = false) }
    }

    /**
     * Request glasses to capture and send photo
     */
    fun requestCapturePhoto() {
        viewModelScope.launch {
            ServiceBridge.requestCapturePhoto()
        }
    }

    // ==================== Recording Control ====================

    /**
     * Start recording from phone microphone
     */
    fun startPhoneRecording() {
        viewModelScope.launch {
            val result = recordingRepository.startPhoneRecording()
            result.onFailure { error ->
                Log.e(TAG, "Failed to start phone recording", error)
            }
        }
    }

    /**
     * Start recording from glasses microphone
     */
    fun startGlassesRecording() {
        viewModelScope.launch {
            val result = recordingRepository.startGlassesRecording()
            result.onSuccess { recordingId ->
                // Send command to glasses to start recording
                ServiceBridge.requestStartGlassesRecording(recordingId)
            }
            result.onFailure { error ->
                Log.e(TAG, "Failed to start glasses recording", error)
            }
        }
    }

    /**
     * Pause current recording (if supported)
     */
    fun pauseRecording() {
        viewModelScope.launch {
            recordingRepository.pauseRecording()
        }
    }

    /**
     * Stop current recording and send to AI for analysis
     */
    fun stopRecording() {
        viewModelScope.launch {
            val result = recordingRepository.stopRecording()
            result.onSuccess { recording ->
                recording?.let {
                    Log.d(TAG, "Recording stopped: ${it.id}, source: ${it.source}, duration: ${it.durationMs}ms")

                    // Only request transcription for phone recordings
                    // Glasses recordings are processed via Bluetooth when the audio data arrives
                    if (it.source == RecordingSource.PHONE && it.filePath.isNotBlank()) {
                        ServiceBridge.requestTranscribeRecording(it.id, it.filePath)
                    } else if (it.source == RecordingSource.GLASSES) {
                        Log.d(TAG, "Glasses recording stopped, sending stop command and waiting for audio via Bluetooth")
                        // Send stop command to glasses - audio data will be received via Bluetooth
                        // The processVoiceData() in PhoneAIService will handle transcription
                        ServiceBridge.requestStopGlassesRecording()
                    }
                }
            }
            result.onFailure { error ->
                Log.e(TAG, "Failed to stop recording", error)
            }
        }
    }
}
