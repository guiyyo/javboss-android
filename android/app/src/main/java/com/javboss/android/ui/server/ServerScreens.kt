package com.javboss.android.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.DirectoryRef
import com.javboss.android.data.model.DownloadJobItem
import com.javboss.android.data.model.DownloadsPage
import com.javboss.android.data.model.ResourceSnapshot
import com.javboss.android.data.model.ToolsResponse
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.EmptyBox
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.LoadingBox
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/* ================================================================ 目录 */

class DirectoryViewModel : ViewModel() {
    var items by mutableStateOf<List<DirectoryRef>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun load() {
        loading = true
        viewModelScope.launch {
            when (val result = AppGraph.config.directories()) {
                is ApiResult.Success -> { items = result.data; error = null }
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun toggleEnabled(dir: DirectoryRef, enabled: Boolean) {
        viewModelScope.launch {
            val body = mapOf<String, kotlinx.serialization.json.JsonElement>(
                "enabled" to JsonPrimitive(enabled),
            )
            when (val result = AppGraph.server.updateDirectory(dir.id, body)) {
                is ApiResult.Success -> load()
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    fun scan(dir: DirectoryRef) {
        viewModelScope.launch {
            when (val result = AppGraph.server.scanDirectory(dir.id)) {
                is ApiResult.Success -> { message = "已触发扫描：${dir.path}"; load() }
                is ApiResult.Failure -> message = result.message
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(onBack: () -> Unit, vm: DirectoryViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("目录管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            when {
                vm.loading -> LoadingBox(Modifier.fillMaxSize())
                vm.error != null -> ErrorBox(vm.error!!, onRetry = vm::load, modifier = Modifier.fillMaxSize())
                vm.items.isEmpty() -> EmptyBox("没有配置目录", modifier = Modifier.fillMaxSize())
                else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    items(vm.items, key = { it.id }) { dir ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    dir.path,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "已扫描 ${dir.scannedVideoCount} · 已刮削 ${dir.scrapedVideoCount}" +
                                        if (dir.missing) " · 目录不可达" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("启用", style = MaterialTheme.typography.labelMedium)
                                        Switch(
                                            checked = dir.enabled,
                                            onCheckedChange = { vm.toggleEnabled(dir, it) },
                                            modifier = Modifier.padding(start = 6.dp),
                                        )
                                    }
                                    TextButton(onClick = { vm.scan(dir) }) {
                                        Text(if (dir.isScanning) "扫描中…" else "扫描")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            vm.message?.let {
                Text(
                    it,
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

}

/* ================================================================ 下载 */

class DownloadViewModel : ViewModel() {
    var page by mutableStateOf(DownloadsPage())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun load() {
        loading = true
        viewModelScope.launch {
            when (val result = AppGraph.server.downloads()) {
                is ApiResult.Success -> { page = result.data; error = null }
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun create(magnetUrl: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            when (val result = AppGraph.server.createDownload(magnetUrl)) {
                is ApiResult.Success -> { message = "已提交下载任务"; load(); onDone() }
                is ApiResult.Failure -> message = "提交失败：${result.message}"
            }
        }
    }

    fun retry(job: DownloadJobItem) = action("重试") { AppGraph.server.retryDownload(job.id) }

    fun cancel(job: DownloadJobItem) = action("取消") { AppGraph.server.cancelDownload(job.id) }

    fun delete(job: DownloadJobItem) = action("删除") { AppGraph.server.deleteDownload(job.id) }

    private fun action(label: String, block: suspend () -> ApiResult<Unit>) {
        viewModelScope.launch {
            when (val result = block()) {
                is ApiResult.Success -> { message = "$label 已提交"; load() }
                is ApiResult.Failure -> message = "$label 失败：${result.message}"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(onBack: () -> Unit, vm: DownloadViewModel = viewModel()) {
    var createOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var magnet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    LaunchedEffect(Unit) { vm.load() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = { TextButton(onClick = { createOpen = true }) { Text("新建") } },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Text(
                "进行中 ${vm.page.counts.active} · 已完成 ${vm.page.counts.completed} · 失败 ${vm.page.counts.failed}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                vm.loading -> LoadingBox(Modifier.fillMaxSize())
                vm.error != null -> ErrorBox(vm.error!!, onRetry = vm::load, modifier = Modifier.fillMaxSize())
                vm.page.items.isEmpty() -> EmptyBox("暂无下载任务", modifier = Modifier.fillMaxSize())
                else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    items(vm.page.items, key = { it.id }) { job ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    job.magnetName.ifBlank { job.infoHash },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${job.status} · ${"%.1f".format(job.bytesDownloaded / 1024.0 / 1024.0)} / " +
                                        "${"%.1f".format(job.bytesTotal / 1024.0 / 1024.0)} MB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (job.bytesTotal > 0) {
                                    LinearProgressIndicator(
                                        progress = { job.progress },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    )
                                }
                                if (job.errorMessage.isNotBlank()) {
                                    Text(
                                        job.errorMessage,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { vm.retry(job) }) { Text("重试") }
                                    TextButton(onClick = { vm.cancel(job) }) { Text("取消") }
                                    TextButton(onClick = { vm.delete(job) }) { Text("删除") }
                                }
                            }
                        }
                    }
                }
            }
            vm.message?.let {
                Text(
                    it,
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (createOpen) {
        AlertDialog(
            onDismissRequest = { createOpen = false },
            title = { Text("新建下载") },
            text = {
                OutlinedTextField(
                    value = magnet,
                    onValueChange = { magnet = it },
                    label = { Text("磁力链接") },
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = magnet.startsWith("magnet:"),
                    onClick = {
                        vm.create(magnet.trim()) { createOpen = false; magnet = "" }
                    },
                ) { Text("提交") }
            },
            dismissButton = { TextButton(onClick = { createOpen = false }) { Text("取消") } },
        )
    }
}

/* ================================================================ 服务器状态 */

class ResourceViewModel : ViewModel() {
    var snapshot by mutableStateOf<ResourceSnapshot?>(null)
        private set
    var tools by mutableStateOf<ToolsResponse?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load() {
        loading = true
        viewModelScope.launch {
            val snapshotResult = AppGraph.server.resources()
            if (snapshotResult is ApiResult.Success) snapshot = snapshotResult.data
            else if (snapshotResult is ApiResult.Failure) error = snapshotResult.message
            val toolsResult = AppGraph.server.tools()
            if (toolsResult is ApiResult.Success) tools = toolsResult.data
            loading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceScreen(onBack: () -> Unit, vm: ResourceViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务器状态") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = { TextButton(onClick = vm::load) { Text("刷新") } },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                vm.loading -> LoadingBox(Modifier.fillMaxWidth())
                vm.error != null -> ErrorBox(vm.error!!, onRetry = vm::load)
                else -> {
                    val s = vm.snapshot
                    if (s != null) {
                        InfoCard("运行环境", listOf(
                            "系统" to s.os,
                            "CPU 核心" to s.logicalCpus.toString(),
                            "容器模式" to if (s.container) "是" else "否",
                            "进程内存" to "${"%.0f".format((s.process?.rssBytes ?: 0) / 1024.0 / 1024.0)} MB",
                            "运行时长" to "${(s.process?.uptimeSeconds ?: 0) / 60} 分钟",
                        ))
                        s.systemMemory?.let { mem ->
                            InfoCard("内存", listOf(
                                "已用" to "${"%.0f".format(mem.usedBytes / 1024.0 / 1024.0 / 1024.0)} GB / " +
                                    "${"%.0f".format(mem.totalBytes / 1024.0 / 1024.0 / 1024.0)} GB",
                                "占比" to "${"%.1f".format(mem.usedPercent)}%",
                            ))
                        }
                        s.dataDisk?.let { disk ->
                            InfoCard("数据盘", listOf(
                                "路径" to disk.path,
                                "剩余" to "${"%.0f".format(disk.freeBytes / 1024.0 / 1024.0 / 1024.0)} GB",
                                "占比" to "${"%.1f".format(disk.usedPercent)}%",
                            ))
                        }
                    }
                    vm.tools?.ffmpeg?.let { ff ->
                        InfoCard("FFmpeg", listOf(
                            "已安装" to if (ff.installed) "是" else "否",
                            "版本" to ff.version.ifBlank { "—" },
                            "路径" to ff.path.ifBlank { "—" },
                        ))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, rows: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            rows.forEach { (k, v) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
