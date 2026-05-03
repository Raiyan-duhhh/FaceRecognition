package com.example.facerecognition.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLog(log: OfflineAttendanceLog): Long

    @Query("SELECT * FROM offline_logs ORDER BY timestamp ASC")
    fun getAllPendingLogs(): List<OfflineAttendanceLog>

    @Query("SELECT COUNT(*) FROM offline_logs WHERE staffId = :staffId AND timestamp >= :startOfDay AND timestamp <= :endOfDay")
    fun getLogCountForToday(staffId: String, startOfDay: Long, endOfDay: Long): Int

    @Delete
    fun deleteLog(log: OfflineAttendanceLog): Int
}