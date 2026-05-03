package com.example.facerecognition.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.facerecognition.data.entity.AttendanceLog

@Entity(tableName = "offline_logs")
data class OfflineAttendanceLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val staffId: String,
    val staffName: String,
    val collegeId: String = "",
    val departmentId: String = "",
    val timestamp: Long,
    val status: String,
    val locationValid: Boolean
) {
    fun toFirestoreLog(): AttendanceLog {
        return AttendanceLog(
            // We omit the DocumentId 'id' since Firestore will auto-generate it
            staffId = staffId,
            staffName = staffName,
            collegeId = collegeId,
            departmentId = departmentId,
            timestamp = timestamp,
            status = status,
            locationValid = locationValid
        )
    }
}
