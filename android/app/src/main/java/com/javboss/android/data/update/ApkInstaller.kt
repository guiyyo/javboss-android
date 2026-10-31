package com.javboss.android.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * APK 下载 + 调起系统安装器（App 内直接升级）。
 */
object ApkInstaller {

    private fun apkDir(context: Context): File =
        File(context.cacheDir, "apk").apply { if (!exists()) mkdirs() }

    /** 下载 APK，回调百分比（0..1，未知时长传 null）。 */
    suspend fun download(
        context: Context,
        url: String,
        mirror: String = "",
        fileName: String = "update.apk",
        onProgress: (Float?) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val realUrl = UpdateMirror.wrap(url, mirror)
            android.util.Log.i("JavBossUpdate", "download: $realUrl")
            val dir = apkDir(context)
            // 清掉旧包，避免占用空间
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, fileName)
            val req = Request.Builder()
                .url(realUrl)
                .header("User-Agent", "JavBoss-Android")
                .build()
            com.javboss.android.core.net.ApiClient.okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("下载失败：HTTP ${resp.code}")
                val body = resp.body ?: error("下载失败：响应为空")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (target.length() < 1024 * 100) error("安装包异常（${target.length()} 字节）")
            onProgress(1f)
            target
        }
    }

    /** 是否已授权「安装未知来源应用」 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    /** 跳去系统开关页授权 */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** 调起安装器 */
    fun install(context: Context, file: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
