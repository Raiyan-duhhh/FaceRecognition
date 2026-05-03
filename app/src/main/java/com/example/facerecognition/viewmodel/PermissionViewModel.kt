package com.example.facerecognition.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.facerecognition.data.entity.PermissionRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PermissionViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val permissionsCollection = db.collection("permissions")

    fun submitPermissionRequest(request: PermissionRequest) {
        viewModelScope.launch {
            try {
                permissionsCollection.add(request).await()
                Log.d("PermissionVM", "Successfully submitted request.")
            } catch (e: Exception) {
                Log.e("PermissionVM", "Failed to submit request", e)
            }
        }
    }

    fun getPendingRequests(collegeId: String): Flow<List<PermissionRequest>> = callbackFlow {
        val listener = permissionsCollection
            .whereEqualTo("collegeId", collegeId)
            .whereEqualTo("status", "Pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("PermissionVM", "Error fetching pending requests", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    // Sorting locally to avoid Firestore composite index requirements
                    val requests = snapshot.toObjects(PermissionRequest::class.java).sortedBy { it.date }
                    trySend(requests)
                }
            }
        
        awaitClose { listener.remove() }
    }

    fun updatePermissionStatus(requestId: String, newStatus: String) {
        viewModelScope.launch {
            try {
                if (requestId.isNotBlank()) {
                    permissionsCollection.document(requestId)
                        .update("status", newStatus)
                        .await()
                    Log.d("PermissionVM", "Successfully updated request $requestId to $newStatus")
                } else {
                    Log.e("PermissionVM", "Cannot update request: Invalid Document ID")
                }
            } catch (e: Exception) {
                Log.e("PermissionVM", "Failed to update request status", e)
            }
        }
    }
}
