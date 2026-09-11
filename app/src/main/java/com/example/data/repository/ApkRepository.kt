package com.example.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.db.AppDatabase
import com.example.data.db.ExtractionEntity
import com.example.data.model.AppItem
import com.example.data.model.ExtractedFile
import com.example.data.model.ExtractionResult
import com.example.util.NetworkMonitor
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class ApkRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val networkMonitor: NetworkMonitor,
    private val notificationHelper: NotificationHelper,
    private val settingsRepository: SettingsRepository
) {
    private val packageManager: PackageManager = context.packageManager
    private val extractionDao = database.extractionDao()

    val extractionHistory: Flow<List<ExtractionEntity>> = extractionDao.getAllHistory()
    val totalHistoryCount: Flow<Int> = extractionDao.getCount()
    val totalBytesExtracted: Flow<Long?> = extractionDao.getTotalBytes()

    fun getExtractedDir(): File {
        val dir = File(context.getExternalFilesDir(null), "ExtractedAPKs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun getInstalledApps(
        searchQuery: String = "",
        filterType: String = "ALL" // "ALL", "USER", "SYSTEM"
    ): List<AppItem> = withContext(Dispatchers.IO) {
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            packageManager.getInstalledPackages(0)
        }

        val appList = mutableListOf<AppItem>()

        for (pkg in installedPackages) {
            val appInfo = pkg.applicationInfo ?: continue
            val sourceDir = appInfo.sourceDir ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            // Apply filter
            if (filterType == "USER" && isSystem) continue
            if (filterType == "SYSTEM" && !isSystem) continue

            val appName = try {
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg.packageName
            }

            // Apply search
            if (searchQuery.isNotBlank()) {
                val matchesName = appName.contains(searchQuery, ignoreCase = true)
                val matchesPackage = pkg.packageName.contains(searchQuery, ignoreCase = true)
                if (!matchesName && !matchesPackage) continue
            }

            val apkFile = File(sourceDir)
            val sizeBytes = if (apkFile.exists()) apkFile.length() else 0L

            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }

            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appInfo.minSdkVersion
            } else {
                24
            }

            appList.add(
                AppItem(
                    packageName = pkg.packageName,
                    appName = appName,
                    versionName = pkg.versionName ?: "1.0",
                    versionCode = vCode,
                    apkSourceDir = sourceDir,
                    sizeBytes = sizeBytes,
                    isSystemApp = isSystem,
                    firstInstallTime = pkg.firstInstallTime,
                    lastUpdateTime = pkg.lastUpdateTime,
                    targetSdkVersion = appInfo.targetSdkVersion,
                    minSdkVersion = minSdk
                )
            )
        }

        appList.sortedBy { it.appName.lowercase() }
    }

    suspend fun extractApk(
        app: AppItem,
        onProgress: ((Float) -> Unit)? = null
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val sourceFile = File(app.apkSourceDir)

        if (!sourceFile.exists() || !sourceFile.canRead()) {
            return@withContext ExtractionResult(
                success = false,
                errorMessage = "Não foi possível acessar o arquivo de origem do APK."
            )
        }

        val totalSize = sourceFile.length()
        val sanitizedName = app.appName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val fileName = "${sanitizedName}_v${app.versionName}.apk"
        val destFile = File(getExtractedDir(), fileName)

        var bytesCopied = 0L
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                        if (totalSize > 0) {
                            val progress = (bytesCopied.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                            onProgress?.invoke(progress)
                        }
                    }
                    output.flush()
                }
            }

            val durationMs = maxOf(1L, System.currentTimeMillis() - startTime)
            val sizeMb = bytesCopied.toDouble() / (1024.0 * 1024.0)
            val durationSec = durationMs.toDouble() / 1000.0
            val speedMbps = if (durationSec > 0) sizeMb / durationSec else 0.0

            val sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }

            val isOnline = networkMonitor.checkCurrentConnectivity()
            val autoSync = settingsRepository.autoCloudSync.value
            val syncStatus = if (autoSync && isOnline) "SYNCED" else "PENDING"

            val historyEntity = ExtractionEntity(
                packageName = app.packageName,
                appName = app.appName,
                versionName = app.versionName,
                versionCode = app.versionCode,
                fileSize = bytesCopied,
                filePath = destFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                durationMs = durationMs,
                speedMbps = speedMbps,
                sha256 = sha256Hex,
                isSystemApp = app.isSystemApp,
                cloudSyncStatus = syncStatus
            )

            val recordId = extractionDao.insert(historyEntity)

            val formattedSize = "%.1f MB".format(sizeMb)
            notificationHelper.showExtractionCompleteNotification(
                appName = app.appName,
                fileSizeMb = formattedSize,
                durationMs = durationMs,
                speedMbps = speedMbps
            )

            if (syncStatus == "SYNCED") {
                notificationHelper.showCloudSyncNotification(app.appName, true)
            }

            ExtractionResult(
                success = true,
                filePath = destFile.absolutePath,
                fileSize = bytesCopied,
                durationMs = durationMs,
                speedMbps = speedMbps,
                sha256 = sha256Hex
            )
        } catch (e: Exception) {
            if (destFile.exists()) {
                destFile.delete()
            }
            ExtractionResult(
                success = false,
                errorMessage = e.localizedMessage ?: "Erro desconhecido durante a extração."
            )
        }
    }

    suspend fun getExtractedFiles(): List<ExtractedFile> = withContext(Dispatchers.IO) {
        val dir = getExtractedDir()
        val files = dir.listFiles { _, name -> name.endsWith(".apk", ignoreCase = true) } ?: emptyArray()

        files.map { file ->
            val archiveInfo = packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            val pkgName = archiveInfo?.packageName ?: file.nameWithoutExtension
            val vName = archiveInfo?.versionName ?: "1.0"

            ExtractedFile(
                file = file,
                name = file.name,
                packageName = pkgName,
                versionName = vName,
                sizeBytes = file.length(),
                lastModified = file.lastModified()
            )
        }.sortedByDescending { it.lastModified }
    }

    suspend fun deleteExtractedFile(file: File): Boolean = withContext(Dispatchers.IO) {
        if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    suspend fun renameExtractedFile(file: File, newName: String): Boolean = withContext(Dispatchers.IO) {
        val targetName = if (newName.endsWith(".apk", ignoreCase = true)) newName else "$newName.apk"
        val targetFile = File(file.parentFile, targetName)
        if (file.exists() && !targetFile.exists()) {
            file.renameTo(targetFile)
        } else {
            false
        }
    }

    suspend fun syncItemToCloud(id: Long, appName: String): Boolean = withContext(Dispatchers.IO) {
        val isOnline = networkMonitor.checkCurrentConnectivity()
        if (!isOnline) {
            extractionDao.updateSyncStatus(id, "PENDING")
            return@withContext false
        }

        extractionDao.updateSyncStatus(id, "SYNCING")
        // Simulate real-time cloud backup upload transfer
        kotlinx.coroutines.delay(700)
        extractionDao.updateSyncStatus(id, "SYNCED")
        notificationHelper.showCloudSyncNotification(appName, true)
        true
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        extractionDao.clearAll()
    }

    suspend fun deleteHistoryItem(id: Long) = withContext(Dispatchers.IO) {
        extractionDao.deleteById(id)
    }

    fun generateAnalyticsJson(records: List<ExtractionEntity>): String {
        val totalBytes = records.sumOf { it.fileSize }
        val avgSpeed = if (records.isNotEmpty()) records.map { it.speedMbps }.average() else 0.0
        val avgDuration = if (records.isNotEmpty()) records.map { it.durationMs }.average() else 0.0
        val syncedCount = records.count { it.cloudSyncStatus == "SYNCED" }

        return buildString {
            append("{\n")
            append("  \"device\": {\n")
            append("    \"model\": \"${Build.MODEL}\",\n")
            append("    \"manufacturer\": \"${Build.MANUFACTURER}\",\n")
            append("    \"androidVersion\": \"${Build.VERSION.RELEASE}\",\n")
            append("    \"sdkInt\": ${Build.VERSION.SDK_INT}\n")
            append("  },\n")
            append("  \"summary\": {\n")
            append("    \"totalExtractions\": ${records.size},\n")
            append("    \"totalBytes\": $totalBytes,\n")
            append("    \"totalMegabytes\": ${"%.2f".format(totalBytes.toDouble() / (1024 * 1024))},\n")
            append("    \"averageSpeedMbps\": ${"%.2f".format(avgSpeed)},\n")
            append("    \"averageDurationMs\": ${"%.1f".format(avgDuration)},\n")
            append("    \"cloudSyncedCount\": $syncedCount\n")
            append("  },\n")
            append("  \"records\": [\n")
            records.forEachIndexed { index, r ->
                append("    {\n")
                append("      \"id\": ${r.id},\n")
                append("      \"appName\": \"${r.appName.replace("\"", "\\\"")}\",\n")
                append("      \"packageName\": \"${r.packageName}\",\n")
                append("      \"fileSize\": ${r.fileSize},\n")
                append("      \"durationMs\": ${r.durationMs},\n")
                append("      \"speedMbps\": ${"%.2f".format(r.speedMbps)},\n")
                append("      \"sha256\": \"${r.sha256}\",\n")
                append("      \"cloudSyncStatus\": \"${r.cloudSyncStatus}\",\n")
                append("      \"timestamp\": ${r.timestamp}\n")
                append("    }${if (index < records.size - 1) "," else ""}\n")
            }
            append("  ]\n")
            append("}")
        }
    }

    fun generateAnalyticsCsv(records: List<ExtractionEntity>): String {
        return buildString {
            append("id,app_name,package_name,version_name,file_size_bytes,duration_ms,speed_mbps,sha256,cloud_sync_status,timestamp\n")
            for (r in records) {
                append("${r.id},\"${r.appName.replace("\"", "\"\"")}\",${r.packageName},${r.versionName},${r.fileSize},${r.durationMs},${"%.2f".format(r.speedMbps)},${r.sha256},${r.cloudSyncStatus},${r.timestamp}\n")
            }
        }
    }
}
