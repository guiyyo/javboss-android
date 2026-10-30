package com.javboss.android.ui.screenshot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.ScreenshotInfo
import com.javboss.android.data.screenshot.LocalScreenshotStore
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.EmptyBox
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.LoadingBox
import com.javboss.android.ui.common.NetworkImage
import kotlinx.coroutines.launch

class ScreenshotViewModel : ViewModel() {
    var items by mutableStateOf<List<ScreenshotInfo>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var pendingLocal by mutableStateOf(0)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var videoId: Long = 0

    fun load(videoId: Long) {
        this.videoId = videoId
        loading = true
        error = null
        viewModelScope.launch {
            when (val result = AppGraph.video.screenshots(videoId)) {
                is ApiResult.Success -> items = result.data
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun refreshPendingCount() {
        pendingLocal = LocalScreenshotStore.pendingCount(AppGraph.context, videoId)
    }

    fun setCover(name: String) {
        viewModelScope.launch {
            when (val result = AppGraph.video.setCover(videoId, name)) {
                is ApiResult.Success -> { message = "已设为封面"; load(videoId) }
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    fun resetCover() {
        viewModelScope.launch {
            when (val result = AppGraph.video.resetCover(videoId)) {
                is ApiResult.Success -> { message = "已恢复默认封面"; load(videoId) }
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    fun delete(name: String) {
        viewModelScope.launch {
            when (val result = AppGraph.video.deleteScreenshot(videoId, name)) {
                is ApiResult.Success -> load(videoId)
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    fun uploadLocal() {
        viewModelScope.launch {
            when (val result = LocalScreenshotStore.uploadAll(AppGraph.context, videoId)) {
                is ApiResult.Success -> {
                    message = "已上传 ${result.data} 张本地截帧"
                    load(videoId)
                    refreshPendingCount()
                }
                is ApiResult.Failure -> message = result.message
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotScreen(
    videoId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ScreenshotViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoId) {
        vm.load(videoId)
        vm.refreshPendingCount()
    }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("截图") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    TextButton(onClick = { vm.uploadLocal() }) {
                        Text("上传本地(${vm.pendingLocal})")
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                vm.loading -> LoadingBox(Modifier.fillMaxSize())
                vm.error != null -> ErrorBox(vm.error!!, onRetry = { vm.load(videoId) }, modifier = Modifier.fillMaxSize())
                vm.items.isEmpty() -> EmptyBox(
                    "还没有截图。播放时点“截帧”即可生成，随后在此上传。",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(vm.items, key = { it.name }) { shot ->
                        Box {
                            NetworkImage(
                                url = AppGraph.video.screenshotUrl(videoId, shot.name),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                            )
                            if (shot.isCover) {
                                Text(
                                    "封面",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            IconButton(
                                onClick = { menuFor = shot.name },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "操作")
                            }
                            DropdownMenu(
                                expanded = menuFor == shot.name,
                                onDismissRequest = { menuFor = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("设为封面") },
                                    onClick = { menuFor = null; vm.setCover(shot.name) },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    onClick = { menuFor = null; vm.delete(shot.name) },
                                )
                            }
                            Text(
                                text = shot.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                            )
                        }
                    }
                    item {
                        Column(Modifier.clickable { vm.resetCover() }.padding(8.dp)) {
                            Text("恢复默认封面", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "删除自定义封面，回到自动缩略图",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
