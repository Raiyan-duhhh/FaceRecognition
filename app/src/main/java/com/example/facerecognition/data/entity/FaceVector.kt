package com.example.facerecognition.data.entity

/**
 * A simple wrapper class to bypass Firestore's nested array limitation.
 */
data class FaceVector(
    val vector: List<Float> = emptyList()
)