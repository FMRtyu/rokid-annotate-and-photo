package com.fxMedia.annotatePhone.service.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Photo Repository
 *
 * Manages received photos from glasses:
 * - Decodes and validates JPEG data
 * - Stores photos locally
 * - Provides access for AI analysis
 * - Handles cleanup of old photos
 */
class PhotoRepository(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "PhotoRepository"

        // Directory for storing received photos
        private const val PHOTO_DIR = "glasses_photos"

        // Maximum photos to keep
        private const val MAX_STORED_PHOTOS = 50

        // Maximum transcript entries to persist (rolling window)
        private const val MAX_TRANSCRIPT_ENTRIES = 5

        // Photo file name format
        private const val PHOTO_NAME_FORMAT = "photo_%s.jpg"
        private const val DATE_FORMAT = "yyyyMMdd_HHmmss_SSS"
        private const val TRANSCRIPTS_FILE = "transcripts_db.json"
    }

    // Current photo being processed
    private val _currentPhoto = MutableStateFlow<PhotoData?>(null)
    val currentPhoto: StateFlow<PhotoData?> = _currentPhoto.asStateFlow()

    // Photo history
    private val _photoHistory = MutableStateFlow<List<PhotoData>>(emptyList())
    val photoHistory: StateFlow<List<PhotoData>> = _photoHistory.asStateFlow()

    // SharedFlow for new photo events - used by ImageAnalysisViewModel
    private val _photoFlow = MutableSharedFlow<PhotoData>(replay = 0, extraBufferCapacity = 1)
    val photoFlow: SharedFlow<PhotoData> = _photoFlow.asSharedFlow()

    // Photo storage directory
    private val photoDir: File by lazy {
        File(context.filesDir, PHOTO_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    // Mutex to prevent concurrent transcript writes
    private val transcriptMutex = kotlinx.coroutines.sync.Mutex()

    // Flag to track if initial load is complete
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    init {
        // Load photo history on init - wait for completion
        scope.launch(Dispatchers.IO) {
            try {
                loadPhotoHistory()
                _isInitialized.value = true
                Log.d(TAG, "PhotoRepository initialization complete")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize PhotoRepository", e)
                _isInitialized.value = true // Mark as done even on error
            }
        }
    }

    /**
     * Processes received photo data from glasses.
     * Decodes JPEG, saves to disk, creates PhotoData, emits to observers
     *
     * @param receivedPhoto The received photo data with raw JPEG bytes
     * @return PhotoData if successful, null if decoding failed
     */
    suspend fun processReceivedPhoto(receivedPhoto: ReceivedPhoto): PhotoData? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing received photo: ${receivedPhoto.data.size} bytes")

                // Step 1: Decode JPEG to verify it's valid
                val bitmap = BitmapFactory.decodeByteArray(
                    receivedPhoto.data,
                    0,
                    receivedPhoto.data.size
                )

                if (bitmap == null) {
                    Log.e(TAG, "Failed to decode JPEG - invalid image data")
                    return@withContext null
                }

                Log.d(TAG, "Decoded photo: ${bitmap.width}x${bitmap.height}")

                // Step 2: Generate unique filename from timestamp
                val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
                val timestamp = dateFormat.format(Date(receivedPhoto.timestamp))
                val fileName = String.format(PHOTO_NAME_FORMAT, timestamp)
                val photoFile = File(photoDir, fileName)

                // Step 3: Write JPEG bytes to disk
                FileOutputStream(photoFile).use { fos ->
                    fos.write(receivedPhoto.data)
                    fos.flush()
                }

                Log.d(TAG, "Saved photo to: ${photoFile.absolutePath}")

                // Step 4: Create immutable PhotoData object with metadata
                val photoData = PhotoData(
                    id = photoFile.nameWithoutExtension,
                    filePath = photoFile.absolutePath,
                    timestamp = receivedPhoto.timestamp, // Use original timestamp, not file's lastModified
                    width = bitmap.width,
                    height = bitmap.height,
                    sizeBytes = receivedPhoto.data.size,
                    transferTimeMs = receivedPhoto.transferTimeMs,
                    analysisResults = emptyList() // Will be filled by analysis later
                )

                // Step 5: Update current photo (UI will observe this)
                _currentPhoto.value = photoData

                // Step 6: Emit to photoFlow for ImageAnalysisViewModel subscription
                _photoFlow.tryEmit(photoData)

                // Step 7: Add to history (prepend to show newest first)
                updateHistory(photoData)

                // Step 8: Optional - cleanup old photos to stay within limit
                // cleanupOldPhotos()

                // Step 9: Cleanup bitmap to free memory
                bitmap.recycle()

                Log.d(TAG, "Photo processing complete: ${photoData.id}")
                photoData

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process photo", e)
                null
            }
        }
    }

    /**
     * Gets the bitmap for a photo.
     * Optionally downsamples to maxSize for memory efficiency
     *
     * @param photoData The photo data containing file path
     * @param maxSize Maximum dimension (width or height) - null means full size
     * @return Bitmap or null if loading failed
     */
    suspend fun getBitmap(photoData: PhotoData, maxSize: Int? = null): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(photoData.filePath)
                if (!file.exists()) {
                    Log.e(TAG, "Photo file not found: ${photoData.filePath}")
                    return@withContext null
                }

                val options = BitmapFactory.Options()

                if (maxSize != null) {
                    // First pass: decode bounds only (fast, no memory allocation)
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(file.absolutePath, options)

                    // Calculate sample size for downsampling
                    val maxDim = maxOf(options.outWidth, options.outHeight)
                    options.inSampleSize = (maxDim / maxSize).coerceAtLeast(1)
                    options.inJustDecodeBounds = false
                }

                // Second pass: actual decode with downsampling applied
                BitmapFactory.decodeFile(file.absolutePath, options)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bitmap: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Gets the raw JPEG bytes for a photo.
     * Used for sending to AI analysis or export
     *
     * @param photoData The photo data containing file path
     * @return ByteArray or null if loading failed
     */
    suspend fun getPhotoBytes(photoData: PhotoData): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(photoData.filePath)
                if (!file.exists()) {
                    Log.e(TAG, "Photo file not found: ${photoData.filePath}")
                    return@withContext null
                }
                file.readBytes()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read photo bytes: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Deletes a specific photo and its metadata.
     * Removes from disk, JSON database, and in-memory history
     *
     * @param photoData The photo to delete
     */
    suspend fun deletePhoto(photoData: PhotoData) {
        withContext(Dispatchers.IO) {
            try {
                // Step 1: Delete physical file from disk
                val photoFile = File(photoData.filePath)
                if (!photoFile.delete()) {
                    Log.w(TAG, "Failed to delete photo file: ${photoData.filePath}")
                }

                // Step 2: Remove from transcripts JSON database (if entry exists)
                transcriptMutex.withLock {
                    val dbFile = File(photoDir, TRANSCRIPTS_FILE)
                    if (dbFile.exists()) {
                        try {
                            val listType = object : TypeToken<MutableList<MutableMap<String, Any>>>() {}.type
                            val list: MutableList<MutableMap<String, Any>> =
                                Gson().fromJson(dbFile.readText(), listType) ?: mutableListOf()

                            // Remove entry matching this photo's ID
                            if (list.removeAll { it["id"] == photoData.id }) {
                                dbFile.writeText(Gson().toJson(list))
                                Log.d(TAG, "Removed transcript entry for: ${photoData.id}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error removing from transcripts: ${e.message}")
                        }
                    }
                }

                // Step 3: Remove from in-memory history
                val currentList = _photoHistory.value.toMutableList()
                currentList.removeAll { it.id == photoData.id }
                _photoHistory.value = currentList

                // Step 4: Clear current photo if it matches
                if (_currentPhoto.value?.id == photoData.id) {
                    _currentPhoto.value = null
                }

                Log.d(TAG, "Deleted photo and metadata: ${photoData.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete photo: ${e.message}", e)
            }
        }
    }

    /**
     * Clears all stored photos and history.
     * Removes all files from disk and clears in-memory state
     */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            try {
                // Step 1: Delete all files in photo directory
                photoDir.listFiles()?.forEach {
                    if (!it.delete()) {
                        Log.w(TAG, "Failed to delete photo file: ${it.absolutePath}")
                    }
                }

                // Step 2: Clear in-memory state
                _photoHistory.value = emptyList()
                _currentPhoto.value = null

                Log.d(TAG, "Cleared all photos and history")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear photos: ${e.message}", e)
            }
        }
    }

    /**
     * Public method to refresh photo history from disk.
     * Useful when multiple repository instances might be used (though singleton is preferred).
     */
    fun refresh() {
        scope.launch(Dispatchers.IO) {
            loadPhotoHistory()
        }
    }

    /**
     * Loads photo history from storage on app startup.
     * Scans disk for .jpg files and loads matching transcripts from JSON database
     */
    private fun loadPhotoHistory() {
        try {
            // Step 1: Read central transcripts database once
            val dbFile = File(photoDir, TRANSCRIPTS_FILE)
            val transcriptMap = mutableMapOf<String, MutableList<Pair<Long, String>>>()

            if (!dbFile.exists()) {
                dbFile.writeText("[]")
                Log.d(TAG, "$TRANSCRIPTS_FILE not found, creating new empty file")
            }

            if (dbFile.exists()) {
                val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val list: List<Map<String, Any>> = Gson().fromJson(dbFile.readText(), listType)
                list.forEach { entry ->
                    val id = entry["id"] as? String
                    val value = entry["value"] as? String
                    val ts = (entry["timestamp"] as? Number)?.toLong() ?: 0L
                    if (id != null && value != null) {
                        transcriptMap.getOrPut(id) { mutableListOf() }.add(ts to value)
                    }
                }
            }

            // Step 2: Scan disk for .jpg files and attach transcript metadata
            val photos = photoDir.listFiles()
                ?.filter { it.isFile && it.extension == "jpg" }
                ?.sortedByDescending { it.lastModified() }
                ?.mapNotNull { file ->
                    try {
                        // Decode bounds only (fast metadata read)
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, options)

                        // Validate that decode succeeded
                        if (options.outWidth == -1 || options.outHeight == -1) {
                            Log.w(TAG, "Skipping corrupted photo file: ${file.name}")
                            return@mapNotNull null
                        }

                        val photoId = file.nameWithoutExtension

                        // Create PhotoData with transcript lookup result
                        PhotoData(
                            id = photoId,
                            filePath = file.absolutePath,
                            timestamp = file.lastModified(),
                            width = options.outWidth,
                            height = options.outHeight,
                            sizeBytes = file.length().toInt(),
                            transferTimeMs = 0,
                            analysisResults = transcriptMap[photoId]
                                ?.sortedByDescending { it.first }
                                ?.map { it.second } ?: emptyList()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading photo metadata for: ${file.name}: ${e.message}")
                        null
                    }
                } ?: emptyList()

            _photoHistory.value = photos
            Log.d(TAG, "Loaded ${photos.size} photos from disk")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load history: ${e.message}", e)
        }
    }

    /**
     * Updates photo history with new photo.
     * Prepends to list so newest photos appear first
     *
     * @param photoData The new photo to add
     */
    private fun updateHistory(photoData: PhotoData) {
        val currentList = _photoHistory.value.toMutableList()
        currentList.add(0, photoData) // Add at beginning for newest-first ordering
        _photoHistory.value = currentList
    }

    /**
     * Removes old photos to stay within storage limit.
     * Keeps MAX_STORED_PHOTOS most recent, deletes oldest
     */
    private fun cleanupOldPhotos() {
        val currentList = _photoHistory.value
        if (currentList.size > MAX_STORED_PHOTOS) {
            val toRemove = currentList.drop(MAX_STORED_PHOTOS)
            toRemove.forEach { photo ->
                try {
                    val photoFile = File(photo.filePath)
                    if (photoFile.exists()) photoFile.delete()

                    // Also clean up transcript entry if it exists in JSON
                    scope.launch(Dispatchers.IO) {
                        deletePhoto(photo)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cleanup old photo: ${photo.id}")
                }
            }
            _photoHistory.value = currentList.take(MAX_STORED_PHOTOS)
        }
    }

    /**
     * Saves analysis result for a photo to persistent JSON database.
     * Keeps rolling window of MAX_TRANSCRIPT_ENTRIES (oldest entries auto-deleted)
     * Thread-safe via Mutex to prevent concurrent write corruption
     *
     * @param photoData The photo that was analyzed
     * @param result The analysis transcript/text result
     */
    suspend fun saveAnalysisResult(photoData: PhotoData, result: String) {
        transcriptMutex.withLock {
            try {
                val gson = Gson()
                val dbFile = File(photoDir, TRANSCRIPTS_FILE)

                val listType = object : TypeToken<MutableList<MutableMap<String, Any>>>() {}.type
                val allTranscripts: MutableList<MutableMap<String, Any>> = if (dbFile.exists()) {
                    gson.fromJson(dbFile.readText(), listType) ?: mutableListOf()
                } else mutableListOf()

                // new entry
                val newEntry = mutableMapOf<String, Any>(
                    "id" to photoData.id,
                    "value" to result,
                    "timestamp" to System.currentTimeMillis()
                )
                allTranscripts.add(newEntry)

                // ROLLING LOGIC: Cari semua teks milik foto ini
                val myTranscripts = allTranscripts.filter { it["id"] == photoData.id }
                    .sortedBy { (it["timestamp"] as? Number)?.toLong() ?: 0L }

                // Jika milik ID ini > 5, hapus yang tertua milik ID ini saja
                if (myTranscripts.size > 5) {
                    val oldestForThisPhoto = myTranscripts.first()
                    allTranscripts.remove(oldestForThisPhoto)
                }

                withContext(Dispatchers.IO) {
                    dbFile.writeText(gson.toJson(allTranscripts))
                }

                // Sync ke memori: update list transkrip di objek PhotoData terkait
                val updatedResults = allTranscripts
                    .filter { it["id"] == photoData.id }
                    .sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }
                    .map { it["value"].toString() }

                val currentHistory = _photoHistory.value.toMutableList()
                val index = currentHistory.indexOfFirst { it.id == photoData.id }
                if (index != -1) {
                    currentHistory[index] = currentHistory[index].copy(analysisResults = updatedResults)
                    _photoHistory.value = currentHistory
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save transcript: ${e.message}")
            }
        }
    }
}

/**
 * Data class representing a stored photo.
 */
data class PhotoData(
    val id: String,
    val filePath: String,
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Int,
    val transferTimeMs: Long,
    var analysisResults: List<String> = emptyList()
) {
    val formattedSize: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
        }

    val formattedDimensions: String
        get() = "${width}x${height}"

    val formattedTimestamp: String
        get() {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}