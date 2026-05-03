package com.example.facerecognition.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Wraps the FaceNet TFLite model to produce face embeddings.
 *
 * The model expects a 160×160 RGB image normalised to [-1, 1].
 * Output is [1, 128] for facenet.tflite or [1, 512] for facenet_512.tflite.
 */
class FaceEmbedder(context: Context) {

    companion object {
        private const val MODEL_FILE = "facenet.tflite" // use facenet_512.tflite if you switch
    }

    private val interpreter: Interpreter
    private val inputSize: Int
    private val embeddingDim: Int

    init {
        val model = loadModelFile(context)
        val options = Interpreter.Options().apply {
            numThreads = 4
        }
        interpreter = Interpreter(model, options)

        // read sizes directly from the model — no more hard-coded mismatches
        val inputShape = interpreter.getInputTensor(0).shape() // [1, h, w, 3]
        inputSize = inputShape[1] // 160 for FaceNet
        val outputShape = interpreter.getOutputTensor(0).shape() // [1, 128] or [1, 512]
        embeddingDim = outputShape[1]
    }

    /**
     * Generates an embedding from a cropped face bitmap.
     *
     * The bitmap is resized to the model's input size and pixels are
     * normalised from [0, 255] to [-1, 1].
     */
    fun getEmbedding(bitmap: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = bitmapToByteBuffer(scaled)

        val output = Array(1) { FloatArray(embeddingDim) }
        interpreter.run(inputBuffer, output)

        // CRITICAL: L2 Normalize the raw logit output
        return FaceMath.l2Normalize(output[0])
    }

    /**
     * Converts bitmap into a direct ByteBuffer with float pixels
     * normalised to [-1, 1].
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(
            1 * inputSize * inputSize * 3 * 4 // batch × H × W × channels × float32
        ).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            // Extract RGB and normalise using the exact formula: (pixel - 127.5f) / 127.5f
            val r = (pixel shr 16 and 0xFF).toFloat()
            val g = (pixel shr 8 and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()

            byteBuffer.putFloat((r - 127.5f) / 127.5f) // R
            byteBuffer.putFloat((g - 127.5f) / 127.5f) // G
            byteBuffer.putFloat((b - 127.5f) / 127.5f) // B
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    /**
     * Memory-maps the TFLite model from assets for zero-copy loading.
     */
    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
    }

    fun close() {
        interpreter.close()
    }
}