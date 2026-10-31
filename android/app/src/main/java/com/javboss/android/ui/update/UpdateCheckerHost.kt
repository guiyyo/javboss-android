package com.javboss.android.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.javboss.android.data.update.MirrorSpeed
import com.javboss.android.data.update.UpdateChecker
import com.javboss.android.data.update.UpdateMirror
import com.javboss.android.data.update.UpdateMirrorTester
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 更新检查宿主（进入主界面后跑，完全后台、不阻塞 UI）：
 *  1. **静默镜像测速**（6 小时内测过就跳过）→ 自动应用最快的那个；
 *  2. 再检查 GitHub Releases；
 *  3. 只有发现新版本才弹窗（同一版本只提醒一次）。
 */
@Composable
fun UpdateCheckerHost() {
    var pending by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var mirror by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (UpdateCheckOnce.done) return@LaunchedEffect
        UpdateCheckOnce.done = true

        withContext(Dispatchers.IO) {
            val prefs = runCatching { AppGraph.settings.uiPrefs.first() }.getOrNull()
                ?: return@withContext
            mirror = prefs.updateMirror
            if (!prefs.autoCheckUpdate) return@withContext

            // 1) 先静默测速（6 小时节流），自动应用最快镜像
            val now = System.currentTimeMillis()
            if (now - prefs.updateMirrorLastTest > 6 * 3600_000L) {
                val apk = runCatching {
                    UpdateChecker.check(com.javboss.android.BuildConfig.VERSION_NAME).getOrNull()?.apkUrl
                }.getOrNull()
                if (!apk.isNullOrBlank()) {
                    var best: MirrorSpeed? = null
                    runCatching {
                        val targets = UpdateMirror.PRESETS.map { it.first }
                        coroutineScope {
                            targets.chunked(4).forEach { group ->
                                group.map { m -> async { UpdateMirrorTester.test(m, apk) } }
                                    .map { it.await() }
                                    .forEach { r ->
                                        if (r.ok && r.kbps > 0 && (best == null || r.kbps > best!!.kbps)) best = r
                                    }
                            }
                        }
                    }
                    val chosen = best
                    if (chosen != null) {
                        android.util.Log.i(
                            "JavBossUpdate",
                            "silent speedtest best=${chosen.mirror} ${chosen.kbps}KB/s ${chosen.ttfbMs}ms",
                        )
                        runCatching { AppGraph.settings.setUpdateMirror(chosen.mirror) }
                        mirror = chosen.mirror
                    }
                    runCatching { AppGraph.settings.setUpdateMirrorLastTest(now) }
                }
            }

            // 2) 再检查更新
            val result = runCatching {
                UpdateChecker.check(com.javboss.android.BuildConfig.VERSION_NAME)
            }.getOrNull() ?: return@withContext
            result.onSuccess { info ->
                if (!info.hasUpdate) return@onSuccess
                val skipped = runCatching { AppGraph.settings.updateSkippedVersion() }.getOrNull()
                if (skipped != info.latestVersion) pending = info
            }
        }
    }

    pending?.let { info ->
        UpdateDialog(
            info = info,
            currentVersion = com.javboss.android.BuildConfig.VERSION_NAME,
            mirror = mirror,
            onDismiss = { pending = null },
            onSkipVersion = {
                kotlinx.coroutines.MainScope().launch {
                    runCatching { AppGraph.settings.setUpdateSkippedVersion(info.latestVersion) }
                }
                pending = null
            },
        )
    }
}

/** 同一进程只检查一次，避免来回切页反复请求。 */
private object UpdateCheckOnce {
    var done: Boolean = false
}
