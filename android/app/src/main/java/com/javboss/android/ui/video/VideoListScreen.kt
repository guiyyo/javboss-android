package com.javboss.android.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import com.javboss.android.ui.common.AppCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.TagCount
import com.javboss.android.ui.common.LoadingBox
import androidx.compose.material3.TextButton
import com.javboss.android.ui.tags.VideoTagDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.javboss.android.ui.common.ScrollToTopButton
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.data.model.VideoRow
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.EmptyBox
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.LoadingBox
import com.javboss.android.ui.common.NetworkImage
import com.javboss.android.ui.common.formatDuration
import com.javboss.android.ui.common.watchedRatio
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class VideoListViewModel : ViewModel() {
    var items by mutableStateOf<List<VideoRow>>(emptyList())
        private set
    var total by mutableStateOf(0L)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set
    var search by mutableStateOf("")
        private set
    var selectedIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    var batchMessage by mutableStateOf<String?>(null)
        private set

    fun toggleSelect(id: Long) {
        selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
    }

    fun clearSelection() { selectedIds = emptySet() }

    fun addTagToSelected(tagId: Long, onDone: () -> Unit = {}) {
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            when (val result = AppGraph.tags.addTagToVideos(selectedIds.toList(), tagId)) {
                is ApiResult.Success -> {
                    batchMessage = "已为 ${selectedIds.size} 个视频添加标签"
                    selectedIds = emptySet()
                    refresh()
                    onDone()
                }
                is ApiResult.Failure -> batchMessage = result.message
            }
        }
    }
    var sort by mutableStateOf("recent")
        internal set

    /** 与网页一致：是否隐藏已刮削（已关联 JAV）的视频，取自服务端 config.video_hide_jav。 */
    var hideJav by mutableStateOf(false)
        private set

    private var configLoaded = false

    init {
        viewModelScope.launch {
            sort = AppGraph.settings.uiPrefs.first().videoSort
        }
        refresh()
    }

    fun applySearch(value: String) {
        if (search == value) return
        search = value
        refresh()
    }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        endReached = false
        viewModelScope.launch {
            if (!configLoaded) {
                AppGraph.config.refresh()
                hideJav = AppGraph.config.bool("video_hide_jav", false)
                configLoaded = true
            }
            when (val result = AppGraph.video.list(offset = 0, limit = 30, search = search, sort = sort, hideJav = hideJav)) {
                is ApiResult.Success -> {
                    items = result.data.items
                    total = result.data.total
                    endReached = items.size >= total || result.data.items.isEmpty()
                }
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun loadMore() {
        if (loading || loadingMore || endReached) return
        loadingMore = true
        viewModelScope.launch {
            when (val result = AppGraph.video.list(offset = items.size, limit = 30, search = search, sort = sort, hideJav = hideJav)) {
                is ApiResult.Success -> {
                    val newItems = result.data.items
                    items = items + newItems
                    total = result.data.total
                    if (newItems.isEmpty() || items.size >= total) endReached = true
                }
                is ApiResult.Failure -> error = result.message
            }
            loadingMore = false
        }
    }
}

@Composable
fun VideoListScreen(
    onPlay: (VideoRow) -> Unit,
    onOpenScrape: (VideoRow) -> Unit = {},
    onPlayQueue: (List<VideoRow>) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: VideoListViewModel = viewModel(),
) {
    var searchText by remember { mutableStateOf(vm.search) }
    var actionTarget by remember { mutableStateOf<VideoRow?>(null) }
    var tagTarget by remember { mutableStateOf<VideoRow?>(null) }
    var batchTagOpen by remember { mutableStateOf(false) }
    var videoTags by remember { mutableStateOf<List<TagCount>>(emptyList()) }
    val gridState = rememberLazyGridState()
    val spec = com.javboss.android.ui.common.rememberLayoutSpec()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= vm.items.size - 6 }
            .collect { vm.loadMore() }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("搜索文件名") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = ""; vm.applySearch("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清空")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.applySearch(searchText.trim()) }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }
        if (vm.selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("已选 ${vm.selectedIds.size}", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { batchTagOpen = true }) { Text("加标签") }
                TextButton(onClick = {
                    onPlayQueue(vm.items.filter { vm.selectedIds.contains(it.id) })
                    vm.clearSelection()
                }) { Text("播放") }
                TextButton(onClick = { vm.clearSelection() }) { Text("取消") }
            }
        }
        vm.batchMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Text(
            text = if (vm.total > 0) "共 ${vm.total} 个视频" + if (vm.hideJav) "（已隐藏已刮削）" else "" else " ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        when {
            vm.loading && vm.items.isEmpty() -> LoadingBox(Modifier.weight(1f))
            vm.error != null && vm.items.isEmpty() -> ErrorBox(vm.error!!, onRetry = vm::refresh, modifier = Modifier.weight(1f))
            vm.items.isEmpty() -> EmptyBox(
                if (vm.hideJav) "视频库为空（已按网页设置隐藏已刮削的视频）" else "视频库为空",
                modifier = Modifier.weight(1f),
            )
            else -> Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(spec.videoColumns),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(vm.items, key = { "${it.id}-${it.locationId}" }) { video ->
                    VideoCard(
                        video = video,
                        selected = vm.selectedIds.contains(video.id),
                        selectionMode = vm.selectedIds.isNotEmpty(),
                        onClick = {
                            if (vm.selectedIds.isNotEmpty()) vm.toggleSelect(video.id) else onPlay(video)
                        },
                        onLongClick = {
                            if (vm.selectedIds.isNotEmpty()) vm.toggleSelect(video.id) else actionTarget = video
                        },
                    )
                }
                if (vm.loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }
                }
            }

                // 滚远了显示「回到顶部」
                val showTopButton by androidx.compose.runtime.remember(gridState) {
                    androidx.compose.runtime.derivedStateOf { gridState.firstVisibleItemIndex > 6 }
                }
                ScrollToTopButton(visible = showTopButton) {
                    scrollScope.launch { gridState.animateScrollToItem(0) }
                }
            }
        }
    }

    actionTarget?.let { video ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(video.filename, maxLines = 1) },
            text = { Text("选择操作") },
            confirmButton = {
                TextButton(onClick = {
                    actionTarget = null
                    tagTarget = video
                }) { Text("编辑标签") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        actionTarget = null
                        vm.toggleSelect(video.id)
                    }) { Text("多选") }
                    TextButton(onClick = {
                        actionTarget = null
                        onOpenScrape(video)
                    }) { Text("刮削设置") }
                    TextButton(onClick = { actionTarget = null }) { Text("取消") }
                }
            },
        )
    }

    tagTarget?.let { video ->
        VideoTagDialog(
            video = video,
            onDismiss = { tagTarget = null },
            onSaved = { vm.refresh() },
        )
    }

    if (batchTagOpen) {
        AlertDialog(
            onDismissRequest = { batchTagOpen = false },
            title = { Text("批量添加标签") },
            text = {
                Column {
                    if (videoTags.isEmpty()) {
                        LoadingBox()
                        LaunchedEffect(Unit) {
                            when (val result = AppGraph.tags.tags()) {
                                is ApiResult.Success -> videoTags = result.data
                                is ApiResult.Failure -> Unit
                            }
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(videoTags.take(80)) { tag ->
                                TextButton(onClick = {
                                    vm.addTagToSelected(tag.id) { batchTagOpen = false }
                                }) { Text(tag.name) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { batchTagOpen = false }) { Text("关闭") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCard(
    video: VideoRow,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    selected: Boolean = false,
    selectionMode: Boolean = false,
) {
    val baseUrl = remember { AppGraph.video.baseUrl().trimEnd('/') }
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, onLongClick = onLongClick) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            NetworkImage(
                url = "$baseUrl/videos/${video.id}/thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))
                )
            )
            Text(
                text = video.jav?.let { it.zhTitle.ifBlank { it.title } } ?: video.filename,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Text(
                text = formatDuration(video.durationSec),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
            val ratio = watchedRatio(video.watchedMs, video.durationSec)
            if (ratio > 0.01f) {
                LinearProgressIndicator(
                    progress = { ratio },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onLongClick() },
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = video.filename,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            video.jav?.code?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
