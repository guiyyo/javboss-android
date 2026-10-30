package com.javboss.android.core.log

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 极简崩溃记录：把未捕获异常的堆栈写到 filesDir/crash/last-crash.txt，
 * 供「设置 -> 关于 -> 崩溃日志」查看（真机没连接 adb 时也能自查）。
 */
object CrashLogger {

    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(app, thread, throwable) }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun crashFile(context: Context): File = File(context.filesDir, "crash/last-crash.txt")

    fun readLast(context: Context): String? {
        val file = crashFile(context)
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }

    fun clear(context: Context) {
        runCatching { crashFile(context).delete() }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val file = crashFile(context)
        file.parentFile?.mkdirs()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val stack = StringWriter().also { sw -> PrintWriter(sw).use { throwable.printStackTrace(it) } }.toString()
        file.writeText(
            buildString {
                appendLine("时间: $time")
                appendLine("线程: ${thread.name}")
                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine("设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("版本: ${com.javboss.android.BuildConfig.VERSION_NAME} (${com.javboss.android.BuildConfig.VERSION_CODE})")
                appendLine("---")
                appendLine(stack)
            }
        )
    }
}