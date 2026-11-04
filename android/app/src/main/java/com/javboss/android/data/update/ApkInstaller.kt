package com.javboss.android.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
            val started = System.currentTimeMillis()
            val realUrl = UpdateMirror.wrap(url, mirror)
            android.util.Log.i("JavBossUpdate", "download: $realUrl")
            val dir = apkDir(context)
            // 清掉旧包，避免占用空间
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, fileName)
            val proxy = com.javboss.android.core.net.NetProxySupport.current()
            android.util.Log.i(
                "JavBossUpdate",
                "download via proxy=" + (proxy?.address()?.toString() ?: "direct") + " url=" + realUrl,
            )
            val client = com.javboss.android.core.net.NetProxySupport.build(
                proxy = proxy,
                connectSeconds = 15,
                readSeconds = 120,
                callSeconds = 900,
            )
            val req = Request.Builder()
                .url(realUrl)
                .header("User-Agent", "JavBoss-Android")
                .build()
            client.newCall(req).execute().use { resp ->
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
            android.util.Log.i("JavBossUpdate", "download done: ${target.absolutePath} ${target.length()}B")
            target
        }
    }

    /** 是否已授权「安装未知来源应用」 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    /**
     * 跳去系统开关页授权（电视盒子/电视的系统设置差异很大，这里逐个兜底尝试）。
     * @return true 表示成功打开了某个设置页
     */
    fun openInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val candidates = listOf(
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ),
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS),
            Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
            // 部分 MIUI/电视系统把「未知来源」放在应用详情页
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
            Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS),
            Intent(android.provider.Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching {
                val resolved = context.packageManager.resolveActivity(intent, 0) != null
                if (resolved) context.startActivity(intent)
                resolved
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /**
     * 用 PackageInstaller 会话安装（系统安装服务执行，最可靠）。
     * 相当于"让系统自己去装"，不依赖电视上的 ACTION_VIEW 处理。
     */
    /**
     * 用 PackageInstaller 会话安装，并**等待系统应答**：
     *  - STATUS_SUCCESS        → 成功
     *  - STATUS_PENDING_USER_ACTION → 接收器会自动拉起系统确认界面，继续等最终结果
     *  - 8 秒内没有任何应答（部分固件会静默吞掉会话） → abandon 会话，交给上层回退 ACTION_VIEW
     */
    suspend fun installViaSession(context: Context, file: File): Result<Unit> = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = android.content.pm.PackageInstaller.SessionParams(
            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                runCatching { setPackageSource(android.content.pm.PackageInstaller.PACKAGE_SOURCE_OTHER) }
            }
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                // Android 12+ 允许自更新免确认（有确认页的系统仍会给 STATUS_PENDING_USER_ACTION）
                runCatching {
                    setRequireUserAction(
                        android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED,
                    )
                }
            }
        }

        InstallSessionBridge.reset()
        val sessionId = installer.createSession(params)
        android.util.Log.i("JavBossUpdate", "install session created: $sessionId")
        try {
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("app.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val resultIntent = Intent(context, InstallResultReceiver::class.java)
                    .setPackage(context.packageName)
                val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_MUTABLE
                val pending = android.app.PendingIntent.getBroadcast(context, sessionId, resultIntent, flags)
                session.commit(pending.intentSender)
                android.util.Log.i("JavBossUpdate", "install session committed, waiting for answer…")
            }

            // 等第一次应答（8 秒）
            val first = kotlinx.coroutines.withTimeoutOrNull(8_000) {
                InstallSessionBridge.status.first { it != null }
            }
            android.util.Log.i("JavBossUpdate", "session first answer: $first")
            when (first) {
                null -> {
                    android.util.Log.w("JavBossUpdate", "no answer in 8s, abandoning session $sessionId")
                    runCatching { installer.abandonSession(sessionId) }
                    error("系统未响应安装会话（可能被固件拦截）")
                }
                android.content.pm.PackageInstaller.STATUS_SUCCESS -> Unit
                android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // 接收器已拉起系统确认界面；等最终结果（最多 90 秒，用户可能在点确认）
                    val final = kotlinx.coroutines.withTimeoutOrNull(90_000) {
                        InstallSessionBridge.status.first {
                            it != null && it != android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
                        }
                    }
                    android.util.Log.i("JavBossUpdate", "session final answer: $final")
                    when (final) {
                        null -> error("等待用户确认超时")
                        android.content.pm.PackageInstaller.STATUS_SUCCESS -> Unit
                        else -> error("安装失败（status=$final）")
                    }
                }
                else -> error("安装失败（status=$first）")
            }
        } catch (t: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw t
        }
    }

    /** 安装：会话优先 → ACTION_VIEW → ACTION_INSTALL_PACKAGE */
    suspend fun install(context: Context, file: File): Result<Unit> {
        val sessionResult = runCatching { installViaSession(context, file).getOrThrow() }
        if (sessionResult.isSuccess) return sessionResult
        android.util.Log.w(
            "JavBossUpdate",
            "session install failed: ${sessionResult.exceptionOrNull()?.message}，回退 ACTION_VIEW",
        )
        return installViaIntent(context, file)
    }

    /** 传统 intent 安装（ACTION_VIEW 不可用时回退 ACTION_INSTALL_PACKAGE） */
    fun installViaIntent(context: Context, file: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val canView = context.packageManager.resolveActivity(view, 0) != null
        android.util.Log.i(
            "JavBossUpdate",
            "intent install: canInstall=" + canInstall(context) + " canView=" + canView,
        )
        if (canView) {
            context.startActivity(view)
            android.util.Log.i("JavBossUpdate", "intent install: ACTION_VIEW fired")
        } else {
            val legacy = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("android.intent.extra.NOT_UNKNOWN_SOURCES", true)
            }
            context.startActivity(legacy)
            android.util.Log.i("JavBossUpdate", "intent install: ACTION_INSTALL_PACKAGE fired")
        }
    }

    /**
     * 把安装包导出到「下载」目录（部分电视会静默拦截 ACTION_VIEW，
     * 这时用户可以用电视的文件管理器 / 当贝市场手动安装）。
     * @return 导出后的路径，失败返回 null
     */
    /** 导出到应用外部目录（/sdcard/Android/data/<pkg>/files/apk），任何系统都写得进去 */
    fun exportToAppDir(context: Context, file: File): String? = runCatching {
        val dir = java.io.File(context.getExternalFilesDir("apk") ?: context.filesDir, "")
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, file.name)
        if (target.exists()) target.delete()
        file.copyTo(target, overwrite = true)
        target.absolutePath
    }.getOrNull()

    fun exportToDownloads(context: Context, file: File): String? = runCatching {
        val dir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS,
        )
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, file.name)
        file.copyTo(target, overwrite = true)
        android.util.Log.i("JavBossUpdate", "exported apk -> ${target.absolutePath}")
        target.absolutePath
    }.getOrNull()
}
