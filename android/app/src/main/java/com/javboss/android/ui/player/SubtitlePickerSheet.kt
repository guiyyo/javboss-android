package com.javboss.android.ui.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javboss.android.data.subtitle.InstalledSubtitle
import com.javboss.android.data.subtitle.SubtitleCandidate
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * 选字幕：在线搜索（SubtitleCat）/ 本地字幕库 / 手动直链。
 * - 顶部固定显示「已下载的字幕」，可直接勾选使用
 * - 下载有进度条（有 content-length 按百分比，否则不确定态）
 * - 加载成功后打勾并自动应用
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitlePickerSheet(
    code: String,
    activeName: String?,
    onDismiss: () -> Unit,
    onLoaded: (InstalledSubtitle) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf(code) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var online by remember { mutableStateOf<List<SubtitleCandidate>>(emptyList()) }
    var locals by remember { mutableStateOf<List<SubtitleCandidate>>(emptyList()) }
    var urlInput by remember { mutableStateOf("") }
    var folderUri by remember { mutableStateOf<String?>(null) }

    var installed by remember { mutableStateOf<InstalledSubtitle?>(null) }
    var loadingId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var elapsedSec by remember { mutableStateOf(0) }
    var justLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(loadingId) {
        if (loadingId == null) return@LaunchedEffect
        elapsedSec = 0
        while (true) {
            delay(1000)
            elapsedSec += 1
        }
    }

    LaunchedEffect(Unit) {
        folderUri = runCatching { AppGraph.settings.uiPrefs.first().subtitleFolder }.getOrNull()
        locals = AppGraph.subtitle.findLocal(code)
        installed = AppGraph.subtitle.current(code)
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            scope.launch {
                AppGraph.settings.setSubtitleFolder(uri.toString())
                folderUri = uri.toString()
                locals = AppGraph.subtitle.findLocal(code)
            }
        }
    }

    fun doSearch() {
        searching = true
        error = null
        status = "搜索中…"
        scope.launch {
            AppGraph.subtitle.searchOnline(query.ifBlank { code }) { attempt, total, source ->
                val host = source.removePrefix("https://").removePrefix("http://").trimEnd('/')
                status = "正在尝试源 $attempt/$total：$host（${elapsedSec}s）"
            }
                .onSuccess {
                    online = it
                    status = if (it.isEmpty()) null else "搜到 ${it.size} 条，点一行加载"
                    if (it.isEmpty()) error = "没搜到字幕（可换关键词或改用直链）"
                }
                .onFailure {
                    error = "所有字幕源都超时/失败：${it.message}"
                    status = null
                }
            searching = false
        }
    }

    fun load(c: SubtitleCandidate) {
        if (loadingId != null) return
        android.util.Log.i("JavBossSub", "picker load clicked: ${c.title}")
        loadingId = c.id
        progress = null
        justLoaded = false
        error = null
        status = "解析字幕页…"
        scope.launch {
            status = "下载中…"
            AppGraph.subtitle.install(code, c) { p -> progress = p }
                .onSuccess { inst ->
                    progress = 1f
                    status = "已保存 ${inst.name}（${File(inst.filePath).length() / 1024}KB），正在应用…"
                    installed = inst
                    onLoaded(inst)
                    justLoaded = true
                    status = "✔ 已加载并应用：${inst.name}"
                    delay(800)
                    loadingId = null
                    progress = null
                }
                .onFailure {
                    error = "加载失败：${it.message}"
                    status = null
                    loadingId = null
                    progress = null
                }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("选择字幕", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (code.isBlank()) "未识别番号" else "番号 $code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onDismiss) { Text("完成") }
            }

            val cur = installed
            if (cur != null) {
                val inUse = activeName != null && activeName == cur.name
                Row(
                    Modifier.fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (inUse) "✔ " else "") + cur.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (inUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "已下载 · ${File(cur.filePath).length() / 1024}KB · 偏移 ${formatOffset(cur.offsetMs)}" +
                                if (inUse) " · 使用中" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (inUse) {
                        Text("使用中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    } else {
                        TextButton(onClick = { onLoaded(cur) }) { Text("使用") }
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                AppGraph.subtitle.clear(code)
                                installed = null
                                status = "已删除该字幕缓存"
                            }
                        },
                    ) { Text("删除", maxLines = 1, softWrap = false) }
                }
            }

            if (status != null || loadingId != null) {
                Column {
                    Text(
                        (status ?: "") + if (loadingId != null && !justLoaded) "（${elapsedSec}s）" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (justLoaded) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (loadingId != null && !justLoaded) {
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
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("在线搜索") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("本地文件") })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("直链") })
            }

            when (tab) {
                0 -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("番号 / 关键词") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { doSearch() }, enabled = !searching && loadingId == null) {
                            Text(if (searching) "搜索中" else "搜索")
                        }
                    }
                    Text(
                        "在线源：SubtitleCat（多源轮询，单个源最多 15 秒；全部失败可重试）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (searching) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 60.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        PickerResults(online, error, loadingId) { load(it) }
                    }
                }
                1 -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (folderUri.isNullOrBlank()) "未选择字幕文件夹" else "已选择文件夹",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { folderPicker.launch(null) }) { Text("选择文件夹") }
                        TextButton(
                            enabled = loadingId == null,
                            onClick = {
                                scope.launch {
                                    folderUri = runCatching { AppGraph.settings.uiPrefs.first().subtitleFolder }.getOrNull()
                                    locals = AppGraph.subtitle.findLocal(code)
                                    error = if (locals.isEmpty()) "该文件夹里没有匹配番号的字幕" else null
                                }
                            },
                        ) { Text("刷新") }
                    }
                    PickerResults(locals, error, loadingId) { load(it) }
                }
                else -> {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("字幕直链（.srt/.ass/.vtt）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        enabled = urlInput.isNotBlank() && loadingId == null,
                        onClick = {
                            val url = urlInput.trim()
                            loadingId = "url"
                            progress = null
                            justLoaded = false
                            error = null
                            status = "下载中…"
                            scope.launch {
                                AppGraph.subtitle.installFromUrl(code, url)
                                    .onSuccess { inst ->
                                        installed = inst
                                        onLoaded(inst)
                                        justLoaded = true
                                        status = "✔ 已加载并应用：${inst.name}"
                                        delay(600)
                                        loadingId = null
                                    }
                                    .onFailure {
                                        error = "加载失败：${it.message}"
                                        status = null
                                        loadingId = null
                                    }
                            }
                        },
                    ) { Text(if (loadingId == "url") "加载中…" else "加载") }
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerResults(
    items: List<SubtitleCandidate>,
    error: String?,
    loadingId: String?,
    onPick: (SubtitleCandidate) -> Unit,
) {
    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 320.dp)) {
        if (items.isEmpty()) {
            Text(
                error ?: "还没有结果",
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(items, key = { it.id }) { c ->
                    val isLoading = loadingId == c.id
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = loadingId == null) { onPick(c) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isLoading) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${c.language} · ${c.source}" + if (isLoading) " · 正在加载" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("加载", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
