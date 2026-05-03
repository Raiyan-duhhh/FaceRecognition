package com.example.facerecognition.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit client for the V2 Face Attendance backend.
 *
 * ── Configuration ──────────────────────────────────────────────────
 *  • Base URL:  Set [BASE_URL] to your Flask server address.
 *  • Timeouts:  60s connect / 60s read — Base64 payloads are large.
 *  • Logging:   Full BODY-level logging in debug builds.
 */
object RetrofitClient {

    // TODO: Move to BuildConfig or Firebase Remote Config for production
    private const val BASE_URL = "http://10.0.2.2:5000/" // localhost from emulator

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: FaceAttendApiService =
        retrofit.create(FaceAttendApiService::class.java)
}
