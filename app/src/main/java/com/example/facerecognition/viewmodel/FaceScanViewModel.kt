package com.example.facerecognition.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.facerecognition.data.remote.FaceAttendApiService
import com.example.facerecognition.data.remote.LocationPayload
import com.example.facerecognition.data.remote.RecognizeRequest
import com.example.facerecognition.data.remote.RecognizeResponse
import com.example.facerecognition.data.remote.RetrofitClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "FaceScanVM"

/**
 * V2 Attendance ViewModel — sends the Base64 face image + GPS location
 * to the Flask backend via Retrofit and exposes the result as a
 * [StateFlow] of [AttendanceUiState].
 *
 * This is intentionally separate from the V1 [AttendanceViewModel]
 * (on-device ML pipeline) so both flows can coexist during migration.
 */
class FaceScanViewModel(application: Application) : AndroidViewModel(application) {

    // ── UI State ───────────────────────────────────────────────────

    /**
     * Sealed hierarchy representing every state the attendance
     * submission flow can be in.
     */
    sealed interface AttendanceUiState {
        /** Screen is ready; no submission in progress. */
        data object Idle : AttendanceUiState

        /** Retrofit call is in-flight. */
        data object Loading : AttendanceUiState

        /** Backend returned a successful attendance mark. */
        data class Success(
            val staffName: String,
            val status: String,
            val message: String
        ) : AttendanceUiState

        /** Device is outside the campus geofence. */
        data class OutOfBounds(val message: String) : AttendanceUiState

        /** Any other error (network, server, conversion). */
        data class Error(val message: String) : AttendanceUiState
    }

    private val _uiState = MutableStateFlow<AttendanceUiState>(AttendanceUiState.Idle)
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    // ── Dependencies ───────────────────────────────────────────────

    private val apiService: FaceAttendApiService = RetrofitClient.apiService

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(application)

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Submits an attendance scan to the V2 backend.
     *
     * 1. Pulls the device's current GPS fix via [fusedLocationClient].
     * 2. Fires a Retrofit POST to `/api/recognize` with the Base64
     *    image and GPS coordinates.
     * 3. Maps the [RecognizeResponse] to the appropriate [AttendanceUiState].
     *
     * @param base64Image The full data-URI string produced by
     *                    [convertImageProxyToBase64Uri] — e.g.
     *                    `data:image/jpeg;base64,/9j/4AAQ…`
     */
    fun submitAttendance(base64Image: String) {
        _uiState.value = AttendanceUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // ── 1. Acquire GPS location ────────────────────────
                val location = fetchCurrentLocation()
                if (location == null) {
                    _uiState.value = AttendanceUiState.Error(
                        "Unable to obtain GPS location. " +
                                "Please ensure Location is enabled and try again."
                    )
                    return@launch
                }

                Log.d(TAG, "GPS acquired: ${location.latitude}, ${location.longitude}")

                // ── 2. Build request payload ───────────────────────
                val request = RecognizeRequest(
                    image = base64Image,
                    location = LocationPayload(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                )

                // ── 3. Fire Retrofit call ──────────────────────────
                val response = apiService.submitFaceScan(request)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        handleApiResponse(body)
                    } else {
                        _uiState.value = AttendanceUiState.Error(
                            "Server returned an empty response."
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "HTTP ${response.code()}: $errorBody")
                    _uiState.value = AttendanceUiState.Error(
                        "Server error (${response.code()}). Please try again."
                    )
                }

            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Connection refused", e)
                _uiState.value = AttendanceUiState.Error(
                    "Cannot reach the server. Check your network or server status."
                )
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Request timed out", e)
                _uiState.value = AttendanceUiState.Error(
                    "Request timed out. The server may be busy — please retry."
                )
            } catch (e: Exception) {
                Log.e(TAG, "submitAttendance failed", e)
                _uiState.value = AttendanceUiState.Error(
                    e.localizedMessage ?: "An unexpected error occurred."
                )
            }
        }
    }

    /**
     * Resets the UI state to [AttendanceUiState.Idle].
     * Call this after the user dismisses a success dialog or error message.
     */
    fun resetState() {
        _uiState.value = AttendanceUiState.Idle
    }

    // ── Private helpers ────────────────────────────────────────────

    /**
     * Maps the backend [RecognizeResponse] to the corresponding
     * [AttendanceUiState], with explicit handling for the
     * `OUT_OF_BOUNDS` error code.
     */
    private fun handleApiResponse(response: RecognizeResponse) {
        Log.d(TAG, "API response: status=${response.status}, " +
                "name=${response.staffName}, errorCode=${response.errorCode}")

        when {
            // ── Explicit OUT_OF_BOUNDS error code ──────────────────
            response.errorCode.equals("OUT_OF_BOUNDS", ignoreCase = true) ||
            response.status.equals("OUT_OF_BOUNDS", ignoreCase = true) -> {
                _uiState.value = AttendanceUiState.OutOfBounds(
                    response.message
                        ?: "You are outside the campus boundary. " +
                                "Move within the geofence and try again."
                )
            }

            // ── Face not recognised ────────────────────────────────
            response.status.equals("UNKNOWN", ignoreCase = true) -> {
                _uiState.value = AttendanceUiState.Error(
                    response.message ?: "Face not recognised. Please try again."
                )
            }

            // ── Generic server error ───────────────────────────────
            response.status.equals("ERROR", ignoreCase = true) -> {
                _uiState.value = AttendanceUiState.Error(
                    response.message ?: "Server-side error occurred."
                )
            }

            // ── Success path (PRESENT, LP, HD, EP, etc.) ──────────
            else -> {
                _uiState.value = AttendanceUiState.Success(
                    staffName = response.staffName ?: "Staff",
                    status = response.status,
                    message = response.message
                        ?: "Attendance marked: ${response.status}"
                )
            }
        }
    }

    /**
     * One-shot GPS fetch using [Priority.PRIORITY_HIGH_ACCURACY].
     * Returns null if the location provider fails.
     */
    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrentLocation(): android.location.Location? {
        return try {
            val cancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).await()
        } catch (e: Exception) {
            Log.e(TAG, "fetchCurrentLocation failed", e)
            null
        }
    }
}
