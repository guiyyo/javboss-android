package com.javboss.android.ui.favorites

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.javboss.android.data.model.FavoriteGroup
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.EmptyBox
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.LoadingBox
import kotlinx.coroutines.launch

/**
 * 收藏夹管理（番号 / 女优 / 片商 / 系列 四类实体共用同一套接口）。
 */
class FavoriteGroupManageViewModel : ViewModel() {
    var entity by mutableStateOf("jav")
        private set
    var groups by mutableStateOf<List<FavoriteGroup>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var editingId by mutableStateOf<Long?>(null)
    var editName by mutableStateOf("")

    fun load(target: String = entity) {
        entity = target
        loading = true
        viewModelScope.launch {
            when (val result = AppGraph.jav.favoriteGroups(target)) {
                is ApiResult.Success -> { groups = result.data; error = null }
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (val result = AppGraph.jav.createFavoriteGroup(entity, name.trim())) {
                is ApiResult.Success -> { message = "已创建"; load() }
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    fun rename(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (val result = AppGraph.jav.renameFavoriteGroup(entity, id, name.trim())) {
                is ApiResult.Success -> { message = "已重命名"; editingId = null; load() }
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            when (val result = AppGraph.jav.deleteFavoriteGroup(entity, id)) {
                is ApiResult.Success -> { message = "已删除"; load() }
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    fun move(id: Long, delta: Int) {
        val index = groups.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = index + delta
        if (target !in groups.indices) return
        val reordered = groups.toMutableList().apply {
            val item = removeAt(index)
            add(target, item)
        }
        groups = reordered
        viewModelScope.launch {
            when (val result = AppGraph.jav.reorderFavoriteGroups(entity, reordered.map { it.id })) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> message = result.message
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteGroupManageScreen(onBack: () -> Unit, vm: FavoriteGroupManageViewModel = viewModel()) {
    var newName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val entities = listOf("jav" to "番号", "idol" to "女优", "studio" to "片商", "series" to "系列")

    LaunchedEffect(Unit) { vm.load("jav") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收藏夹管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entities.forEach { (key, label) ->
                    FilterChip(
                        selected = vm.entity == key,
                        onClick = { vm.load(key) },
                        label = { Text(label) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("新建收藏夹") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        vm.create(newName)
                        newName = ""
                    },
                ) { Text("添加") }
            }

            when {
                vm.loading -> LoadingBox(Modifier.fillMaxWidth())
                vm.error != null -> ErrorBox(vm.error!!, onRetry = { vm.load() })
                vm.groups.isEmpty() -> EmptyBox("还没有收藏夹")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.groups, key = { it.id }) { group ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    if (vm.editingId == group.id) {
                                        OutlinedTextField(
                                            value = vm.editName,
                                            onValueChange = { vm.editName = it },
                                            singleLine = true,
                                        )
                                    } else {
                                        Text(group.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${group.count} 项",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (vm.editingId == group.id) {
                                    TextButton(onClick = { vm.rename(group.id, vm.editName) }) { Text("保存") }
                                } else {
                                    TextButton(onClick = {
                                        vm.editingId = group.id
                                        vm.editName = group.name
                                    }) { Text("改名") }
                                }
                                IconButton(onClick = { vm.move(group.id, -1) }) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                                }
                                IconButton(onClick = { vm.move(group.id, 1) }) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                                }
                                IconButton(onClick = { vm.delete(group.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }
            }

            vm.message?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


