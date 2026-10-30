package com.javboss.android.data.screenshot

import android.content.Context
import android.graphics.Bitmap
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.repo.VideoRepository
import com.javboss.android.di.AppGraph
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地截帧存储（Client 模式语义）。
 *
 * 目录按远端服务器 URL 的哈希隔离：
 *   filesDir/screenshots/<sha256(baseUrl)[:8]>/<videoId>/mpv_<HH-mm-ss.SSS>.jpg
 *
 * 命名必须是 `mpv_` 前缀 + jpg/png/webp，否则服务端 `PUT /videos/{id}/screenshots/{name}` 直接 400。
 */
object LocalScreenshotStore {

    private fun root(context: Context): File {
        val base = kotlinx.coroutines.runBlocking { AppGraph.settings.currentServerUrl() }
        val digest = MessageDigest.getInstance("SHA-256").digest(base.trimEnd('/').toByteArray())
        val key = digest.take(8).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "screenshots/$key")
    }

    private fun videoDir(context: Context, videoId: Long): File =
        File(root(context), videoId.toString())

    fun saveJpeg(context: Context, videoId: Long, bitmap: Bitmap): File {
        val dir = videoDir(context, videoId).apply { mkdirs() }
        val stamp = SimpleDateFormat("HH-mm-ss.SSS", Locale.US).format(Date())
        val file = File(dir, "mpv_$stamp.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return file
    }

    fun pending(context: Context, videoId: Long): List<File> =
        videoDir(context, videoId).listFiles { f -> f.isFile && f.name.startsWith("mpv_") }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun pendingCount(context: Context, videoId: Long): Int = pending(context, videoId).size

    fun delete(file: File) {
        runCatching { file.delete() }
    }

    /** 上传指定视频的所有本地截帧；成功即删本地文件。 */
    suspend fun uploadAll(context: Context, videoId: Long): ApiResult<Int> {
        val files = pending(context, videoId)
        if (files.isEmpty()) return ApiResult.Success(0)
        var uploaded = 0
        var lastError: ApiResult.Failure? = null
        for (file in files) {
            when (val result = AppGraph.video.uploadScreenshot(videoId, file.name, file)) {
                is ApiResult.Success -> {
                    uploaded++
                    delete(file)
                }
                is ApiResult.Failure -> lastError = result
            }
        }
        return lastError?.takeIf { uploaded == 0 } ?: ApiResult.Success(uploaded)
    }
}
