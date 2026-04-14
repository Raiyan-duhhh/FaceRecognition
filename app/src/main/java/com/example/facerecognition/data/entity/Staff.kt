package com.example.facerecognition.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "staff")
data class Staff(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val employeeId: String,
    val registeredAt: Long = System.currentTimeMillis()
)
