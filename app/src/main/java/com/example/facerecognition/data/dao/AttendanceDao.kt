package com.example.facerecognition.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.facerecognition.data.entity.AttendanceLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AttendanceLog): Long

    /**
     * Returns logs whose timestamp falls within the given day boundaries
     * (start-of-day inclusive, end-of-day exclusive, both as epoch millis).
     */
    @Query("SELECT * FROM attendance_log WHERE timestamp >= :startOfDay AND timestamp < :endOfDay ORDER BY timestamp DESC")
    fun getLogsByDate(startOfDay: Long, endOfDay: Long): Flow<List<AttendanceLog>>

    @Query("SELECT * FROM attendance_log WHERE staffId = :staffId ORDER BY timestamp DESC")
    fun getLogsByStaffId(staffId: Long): Flow<List<AttendanceLog>>
}
