package com.example.facerecognition.data.entity

import com.google.firebase.firestore.DocumentId

data class PermissionRequest(
    @DocumentId
    val id: String = "",
    val collegeId: String = "",
    val departmentId: String = "",
    val facultyId: String = "",
    val facultyName: String = "",
    val permissionType: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val reason: String = "",
    val status: String = "Pending"
)
