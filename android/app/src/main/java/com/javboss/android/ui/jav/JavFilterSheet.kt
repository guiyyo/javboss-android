package com.javboss.android.ui.jav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.JavFilterOptions
import com.javboss.android.data.repo.JavQuery
import com.javboss.android.di.AppGraph

/**
 * 三级筛选面板：
 *   一级 = 筛选类别（观看状态 / 类型 / 番号前缀 / 片商 / 系列 / 女优 / 标签 / 喜爱度）
 *   二级 = 具体类别的待选项（带搜索）
 *   三级 = 选项内可多选/单选，底部「应用」统一下发
 * 不会一次性把所有候选项铺开。
 */
private enum class FilterPage(val title: String) {
    Root("筛选"),
    Watched("观看状态"),
    Type("作品类型"),
    Prefix("番号前缀"),
    Studio("片商"),
    Series("系列"),
    Idol("女优"),
    Tag("标签"),
    Favorite("收藏夹"),
    Rating("喜爱度"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JavFilterSheet(
    initial: JavQuery,
    onDismiss: () -> Unit,
    onApply: (JavQuery) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var options by remember { mutableStateOf<JavFilterOptions?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var page by remember { mutableStateOf(FilterPage.Root) }
    var query by remember { mutableStateOf("") }

    var watchedState by remember { mutableStateOf(initial.watchedState) }
    var soloOnly by remember { mutableStateOf(initial.soloOnly) }
    var prefix by remember { mutableStateOf(initial.prefix) }
    var idolIds by remember { mutableStateOf(initial.idolIds.toSet()) }
    var tagIds by remember { mutableStateOf(initial.tagIds.toSet()) }
    var studioId by remember { mutableStateOf(initial.studioId) }
    var seriesId by remember { mutableStateOf(initial.seriesId) }
    var favoriteGroupId by remember { mutableStateOf(initial.favoriteGroupId) }
    var favoriteGroups by remember { mutableStateOf<List<com.javboss.android.data.model.FavoriteGroup>>(emptyList()) }
    var ratingMin by remember { mutableStateOf(initial.ratingMin) }
    var ratingMax by remember { mutableStateOf(initial.ratingMax) }

    LaunchedEffect(Unit) {
        when (val result = AppGraph.jav.filterOptions()) {
            is ApiResult.Success -> options = result.data
            is ApiResult.Failure -> error = result.message
        }
        when (val groups = AppGraph.jav.favoriteGroups("jav")) {
            is ApiResult.Success -> favoriteGroups = groups.data
            is ApiResult.Failure -> Unit
        }
    }

    fun buildQuery(): JavQuery = initial.copy(
        soloOnly = soloOnly,
        prefix = prefix,
        idolIds = idolIds.toList(),
        tagIds = tagIds.toList(),
        studioId = studioId,
        seriesId = seriesId,
        ratingMin = ratingMin,
        ratingMax = ratingMax,
        watchedState = watchedState,
        favoriteGroupId = favoriteGroupId,
    )

    fun summaryFor(p: FilterPage): String = when (p) {
        FilterPage.Watched -> when (watchedState) {
            "watched" -> "已播放"
            "unwatched" -> "未播放"
            else -> "全部"
        }
        FilterPage.Type -> if (soloOnly) "单体作品" else "全部"
        FilterPage.Prefix -> prefix ?: "全部"
        FilterPage.Studio -> options?.studios?.firstOrNull { it.id == studioId }?.name ?: "全部"
        FilterPage.Series -> options?.series?.firstOrNull { it.id == seriesId }
            ?.let { it.zhName.ifBlank { it.name } } ?: "全部"
        FilterPage.Idol -> if (idolIds.isEmpty()) "全部" else "已选 ${idolIds.size}"
        FilterPage.Tag -> if (tagIds.isEmpty()) "全部" else "已选 ${tagIds.size}"
        FilterPage.Rating -> if (ratingMin == null || ratingMax == null) "不限" else "${ratingMin} - ${ratingMax} 星"
        FilterPage.Favorite -> favoriteGroups.firstOrNull { it.id == favoriteGroupId }?.name ?: "全部"
        FilterPage.Root -> ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            // 顶部：返回 + 标题
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page != FilterPage.Root) {
                    IconButton(onClick = { page = FilterPage.Root; query = "" }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回筛选类别")
                    }
                }
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = if (page == FilterPage.Root) 12.dp else 0.dp),
                )
            }

            when {
                error != null -> Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )

