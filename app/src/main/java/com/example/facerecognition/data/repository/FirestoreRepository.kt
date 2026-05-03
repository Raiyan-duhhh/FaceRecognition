package com.example.facerecognition.data.repository

import com.example.facerecognition.data.entity.Staff
import com.example.facerecognition.data.entity.AttendanceLog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val staffCollection = db.collection("Staff")
    private val attendanceCollection = db.collection("Attendance")
    private val settingsCollection = db.collection("settings")

    suspend fun deleteStaff(staffId: String) {
        try {
            staffCollection.document(staffId).delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun registerStaff(staff: Staff) {
        try {
            staffCollection.add(staff).await()
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to register staff to Firestore: ${e.message}")
        }
    }

    /** Query the Staff collection for an existing document with the given employeeId. */
    suspend fun findStaffByEmployeeId(employeeId: String): Staff? {
        return try {
            val snapshot = staffCollection
                .whereEqualTo("employeeId", employeeId)
                .limit(1)
                .get()
                .await()
            snapshot.toObjects(Staff::class.java).firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getPendingApprovals(): Flow<List<Staff>> {
        return staffCollection
            .whereEqualTo("approved", false)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Staff::class.java)
            }
    }

    suspend fun approveStaff(staffId: String) {
        try {
            staffCollection.document(staffId)
                .update("approved", true)
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to approve staff: ${e.message}")
        }
    }

    suspend fun rejectStaff(staffId: String) {
        try {
            staffCollection.document(staffId)
                .delete()
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to reject staff: ${e.message}")
        }
    }

    /** Fetch ALL staff (approved + unapproved) for manual filtering */
    suspend fun getAllStaff(): List<Staff> {
        return try {
            val snapshot = staffCollection.get().await()
            snapshot.toObjects(Staff::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getAllApprovedStaff(): List<Staff> {
        return try {
            val snapshot = staffCollection
                .whereEqualTo("approved", true)
                .get()
                .await()
            snapshot.toObjects(Staff::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun markAttendance(log: AttendanceLog) {
        try {
            attendanceCollection.add(log).await()
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to mark attendance: ${e.message}")
        }
    }

    suspend fun getAttendanceForMonth(year: Int, month: Int): List<AttendanceLog> {
        return try {
            val zoneId = java.time.ZoneId.of("Asia/Kolkata")
            val yearMonth = java.time.YearMonth.of(year, month)
            val startOfMonth = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endOfMonth = yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()

            val snapshot = attendanceCollection
                .whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                .whereLessThanOrEqualTo("timestamp", endOfMonth)
                .get()
                .await()
            snapshot.toObjects(AttendanceLog::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun hasCheckedInToday(staffId: String, startOfDay: Long, endOfDay: Long): Boolean {
        return try {
            val snapshot = attendanceCollection
                .whereEqualTo("staffId", staffId)
                .whereGreaterThanOrEqualTo("timestamp", startOfDay)
                .whereLessThanOrEqualTo("timestamp", endOfDay)
                .limit(1)
                .get()
                .await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateStaffProfile(staffId: String, name: String, employeeId: String) {
        try {
            staffCollection.document(staffId)
                .update(mapOf(
                    "name" to name,
                    "employeeId" to employeeId
                ))
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to update staff profile: ${e.message}")
        }
    }

    suspend fun getAttendanceRules(): Map<String, Any>? {
        return try {
            val doc = settingsCollection.document("attendance_rules").get().await()
            doc.data
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun updateAttendanceRules(rules: Map<String, Any>) {
        try {
            settingsCollection.document("attendance_rules").set(rules).await()
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to update rules: ${e.message}")
        }
    }

    /** 
     * Offline-First Sync Engine:
     * Fetches all pending logs from Room, uploads to Firestore, and deletes locally if successful.
     */
    suspend fun syncOfflineLogs(dao: com.example.facerecognition.data.local.AttendanceDao) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pendingLogs = dao.getAllPendingLogs()
                if (pendingLogs.isEmpty()) return@withContext

                android.util.Log.d("FirestoreSync", "Found ${pendingLogs.size} offline logs to sync.")

                for (offlineLog in pendingLogs) {
                    try {
                        // 1. Upload to Firestore
                        markAttendance(offlineLog.toFirestoreLog())
                        // 2. If successful, delete from Room
                        dao.deleteLog(offlineLog)
                        android.util.Log.d("FirestoreSync", "Successfully synced log for: ${offlineLog.staffName}")
                    } catch (e: Exception) {
                        android.util.Log.e("FirestoreSync", "Failed to sync log for ${offlineLog.staffName}, keeping in Room: ${e.message}")
                        // Continue to next log instead of aborting the whole sync
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FirestoreSync", "Error during offline sync process: ${e.message}")
            }
        }
    }
}
