package com.javboss.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.javboss.android.data.subtitle.SubtitleFill
import kotlinx.coroutines.launch

/**
 * 批量补齐（S7）。
 *
 * 规则：已有字幕（外挂/内嵌/无需）跳过；番号字母+数字完全对上才下载；
 * 未命中的条目进入「待人工处理」，可批量标记无需字幕或稍后重试。
 */
@Composable
fun SubtitleFillScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by SubtitleFill.state.collectAsState()
    val manual by SubtitleFill.manual.collectAsState()
    var limit by remember { mutableStateOf(20) }
    var interval by remember { mutableStateOf(2000L) }
    var showManual by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { SubtitleFill.init(context) }
    }
    var candidateFor by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<com.javboss.android.data.subtitle.SubtitleCandidate>>(emptyList()) }
    var loadingCandidates by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("批量补齐字幕", style = MaterialTheme.typography.titleLarge)
        }

        Text(
            "规则：已有字幕（在线/内嵌/已标记无需）自动跳过；" +
                "候选标题里的番号必须与字母+数字完全一致才会下载，否则留给你手动处理。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("处理范围", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20 to "前 20 部", 100 to "前 100 部", 0 to "全部").forEach { (v, label) ->
                FilterChip(
                    selected = limit == v,
                    onClick = { limit = v },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    enabled = !state.running,
                )
            }
        }

        Text("请求间隔（防字幕源风控）", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1500L to "1.5 秒", 2000L to "2 秒", 3000L to "3 秒").forEach { (v, label) ->
                FilterChip(
                    selected = interval == v,
                    onClick = { interval = v },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    enabled = !state.running,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { SubtitleFill.start(context, limit = limit, intervalMs = interval) },
                enabled = !state.running,
            ) { Text("开始补齐") }
            OutlinedButton(onClick = { SubtitleFill.stop() }, enabled = state.running) { Text("停止") }
        }


        if (state.total > 0) {
            LinearProgressIndicator(
                progress = { (state.done.toFloat() / state.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
        Text(state.message, style = MaterialTheme.typography.bodyMedium)
        Text(
            "任务不会自动开始：只有点「开始补齐」才会跑。中途停止/被杀后，再点一次会跳过已处理的继续。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "进度 ${state.done}/${state.total} · 成功 ${state.filled} · 跳过 ${state.skipped} · 无匹配 ${state.noMatch} · 失败 ${state.failed}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.currentCode.isNotBlank() && state.running) {
            Text("正在处理：${state.currentCode}", style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // 待人工处理
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "待复核：${manual.size} 部",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showManual = !showManual }) {
                Text(if (showManual) "收起" else "展开")
            }
        }
        Text(
            "这些条目自动匹配没通过（站点没有该番号，或候选番号对不上）。" +
                "点「查看候选」可以看到站点到底返回了什么，自己决定用哪条或标记无字幕。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (showManual) {
            if (manual.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch { SubtitleFill.markAllManualNoSubtitle(context) }
                    }) { Text("全部标记无字幕") }
                    OutlinedButton(onClick = { SubtitleFill.retryManual(context) }) { Text("全部重试") }
                    OutlinedButton(onClick = { SubtitleFill.clearManual() }) { Text("清空列表") }
                }
            }
            // 纯文本列表，不显示海报；每行：番号 + 两个操作
            manual.take(200).forEach { code ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(code, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        candidateFor = code
                        loadingCandidates = true
                        candidates = emptyList()
                        scope.launch {
                            val r = com.javboss.android.di.AppGraph.subtitle.searchOnline(code)
                            candidates = r.getOrNull().orEmpty()
                            loadingCandidates = false
                        }
                    }) { Text("查看候选") }
                    TextButton(onClick = {
                        scope.launch { SubtitleFill.markNoNeed(code) }
                    }) { Text("无需字幕") }
                    TextButton(onClick = { SubtitleFill.removeManual(code) }) { Text("忽略") }
                }
            }
            if (manual.size > 200) {
                Text("（仅显示前 200 条，共 ${manual.size} 条）", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    // 候选字幕弹窗：把站点返回的全部候选列出来，由用户自己判断
    candidateFor?.let { code ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { candidateFor = null },
            title = { Text("$code 的候选字幕") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "下面就是字幕站返回的全部结果（共 ${candidates.size} 条）。" +
                            "标「番号不符」的是自动匹配没通过的，你可以自行判断是否使用。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (loadingCandidates) {
                        Text("正在搜索…", style = MaterialTheme.typography.bodySmall)
                    }
                    candidates.forEach { c ->
                        val matched = SubtitleFill.matchesCode(c.title, code)
                        val machine = SubtitleFill.isMachineTranslated(c.title)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                Text(
                                    buildString {
                                        append(c.language.ifBlank { "语言未知" })
                                        if (machine) append(" · 机翻")
                                        if (!matched) append(" · 番号不符")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (matched) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    com.javboss.android.di.AppGraph.subtitle.install(code, c)
                                        .onSuccess {
                                            com.javboss.android.data.subtitle.SubtitleIndex.markOnline(code)
                                            SubtitleFill.removeManual(code)
                                            candidateFor = null
                                        }
                                }
                            }) { Text("使用") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        SubtitleFill.markNoNeed(code)
                        candidateFor = null
                    }
                }) { Text("标记无字幕") }
            },
            dismissButton = { TextButton(onClick = { candidateFor = null }) { Text("关闭") } },
        )
    }
}
