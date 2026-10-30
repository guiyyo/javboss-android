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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.AvailabilityProviderItem
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.EmptyBox
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.LoadingBox
import kotlinx.coroutines.launch

/** 数据源可用性检测（GET /jav/providers + POST /jav/providers/{id}/availability）。 */
class ProviderViewModel : ViewModel() {
    var items by mutableStateOf<List<AvailabilityProviderItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf<Int?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun load() {
        loading = true
        viewModelScope.launch {
            when (val result = AppGraph.server.providers()) {
                is ApiResult.Success -> { items = result.data; error = null }
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun check(id: Int) {
        if (checking != null) return
        checking = id
        viewModelScope.launch {
            when (val result = AppGraph.server.checkProvider(id)) {
                is ApiResult.Success -> message = "检测完成：${result.data.status}（${result.data.elapsedMs} ms）"
                is ApiResult.Failure -> message = result.message
            }
            checking = null
            load()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderScreen(onBack: () -> Unit, vm: ProviderViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据源可用性") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = { TextButton(onClick = vm::load) { Text("刷新") } },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            when {
                vm.loading -> LoadingBox(Modifier.fillMaxWidth())
                vm.error != null -> ErrorBox(vm.error!!, onRetry = vm::load)
                vm.items.isEmpty() -> EmptyBox("没有可检测的数据源")
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(vm.items, key = { it.id }) { provider ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(provider.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${provider.domain} · 样例 ${provider.sample}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val last = provider.lastResult
                                    val statusText = when (last?.status) {
                                        "ok" -> "正常 · ${last.elapsedMs} ms"
                                        "timeout" -> "超时 · ${last.elapsedMs} ms"
                                        "network_error" -> "网络错误"
                                        null, "" -> "尚未检测"
                                        else -> "${last.status} · ${last.elapsedMs} ms"
                                    }
                                    Text(
                                        statusText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (last?.status == "ok") {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                                TextButton(
                                    enabled = vm.checking == null,
                                    onClick = { vm.check(provider.id) },
                                ) {
                                    Text(if (vm.checking == provider.id) "检测中…" else "重测")
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

@Suppress("unused")
private val UnusedColor = Color.Unspecified
