package com.javboss.android.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.VideoRow
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.AppCard
import com.javboss.android.ui.common.EmptyBox
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.LoadingBox
import com.javboss.android.ui.common.NetworkImage
import com.javboss.android.ui.common.ScrollToTopButton
import com.javboss.android.ui.common.formatDuration
import com.javboss.android.ui.common.rememberLayoutSpec
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** 观看记录页：布局与首页一致（网格卡片），卡片上显示上次看到的位置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onPlay: (VideoRow) -> Unit,
    modifier: Modifier = Modifier,
    vm: HistoryViewModel = viewModel(),
) {
    val spec = rememberLayoutSpec()
    val gridState = rememberLazyGridState()
    val scrollScope = rememberCoroutineScope()
    val baseUrl = remember { AppGraph.video.baseUrl().trimEnd('/') }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= vm.items.size - 6 }
            .collect { vm.loadMore() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("观看记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                vm.loading && vm.items.isEmpty() -> LoadingBox(Modifier.fillMaxSize())
                vm.error != null && vm.items.isEmpty() -> ErrorBox(
                    vm.error!!,
                    onRetry = vm::refresh,
                    modifier = Modifier.fillMaxSize(),
                )
                vm.items.isEmpty() -> EmptyBox("还没有观看记录", modifier = Modifier.fillMaxSize())
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(spec.javColumns),
                        state = gridState,
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(vm.items, key = { "${it.id}-${it.locationId}" }) { video ->
                            HistoryCard(
                                video = video,
                                baseUrl = baseUrl,
                                entry = vm.entries[com.javboss.android.data.history.WatchHistory.key(baseUrl, video.id, video.locationId)],
                                onClick = { onPlay(video) },
                            )
                        }
                        if (vm.loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator(strokeWidth = 2.dp) }
                            }
                        }
                    }
                    val showTopButton by remember(gridState) {
                        androidx.compose.runtime.derivedStateOf { gridState.firstVisibleItemIndex > 6 }
                    }
                    ScrollToTopButton(visible = showTopButton) {
                        scrollScope.launch { gridState.animateScrollToItem(0) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    video: VideoRow,
    baseUrl: String,
    entry: com.javboss.android.data.history.WatchEntry?,
    onClick: () -> Unit,
) {
    val total = video.durationSec
    // 优先用本地记录的精确播放头，其次服务端累计时长
    val watchedSec = ((entry?.positionMs?.takeIf { it > 0 } ?: video.watchedMs) / 1000).coerceAtLeast(0)
    val ratio = if (total > 0) (watchedSec.toFloat() / total).coerceIn(0f, 1f) else 0f
    val relative = entry?.at?.let { relativeTime(it) }

    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Box(Modifier.fillMaxWidth().aspectRatio(800f / 538f)) {
            val coverUrl = if (!video.jav?.code.isNullOrBlank()) {
                "${baseUrl}/jav/${video.jav!!.code}/cover"
            } else {
                "${baseUrl}/videos/${video.id}/thumbnail"
            }
            NetworkImage(url = coverUrl, modifier = Modifier.fillMaxSize())

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "上次看到 ${formatDuration(watchedSec)} / ${formatDuration(total)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (relative != null) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        Text(
                            text = relative,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { ratio },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
        Text(
            text = video.jav?.let { it.zhTitle.ifBlank { it.title } } ?: video.filename,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 2.dp),
            )
            Text(
                text = video.jav?.code ?: video.filename,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 观看记录数据：只取看过（watched_ms > 0）的视频，按观看进度倒序。 */
class HistoryViewModel : ViewModel() {
    var items by mutableStateOf<List<VideoRow>>(emptyList())
        private set
    /** 本地观看记录（时间戳 + 精确位置） */
    var entries by mutableStateOf<Map<String, com.javboss.android.data.history.WatchEntry>>(emptyMap())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var endReached = false
    private var offset = 0

    init { refresh() }

    /** 「最近看的排最前」（用本地时间戳），没有本地记录的按观看时长排在后面。 */
    private fun sortByLocalTime(list: List<VideoRow>): List<VideoRow> {
        val baseUrl = AppGraph.video.baseUrl()
        return list.sortedWith(
            compareByDescending<VideoRow> {
                entries[com.javboss.android.data.history.WatchHistory.key(baseUrl, it.id, it.locationId)]?.at ?: 0L
            }.thenByDescending { it.watchedMs },
        )
    }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        endReached = false
        offset = 0
        viewModelScope.launch {
            when (val result = AppGraph.video.list(offset = 0, limit = 30, sort = "watched", hideJav = false)) {
                is ApiResult.Success -> {
                    entries = runCatching { AppGraph.settings.watchHistory.first() }.getOrDefault(emptyMap())
                    val rows = result.data.items.filter { it.watchedMs > 0 || entries.isNotEmpty() }
                    items = sortByLocalTime(rows)
                    offset = result.data.items.size
                    endReached = result.data.items.isEmpty() || offset >= result.data.total
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
            when (val result = AppGraph.video.list(offset = offset, limit = 30, sort = "watched", hideJav = false)) {
                is ApiResult.Success -> {
                    val raw = result.data.items
                    items = sortByLocalTime(items + raw.filter { it.watchedMs > 0 })
                    offset += raw.size
                    if (raw.isEmpty() || offset >= result.data.total) endReached = true
                }
                is ApiResult.Failure -> error = result.message
            }
            loadingMore = false
        }
    }
}
/** 「刚刚 / 12 分钟前 / 3 小时前 / 昨天 / 10-27」 */
private fun relativeTime(at: Long): String {
    if (at <= 0) return ""
    val diff = System.currentTimeMillis() - at
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        diff < 2 * day -> "昨天"
        diff < 7 * day -> "${diff / day} 天前"
        else -> java.text.SimpleDateFormat("MM-dd", java.util.Locale.US).format(java.util.Date(at))
    }
}