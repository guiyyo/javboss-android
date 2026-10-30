package com.javboss.android.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.DownloaderSettings
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.AppCard
import com.javboss.android.ui.common.LoadingBox
import kotlinx.coroutines.launch

/** 下载设置（GET/PUT /downloader/settings）：并发与最小体积可改，目录与 CloudDrive2 只读展示。 */
class DownloaderSettingsViewModel : ViewModel() {
    var settings by mutableStateOf<DownloaderSettings?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun load() {
        loading = true
        viewModelScope.launch {
            when (val result = AppGraph.server.downloaderSettings()) {
                is ApiResult.Success -> settings = result.data
                is ApiResult.Failure -> message = result.message
            }
            loading = false
        }
    }

    fun save(concurrency: Int, minSizeMb: Long) {
        viewModelScope.launch {
            when (val result = AppGraph.server.updateDownloaderSettings(concurrency, minSizeMb)) {
                is ApiResult.Success -> { settings = result.data; message = "已保存" }
                is ApiResult.Failure -> message = result.message
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderSettingsScreen(onBack: () -> Unit, vm: DownloaderSettingsViewModel = viewModel()) {
    var concurrency by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(2) }
    var minSize by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(50L) }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.settings) {
        vm.settings?.let {
            concurrency = it.localConcurrency
            minSize = it.minVideoSizeMb
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (vm.loading) {
                LoadingBox(Modifier.fillMaxWidth())
                return@Scaffold
            }
            val s = vm.settings
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("下载目录（只读）", style = MaterialTheme.typography.labelLarge)
                    Text(
                        s?.downloadDirectory?.ifBlank { "未设置" } ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("并发任务数", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 4, 8).forEach { value ->
                            FilterChip(
                                selected = concurrency == value,
                                onClick = { concurrency = value },
                                label = { Text("$value") },
                            )
                        }
                    }
                    Text("最小视频体积（MB）", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0L, 20L, 50L, 100L).forEach { value ->
                            FilterChip(
                                selected = minSize == value,
                                onClick = { minSize = value },
                                label = { Text(if (value == 0L) "不限" else "$value") },
                            )
                        }
                    }
                    Button(onClick = { vm.save(concurrency, minSize) }, modifier = Modifier.fillMaxWidth()) {
                        Text("保存")
                    }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CloudDrive2（只读）", style = MaterialTheme.typography.labelLarge)
                    Text("地址：${s?.address?.ifBlank { "未配置" } ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    Text("远端目录：${s?.remoteFolder?.ifBlank { "未配置" } ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "令牌：${if (s?.tokenConfigured == true) "已配置" else "未配置"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "CloudDrive2 的地址/令牌请先在网页端配置；Android 端只做只读展示。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            vm.message?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
