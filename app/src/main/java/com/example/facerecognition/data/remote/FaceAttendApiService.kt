package com.example.facerecognition.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// ════════════════════════════════════════════════════════════════════
//  Request / Response DTOs
// ════════════════════════════════════════════════════════════════════

/**
 * Nested GPS coordinate object sent inside [RecognizeRequest].
 */
data class LocationPayload(
    val latitude: Double,
    val longitude: Double
)

/**
 * POST body for `/api/recognize`.
 *
 * @property image  Base64 data-URI string (`data:image/jpeg;base64,…`)
 * @property location  Current GPS coordinates for geofence validation.
 */
data class RecognizeRequest(
    val image: String,
    val location: LocationPayload
)

/**
 * Response from the Flask backend.
 *
 * Possible [status] values the backend may return:
 *   - `"PRESENT"` / `"LP"` / `"HD"` — attendance marked successfully.
 *   - `"OUT_OF_BOUNDS"` — device is outside the campus geofence.
 *   - `"UNKNOWN"` — face not matched against any registered staff.
 *   - `"ERROR"` — generic server-side failure.
 */
data class RecognizeResponse(
    val status: String,
    val staffName: String? = null,
    val message: String? = null,
    val errorCode: String? = null
)

// ════════════════════════════════════════════════════════════════════
//  Retrofit Interface
// ════════════════════════════════════════════════════════════════════

/**
 * V2 Face Attendance REST API surface.
 *
 * Backend is a Flask server that accepts a JPEG Base64 image + GPS
 * coordinates, runs server-side recognition, and returns the
 * attendance result.
 */
interface FaceAttendApiService {

    @POST("/api/recognize")
    suspend fun submitFaceScan(
        @Body request: RecognizeRequest
    ): Response<RecognizeResponse>
}
