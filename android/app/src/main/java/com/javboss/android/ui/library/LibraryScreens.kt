package com.javboss.android.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.javboss.android.ui.common.ScrollToTopButton
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.IdolRef
import com.javboss.android.data.model.JavSeriesSummary
import com.javboss.android.data.model.JavStudioSummary
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.EmptyBox
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.JavCover
import com.javboss.android.ui.common.LoadingBox
import com.javboss.android.ui.common.AppCard
import com.javboss.android.ui.common.NetworkImage
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ 通用分页基类 */

abstract class PagedLibraryViewModel<T> : ViewModel() {
    var items by mutableStateOf<List<T>>(emptyList())
        protected set
    var total by mutableStateOf(0L)
        protected set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        protected set
    var endReached by mutableStateOf(false)
        private set
    var search by mutableStateOf("")
        private set

    abstract suspend fun fetch(offset: Int, search: String): ApiResult<Pair<List<T>, Long>>

    init { refresh() }

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
            when (val result = fetch(0, search)) {
                is ApiResult.Success -> {
                    items = result.data.first
                    total = result.data.second
                    endReached = items.size >= total || result.data.first.isEmpty()
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
            when (val result = fetch(items.size, search)) {
                is ApiResult.Success -> {
                    items = items + result.data.first
                    total = result.data.second
                    if (result.data.first.isEmpty() || items.size >= total) endReached = true
                }
                is ApiResult.Failure -> error = result.message
            }
            loadingMore = false
        }
    }
}

class IdolListViewModel : PagedLibraryViewModel<IdolRef>() {
    override suspend fun fetch(offset: Int, search: String): ApiResult<Pair<List<IdolRef>, Long>> =
        when (val r = AppGraph.jav.idols(offset = offset, search = search)) {
            is ApiResult.Success -> ApiResult.Success(r.data.items to r.data.total)
            is ApiResult.Failure -> ApiResult.Failure(r.status, r.message, r.cause)
        }
}

class StudioListViewModel : PagedLibraryViewModel<JavStudioSummary>() {
    override suspend fun fetch(offset: Int, search: String): ApiResult<Pair<List<JavStudioSummary>, Long>> =
        when (val r = AppGraph.jav.studios(offset = offset, search = search)) {
            is ApiResult.Success -> ApiResult.Success(r.data.items to r.data.total)
            is ApiResult.Failure -> ApiResult.Failure(r.status, r.message, r.cause)
        }
}

class SeriesListViewModel : PagedLibraryViewModel<JavSeriesSummary>() {
    override suspend fun fetch(offset: Int, search: String): ApiResult<Pair<List<JavSeriesSummary>, Long>> =
        when (val r = AppGraph.jav.seriesList(offset = offset, search = search)) {
            is ApiResult.Success -> ApiResult.Success(r.data.items to r.data.total)
            is ApiResult.Failure -> ApiResult.Failure(r.status, r.message, r.cause)
        }
}

/* ------------------------------------------------------------------ 通用列表骨架 */

@Composable
private fun <T> LibraryGrid(
    vm: PagedLibraryViewModel<T>,
    placeholder: String,
    modifier: Modifier,
    columns: Int = 2,
    onOpenSettings: () -> Unit = {},
    itemContent: @Composable (T) -> Unit,
) {
    var searchText by remember { mutableStateOf(vm.search) }
    val gridState = rememberLazyGridState()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= vm.items.size - 6 }
            .collect { vm.loadMore() }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.applySearch(searchText.trim()) }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }
        Text(
            text = if (vm.total > 0) "共 ${vm.total} 项" else " ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        when {
            vm.loading && vm.items.isEmpty() -> LoadingBox(Modifier.weight(1f))
            vm.error != null && vm.items.isEmpty() -> ErrorBox(vm.error!!, onRetry = vm::refresh, modifier = Modifier.weight(1f))
            vm.items.isEmpty() -> EmptyBox("暂无数据", modifier = Modifier.weight(1f))
            else -> Box(Modifier.weight(1f)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(vm.items.size) { index -> itemContent(vm.items[index]) }
                    if (vm.loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        }
                    }
                }
                val showTopButton by androidx.compose.runtime.remember(gridState) {
                    androidx.compose.runtime.derivedStateOf { gridState.firstVisibleItemIndex > 6 }
                }
                ScrollToTopButton(visible = showTopButton) {
                    scrollScope.launch { gridState.animateScrollToItem(0) }
                }
            }
        }
    }
}

@Composable
fun IdolListScreen(
    onOpenIdol: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: IdolListViewModel = viewModel(),
) {
    val baseUrl = remember { AppGraph.video.baseUrl().trimEnd('/') }
    val spec = com.javboss.android.ui.common.rememberLayoutSpec()
    LibraryGrid(vm = vm, placeholder = "搜索女优", modifier = modifier, columns = spec.idolColumns, onOpenSettings = onOpenSettings) { idol ->
        AppCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenIdol(idol.id) }) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
                if (idol.coverCode.isNotBlank()) {
                    NetworkImage(
                        url = "$baseUrl/jav/${idol.coverCode}/cover",
                        modifier = Modifier.fillMaxSize(),
                        alignment = if (idol.coverCropLeft >= 0.5) Alignment.CenterEnd else Alignment.CenterStart,
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                    Text(
                        text = idol.chineseName.ifBlank { idol.name },
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${idol.workCount} 部",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
fun StudioListScreen(
    onOpenStudio: (JavStudioSummary) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: StudioListViewModel = viewModel(),
) {
    val baseUrl = remember { AppGraph.video.baseUrl().trimEnd('/') }
    val spec = com.javboss.android.ui.common.rememberLayoutSpec()
    LibraryGrid(vm = vm, placeholder = "搜索片商", modifier = modifier, columns = spec.libraryColumns, onOpenSettings = onOpenSettings) { studio ->
        AppCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenStudio(studio) }) {
            Box(Modifier.fillMaxWidth().aspectRatio(800f / 538f)) {
                if (studio.sampleCode.isNotBlank()) {
                    NetworkImage(
                        url = "$baseUrl/jav/${studio.sampleCode}/cover",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                    Text(
                        studio.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            "${studio.workCount} 部".takeIf { studio.workCount > 0 },
                            studio.sampleCode.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesListScreen(
    onOpenSeries: (JavSeriesSummary) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: SeriesListViewModel = viewModel(),
) {
    val baseUrl = remember { AppGraph.video.baseUrl().trimEnd('/') }
    val spec = com.javboss.android.ui.common.rememberLayoutSpec()
    LibraryGrid(vm = vm, placeholder = "搜索系列", modifier = modifier, columns = spec.libraryColumns, onOpenSettings = onOpenSettings) { series ->
        AppCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenSeries(series) }) {
            Box(Modifier.fillMaxWidth().aspectRatio(800f / 538f)) {
                if (series.sampleCode.isNotBlank()) {
                    NetworkImage(
                        url = "$baseUrl/jav/${series.sampleCode}/cover",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                    Text(
                        text = series.zhName.ifBlank { series.name },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            "${series.workCount} 部".takeIf { series.workCount > 0 },
                            series.studioName.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}
