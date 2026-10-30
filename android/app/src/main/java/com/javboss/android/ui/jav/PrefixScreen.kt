package com.javboss.android.ui.jav

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.JavPrefixSummary
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.EmptyBox
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.LoadingBox
import kotlinx.coroutines.launch

/** 番号字典：`GET /jav/prefixes`，按首字符分组，点击进入该前缀的作品列表。 */
class PrefixViewModel : ViewModel() {
    var items by mutableStateOf<List<JavPrefixSummary>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load() {
        loading = true
        viewModelScope.launch {
            when (val result = AppGraph.jav.prefixes()) {
                is ApiResult.Success -> { items = result.data; error = null }
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrefixScreen(
    onBack: () -> Unit,
    onOpenPrefix: (String) -> Unit,
    vm: PrefixViewModel = viewModel(),
) {
    var query by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    LaunchedEffect(Unit) { vm.load() }

    val filtered = vm.items.filter {
        query.isBlank() ||
            it.prefix.contains(query, true) ||
            it.studioName.contains(query, true) ||
            it.sampleCode.contains(query, true)
    }
    val grouped = filtered
        .groupBy { prefix -> prefix.prefix.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
        .toSortedMap()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("番号字典") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索番号前缀 / 片商") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            when {
                vm.loading -> LoadingBox(Modifier.fillMaxWidth())
                vm.error != null -> ErrorBox(vm.error!!, onRetry = vm::load)
                filtered.isEmpty() -> EmptyBox("没有匹配的番号")
                else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    grouped.forEach { (initial, prefixes) ->
                        item(key = "header-$initial") {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                        items(prefixes, key = { "p-${it.prefix}" }) { prefix ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable { onOpenPrefix(prefix.prefix) },
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(prefix.prefix, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            listOfNotNull(
                                                prefix.studioName.takeIf { it.isNotBlank() },
                                                prefix.sampleCode.takeIf { it.isNotBlank() },
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        "${prefix.workCount}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
