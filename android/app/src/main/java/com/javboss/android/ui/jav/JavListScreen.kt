package com.javboss.android.ui.jav

import androidx.compose.foundation.ExperimentalFoundationApi
import com.javboss.android.ui.common.formatDuration
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import com.javboss.android.ui.common.LoadingBox
import com.javboss.android.ui.common.NetworkImage
import com.javboss.android.core.net.ApiResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.javboss.android.ui.common.AppCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.data.model.JavItem
import com.javboss.android.ui.common.EmptyBox
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.JavCover
import com.javboss.android.ui.common.LoadingBox
import com.javboss.android.ui.common.NetworkImage
import com.javboss.android.ui.common.formatMinutes
import com.javboss.android.ui.common.watchedRatio
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.ScrollToTopButton
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Sort
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JavListScreen(
    onOpenItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialStudioId: Long? = null,
    initialSeriesId: Long? = null,
    initialIdolIds: List<Long> = emptyList(),
    initialPrefix: String? = null,
    initialTagIds: List<Long> = emptyList(),
    onOpenPrefixes: () -> Unit = {},
    onPlayVideo: (com.javboss.android.data.model.VideoRow) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    vm: JavListViewModel = viewModel(),
) {
    var searchText by remember { mutableStateOf(vm.query.search) }
    // 手机上搜索框默认收起为一个图标，点击后铺满并盖住其它按钮
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) runCatching { searchFocus.requestFocus() }
    }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var groupPickerOpen by remember { mutableStateOf(false) }
    var tagPickerOpen by remember { mutableStateOf(false) }
    var javTags by remember { mutableStateOf<List<com.javboss.android.data.model.JavTagCount>>(emptyList()) }
    val gridState = rememberLazyGridState()
    val baseUrl = remember { AppGraph.video.baseUrl() }
    val prefs = com.javboss.android.data.settings.LocalUiPrefs.current
    val navFiltered = initialStudioId != null || initialSeriesId != null ||
        initialIdolIds.isNotEmpty() || initialPrefix != null || initialTagIds.isNotEmpty()
    val spec = com.javboss.android.ui.common.rememberLayoutSpec()
    val scrollScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(initialStudioId, initialSeriesId, initialIdolIds, initialPrefix, initialTagIds) {
        if (initialStudioId != null || initialSeriesId != null ||
            initialIdolIds.isNotEmpty() || initialPrefix != null || initialTagIds.isNotEmpty()
        ) {
            vm.applyNavigationFilter(
                studioId = initialStudioId,
                seriesId = initialSeriesId,
                idolIds = initialIdolIds,
                prefix = initialPrefix,
                tagIds = initialTagIds,
            )
        }
    }

    // 让输入框与 query 保持同步（从详情返回 / 快照恢复时都能回显）
    LaunchedEffect(vm.query.search) {
        if (searchText != vm.query.search) searchText = vm.query.search
        if (vm.query.search.isNotBlank()) searchExpanded = true
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= vm.items.size - 6 }
            .collect { vm.loadMore() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val inlineSearch = spec.isLarge || searchExpanded
            if (inlineSearch) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("搜索番号 / 标题 / 女优") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = {
                                searchText = ""
                                vm.setSearch("")
                            }) { Icon(Icons.Filled.Clear, contentDescription = "清空") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.setSearch(searchText.trim()) }),
                    modifier = Modifier.weight(1f).focusRequester(searchFocus),
                )
                if (searchExpanded) {
                    // 收起搜索（文字按钮，避免和输入框里的「清空」X 撞脸）
                    TextButton(onClick = {
                        vm.setSearch(searchText.trim())
                        searchExpanded = false
                    }) { Text("收起") }
                }
            } else {
                IconButton(onClick = { searchExpanded = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
            }
            if (!searchExpanded) {
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Filled.History, contentDescription = "观看记录")
            }
            IconButton(onClick = { filterOpen = true }) {
                Icon(Icons.Filled.FilterList, contentDescription = "筛选")
            }
            IconButton(onClick = onOpenPrefixes) {
                Icon(Icons.Filled.MenuBook, contentDescription = "番号字典")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = "排序")
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    JavSortOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    color = if (option.value == vm.query.sort) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                            onClick = {
                                sortMenuOpen = false
                                vm.setSort(option.value)
                            },
                        )
                    }
                }
            }
            }
        }

        if (vm.selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("已选 ${vm.selectedIds.size}", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { groupPickerOpen = true }) { Text("收藏") }
                TextButton(onClick = { tagPickerOpen = true }) { Text("加标签") }
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

        if (vm.favoriteGroups.isNotEmpty() || vm.query.hasFilter()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (vm.query.hasFilter()) {
                    item {
                        TextButton(onClick = {
                vm.clearFilter()
                searchText = ""
                searchExpanded = false
            }) { Text("清除筛选") }
                    }
                }
                items(vm.favoriteGroups) { group ->
                    FilterChip(
                        selected = vm.query.favoriteGroupId == group.id,
                        onClick = { vm.toggleFavoriteGroup(group.id) },
                        label = { Text("${group.name} (${group.count})", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }

        Text(
            text = if (vm.total > 0) "共 ${vm.total} 部作品" else " ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        when {
            vm.loading && vm.items.isEmpty() -> LoadingBox(Modifier.weight(1f))
            vm.error != null && vm.items.isEmpty() -> ErrorBox(vm.error!!, onRetry = vm::refresh, modifier = Modifier.weight(1f))
            vm.items.isEmpty() -> EmptyBox("没有符合条件的作品", modifier = Modifier.weight(1f))
            else -> Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(
                    when {
                        prefs.tvColumns > 0 -> prefs.tvColumns
                        prefs.coverStyle == "portrait" -> 4
                        else -> spec.javColumns
                    },
                ),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // 有筛选条件时不显示「继续观看」（避免与筛选结果混杂）
                if (vm.continueWatching.isNotEmpty() && !vm.query.hasFilter() && !navFiltered) {
                    item(key = "continue-title", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "继续观看",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }
                    item(key = "continue-row", span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(vm.continueWatching, key = { "cw-${it.id}-${it.locationId}" }) { video ->
                                ContinueWatchingCard(video = video, baseUrl = baseUrl, compact = spec.isLarge) { onPlayVideo(video) }
                            }
                        }
                    }
                }
                items(vm.items, key = { it.id }) { item ->
                    JavCard(
                        item = item,
                        coverUrl = "${baseUrl.trimEnd('/')}/jav/${item.code}/cover",
                        selected = vm.selectedIds.contains(item.id),
                        selectionMode = vm.selectedIds.isNotEmpty(),
                        onClick = {
                            if (vm.selectedIds.isNotEmpty()) vm.toggleSelect(item.id) else onOpenItem(item.id)
                        },
                        onLongClick = { vm.toggleSelect(item.id) },
                    )
                }
                if (vm.loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
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

    if (groupPickerOpen) {
        AlertDialog(
            onDismissRequest = { groupPickerOpen = false },
            title = { Text("加入收藏夹") },
            text = {
                Column {
                    vm.favoriteGroups.forEach { group ->
                        TextButton(onClick = {
                            vm.addSelectedToGroups(listOf(group.id))
                            groupPickerOpen = false
                        }) { Text("${group.name}（${group.count}）") }
                    }
                    if (vm.favoriteGroups.isEmpty()) Text("还没有收藏夹，请先在详情页创建")
                }
            },
            confirmButton = { TextButton(onClick = { groupPickerOpen = false }) { Text("关闭") } },
        )
    }

    if (tagPickerOpen) {
        AlertDialog(
            onDismissRequest = { tagPickerOpen = false },
            title = { Text("添加标签") },
            text = {
                Column {
                    if (javTags.isEmpty()) {
                        LoadingBox()
                        LaunchedEffect(Unit) {
                            when (val result = AppGraph.jav.tags()) {
                                is ApiResult.Success -> javTags = result.data
                                is ApiResult.Failure -> Unit
                            }
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.heightIn(max = 300.dp),
                        ) {
                            items(javTags.take(80)) { tag ->
                                TextButton(onClick = {
                                    vm.addTagToSelected(tag.id)
                                    tagPickerOpen = false
                                }) { Text("${tag.simplifiedName.ifBlank { tag.name }}（${tag.count}）") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { tagPickerOpen = false }) { Text("关闭") } },
        )
    }

    if (filterOpen) {
        JavFilterSheet(
            initial = vm.query,
            onDismiss = { filterOpen = false },
            onApply = { newQuery ->
                searchText = newQuery.search
                vm.applyFilter(newQuery)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JavCard(
    item: JavItem,
    coverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    val prefs = com.javboss.android.data.settings.LocalUiPrefs.current
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        // 封面：跟随网页/本机设置决定横版(800:538 完整)还是竖版(376:538 右侧裁切)
        val webConfig by com.javboss.android.di.AppGraph.config.config.collectAsState()
        val portraitCard = when (prefs.coverStyle) {
            "portrait" -> true
            "landscape" -> false
            else -> (webConfig["jav_card_portrait"] ?: "").let { it == "true" || it == "1" }
        }
        val cardAspect = if (portraitCard) 376f / 538f else 800f / 538f
        Box(Modifier.fillMaxWidth().aspectRatio(cardAspect)) {
            NetworkImage(
                url = coverUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (portraitCard) {
                    androidx.compose.ui.layout.ContentScale.Crop
                } else {
                    androidx.compose.ui.layout.ContentScale.Fit
                },
                alignment = if (portraitCard) {
                    androidx.compose.ui.BiasAlignment(item.coverCropPosition.toFloat() * 2f - 1f, 0f)
                } else {
                    Alignment.TopCenter
                },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.78f),
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = item.code,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Text(
                    text = item.zhTitle.ifBlank { item.title },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = prefs.titleRows.coerceIn(1, 4),
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val ratio = watchedRatio(item.watchedMs, item.durationMin.toLong() * 60)
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
            if (item.isUncensored == true) {
                Text(
                    text = "无码",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append(formatMinutes(item.durationMin))
                    if (!prefs.hideIdols) {
                        item.idols.firstOrNull()?.name?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    }
                    if (!prefs.hideStudio) {
                        item.studio?.name?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (item.favoriteRating > 0) {
                Text(
                    text = "★ ${item.favoriteRating}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 首页「继续观看」小卡片：缩略图 + 进度 + 标题。 */
@Composable
private fun ContinueWatchingCard(
    video: com.javboss.android.data.model.VideoRow,
    baseUrl: String,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    // 固定宽高：避免标题行数不同导致整行高度变化、下方列表跳动
    val cardWidth = if (compact) 150.dp else 190.dp
    val coverHeight = if (compact) 84.dp else 107.dp
    // 标题区高度按字号自适应（平板/电视字号放大会把文字挤出去，这里用行高算）
    val titleRows = if (compact) 1 else 2
    val lineHeightDp = with(androidx.compose.ui.platform.LocalDensity.current) {
        MaterialTheme.typography.labelMedium.lineHeight.toDp()
    }
    val titleHeight = lineHeightDp * titleRows + 8.dp
    AppCard(
        modifier = Modifier.width(cardWidth),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxWidth().height(coverHeight)) {
            NetworkImage(
                url = "${baseUrl.trimEnd('/')}/videos/${video.id}/thumbnail",
                modifier = Modifier.fillMaxSize(),
            )
            val ratio = watchedRatio(video.watchedMs, video.durationSec)
            LinearProgressIndicator(
                progress = { ratio },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
            Text(
                text = formatDuration(video.durationSec),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp),
            )
        }
        Text(
            text = video.jav?.let { it.zhTitle.ifBlank { it.title } } ?: video.filename,
            style = MaterialTheme.typography.labelMedium,
            maxLines = titleRows,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(titleHeight)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
