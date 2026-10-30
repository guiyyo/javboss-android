package com.javboss.android.ui.jav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.remote.JavItemUpdateRequest
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.LoadingBox
import com.javboss.android.ui.common.formatUnixDate
import kotlinx.coroutines.launch

class JavEditViewModel : ViewModel() {
    var title by mutableStateOf("")
    var zhTitle by mutableStateOf("")
    var releaseDate by mutableStateOf("")
    var durationMin by mutableStateOf("")
    var coverUrl by mutableStateOf("")
    var coverCrop by mutableStateOf("1.0")
    var studioId by mutableStateOf<Long?>(null)
    var seriesId by mutableStateOf<Long?>(null)
    var studios by mutableStateOf<List<com.javboss.android.data.model.JavStudioSummary>>(emptyList())
    var seriesList by mutableStateOf<List<com.javboss.android.data.model.JavSeriesSummary>>(emptyList())

    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var translating by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var itemId: Long = 0

    fun load(id: Long) {
        itemId = id
        loading = true
        viewModelScope.launch {
            when (val result = AppGraph.jav.detail(id)) {
                is ApiResult.Success -> {
                    val item = result.data
                    title = item.title
                    zhTitle = item.zhTitle
                    releaseDate = formatUnixDate(item.releaseUnix).takeIf { it != "—" }.orEmpty()
                    durationMin = item.durationMin.takeIf { it > 0 }?.toString().orEmpty()
                    coverCrop = item.coverCropPosition.toString()
                    studioId = item.studioId
                    seriesId = item.seriesId
                    message = null
                }
                is ApiResult.Failure -> message = result.message
            }
            loading = false
        }
        viewModelScope.launch {
            (AppGraph.jav.studios(offset = 0, limit = 200) as? ApiResult.Success)?.let { studios = it.data.items }
            (AppGraph.jav.seriesList(offset = 0, limit = 200) as? ApiResult.Success)?.let { seriesList = it.data.items }
        }
    }

    fun save(onSaved: () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            val body = JavItemUpdateRequest(
                title = title.trim().ifBlank { null },
                zhTitle = zhTitle.trim(),
                releaseDate = releaseDate.trim().ifBlank { null },
                durationMin = durationMin.trim().toIntOrNull(),
                coverCropPosition = coverCrop.trim().toDoubleOrNull(),
                studioId = studioId,
                seriesId = seriesId,
                coverUrl = coverUrl.trim().ifBlank { null },
            )
            when (val result = AppGraph.jav.updateItem(itemId, body)) {
                is ApiResult.Success -> {
                    message = "已保存"
                    coverUrl = ""
                    onSaved()
                }
                is ApiResult.Failure -> message = result.message
            }
            saving = false
        }
    }

    fun translate() {
        if (translating) return
        translating = true
        viewModelScope.launch {
            when (val result = AppGraph.jav.translateTitle(itemId)) {
                is ApiResult.Success -> {
                    if (result.data.zhTitle.isNotBlank()) zhTitle = result.data.zhTitle
                    message = "已重新翻译"
                }
                is ApiResult.Failure -> message = result.message
            }
            translating = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JavEditScreen(
    itemId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: JavEditViewModel = viewModel(),
) {
    LaunchedEffect(itemId) { vm.load(itemId) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("编辑作品") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { inner ->
        if (vm.loading) {
            LoadingBox(Modifier.fillMaxSize().padding(inner))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = vm.title,
                onValueChange = { vm.title = it },
                label = { Text("原题") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.zhTitle,
                onValueChange = { vm.zhTitle = it },
                label = { Text("中文标题") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = vm::translate,
                    enabled = !vm.translating,
                    modifier = Modifier.weight(1f),
                ) { Text(if (vm.translating) "翻译中…" else "AI 重译标题") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vm.releaseDate,
                    onValueChange = { vm.releaseDate = it },
                    label = { Text("发行日期 YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = vm.durationMin,
                    onValueChange = { vm.durationMin = it.filter { c -> c.isDigit() } },
                    label = { Text("时长（分）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = vm.coverUrl,
                onValueChange = { vm.coverUrl = it },
                label = { Text("更换封面图片地址（留空不改）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.coverCrop,
                onValueChange = { vm.coverCrop = it },
                label = { Text("封面裁切位置（0-1）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            // 片商 / 系列
            var studioPicker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var seriesPicker by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val studioName = vm.studios.firstOrNull { it.id == vm.studioId }?.name ?: "未设置"
            val seriesName = vm.seriesList.firstOrNull { it.id == vm.seriesId }
                ?.let { it.zhName.ifBlank { it.name } } ?: "未设置"

            androidx.compose.material3.OutlinedButton(
                onClick = { studioPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("片商：$studioName") }
            androidx.compose.material3.OutlinedButton(
                onClick = { seriesPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("系列：$seriesName") }

            if (studioPicker) {
                PickerDialog(
                    title = "选择片商",
                    options = vm.studios.map { it.id to it.name },
                    onDismiss = { studioPicker = false },
                    onPick = { id ->
                        vm.studioId = id
                        studioPicker = false
                    },
                    allowClear = true,
                    onClear = { vm.studioId = null },
                )
            }
            if (seriesPicker) {
                PickerDialog(
                    title = "选择系列",
                    options = vm.seriesList.map { it.id to it.zhName.ifBlank { it.name } },
                    onDismiss = { seriesPicker = false },
                    onPick = { id ->
                        vm.seriesId = id
                        seriesPicker = false
                    },
                    allowClear = true,
                    onClear = { vm.seriesId = null },
                )
            }

            vm.message?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
                onClick = { vm.save(onSaved) },
                enabled = !vm.saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.saving) "保存中…" else "保存") }
        }
    }
}

@Composable
private fun PickerDialog(
    title: String,
    options: List<Pair<Long, String>>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    allowClear: Boolean = false,
    onClear: () -> Unit = {},
) {
    var query by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.padding(top = 8.dp).heightIn(max = 360.dp),
                ) {
                    items(options.filter { query.isBlank() || it.second.contains(query, true) }.take(200)) { (id, name) ->
                        androidx.compose.material3.TextButton(onClick = { onPick(id) }) {
                            Text(name, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (allowClear) {
                androidx.compose.material3.TextButton(onClick = { onClear(); onDismiss() }) { Text("清除") }
            } else {
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") } },
    )
}