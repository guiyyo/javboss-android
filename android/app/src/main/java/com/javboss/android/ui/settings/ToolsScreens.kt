package com.javboss.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.TranslationBatchStatus
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/* ================================================================ AI 翻译（批量） */

class TranslationViewModel : ViewModel() {
    var status by mutableStateOf(TranslationBatchStatus())
        private set
    var scope by mutableStateOf("missing")
        private set
    var confirmOverwrite by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var polling by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            while (polling) {
                val result = AppGraph.jav.translationBatch()
                if (result is ApiResult.Success) status = result.data
                else if (result is ApiResult.Failure) message = result.message
                delay(3000)
            }
        }
    }

    fun chooseScope(value: String) { scope = value }
    fun toggleOverwrite(value: Boolean) { confirmOverwrite = value }

    fun start() {
        viewModelScope.launch {
            when (val result = AppGraph.jav.startTranslationBatch(scope, confirmOverwrite)) {
                is ApiResult.Success -> { status = result.data; message = "批量翻译已启动" }
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    fun stop() {
        viewModelScope.launch {
            when (val result = AppGraph.jav.stopTranslationBatch()) {
                is ApiResult.Success -> message = "正在停止…"
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    override fun onCleared() {
        polling = false
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(onBack: () -> Unit, vm: TranslationViewModel = viewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 标题翻译") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("批量任务", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = vm.scope == "missing",
                    onClick = { vm.chooseScope("missing") },
                    label = { Text("仅缺失") },
                )
                FilterChip(
                    selected = vm.scope == "all",
                    onClick = { vm.chooseScope("all") },
                    label = { Text("全部") },
                )
                FilterChip(
                    selected = vm.confirmOverwrite,
                    onClick = { vm.toggleOverwrite(!vm.confirmOverwrite) },
                    label = { Text("覆盖已有") },
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (vm.status.running) "运行中" else "空闲",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "进度 ${vm.status.processed}/${vm.status.total} · 译出 ${vm.status.translated} · 跳过 ${vm.status.skipped}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (vm.status.total > 0) {
                        LinearProgressIndicator(
                            progress = { vm.status.processed.toFloat() / vm.status.total },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                    if (vm.status.errorZh.isNotBlank()) {
                        Text(vm.status.errorZh, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::start, enabled = !vm.status.running, modifier = Modifier.weight(1f)) {
                    Text("开始")
                }
                OutlinedButton(onClick = vm::stop, enabled = vm.status.running, modifier = Modifier.weight(1f)) {
                    Text("停止")
                }
            }

            Text(
                "翻译服务商与密钥需在网页端配置；此处调用服务器上已有的翻译能力。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            vm.message?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun RowSpacer() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = {}) { Text("") } }
}
