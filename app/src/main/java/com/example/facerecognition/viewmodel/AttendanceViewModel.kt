package com.example.facerecognition.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.location.Location
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.facerecognition.data.repository.FirestoreRepository
import com.example.facerecognition.data.entity.Staff
import com.example.facerecognition.data.entity.AttendanceLog
import com.example.facerecognition.data.local.AppDatabase
import com.example.facerecognition.data.local.OfflineAttendanceLog
import com.example.facerecognition.ml.FaceDetectorHelper
import com.example.facerecognition.ml.FaceEmbedder
import com.example.facerecognition.ml.FaceMath
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface UiState {
        data object Scanning : UiState
        data class Recognised(val message: String) : UiState
        data object Unknown : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Scanning)
    val uiState: StateFlow<UiState> = _uiState

    private val firestoreRepository = FirestoreRepository()
    private val faceDetector = FaceDetectorHelper()
    private val faceEmbedder = FaceEmbedder(application)
    
    private val appDatabase by lazy { AppDatabase.getDatabase(application) }
    private val attendanceDao by lazy { appDatabase.attendanceDao() }

    private var cachedStaff: List<Staff> = emptyList()

    // Bug 4 Fix: Dynamic attendance rules fetched from Firestore
    private var attendanceRules: Map<String, Any>? = null

    companion object {
        private const val COOLDOWN_MS = 60_000L
        private const val LOCATION_POLL_INTERVAL_MS = 15_000L // Poll GPS every 15 seconds
        // BITS Narsampet campus coordinates
        private const val CAMPUS_LAT = 17.937320
        private const val CAMPUS_LNG = 79.849330
        // Strict Campus Boundaries
        private const val CAMPUS_RADIUS_METERS = 234.23f
    }

    private val cooldownMap = ConcurrentHashMap<String, Long>()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    // ── Cached GPS Location (polled every 15s, NOT per-frame) ────────────
    @Volatile
    private var cachedLocation: Location? = null
    private var locationJob: Job? = null

    @Volatile
    private var isProcessing = false

    init {
        loadEncodings()
        startLocationPolling()
        
        // Trigger offline sync automatically when ViewModel initializes
        viewModelScope.launch(Dispatchers.IO) {
            firestoreRepository.syncOfflineLogs(attendanceDao)
        }
    }

    /**
     * Polls GPS every [LOCATION_POLL_INTERVAL_MS] ms and stores the result in
     * [cachedLocation]. This runs for the lifetime of the ViewModel and is
     * cancelled in [onCleared]. Using a polling loop instead of per-frame
     * requests prevents GPS spam, battery drain, and inaccurate readings.
     */
    @SuppressLint("MissingPermission")
    private fun startLocationPolling() {
        locationJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val cancellationToken = CancellationTokenSource()
                    val location = fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationToken.token
                    ).await()

                    if (location != null) {
                        cachedLocation = location
                        Log.d("AttendanceVM", "Location cached: ${location.latitude}, ${location.longitude} | accuracy=${location.accuracy}m")
                    } else {
                        Log.w("AttendanceVM", "Location poll returned null — GPS may be off")
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceVM", "Location poll failed", e)
                }
                delay(LOCATION_POLL_INTERVAL_MS)
            }
        }
    }

    fun loadEncodings() {
        viewModelScope.launch(Dispatchers.IO) {
            cachedStaff = firestoreRepository.getAllApprovedStaff()
            Log.d("FaceDebug", "Loaded ${cachedStaff.size} approved staff")
            for (staff in cachedStaff) {
                Log.d("FaceDebug", "  Staff: ${staff.name}, embeddings count: ${staff.embeddings.size}, " +
                        "vector size: ${staff.embeddings.firstOrNull()?.vector?.size ?: 0}")
            }
            // Fetch dynamic attendance rules alongside staff encodings
            attendanceRules = firestoreRepository.getAttendanceRules()
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }
        isProcessing = true

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val bitmap = imageProxyToBitmap(imageProxy)
                if (bitmap == null) {
                    imageProxy.close()
                    isProcessing = false
                    return@launch
                }

                val faces = faceDetector.detectFaces(bitmap)

                if (faces.isEmpty()) {
                    _uiState.value = UiState.Scanning
                    imageProxy.close()
                    isProcessing = false
                    return@launch
                }

                val faceBitmap = faces[0]
                val embedding = faceEmbedder.getEmbedding(faceBitmap)
                val match = findClosestMatch(embedding)

                if (match != null) {
                    handleMatch(match.first, match.second)
                } else {
                    _uiState.value = UiState.Unknown
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Processing error")
            } finally {
                imageProxy.close()
                isProcessing = false
            }
        }
    }

    private fun findClosestMatch(embedding: FloatArray): Pair<Staff, Float>? {
        var bestSimilarity = -1.0f
        var bestStaff: Staff? = null

        Log.d("FaceDebug", "Comparing query embedding (size=${embedding.size}) against ${cachedStaff.size} staff")

        for (staff in cachedStaff) {
            if (staff.embeddings.isEmpty()) {
                Log.d("FaceDebug", "${staff.name}: SKIPPED (no embeddings)")
                continue
            }

            var maxSimilarityForStaff = -1.0f
            for (stored in staff.embeddings) {
                val storedEmbedding = stored.vector.toFloatArray()
                val similarity = FaceMath.cosineSimilarity(embedding, storedEmbedding)
                if (similarity > maxSimilarityForStaff) {
                    maxSimilarityForStaff = similarity
                }
            }

            Log.d("FaceDebug", "${staff.name} best similarity=$maxSimilarityForStaff")

            if (maxSimilarityForStaff > bestSimilarity) {
                bestSimilarity = maxSimilarityForStaff
                bestStaff = staff
            }
        }

        Log.d("FaceDebug", "Best match: ${bestStaff?.name} similarity=$bestSimilarity threshold=${FaceMath.MATCH_THRESHOLD}")

        return if (bestStaff != null && bestSimilarity > FaceMath.MATCH_THRESHOLD) {
            bestStaff to bestSimilarity
        } else {
            null
        }
    }

    /**
     * Parses a time string in "hh:mm AM/PM" format into a [java.time.LocalTime].
     * Returns null if the string is malformed, so callers can fall back to defaults.
     */
    private fun parseTimeString(timeStr: String): java.time.LocalTime? {
        return try {
            val trimmed = timeStr.trim()
            val parts = trimmed.split(":", " ")
            if (parts.size < 3) return null

            var hour = parts[0].toInt()
            val minute = parts[1].toInt()
            val amPm = parts[2].uppercase()

            if (amPm == "PM" && hour != 12) hour += 12
            if (amPm == "AM" && hour == 12) hour = 0

            java.time.LocalTime.of(hour, minute)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Bug 4 Fix: Evaluates the attendance status based on the current time.
     *
     * Reads dynamic thresholds from [attendanceRules] fetched from Firestore.
     * Falls back to hardcoded defaults if the rules haven't loaded yet or
     * if a specific field is missing/malformed.
     *
     * Time windows (defaults):
     *   Before 09:00         → INVALID (too early)
     *   09:00 – latePunch    → P  (Present)
     *   latePunch – 10:30    → LP (Late Present)
     *   10:30 – 12:40        → INVALID (gap)
     *   12:40 – halfDay      → HD (Half Day)
     *   halfDay – 15:10      → INVALID (gap)
     *   15:10 – 16:10        → EP (Early Present afternoon)
     *   16:10+               → P  (Present afternoon)
     */
    private fun evaluateAttendanceStatus(time: java.time.LocalTime): String {
        // ── Dynamic thresholds from Firestore (with safe hardcoded fallbacks) ──
        val latePunchTime = (attendanceRules?.get("latePunchTime") as? String)
            ?.let { parseTimeString(it) }
            ?: java.time.LocalTime.of(9, 35)   // Fallback: 09:35 AM

        val halfDayTime = (attendanceRules?.get("halfDayTime") as? String)
            ?.let { parseTimeString(it) }
            ?: java.time.LocalTime.of(13, 40)  // Fallback: 01:40 PM

        // ── Fixed boundaries (not yet configurable via settings) ──────────────
        val dayStart       = java.time.LocalTime.of(9, 0)    // 09:00 AM
        val maxLateFN      = java.time.LocalTime.of(10, 30)   // 10:30 AM
        val hdWindowStart  = java.time.LocalTime.of(12, 40)   // 12:40 PM
        val afternoonStart = java.time.LocalTime.of(15, 10)   // 03:10 PM
        val earlyLeaveEnd  = java.time.LocalTime.of(16, 10)   // 04:10 PM

        return when {
            time.isBefore(dayStart)       -> "INVALID"
            time.isBefore(latePunchTime)  -> "P"
            time.isBefore(maxLateFN)      -> "LP"
            time.isBefore(hdWindowStart)  -> "INVALID"
            time.isBefore(halfDayTime)    -> "HD"
            time.isBefore(afternoonStart) -> "INVALID"
            time.isBefore(earlyLeaveEnd)  -> "EP"
            else                          -> "P"
        }
    }

    /**
     * Checks Room and Firestore to ensure the staff hasn't already checked in today.
     * Passes the currentStaffId and explicitly filters the database by that ID.
     */
    private suspend fun checkIfCheckedInToday(staffId: String): Boolean {
        val zoneId = java.time.ZoneId.of("Asia/Kolkata")
        val now = java.time.LocalDate.now(zoneId)
        val startOfDay = now.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = now.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

        // 1. Check local Room database
        val localCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            attendanceDao.getLogCountForToday(staffId, startOfDay, endOfDay)
        }
        if (localCount > 0) return true

        // 2. Check Firestore
        return firestoreRepository.hasCheckedInToday(staffId, startOfDay, endOfDay)
    }

    /**
     * Calculates distance from the cached location to campus.
     * Returns null if no location has been cached yet.
     */
    private fun getDistanceFromCachedLocation(): Float? {
        val loc = cachedLocation ?: return null
        val campusLocation = Location("campus").apply {
            latitude = CAMPUS_LAT
            longitude = CAMPUS_LNG
        }
        return loc.distanceTo(campusLocation)
    }

    private suspend fun handleMatch(staff: Staff, similarity: Float) {
        val now = System.currentTimeMillis()
        val staffId = staff.id

        if (staffId.isEmpty()) {
            _uiState.value = UiState.Error("Invalid staff record. Please try registering again.")
            return
        }

        val lastSeen = cooldownMap[staffId] ?: 0L

        // 1. Debounce check FIRST to avoid spamming the database per frame
        if (now - lastSeen < 10_000L) { // 10 seconds debounce
            return
        }

        // 2. Database check for today's attendance
        if (checkIfCheckedInToday(staffId)) {
            cooldownMap[staffId] = now // Keep debounce active
            _uiState.value = UiState.Recognised("Welcome back ${staff.name}, you're already checked in for today.")
            return
        }

        // 2. Geofence check — uses cached location, no GPS call
        val distance = getDistanceFromCachedLocation()
        if (distance == null) {
            _uiState.value = UiState.Error("Waiting for GPS fix. Please try again in a moment.")
            return
        }
        if (distance > CAMPUS_RADIUS_METERS) {
            _uiState.value = UiState.Error(
                "Too far from campus. Must be within ${CAMPUS_RADIUS_METERS.toInt()}m. (Current: ${distance.toInt()}m away)"
            )
            return
        }
        Log.d("AttendanceVM", "Location OK: ${distance.toInt()}m from campus")

        // 3. Time enforcement — stamp cooldown only after location passes
        cooldownMap[staffId] = now
        val name = staff.name
        val currentTime = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"))
        var status = evaluateAttendanceStatus(currentTime)

        // TODO: Revert this time-check bypass before production
        if (status == "INVALID") {
            status = "P" // Override to Present for testing
        }

        if (status == "INVALID") {
            _uiState.value = UiState.Recognised("Welcome $name, but scanning is not allowed at this time.")
        } else {
            // 4. Log to Firestore
            val log = AttendanceLog(
                staffId = staffId,
                staffName = name,
                timestamp = now,
                status = status,
                locationValid = true
            )
            
            try {
                firestoreRepository.markAttendance(log)
                _uiState.value = UiState.Recognised("Welcome $name! Status: $status | Location: ✓")
            } catch (e: Exception) {
                // Offline Fallback
                val offlineLog = OfflineAttendanceLog(
                    staffId = staffId,
                    staffName = name,
                    timestamp = now,
                    status = status,
                    locationValid = true
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    attendanceDao.insertLog(offlineLog)
                }
                Log.e("AttendanceVM", "Network error. Saved log locally for ${name}. ${e.message}")
                _uiState.value = UiState.Recognised("Saved locally. Will sync when online. Status: $status")
            }
        }

        viewModelScope.launch {
            kotlinx.coroutines.delay(3_000L)
            _uiState.value = UiState.Scanning
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            90,
            out
        )

        val jpegBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: return null

        val rotation = imageProxy.imageInfo.rotationDegrees
        return if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        faceDetector.close()
        faceEmbedder.close()
    }
}