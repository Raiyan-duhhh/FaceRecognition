package com.example.facerecognition.ml

import kotlin.math.sqrt

object FaceMath {
    // Cosine similarity threshold: higher is more similar. 
    // For 128-d FaceNet, > 0.6f is a good starting point.
    const val MATCH_THRESHOLD = 0.6f

    /**
     * L2 Normalization: Scales a vector so its magnitude is 1.0.
     * This is critical before distance/similarity calculations.
     */
    fun l2Normalize(embedding: FloatArray): FloatArray {
        var sum = 0f
        for (value in embedding) {
            sum += value * value
        }
        val magnitude = sqrt(sum)
        if (magnitude > 0) {
            for (i in embedding.indices) {
                embedding[i] = embedding[i] / magnitude
            }
        }
        return embedding
    }

    /**
     * Computes the Cosine Similarity between two vectors.
     * Range is [-1, 1], where 1 means perfectly identical.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding size mismatch: ${a.size} vs ${b.size}" }
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val magnitude = sqrt(normA) * sqrt(normB)
        return if (magnitude > 0) dotProduct / magnitude else 0f
    }
}