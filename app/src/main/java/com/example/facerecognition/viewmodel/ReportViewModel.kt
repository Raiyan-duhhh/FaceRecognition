package com.example.facerecognition.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.facerecognition.data.entity.AttendanceLog
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ReportViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    suspend fun generateReport(
        collegeId: String, 
        startDate: Long, 
        endDate: Long, 
        violationsOnly: Boolean
    ): List<AttendanceLog> {
        return try {
            val querySnapshot = db.collection("attendanceLogs")
                // We avoid filtering by collegeId in the query here to bypass the need for a composite index initially.
                // We'll filter it locally.
                .whereGreaterThanOrEqualTo("timestamp", startDate)
                .whereLessThanOrEqualTo("timestamp", endDate)
                .get()
                .await()

            var logs = querySnapshot.toObjects(AttendanceLog::class.java)

            // Local filter for Multi-Tenant architecture
            if (collegeId.isNotEmpty()) {
                logs = logs.filter { it.collegeId == collegeId || it.collegeId.isEmpty() }
            }

            // Apply business logic: Violations Only (Absent, Late, Half Day)
            if (violationsOnly) {
                logs = logs.filter { 
                    it.status.equals("Absent", ignoreCase = true) || 
                    it.status.equals("Late", ignoreCase = true) ||
                    it.status.equals("LP", ignoreCase = true) ||
                    it.status.equals("HD", ignoreCase = true)
                }
            }

            // Sort by most recent first
            logs.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e("ReportVM", "Failed to generate report", e)
            emptyList()
        }
    }
}
