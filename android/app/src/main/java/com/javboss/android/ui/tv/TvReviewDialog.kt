package com.javboss.android.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.javboss.android.data.subtitle.SubtitleFill
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * TV「待复核」列表：列出所有没匹配到字幕的番号，可逐条查看候选或标记无字幕。
 */
@Composable
fun TvReviewDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manual by SubtitleFill.manual.collectAsState()
    var candidateCode by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<com.javboss.android.data.subtitle.SubtitleCandidate>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val reviewScope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 1400.dp).heightIn(max = 560.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "待复核（" + manual.size + " 部）",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TvFocusable(onClick = onDismiss, shape = RoundedCornerShape(8.dp), flat = true) {
                        Text("关闭", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                Text(
                    "这些是自动匹配没通过的番号（站点没有、或候选番号对不上）。点番号看候选，自己决定用哪条。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (manual.isEmpty()) {
                    Text("暂无待复核条目", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvFocusable(
                        onClick = {
                            reviewScope.launch(Dispatchers.IO) {
                                SubtitleFill.markAllManualNoSubtitle(context)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("全部标记无字幕", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium) }
                    TvFocusable(
                        onClick = { SubtitleFill.retryManual(context) },
                        shape = RoundedCornerShape(8.dp),
                    ) { Text("全部重试", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium) }
                }
                HorizontalDivider()
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    manual.take(120).forEach { code ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TvFocusable(
                                onClick = {
                                    candidateCode = code
                                    loading = true
                                    candidates = emptyList()
                                    reviewScope.launch(Dispatchers.IO) {
                                        val r = AppGraph.subtitle.searchOnline(code)
                                        candidates = r.getOrNull().orEmpty()
                                        loading = false
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                flat = true,
                            ) {
                                Text(code, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                            }
                            TvFocusable(
                                onClick = {
                                    reviewScope.launch(Dispatchers.IO) { SubtitleFill.markNoNeed(code) }
                                },
                                shape = RoundedCornerShape(6.dp),
                                flat = true,
                            ) {
                                Text(
                                    "标记无字幕",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                            TvFocusable(
                                onClick = { SubtitleFill.removeManual(code) },
                                shape = RoundedCornerShape(6.dp),
                                flat = true,
                            ) {
                                Text(
                                    "忽略",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 候选列表
    candidateCode?.let { code ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { candidateCode = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 1400.dp).heightIn(max = 560.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            code + " 的候选字幕（" + candidates.size + "）",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TvFocusable(onClick = { candidateCode = null }, shape = RoundedCornerShape(8.dp), flat = true) {
                            Text("关闭", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                    if (loading) Text("正在搜索…", style = MaterialTheme.typography.bodySmall)
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        candidates.take(40).forEach { cand ->
                            val matched = SubtitleFill.matchesCode(cand.title, code)
                            val machine = SubtitleFill.isMachineTranslated(cand.title)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(cand.title, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    Text(
                                        buildString {
                                            append(cand.language.ifBlank { "语言未知" })
                                            if (machine) append(" · 机翻")
                                            if (!matched) append(" · 番号不符")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (matched) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TvFocusable(
                                    onClick = {
                                        reviewScope.launch(Dispatchers.IO) {
                                            AppGraph.subtitle.install(code, cand).onSuccess {
                                                com.javboss.android.data.subtitle.SubtitleIndex.markOnline(code)
                                                SubtitleFill.removeManual(code)
                                            }
                                        }
                                        candidateCode = null
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    flat = true,
                                ) {
                                    Text(
                                        "使用",
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
