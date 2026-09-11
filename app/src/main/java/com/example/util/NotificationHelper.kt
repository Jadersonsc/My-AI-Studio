package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_EXTRACTIONS = "channel_extractions"
        const val CHANNEL_CLOUD_BACKUP = "channel_cloud_backup"
        private const val NOTIFICATION_ID_EXTRACTION = 1001
        private const val NOTIFICATION_ID_CLOUD = 1002
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val extractionChannel = NotificationChannel(
                CHANNEL_EXTRACTIONS,
                "Extrações de APK",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações quando um APK for extraído com sucesso"
            }

            val cloudChannel = NotificationChannel(
                CHANNEL_CLOUD_BACKUP,
                "Backup em Nuvem",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificações sobre sincronização em tempo real na nuvem"
            }

            manager.createNotificationChannel(extractionChannel)
            manager.createNotificationChannel(cloudChannel)
        }
    }

    fun showExtractionCompleteNotification(
        appName: String,
        fileSizeMb: String,
        durationMs: Long,
        speedMbps: Double
    ) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EXTRACTIONS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("APK Extraído com Sucesso: $appName")
            .setContentText("$fileSizeMb • ${durationMs}ms • ${"%.1f".format(speedMbps)} MB/s")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "O aplicativo $appName foi extraído e verificado com segurança.\nTamanho: $fileSizeMb | Duração: ${durationMs}ms | Velocidade: ${"%.1f".format(speedMbps)} MB/s"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_EXTRACTION + (System.currentTimeMillis() % 1000).toInt(),
                notification
            )
        } catch (_: SecurityException) {
            // Ignored if permission was revoked
        }
    }

    fun showCloudSyncNotification(
        appName: String,
        success: Boolean
    ) {
        if (!hasNotificationPermission()) return

        val title = if (success) "Backup em Nuvem Concluído" else "Falha no Backup em Nuvem"
        val text = if (success) "$appName sincronizado com a nuvem com sucesso." else "Não foi possível sincronizar $appName."

        val notification = NotificationCompat.Builder(context, CHANNEL_CLOUD_BACKUP)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CLOUD, notification)
        } catch (_: SecurityException) {
            // Ignored
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
