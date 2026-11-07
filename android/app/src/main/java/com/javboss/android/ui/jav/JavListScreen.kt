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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Casino
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var moreMenuOpen by remember { mutableStateOf(false) }
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
    // 当前网格列数（网格与「随机」共用）：随机视图按「4 行」取数
    val gridColumns = when {
        prefs.tvColumns > 0 -> prefs.tvColumns
        prefs.coverStyle == "portrait" -> 4
        else -> spec.javColumns
    }
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
                    placeholder = { Text("搜索番号 / 标题 / 女优", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            if (!searchExpanded || spec.isLarge) {
                // 顶部工具条（产品化重排）：动作都带文字标签，按「常用→低频」从左到右；
                // 低频项收进「更多」菜单，避免一排图标看不出是什么。
                // 只有当搜索框收起（手机未展开）时才用 Spacer 把动作推到右边；
                // 大屏搜索框常驻时，让搜索框吃掉剩余宽度（之前两者各 weight(1f) 平分，导致输入框过窄、占位文字换行）
                if (!inlineSearch) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                ToolAction("筛选", Icons.Filled.FilterList, active = vm.query.hasFilter()) { filterOpen = true }
                Box {
                    ToolAction("排序", Icons.Filled.Sort) { sortMenuOpen = true }
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
                ToolAction("随机", Icons.Filled.Casino, active = vm.randomMode) {
                    // 平板/横屏按「4 行 × 当前列数」铺满（如 3 列 → 12、2 列 → 8）；
                    // 手机大卡片一行只放得下 1 张，4 行只有 4 个太少，固定给 10 个。
                    vm.randomTen(if (spec.isLarge) gridColumns * 4 else 10)
                }
                Box {
                    IconButton(onClick = { moreMenuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("番号字典") }, onClick = {
                            moreMenuOpen = false
                            onOpenPrefixes()
                        })
                        DropdownMenuItem(text = { Text("观看记录") }, onClick = {
                            moreMenuOpen = false
                            onOpenHistory()
                        })
                        DropdownMenuItem(text = { Text("设置") }, onClick = {
                            moreMenuOpen = false
                            onOpenSettings()
                        })
                    }
                }
            }
        }

        // 搜索历史：只在「搜索框展开 + 还没输入」时出现（F1）
        if (searchExpanded && searchText.isBlank() && vm.searchHistory.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                vm.searchHistory.forEach { h ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            searchText = h
                            vm.setSearch(h)
                        },
                        label = { Text(h, style = MaterialTheme.typography.labelSmall) },
                    )
                }
                TextButton(onClick = { vm.clearSearchHistory() }) { Text("清空历史") }
            }
        }

        vm.randomHint?.let { hint ->
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clickable { vm.dismissRandomHint() },
            )
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

        // 随机视图提示条：说明当前列表是随机的，可一键返回
        if (vm.randomMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = true,
                    onClick = { vm.clearRandom() },
                    label = { Text("随机推荐 " + vm.total + " 部 · 点此返回", style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = "返回", modifier = Modifier.size(16.dp))
                    },
                )
            }
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
            vm.itemsFiltered.isEmpty() -> EmptyBox(
                if (vm.subtitleFilter == SubtitleFilter.ALL) "没有符合条件的作品" else "没有「${vm.subtitleFilter.label}」的作品",
                modifier = Modifier.weight(1f),
            )
            else -> Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
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
                items(vm.itemsFiltered, key = { it.id }) { item ->
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
            subtitleFilter = vm.subtitleFilter,
            subtitleStats = vm.subtitleStats(),
            onSubtitleFilter = { vm.applySubtitleFilter(it) },
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
            // 字幕角标：统一放左上角（多选模式隐藏，避免与勾选框叠在一起）
            if (!selectionMode) {
                SubtitleBadge(
                    code = item.code,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
            if (item.isUncensored == true) {
                Text(
                    text = "无码",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
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

/** 顶部工具条按钮：图标 + 文字（比纯图标好认，选中态用主色）。 */
@Composable
private fun ToolAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
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
                // 「只要有封面就显示封面」：该视频关联了番号就用作品封面，否则才用视频截图
                url = com.javboss.android.core.media.CardImageUrl.forVideo(baseUrl, video.jav?.code, video.id),
                modifier = Modifier.fillMaxSize(),
            )
            // 字幕角标：左上角（右上角是时长），小卡片用 dense 样式
            video.jav?.code?.let { code ->
                SubtitleBadge(
                    code = code,
                    dense = true,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                )
            }
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
