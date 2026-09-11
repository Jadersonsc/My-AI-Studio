package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "extraction_history")
data class ExtractionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val fileSize: Long,
    val filePath: String,
    val timestamp: Long,
    val durationMs: Long,
    val speedMbps: Double,
    val sha256: String,
    val isSystemApp: Boolean,
    val cloudSyncStatus: String = "PENDING"
)
