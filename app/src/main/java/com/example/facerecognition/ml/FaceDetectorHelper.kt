package com.example.facerecognition.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

/**
 * Wraps ML Kit Face Detection for use in the camera analysis pipeline.
 *
 * Configured for real-time performance mode with a minimum face size of
 * 30% of the image — suitable for a kiosk where the user is standing close.
 */
class FaceDetectorHelper {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.1f) // Changed from 0.3f to 0.1f (10% of the screen)
            .build()
    )

    /**
     * Detects faces in the given [bitmap] and returns a list of cropped
     * face bitmaps, each clipped to the detected bounding box.
     *
     * Returns an empty list if no faces are found.
     */
    suspend fun detectFaces(bitmap: Bitmap): List<Bitmap> {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(inputImage).await()

        return faces.mapNotNull { face ->
            val bounds = face.boundingBox
            // Clamp bounding box to image boundaries
            val left = bounds.left.coerceAtLeast(0)
            val top = bounds.top.coerceAtLeast(0)
            val right = bounds.right.coerceAtMost(bitmap.width)
            val bottom = bounds.bottom.coerceAtMost(bitmap.height)

            val width = right - left
            val height = bottom - top

            if (width > 0 && height > 0) {
                Bitmap.createBitmap(bitmap, left, top, width, height)
            } else {
                null
            }
        }
    }

    fun close() {
        detector.close()
    }
}
