package com.javboss.android.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.IdolRef
import com.javboss.android.data.model.JavSeriesSummary
import com.javboss.android.data.model.JavStudioSummary
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.library.IdolListViewModel
import com.javboss.android.ui.library.SeriesListViewModel
import com.javboss.android.ui.library.StudioListViewModel

/** 女优/片商/系列统一用竖版海报网格（每行按屏宽自适应，1080p 电视约 5 列）。 */
@Composable
private fun tvLibraryColumns(): Int {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val available = (widthDp - 188).coerceAtLeast(320)   // 侧栏 148 + 内边距 40
    return (available / 165).coerceIn(3, 8)
}

@Composable
private fun TvLibraryScaffold(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    title: String,
    total: Long,
    columnsOverride: Int = 0,
    searchText: String,
    onSearch: (String) -> Unit,
    loading: Boolean,
    onLoadMore: () -> Unit,
    onOpenSettings: () -> Unit = {},
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit,
) {
    val columns = if (columnsOverride > 0) columnsOverride else tvLibraryColumns()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TvListHeader(
                    title = title,
                    total = total,
                    searchPlaceholder = "搜索",
                    searchText = searchText,
                    onSearch = onSearch,
                    onOpenSettings = onOpenSettings,
                )
            }
            content()
            item(span = { GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(Unit) { onLoadMore() }
            }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

/* ------------------------------------------------------------------ 女优 */

@Composable
fun TvIdolScreen(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    onOpenSettings: () -> Unit = {},
    onOpenWorks: (Long, String) -> Unit,
    vm: IdolListViewModel = viewModel()) {
    val baseUrl = remember { AppGraph.video.baseUrl().trimEnd('/') }
    val requesterFor = rememberTvFocusRestorer()
    var search by remember { mutableStateOf("") }
    TvLibraryScaffold(
        state = state,
        title = "女优",
        total = vm.total,
        searchText = search,
        onSearch = { search = it; vm.applySearch(it.trim()) },
        loading = vm.loading && vm.items.isEmpty(),
        onLoadMore = { vm.loadMore() },
        onOpenSettings = onOpenSettings,
    ) {
        items(vm.items, key = { it.id }) { idol: IdolRef ->
            TvPosterCard(
                focusKey = "idol-${idol.id}",
                focusRequester = requesterFor("idol-${idol.id}"),
                coverUrl = if (idol.coverCode.isNotBlank()) "$baseUrl/jav/${idol.coverCode}/cover" else "",
                title = idol.chineseName.ifBlank { idol.name },
                subtitle = "${idol.workCount} 部",
                portrait = true,
                cropPosition = 1.0,
                titleRows = 1,
                onClick = { onOpenWorks(idol.id, idol.chineseName.ifBlank { idol.name }) },
            )
        }
    }
}

/* ------------------------------------------------------------------ 片商 */

@Composable
fun TvStudioScreen(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    onOpenSettings: () -> Unit = {},
    onOpenWorks: (Long, String) -> Unit,
    vm: StudioListViewModel = viewModel()) {
    val baseUrl = remember { AppGraph.video.baseUrl().trimEnd('/') }
    val requesterFor = rememberTvFocusRestorer()
    var search by remember { mutableStateOf("") }
    TvLibraryScaffold(
        state = state,
        title = "片商",
        columnsOverride = 3,
        total = vm.total,
        searchText = search,
        onSearch = { search = it; vm.applySearch(it.trim()) },
        loading = vm.loading && vm.items.isEmpty(),
        onLoadMore = { vm.loadMore() },
        onOpenSettings = onOpenSettings,
    ) {
        items(vm.items, key = { it.id }) { studio: JavStudioSummary ->
            TvPosterCard(
                focusKey = "studio-${studio.id}",
                focusRequester = requesterFor("studio-${studio.id}"),
                coverUrl = if (studio.sampleCode.isNotBlank()) "$baseUrl/jav/${studio.sampleCode}/cover" else "",
                title = studio.name,
                subtitle = "${studio.workCount} 部" + if (studio.sampleCode.isNotBlank()) " · ${studio.sampleCode}" else "",
                portrait = false,
                titleRows = 2,
                onClick = { onOpenWorks(studio.id, studio.name) },
            )
        }
    }
}

/* ------------------------------------------------------------------ 系列 */

@Composable
fun TvSeriesScreen(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    onOpenSettings: () -> Unit = {},
    onOpenWorks: (Long, String) -> Unit,
    vm: SeriesListViewModel = viewModel()) {
    val baseUrl = remember { AppGraph.video.baseUrl().trimEnd('/') }
    val requesterFor = rememberTvFocusRestorer()
    var search by remember { mutableStateOf("") }
    TvLibraryScaffold(
        state = state,
        title = "系列",
        columnsOverride = 3,
        total = vm.total,
        searchText = search,
        onSearch = { search = it; vm.applySearch(it.trim()) },
        loading = vm.loading && vm.items.isEmpty(),
        onLoadMore = { vm.loadMore() },
        onOpenSettings = onOpenSettings,
    ) {
        items(vm.items, key = { it.id }) { series: JavSeriesSummary ->
            TvPosterCard(
                focusKey = "series-${series.id}",
                focusRequester = requesterFor("series-${series.id}"),
                coverUrl = if (series.sampleCode.isNotBlank()) "$baseUrl/jav/${series.sampleCode}/cover" else "",
                title = series.zhName.ifBlank { series.name },
                subtitle = "${series.workCount} 部" + if (series.studioName.isNotBlank()) " · ${series.studioName}" else "",
                portrait = false,
                titleRows = 2,
                onClick = { onOpenWorks(series.id, series.zhName.ifBlank { series.name }) },
            )
        }
    }
}

/* ------------------------------------------------------------------ 番号前缀 */

@Composable
fun TvPrefixScreen(onPick: (String) -> Unit) {
    var prefixes by remember { mutableStateOf<List<com.javboss.android.data.model.JavPrefixSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }

    val requesterFor = rememberTvFocusRestorer()
    LaunchedEffect(Unit) {
        when (val result = AppGraph.jav.filterOptions()) {
            is ApiResult.Success -> prefixes = result.data.prefixes.sortedByDescending { it.workCount }
            is ApiResult.Failure -> error = result.message
        }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(tvLibraryColumns()),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("番号前缀", style = MaterialTheme.typography.headlineSmall)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    TvInputField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = "搜索前缀 / 片商",
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
            items(
                prefixes.filter {
                    search.isBlank() || it.prefix.contains(search, true) || it.studioName.contains(search, true)
                },
                key = { it.prefix },
            ) { p ->
                TvFocusable(
                    onClick = { onPick(p.prefix) },
                    modifier = Modifier.fillMaxWidth()
                        .then(rememberTvFocusModifier("prefix-${p.prefix}", requesterFor)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp)) {
                        Text(p.prefix, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${p.workCount} 部" + if (p.studioName.isNotBlank()) " · ${p.studioName}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        error?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
