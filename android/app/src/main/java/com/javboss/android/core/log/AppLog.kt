package com.javboss.android.core.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内日志：镜像 android.util.Log 的同时，把日志留在内存环形缓冲里，
 * 供「诊断日志」页导出**完整运行日志**（不只是崩溃记录）。
 */
object AppLog {

    private const val CAPACITY = 4000
    private val buffer = ArrayDeque<String>(CAPACITY)
    private val lock = Any()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private fun record(level: Char, tag: String, msg: String?, tr: Throwable?) {
        val line = buildString {
            append(fmt.format(Date()))
            append(' ')
            append(level)
            append('/')
            append(tag)
            append(": ")
            append(msg ?: "")
            if (tr != null) {
                append(" | ")
                append(tr.javaClass.simpleName)
                append(": ")
                append(tr.message ?: "")
            }
        }
        synchronized(lock) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(line)
        }
    }

    fun i(tag: String, msg: String): Int {
        record('I', tag, msg, null)
        return android.util.Log.i(tag, msg)
    }

    fun w(tag: String, msg: String): Int {
        record('W', tag, msg, null)
        return android.util.Log.w(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable): Int {
        record('W', tag, msg, tr)
        return android.util.Log.w(tag, msg, tr)
    }

    fun e(tag: String, msg: String): Int {
        record('E', tag, msg, null)
        return android.util.Log.e(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable): Int {
        record('E', tag, msg, tr)
        return android.util.Log.e(tag, msg, tr)
    }

    fun d(tag: String, msg: String): Int {
        record('D', tag, msg, null)
        return android.util.Log.d(tag, msg)
    }

    /** 导出全部内存日志 */
    fun dump(): String = synchronized(lock) { buffer.joinToString("\n") }

    fun clear() = synchronized(lock) { buffer.clear() }

    fun size(): Int = synchronized(lock) { buffer.size }
}
