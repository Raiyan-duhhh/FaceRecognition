package com.example.facerecognition.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.facerecognition.data.converter.Converters
import com.example.facerecognition.data.dao.AttendanceDao
import com.example.facerecognition.data.dao.EncodingDao
import com.example.facerecognition.data.dao.StaffDao
import com.example.facerecognition.data.entity.AttendanceLog
import com.example.facerecognition.data.entity.FaceEncoding
import com.example.facerecognition.data.entity.Staff

@Database(
    entities = [Staff::class, FaceEncoding::class, AttendanceLog::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun staffDao(): StaffDao
    abstract fun encodingDao(): EncodingDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "face_recognition_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
