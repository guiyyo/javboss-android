package com.javboss.android.ui.update

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.javboss.android.data.update.UpdateChecker
import com.javboss.android.data.update.UpdateInstaller
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 更新弹窗：说明可滚动；下载与安装交给 [UpdateInstaller]（应用级作用域），
 * 弹窗关闭也不会中断，重新打开还能看到进度/结果。
 */
@Composable
fun UpdateDialog(
    info: UpdateChecker.UpdateInfo,
    currentVersion: String,
    mirror: String = "",
    onDismiss: () -> Unit,
    onSkipVersion: (() -> Unit)? = null,
    tvMode: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installState by UpdateInstaller.state.collectAsState()

    val notesScroll = rememberScrollState()
    val notesFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (tvMode && info.notes.isNotBlank()) {
            delay(300)
            runCatching { notesFocus.requestFocus() }
        }
    }
    // 只有"本次弹窗内真的跑过下载"并成功后才自动关闭；
    // 否则单例里遗留的旧状态会让新弹窗一出现就自动消失。
    var sawRunning by remember { mutableStateOf(false) }
    LaunchedEffect(installState) {
        val st = installState ?: return@LaunchedEffect
        if (!st.done) sawRunning = true
        if (sawRunning && st.done && st.error == null) {
            delay(600)
            onDismiss()
        }
    }

    fun primaryAction() {
        val st = installState
        when {
            st == null || st.done -> {
                val apk = info.apkUrl
                if (apk.isNullOrBlank()) {
                    onDismiss()
                } else {
                    UpdateInstaller.start(context, apk, mirror, info.latestVersion, info.sha256)
                }
            }
            st.readyToInstall -> UpdateInstaller.retryInstall(context)
        }
    }

    val busy = installState?.done == false
    val primaryLabel = when {
        busy && installState?.readyToInstall == true -> "继续安装"
        busy -> "下载中…"
        installState?.readyToInstall == true -> "继续安装"
        else -> "下载并安装"
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("发现新版本 v${info.latestVersion}") },
        text = {
            Column {
                Text(
                    "当前版本 v$currentVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                installState?.let { st ->
                    Text(
                        st.phase,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (st.error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    st.error?.let { err ->
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    val p = st.progress
                    if (p != null && p < 1f) {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                }

                if (info.notes.isNotBlank()) {
                    Text(
                        if (tvMode) "更新内容（方向键 ↑↓ 滚动）" else "更新内容（上下滑动查看全部）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (tvMode) 280.dp else 340.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .then(if (tvMode) Modifier.focusable().focusRequester(notesFocus) else Modifier)
                            .onKeyEvent { ev ->
                                if (!tvMode || ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (ev.key) {
                                    Key.DirectionUp -> {
                                        if (notesScroll.value <= 0) return@onKeyEvent false
                                        scope.launch { notesScroll.animateScrollBy(-180f) }
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        if (notesScroll.value >= notesScroll.maxValue) return@onKeyEvent false
                                        scope.launch { notesScroll.animateScrollBy(180f) }
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .verticalScroll(notesScroll)
                            .padding(10.dp),
                    ) {
                        Text(info.notes.trim(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { primaryAction() }, enabled = !busy) {
                Text(primaryLabel)
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
                TextButton(onClick = { if (!busy) onDismiss() }, enabled = !busy) { Text("稍后") }
                if (onSkipVersion != null && !busy) {
                    TextButton(onClick = onSkipVersion) { Text("跳过此版本") }
                }
            }
        },
    )
}
