package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {

    fun getApkUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.provider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun shareApkFile(context: Context, file: File, appName: String) {
        try {
            val uri = getApkUri(context, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "APK: $appName")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Enviando APK de $appName extraído com APK Extractor Pro."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartilhar APK via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao compartilhar arquivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareToSocialApp(context: Context, file: File, appName: String, targetPackage: String) {
        try {
            val uri = getApkUri(context, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Aplicativo $appName extraído pelo APK Extractor Pro."
                )
                `package` = targetPackage
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // If target app is not installed, open system chooser
            shareApkFile(context, file, appName)
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao compartilhar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun installApk(context: Context, file: File) {
        try {
            val uri = getApkUri(context, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Não foi possível abrir o instalador: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareTextReport(context: Context, title: String, content: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
            }
            context.startActivity(Intent.createChooser(intent, "Exportar Relatório"))
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao exportar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
