package com.javboss.android.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.javboss.android.data.update.ApkInstaller
import com.javboss.android.data.update.UpdateChecker
import kotlinx.coroutines.launch

/**
 * 更新弹窗：App 内下载 APK → 自动调起系统安装器。
 */
@Composable
fun UpdateDialog(
    info: UpdateChecker.UpdateInfo,
    currentVersion: String,
    mirror: String = "",
    onDismiss: () -> Unit,
    /** 启动提示里可「跳过此版本」，设置里传 null 表示不显示 */
    onSkipVersion: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun start() {
        val apk = info.apkUrl
        if (apk == null) {
            error = "该版本没有 APK 资源，请用「打开 Releases 页面」手动下载"
            return
        }
        if (!ApkInstaller.canInstall(context)) {
            ApkInstaller.openInstallPermission(context)
            error = "请先允许「安装未知来源应用」，再回来点一次下载"
            return
        }
        downloading = true
        progress = null
        error = null
        status = "开始下载…"
        scope.launch {
            ApkInstaller.download(context, apk, mirror) { p ->
                progress = p
                status = if (p != null) "下载中 ${(p * 100).toInt()}%" else "下载中…"
            }
                .onSuccess { file ->
                    status = "下载完成，正在调起安装器…"
                    ApkInstaller.install(context, file)
                        .onSuccess {
                            downloading = false
                            onDismiss()
                        }
                        .onFailure {
                            downloading = false
                            error = "调起安装器失败：${it.message}（可在 Releases 页面手动安装）"
                        }
                }
                .onFailure {
                    downloading = false
                    error = "下载失败：${it.message}"
                }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("发现新版本 v${info.latestVersion}") },
        text = {
            Column {
                Text(
                    "当前版本 v$currentVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes.take(600),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                status?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (downloading) {
                    val p = progress
                    if (p != null && p > 0f) {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!downloading) start() }, enabled = !downloading) {
                Text(if (downloading) "下载中…" else "下载并安装")
            }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    android.net.Uri.parse(
                                        com.javboss.android.data.update.UpdateMirror.wrap(info.pageUrl, mirror),
                                    ),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                ) { Text("浏览器打开") }
                TextButton(onClick = { if (!downloading) onDismiss() }, enabled = !downloading) { Text("稍后") }
                if (onSkipVersion != null && !downloading) {
                    TextButton(onClick = onSkipVersion) { Text("跳过此版本") }
                }
            }
        },
    )
}
