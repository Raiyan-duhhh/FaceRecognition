package com.example.facerecognition.data.entity

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class Staff(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val employeeId: String = "",
    val collegeId: String = "",
    val departmentId: String = "",
    // keep as List<FaceVector> — we'll use the first one for now
    val embeddings: List<FaceVector> = emptyList(),
    @get:PropertyName("approved")
    @set:PropertyName("approved")
    var isApproved: Boolean = false
)