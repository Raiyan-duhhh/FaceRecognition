package com.example.facerecognition.data.entity

import com.google.firebase.firestore.DocumentId

/**
 * Cloud-ready Attendance model mapping staff punches to their identity.
 */
data class AttendanceLog(
    @DocumentId
    val id: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val collegeId: String = "",
    val departmentId: String = "",
    val timestamp: Long = 0L,
    val status: String = "Present",
    val locationValid: Boolean = false
)
