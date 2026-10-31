package com.javboss.android.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javboss.android.data.subtitle.InstalledSubtitle
import com.javboss.android.data.subtitle.SubtitleCandidate
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/* ------------------------------------------------------------------ 公共外壳 */

/** 与「设置密码」弹窗一致的外观：方角面板 + outline 描边 + 无阴影。 */
@Composable
private fun TvDialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        // 关键：默认对话框窗口是 wrap_content，fillMaxWidth 撑不开
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 1500.dp),
        ) {
            Column(
                Modifier
                    .padding(28.dp)
                    .heightIn(max = 680.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}

/* ------------------------------------------------------------------ 只做查找/下载 */

/**
 * 详情页用：**只负责查找与下载字幕**（不做偏移）。
 * 搜索框为「按 OK 才输入」的 TvInputField。
 */
@Composable
fun TvSubtitleSearchDialog(
    code: String,
    currentName: String?,
    onClose: () -> Unit,
    onLoaded: (InstalledSubtitle) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(code) }
    var searching by remember { mutableStateOf(false) }
    var loadingId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<SubtitleCandidate>>(emptyList()) }
    var installed by remember { mutableStateOf<InstalledSubtitle?>(null) }
    var attemptInfo by remember { mutableStateOf<String?>(null) }
    var elapsedSec by remember { mutableStateOf(0) }

    LaunchedEffect(searching) {
        if (searching) {
            elapsedSec = 0
            while (true) {
                delay(1000)
                elapsedSec += 1
            }
        }
    }

    fun search() {
        searching = true
        error = null
        status = "搜索中…"
        scope.launch {
            AppGraph.subtitle.searchOnline(query.ifBlank { code }) { attempt, total, source ->
                val host = source.removePrefix("https://").removePrefix("http://").trimEnd('/')
                attemptInfo = "正在尝试源 $attempt/$total：$host"
                status = attemptInfo
            }
                .onSuccess {
                    attemptInfo = null
                    results = it
                    status = if (it.isEmpty()) null else "共 ${it.size} 条，按 OK 加载"
                    if (it.isEmpty()) error = "没搜到字幕（可换关键词重试）"
                }
                .onFailure {
                    attemptInfo = null
                    status = null
                    error = "所有字幕源都超时/失败：${it.message}"
                }
            searching = false
        }
    }

    fun load(c: SubtitleCandidate) {
        if (loadingId != null) return
        loadingId = c.id
        progress = null
        error = null
        status = "下载中…"
        scope.launch {
            AppGraph.subtitle.install(code, c) { p -> progress = p }
                .onSuccess { inst ->
                    installed = inst
                    status = "✔ 已下载：${inst.name}"
                    onLoaded(inst)
                    delay(500)
                    loadingId = null
                    progress = null
                }
                .onFailure {
                    error = "加载失败：${it.message}"
                    status = null
                    loadingId = null
                }
        }
    }

    LaunchedEffect(Unit) {
        installed = AppGraph.subtitle.current(code)
        search()
    }

    TvDialogFrame(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "查找字幕",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f),
            )
            Text(
                "番号 ${if (code.isBlank()) "未识别" else code}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TvFocusable(onClick = onClose, shape = RoundedCornerShape(8.dp), flat = true) {
                Text(
                    "关闭",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // 关键词（按 OK 才进入输入）
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvInputField(
                value = query,
                onValueChange = { query = it },
                placeholder = "番号 / 关键词",
                modifier = Modifier.width(420.dp),
            )
            TvFocusable(onClick = { if (!searching && loadingId == null) search() }, shape = RoundedCornerShape(8.dp)) {
                Text(
                    when {
                        searching -> "搜索中…"
                        error != null -> "重试"
                        else -> "搜索"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }

        Text(
            "已下载：${installed?.name ?: (currentName ?: "无")}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 明显状态提示：正在尝试的源 + 已用时
        if (searching) {
            Text(
                "🔍 正在搜索字幕（${elapsedSec}s）" + (attemptInfo?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "网络较慢时会逐个源尝试，个个源最多 7 秒；全部失败后可重试",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        status?.let { if (!searching) Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        val p = progress
        if (loadingId != null) {
            if (p != null && p > 0f) {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            items(results, key = { it.id }) { c ->
                val loading = loadingId == c.id
                TvFocusable(
                    onClick = { load(c) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    flat = true,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.title,
                                color = if (loading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${c.language} · ${c.source}" + if (loading) " · 加载中" else "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text(
                            if (loading) "…" else "下载",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ 只做偏移/开关 */

/** 播放页用：**只调偏移 + 是否使用字幕**（不放搜索下载）。 */
@Composable
fun TvSubtitleOffsetDialog(
    currentName: String?,
    currentOffsetMs: Long,
    skipped: Boolean,
    hasCues: Boolean,
    onClose: () -> Unit,
    onShift: (Long) -> Unit,
    onReset: () -> Unit,
    onDetach: () -> Unit,
    onToggleSkip: (Boolean) -> Unit,
) {
    TvDialogFrame(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("字幕", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TvFocusable(onClick = onClose, shape = RoundedCornerShape(8.dp), flat = true) {
                Text(
                    "关闭",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Text(
            currentName ?: "未加载字幕",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "偏移 ${fmtOffset2(currentOffsetMs)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OffsetChip("−1s", currentName != null) { onShift(-1000) }
            OffsetChip("−0.5s", currentName != null) { onShift(-500) }
            OffsetChip("−0.1s", currentName != null) { onShift(-100) }
            OffsetChip("+0.1s", currentName != null) { onShift(100) }
            OffsetChip("+0.5s", currentName != null) { onShift(500) }
            OffsetChip("+1s", currentName != null) { onShift(1000) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvFocusable(onClick = onReset, shape = RoundedCornerShape(8.dp), flat = true) {
                Text("归零", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
            TvFocusable(onClick = onDetach, shape = RoundedCornerShape(8.dp), flat = true) {
                Text("移除字幕", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
            TvFocusable(
                onClick = { onToggleSkip(!skipped) },
                shape = RoundedCornerShape(8.dp),
                flat = true,
            ) {
                Text(
                    if (skipped) "✔ 本片无需字幕" else "本片无需字幕",
                    color = if (skipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Text(
            if (hasCues) "字幕显示中：菜个键可再次打开本窗口" else "本片没有已加载的字幕（可在详情页查找下载）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun fmtOffset2(ms: Long): String {
    val sign = if (ms >= 0) "+" else "−"
    val abs = kotlin.math.abs(ms)
    return "$sign${abs / 1000}.${(abs % 1000) / 100}s"
}

@Composable
private fun OffsetChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    TvFocusable(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(8.dp),
        flat = !enabled,
    ) {
        Text(
            label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
