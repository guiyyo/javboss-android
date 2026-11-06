package com.javboss.android.core.log

import android.content.Context
import android.os.Build

/** 诊断报告：设备信息 + 完整运行日志 + 崩溃记录。 */
object Diagnostics {

    /** 把诊断报告写成文件并调起分享（QQ/微信/邮件等） */
    fun shareFile(context: Context, report: String) {
        runCatching {
            val dir = java.io.File(context.cacheDir, "logs").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val f = java.io.File(dir, "javboss-log-" + stamp + ".txt")
            f.writeText(report)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                f,
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "JavBoss 诊断日志")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                android.content.Intent.createChooser(intent, "分享诊断日志")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun buildReport(context: Context): String = buildString {
        appendLine("=== JavBoss Android 诊断日志 ===")
        appendLine("生成时间: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
        appendLine("App 版本: " + com.javboss.android.BuildConfig.VERSION_NAME + " (" + com.javboss.android.BuildConfig.VERSION_CODE + ")")
        appendLine("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
        appendLine("设备: " + Build.MANUFACTURER + " " + Build.MODEL)
        appendLine("ABI: " + Build.SUPPORTED_ABIS.joinToString(","))
        runCatching {
            val dm = context.resources.displayMetrics
            appendLine("屏幕: " + dm.widthPixels + "x" + dm.heightPixels + " @" + dm.densityDpi + "dpi")
        }
        appendLine()
        appendLine("=== 崩溃记录 ===")
        appendLine(CrashLogger.readLast(context) ?: "（无）")
        appendLine()
        appendLine("=== 运行日志（最近 " + AppLog.size() + " 条）===")
        appendLine(AppLog.dump().ifBlank { "（暂无）" })
    }
}
