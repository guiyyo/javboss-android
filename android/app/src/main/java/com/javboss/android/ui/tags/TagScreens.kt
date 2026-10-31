package com.javboss.android.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.TagCount
import com.javboss.android.data.model.JavTagCount
import com.javboss.android.data.model.JavTagCategoryRef
import com.javboss.android.data.model.VideoRow
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.EmptyBox
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.LoadingBox
import kotlinx.coroutines.launch

/* ================================================================ 标签管理 */

class TagManagementViewModel : ViewModel() {
    var tags by mutableStateOf<List<JavTagCount>>(emptyList())
        private set
    var categories by mutableStateOf<List<JavTagCategoryRef>>(emptyList())
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
            // 与网页一致：管理的是 JAV 标签（/jav/tags），不是视频标签（/tags）
            when (val tagResult = AppGraph.jav.tags()) {
                is ApiResult.Success -> { tags = tagResult.data; error = null }
                is ApiResult.Failure -> error = tagResult.message
            }
            when (val catResult = AppGraph.jav.tagCategories()) {
                is ApiResult.Success -> categories = catResult.data
                is ApiResult.Failure -> Unit
            }
            loading = false
        }
    }

    fun createTag(name: String) = mutate { AppGraph.jav.createTag(name) }

    fun renameTag(id: Long, name: String) = mutate { AppGraph.jav.renameTag(id, name) }

    fun deleteTag(id: Long) = mutate { AppGraph.jav.deleteTag(id) }

    fun deleteTags(ids: List<Long>) = mutate { AppGraph.jav.deleteTags(ids) }

    fun createCategory(name: String) = mutate { AppGraph.jav.createTagCategory(name) }

    fun deleteCategory(id: Long) = mutate { AppGraph.jav.deleteTagCategory(id) }

    fun assignCategory(tagIds: List<Long>, categoryId: Long?) =
        mutate { AppGraph.jav.assignTagCategory(tagIds, categoryId) }

    private fun mutate(block: suspend () -> ApiResult<Unit>) {
        viewModelScope.launch {
            when (val result = block()) {
                is ApiResult.Success -> { message = "已更新"; load() }
                is ApiResult.Failure -> message = result.message
            }
        }
    }
}

private fun ApiResult<*>.mapUnit(): ApiResult<Unit> = when (this) {
    is ApiResult.Success -> ApiResult.Success(Unit)
    is ApiResult.Failure -> this
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagManagementScreen(onBack: () -> Unit, vm: TagManagementViewModel = viewModel()) {
    var newTag by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var categoryTarget by remember { mutableStateOf<Long?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("标签管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                vm.loading -> LoadingBox(Modifier.fillMaxWidth())
                vm.error != null -> ErrorBox(vm.error!!, onRetry = vm::load)
                vm.tags.isEmpty() -> EmptyBox("还没有 JAV 标签")
                else -> {
                    Text("JAV 标签（${vm.tags.size}）", style = MaterialTheme.typography.titleSmall)

                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it },
                            placeholder = { Text("新建标签") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = newTag.isNotBlank(),
                            onClick = { vm.createTag(newTag.trim()); newTag = "" },
                        ) { Text("添加") }
                    }

                    if (selected.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showCategoryPicker = true }) {
                                Text("设分类（${selected.size}）")
                            }
                            OutlinedButton(onClick = {
                                selected.forEach { id -> vm.deleteTag(id) }
                                selected = emptySet()
                            }) { Text("批量删除") }
                            TextButton(onClick = { selected = emptySet() }) { Text("取消选择") }
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.tags.take(300).forEach { tag ->
                            FilterChip(
                                selected = selected.contains(tag.id),
                                onClick = {
                                    selected = if (selected.contains(tag.id)) selected - tag.id else selected + tag.id
                                },
                                label = {
                                    Text(
                                        "${tag.simplifiedName.ifBlank { tag.name }} (${tag.count})",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "删除",
                                        modifier = Modifier
                                            .heightIn(max = 16.dp)
                                            .clickable { vm.deleteTag(tag.id) },
                                    )
                                },
                            )
                        }
                    }

                    Text("分类（${vm.categories.size}）", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            placeholder = { Text("新建分类") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = newCategory.isNotBlank(),
                            onClick = { vm.createCategory(newCategory.trim()); newCategory = "" },
                        ) { Text("添加") }
                    }
                    vm.categories.forEach { category ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(category.name, style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { vm.deleteCategory(category.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除分类")
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

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("移动到分类") },
            text = {
                Column {
                    TextButton(onClick = {
                        vm.assignCategory(selected.toList(), null)
                        showCategoryPicker = false
                    }) { Text("取消分类") }
                    vm.categories.forEach { category ->
                        TextButton(onClick = {
                            categoryTarget = category.id
                            vm.assignCategory(selected.toList(), category.id)
                            showCategoryPicker = false
                        }) { Text(category.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategoryPicker = false }) { Text("关闭") } },
        )
    }
}

/* ================================================================ 视频标签编辑 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoTagDialog(
    video: VideoRow,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
) {
    var allTags by remember { mutableStateOf<List<TagCount>>(emptyList()) }
    var selected by remember { mutableStateOf(video.tags.map { it.id }.toSet()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var newTag by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(video.id) {
        when (val result = AppGraph.tags.tags()) {
            is ApiResult.Success -> allTags = result.data
            is ApiResult.Failure -> error = result.message
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("视频标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    video.filename,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loading) LoadingBox()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        placeholder = { Text("新建标签") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newTag.isNotBlank(),
                        onClick = {
                            val name = newTag.trim()
                            newTag = ""
                            scope.launch {
                                when (val created = AppGraph.tags.createTag(name)) {
                                    is ApiResult.Success -> {
                                        allTags = allTags + created.data
                                        selected = selected + created.data.id
                                    }
                                    is ApiResult.Failure -> error = created.message
                                }
                            }
                        },
                    ) { Text("添加") }
                }
                FlowRow(
                    modifier = Modifier.heightIn(max = 260.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allTags.take(150).forEach { tag ->
                        FilterChip(
                            selected = selected.contains(tag.id),
                            onClick = {
                                selected = if (selected.contains(tag.id)) selected - tag.id else selected + tag.id
                            },
                            label = { Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    when (val result = AppGraph.tags.replaceVideoTags(listOf(video.id), selected.toList())) {
                        is ApiResult.Success -> { onSaved(); onDismiss() }
                        is ApiResult.Failure -> error = result.message
                    }
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
