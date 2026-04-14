package com.example.facerecognition.data.converter

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

class Converters {

    // ── Date ↔ Long ────────────────────────────────────────────────

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    // ── FloatArray ↔ ByteArray ─────────────────────────────────────
    //
    // Each float is 4 bytes, so a 128-dimensional embedding produces a
    // compact 512-byte BLOB in SQLite — far more efficient than a JSON
    // string representation.

    @TypeConverter
    fun fromFloatArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(floats)
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(floats)
        return floats
    }
}
