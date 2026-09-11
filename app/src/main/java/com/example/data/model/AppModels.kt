package com.example.data.model

import java.io.File

data class AppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val apkSourceDir: String,
    val sizeBytes: Long,
    val isSystemApp: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val targetSdkVersion: Int,
    val minSdkVersion: Int
)

data class ExtractedFile(
    val file: File,
    val name: String,
    val packageName: String,
    val versionName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val sha256: String = "",
    val isSynced: Boolean = false
)

data class ExtractionResult(
    val success: Boolean,
    val filePath: String? = null,
    val fileSize: Long = 0L,
    val durationMs: Long = 0L,
    val speedMbps: Double = 0.0,
    val sha256: String = "",
    val errorMessage: String? = null
)

data class CloudSyncItem(
    val id: Long,
    val appName: String,
    val packageName: String,
    val fileSize: Long,
    val status: SyncStatus,
    val syncedAt: Long? = null
)

enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    OLED_BLACK
}

enum class AppLanguage(val code: String, val displayName: String) {
    PORTUGUESE("pt", "Português (Brasil)"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español")
}

data class PerformanceMetric(
    val totalExtractions: Int,
    val totalBytesExtracted: Long,
    val averageSpeedMbps: Double,
    val averageDurationMs: Long,
    val fastestExtractionMs: Long,
    val estimatedBatterySavedPercent: Double,
    val cloudSyncRatePercent: Int
)