                page == FilterPage.Root -> {
                    val opts = options
                    LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                        val entries = listOf(
                            FilterPage.Watched,
                            FilterPage.Type,
                            FilterPage.Favorite,
                            FilterPage.Rating,
                            FilterPage.Prefix,
                            FilterPage.Studio,
                            FilterPage.Series,
                            FilterPage.Idol,
                            FilterPage.Tag,
                        )
                        items(entries) { entry ->
                            if (entry != FilterPage.Prefix || (opts?.prefixes?.isNotEmpty() == true)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { page = entry; query = "" }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(entry.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = summaryFor(entry),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (summaryFor(entry) == "全部" || summaryFor(entry) == "不限") {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                                    )
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                else -> {
                    // 二级/三级：搜索 + 待选项
                    val opts = options
                    Column {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("搜索 ${page.title}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                            when (page) {
                                FilterPage.Watched -> {
                                    val choices = listOf(
                                        null to "全部",
                                        "watched" to "已播放",
                                        "unwatched" to "未播放",
                                    )
                                    items(choices) { (value, label) ->
                                        OptionRow(label = label, selected = watchedState == value) {
                                            watchedState = value
                                        }
                                    }
                                }

                                FilterPage.Type -> {
                                    val total = opts?.total ?: 0
                                    val solo = opts?.soloCount ?: 0
                                    items(
                                        listOf(
                                            Triple(false, "全部作品", total),
                                            Triple(true, "单体作品", solo),
                                        )
                                    ) { (single, label, count) ->
                                        OptionRow(
                                            label = "$label（$count）",
                                            selected = soloOnly == single,
                                        ) { soloOnly = single }
                                    }
                                }

                                FilterPage.Favorite -> {
                                    item {
                                        OptionRow(label = "全部收藏夹", selected = favoriteGroupId == null) {
                                            favoriteGroupId = null
                                        }
                                    }
                                    items(favoriteGroups.filter { query.isBlank() || it.name.contains(query, true) }) { group ->
                                        OptionRow(
                                            label = "${group.name}（${group.count}）",
                                            selected = favoriteGroupId == group.id,
                                        ) { favoriteGroupId = group.id }
                                    }
                                }

                                FilterPage.Rating -> {
                                    item {
                                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                            Text("最低分", style = MaterialTheme.typography.labelMedium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                (1..5).forEach { value ->
                                                    FilterChip(
                                                        selected = ratingMin == value.toDouble(),
                                                        onClick = {
                                                            ratingMin = value.toDouble()
                                                            if (ratingMax == null || ratingMax!! < value) ratingMax = value.toDouble()
                                                        },
                                                        label = { Text("$value") },
                                                    )
                                                }
                                            }
                                            Text(
                                                "最高分",
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(top = 6.dp),
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                (1..5).forEach { value ->
                                                    FilterChip(
                                                        selected = ratingMax == value.toDouble(),
                                                        onClick = { ratingMax = value.toDouble() },
                                                        label = { Text("$value") },
                                                    )
                                                }
                                            }
                                            TextButton(onClick = { ratingMin = null; ratingMax = null }) {
                                                Text("清除喜爱度条件")
                                            }
                                        }
                                    }
                                }

                                FilterPage.Prefix -> {
                                    item {
                                        OptionRow(label = "全部前缀", selected = prefix == null) { prefix = null }
                                    }
                                    items(
                                        (opts?.prefixes ?: emptyList()).filter {
                                            query.isBlank() ||
                                                it.prefix.contains(query, true) ||
                                                it.studioName.contains(query, true)
                                        }.take(200)
                                    ) { p ->
                                        OptionRow(
                                            label = "${p.prefix}（${p.workCount}）${if (p.studioName.isNotBlank()) " · ${p.studioName}" else ""}",
                                            selected = prefix == p.prefix,
                                        ) { prefix = p.prefix }
                                    }
                                }

                                FilterPage.Studio -> {
                                    item {
                                        OptionRow(label = "全部片商", selected = studioId == null) { studioId = null }
                                    }
                                    items(
                                        (opts?.studios ?: emptyList()).filter {
                                            query.isBlank() || it.name.contains(query, true)
                                        }.take(200)
                                    ) { s ->
                                        OptionRow(
                                            label = "${s.name}（${s.workCount}）",
                                            selected = studioId == s.id,
                                        ) { studioId = s.id }
                                    }
                                }

                                FilterPage.Series -> {
                                    item {
                                        OptionRow(label = "全部系列", selected = seriesId == null) { seriesId = null }
                                    }
                                    items(
                                        (opts?.series ?: emptyList()).filter {
                                            query.isBlank() ||
                                                it.name.contains(query, true) ||
                                                it.zhName.contains(query, true)
                                        }.take(200)
                                    ) { s ->
                                        OptionRow(
                                            label = "${s.zhName.ifBlank { s.name }}（${s.workCount}）",
                                            selected = seriesId == s.id,
                                        ) { seriesId = s.id }
                                    }
                                }

                                FilterPage.Idol -> {
                                    items(
                                        (opts?.idols ?: emptyList()).filter {
                                            query.isBlank() ||
                                                it.name.contains(query, true) ||
                                                it.chineseName.contains(query, true) ||
                                                it.romanName.contains(query, true)
                                        }.take(300)
                                    ) { idol ->
                                        OptionRow(
                                            label = "${idol.chineseName.ifBlank { idol.name }}（${idol.workCount}）",
                                            selected = idolIds.contains(idol.id),
                                            multi = true,
                                        ) {
                                            idolIds = if (idolIds.contains(idol.id)) idolIds - idol.id else idolIds + idol.id
                                        }
                                    }
                                }

                                FilterPage.Tag -> {
                                    items(
                                        (opts?.tags ?: emptyList()).filter {
                                            query.isBlank() ||
                                                it.name.contains(query, true) ||
                                                it.simplifiedName.contains(query, true)
                                        }.take(300)
                                    ) { tag ->
                                        OptionRow(
                                            label = "${tag.simplifiedName.ifBlank { tag.name }}（${tag.count}）",
                                            selected = tagIds.contains(tag.id),
                                            multi = true,
                                        ) {
                                            tagIds = if (tagIds.contains(tag.id)) tagIds - tag.id else tagIds + tag.id
                                        }
                                    }
                                }

                                FilterPage.Root -> Unit
                            }
                        }
                    }
                }
            }

            // 底部操作
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = {
                        onApply(initial.copy(soloOnly = false, prefix = null, idolIds = emptyList(), tagIds = emptyList(), studioId = null, seriesId = null, ratingMin = null, ratingMax = null, watchedState = null, favoriteGroupId = null))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("清空") }
                Button(
                    onClick = {
                        onApply(buildQuery())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("应用") }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, multi: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = if (multi) "已选中" else "已选中",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
