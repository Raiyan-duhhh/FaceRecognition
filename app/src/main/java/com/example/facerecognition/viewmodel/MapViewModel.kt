package com.example.facerecognition.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class LiveLocation(
    val staffId: String = "",
    val staffName: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val status: String = "Safe",
    val timestamp: Long = 0L
)

class MapViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val liveLocationsCollection = db.collection("Attendance")

    fun getLiveLocations(collegeId: String): Flow<List<LiveLocation>> = callbackFlow {
        val listener = liveLocationsCollection
            // In a real multi-tenant scenario, we would filter by collegeId if it's stored on the document
            // .whereEqualTo("collegeId", collegeId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("MapVM", "Error fetching live locations", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val locations = snapshot.toObjects(LiveLocation::class.java)
                        .groupBy { it.staffId }
                        .map { it.value.maxByOrNull { log -> log.timestamp } ?: it.value.first() }
                    trySend(locations)
                }
            }

        awaitClose { listener.remove() }
    }
}
