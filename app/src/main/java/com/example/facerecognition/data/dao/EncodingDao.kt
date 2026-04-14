package com.example.facerecognition.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.facerecognition.data.entity.FaceEncoding

@Dao
interface EncodingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(encoding: FaceEncoding): Long

    /**
     * Returns all encodings as a plain list (not Flow) because these
     * are loaded into memory once when the camera session starts for
     * real-time face matching.
     */
    @Query("SELECT * FROM face_encoding")
    suspend fun getAllEncodings(): List<FaceEncoding>

    @Query("SELECT * FROM face_encoding WHERE staffId = :staffId")
    suspend fun getEncodingsByStaffId(staffId: Long): List<FaceEncoding>
}
