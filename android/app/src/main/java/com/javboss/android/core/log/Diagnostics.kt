package com.javboss.android.core.log

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.javboss.android.BuildConfig
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 诊断日志：把崩溃信息 + 设备信息 + 关键设置汇总成一个 txt，
 * 通过系统分享面板发给 QQ / 微信 等（走 FileProvider，无需存储权限）。
 */
object Diagnostics {

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    suspend fun buildReport(context: Context): File {
        val prefs = runCatching { AppGraph.settings.uiPrefs.first() }.getOrNull()
        val serverUrl = runCatching { AppGraph.settings.serverUrl.first() }.getOrNull()
        val crash = CrashLogger.readLast(context).orEmpty()

        val text = buildString {
            appendLine("=== JavBoss Android 诊断日志 ===")
            appendLine("生成时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("App 版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) " debug" else " release"}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (${android.os.Build.DEVICE})")
            appendLine("ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString(",")}")
            appendLine("屏幕: ${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels} @${context.resources.displayMetrics.densityDpi}dpi")
            appendLine("服务器: ${serverUrl.orEmpty()}")
            if (prefs != null) {
                appendLine("偏好: 排序=${prefs.javSort} 直连=${prefs.preferDirect} 倍速=${prefs.playbackSpeed} 指纹锁=${prefs.appLockEnabled} 自动画中画=${prefs.autoPictureInPicture}")
            }
            appendLine()
            appendLine("=== 崩溃记录 ===")
            appendLine(if (crash.isBlank()) "（无）" else crash)
        }

        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val file = File(dir, "javboss-log-${stamp()}.txt")
        file.writeText(text)
        return file
    }

    /** 打开系统分享面板（QQ / 微信 / 邮件 均可）。 */
    fun shareFile(context: Context, file: File, subject: String = "JavBoss 诊断日志") {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "JavBoss Android ${BuildConfig.VERSION_NAME} 诊断日志")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "分享日志").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(chooser) }
    }
}