package com.javboss.android.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 更新安装任务（与 UI 生命周期解耦）。
 *
 * 关键点：下载 + 安装跑在**应用级作用域**里，弹窗关闭 / 界面重组 / 协程取消都不会中断，
 * 进度和结果保留在 [state] 里，重新打开弹窗还能看到。
 */
object UpdateInstaller {

    data class State(
        val phase: String,
        val progress: Float? = null,
        val done: Boolean = false,
        val error: String? = null,
        val apkPath: String? = null,
        /** 已下载好、等待安装（例如需要用户授权） */
        val readyToInstall: Boolean = false,
    )

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false

    /** 读 APK 的版本信息（不安装） */
    private fun apkVersion(context: Context, file: File): Pair<Long, String>? = runCatching {
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        if (info == null) null
        else {
            @Suppress("DEPRECATION")
            (info.versionCode.toLong()) to (info.versionName ?: "")
        }
    }.getOrNull()

    fun reset() {
        _state.value = null
    }

    /** 下载并安装（幂等：正在跑就忽略重复点击） */
    fun start(
        context: Context,
        url: String,
        mirror: String,
        expectedVersion: String = "",
        expectedSha256: String? = null,
    ) {
        if (running) return
        // 上次已下载好的包直接复用，避免每次重下 4MB
        val cached = File(File(context.cacheDir, "apk"), "update.apk")
        val expectedName = expectedVersion
        if (cached.exists() && cached.length() > 1_000_000) {
            val ver = apkVersion(context, cached)
            val ok = ver != null && expectedName.isNotBlank() && ver.second == expectedName
            if (!ok) {
                com.javboss.android.core.log.AppLog.w(
                    "JavBossUpdate",
                    "cached apk version mismatch: " + ver + " expected=" + expectedName + "，删除后重新下载",
                )
                runCatching { cached.delete() }
            }
        }
        if (cached.exists() && cached.length() > 1_000_000) {
            com.javboss.android.core.log.AppLog.i("JavBossUpdate", "reuse cached apk: ${cached.length()}B")
            _state.value = State(
                phase = "使用已下载的安装包，正在调起安装器…",
                progress = 1f,
                apkPath = cached.absolutePath,
                readyToInstall = true,
            )
            install(context, cached)
            return
        }
        running = true
        _state.value = State(phase = "开始下载…")
        scope.launch {
            try {
                val result = ApkInstaller.download(
                    context = context,
                    url = url,
                    mirror = mirror,
                    expectedSha256 = expectedSha256,
                ) { p ->
                    val cur = _state.value
                    if (cur != null && !cur.done) {
                        _state.value = cur.copy(
                            phase = if (p != null) "下载中 ${(p * 100).toInt()}%" else "下载中…",
                            progress = p,
                        )
                    }
                }
                result
                    .onSuccess { file ->
                        // 下载完成后校验版本，避免装到旧包/错误包
                        val ver = apkVersion(context, file)
                        if (expectedVersion.isNotBlank() && ver != null && ver.second != expectedVersion) {
                            com.javboss.android.core.log.AppLog.w(
                                "JavBossUpdate",
                                "downloaded apk version mismatch: " + ver + " expected=" + expectedVersion,
                            )
                            _state.value = State(
                                phase = "下载到的安装包版本不对",
                                done = true,
                                error = "期望 v" + expectedVersion + "，实际 v" + ver.second + "，请重试",
                            )
                            return@onSuccess
                        }
                        _state.value = State(
                            phase = "下载完成（${file.length() / 1024}KB），正在安装…",
                            progress = 1f,
                            apkPath = file.absolutePath,
                            readyToInstall = true,
                        )
                        // 另存一份到应用外部目录，方便电视手动安装
                        runCatching { ApkInstaller.exportToAppDir(context, file) }
                            .onSuccess { path ->
                                com.javboss.android.core.log.AppLog.i("JavBossUpdate", "exported copy -> $path")
                            }
                        install(context, file)
                    }
                    .onFailure { e ->
                        _state.value = State(
                            phase = "下载失败",
                            done = true,
                            error = e.message ?: e.javaClass.simpleName,
                        )
                    }
            } catch (t: Throwable) {
                _state.value = State(phase = "下载失败", done = true, error = t.message ?: "未知错误")
            } finally {
                running = false
            }
        }
    }

    /** 对已下载好的 APK 再次尝试安装（例如用户刚授权「未知来源」） */
    fun retryInstall(context: Context) {
        val path = _state.value?.apkPath ?: return
        val file = File(path)
        if (!file.exists()) {
            _state.value = _state.value?.copy(done = true, error = "安装包已丢失，请重新下载")
            return
        }
        install(context, file)
    }

    private fun install(context: Context, file: File) {
        // 注意：MIUI TV 上 canRequestPackageInstalls() 始终返回 false（即使系统里已允许未知来源），
        // 所以这里只记录、不拦路——直接尝试安装，失败再提示手动安装。
        val canInstall = runCatching { ApkInstaller.canInstall(context) }.getOrDefault(false)
        com.javboss.android.core.log.AppLog.i("JavBossUpdate", "install: canRequestPackageInstalls=$canInstall（仅参考，不拦截）")
        _state.value = _state.value?.copy(phase = "正在调起安装器…")
        scope.launch {
            ApkInstaller.install(context, file)
                .onSuccess {
                    com.javboss.android.core.log.AppLog.i("JavBossUpdate", "install requested ok")
                    _state.value = _state.value?.copy(phase = "已调起安装，请在电视上确认", done = true)
                }
                .onFailure { e ->
                    com.javboss.android.core.log.AppLog.w("JavBossUpdate", "install failed: ${e.message}")
                    val exported = runCatching { ApkInstaller.exportToAppDir(context, file) }.getOrNull()
                    _state.value = _state.value?.copy(
                        phase = "安装失败",
                        done = true,
                        error = buildString {
                            append("系统未弹出安装界面：").append(e.message ?: "未知")
                            if (exported != null) {
                                append("；也可用文件管理手动安装：" + exported)
                            }
                        },
                    )
                }
        }
    }
}
