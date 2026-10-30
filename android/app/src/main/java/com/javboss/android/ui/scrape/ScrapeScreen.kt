package com.javboss.android.ui.scrape

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.VideoRow
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.LoadingBox
import kotlinx.coroutines.launch

/**
 * 刮削设置（视频 ↔ 番号关联）：
 * 自动 / 指定番号 / 不刮削，候选番号，关联已有番号。
 */
class ScrapeViewModel : ViewModel() {
    var video by mutableStateOf<VideoRow?>(null)
        private set
    var possibleCodes by mutableStateOf<List<String>>(emptyList())
        private set
    var mode by mutableStateOf("auto")
        private set
    var codeInput by mutableStateOf("")
        private set
    var linkCode by mutableStateOf("")
        private set
    var loading by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var videoId: Long = 0

    fun load(id: Long) {
        videoId = id
        loading = true
        viewModelScope.launch {
            when (val result = AppGraph.video.detail(id)) {
                is ApiResult.Success -> {
                    video = result.data
                    val override = result.data.javScrapeOverride
                    mode = when {
                        override.isBlank() -> "auto"
                        override == "skip" -> "skip"
                        else -> "code"
                    }
                    if (mode == "code") codeInput = override
                }
                is ApiResult.Failure -> message = result.message
            }
            when (val codes = AppGraph.scrape.possibleCodes(id)) {
                is ApiResult.Success -> possibleCodes = codes.data.possibleCodes
                is ApiResult.Failure -> Unit
            }
            loading = false
        }
    }

    fun chooseMode(value: String) { mode = value }

    fun updateCodeInput(value: String) { codeInput = value.uppercase() }

    fun updateLinkCode(value: String) { linkCode = value.uppercase() }

    fun saveMode(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val body = when (mode) {
                "skip" -> com.javboss.android.data.remote.ScrapeModeRequest("skip")
                "code" -> com.javboss.android.data.remote.ScrapeModeRequest("code", codeInput.trim())
                else -> com.javboss.android.data.remote.ScrapeModeRequest("auto")
            }
            when (val result = AppGraph.scrape.setMode(videoId, body)) {
                is ApiResult.Success -> { video = result.data; message = "已保存刮削设置"; onDone() }
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    fun linkExisting() {
        val code = linkCode.trim()
        val locationId = video?.locationId ?: 0
        if (code.isBlank() || locationId <= 0) {
            message = "请填写番号"
            return
        }
        viewModelScope.launch {
            when (val result = AppGraph.scrape.link(videoId, locationId, code)) {
                is ApiResult.Success -> { video = result.data; message = "已关联番号 $code" }
                is ApiResult.Failure -> message = result.message
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrapeScreen(
    videoId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ScrapeViewModel = viewModel(),
) {
    LaunchedEffect(videoId) { vm.load(videoId) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("刮削设置") },
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
            Text(
                vm.video?.filename.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            Text(
                "当前番号：${vm.video?.jav?.code ?: "未关联"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("刮削模式", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vm.mode == "auto", onClick = { vm.chooseMode("auto") }, label = { Text("自动") })
                FilterChip(selected = vm.mode == "code", onClick = { vm.chooseMode("code") }, label = { Text("指定番号") })
                FilterChip(selected = vm.mode == "skip", onClick = { vm.chooseMode("skip") }, label = { Text("不刮削") })
            }
            if (vm.mode == "code") {
                OutlinedTextField(
                    value = vm.codeInput,
                    onValueChange = vm::updateCodeInput,
                    label = { Text("番号（如 SNOS-079）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(onClick = { vm.saveMode() }, modifier = Modifier.fillMaxWidth()) { Text("保存模式") }

            if (vm.possibleCodes.isNotEmpty()) {
                Text("文件名中的候选番号", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.possibleCodes.take(6).forEach { code ->
                        OutlinedButton(onClick = {
                            vm.chooseMode("code")
                            vm.updateCodeInput(code)
                        }) { Text(code) }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("关联已有番号", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "把这条视频位置直接挂到库里已有的番号上",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = vm.linkCode,
                        onValueChange = vm::updateLinkCode,
                        label = { Text("番号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = vm::linkExisting, modifier = Modifier.fillMaxWidth()) { Text("关联") }
                }
            }

            vm.message?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
