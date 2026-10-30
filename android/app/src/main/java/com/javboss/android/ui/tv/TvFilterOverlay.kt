package com.javboss.android.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.JavFilterOptions
import com.javboss.android.data.repo.JavQuery
import com.javboss.android.di.AppGraph

/**
 * TV 筛选面板 v2：左「类别」栏 + 右「选项」区，按钮单行不换行，整体宽度 92% / 上限 1120dp。
 * 维度与手机端一致：作品类型 / 片商 / 系列 / 女优 / 标签。
 */
private enum class TvFilterPage(val title: String, val searchable: Boolean) {
    Type("作品类型", false),
    Studio("片商", true),
    Series("系列", true),
    Idol("女优", true),
    Tag("标签", true),
    Favorite("收藏夹", true),
}

@Composable
fun TvFilterOverlay(
    initial: JavQuery,
    onDismiss: () -> Unit,
    onApply: (JavQuery) -> Unit,
) {
    var options by remember { mutableStateOf<JavFilterOptions?>(null) }
    var page by remember { mutableStateOf(TvFilterPage.Type) }
    var search by remember { mutableStateOf("") }

    var soloOnly by remember { mutableStateOf(initial.soloOnly) }
    var studioId by remember { mutableStateOf(initial.studioId) }
    var seriesId by remember { mutableStateOf(initial.seriesId) }
    var favoriteGroupId by remember { mutableStateOf(initial.favoriteGroupId) }
    var favoriteGroups by remember { mutableStateOf<List<com.javboss.android.data.model.FavoriteGroup>>(emptyList()) }
    val idolIds = remember { mutableStateListOf<Long>().apply { addAll(initial.idolIds) } }
    val tagIds = remember { mutableStateListOf<Long>().apply { addAll(initial.tagIds) } }

    LaunchedEffect(Unit) {
        when (val r = AppGraph.jav.filterOptions()) {
            is ApiResult.Success -> options = r.data
            is ApiResult.Failure -> Unit
        }
        when (val g = AppGraph.jav.favoriteGroups("jav")) {
            is ApiResult.Success -> favoriteGroups = g.data
            is ApiResult.Failure -> Unit
        }
    }

    fun summary(p: TvFilterPage): String = when (p) {
        TvFilterPage.Type -> if (soloOnly) "单体" else "全部"
        TvFilterPage.Studio -> options?.studios?.firstOrNull { it.id == studioId }?.name ?: "全部"
        TvFilterPage.Series -> options?.series?.firstOrNull { it.id == seriesId }
            ?.let { it.zhName.ifBlank { it.name } } ?: "全部"
        TvFilterPage.Idol -> if (idolIds.isEmpty()) "全部" else "${idolIds.size} 项"
        TvFilterPage.Tag -> if (tagIds.isEmpty()) "全部" else "${tagIds.size} 项"
        TvFilterPage.Favorite -> favoriteGroups.firstOrNull { it.id == favoriteGroupId }?.name ?: "全部"
    }

    fun apply() {
        onApply(
            initial.copy(
                soloOnly = soloOnly,
                studioId = studioId,
                seriesId = seriesId,
                idolIds = idolIds.toList(),
                tagIds = tagIds.toList(),
                favoriteGroupId = favoriteGroupId,
            ),
        )
        onDismiss()
    }

    fun clear() {
        soloOnly = false
        studioId = null
        seriesId = null
        idolIds.clear()
        tagIds.clear()
        favoriteGroupId = null
        search = ""
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            ),
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 1120.dp),
        ) {
            Column(Modifier.fillMaxWidth().heightIn(min = 380.dp, max = 640.dp)) {
                /* ---------------- 顶部：标题 + 关闭 ---------------- */
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("筛选", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TvFocusable(onClick = onDismiss, shape = RoundedCornerShape(8.dp), flat = true) {
                        Text(
                            "关闭",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                /* ---------------- 主体：左类别 + 右选项 ---------------- */
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    // 左：类别栏
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TvFilterPage.entries.forEach { entry ->
                            val selected = entry == page
                            TvFocusable(
                                onClick = { page = entry; search = "" },
                                shape = RoundedCornerShape(6.dp),
                                flat = true,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        entry.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                                    Text(
                                        summary(entry),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    // 分隔线
                    Box(
                        Modifier.width(1.dp).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    )

                    // 右：工具条 + 选项
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (page.searchable) {
                                TvInputField(
                                    value = search,
                                    onValueChange = { search = it },
                                    placeholder = "搜索",
                                    modifier = Modifier.width(220.dp),
                                )
                            }
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            TvFocusable(onClick = { clear() }, shape = RoundedCornerShape(8.dp), flat = true) {
                                Text(
                                    "清空",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                )
                            }
                            TvFocusable(onClick = { apply() }, shape = RoundedCornerShape(8.dp), flat = true) {
                                Text(
                                    "应用",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        val opts = options
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        ) {
                            when (page) {
                                TvFilterPage.Type -> {
                                    items(
                                        listOf(
                                            false to "全部作品（${opts?.total ?: 0}）",
                                            true to "单体作品（${opts?.soloCount ?: 0}）",
                                        ),
                                    ) { pair ->
                                        TvFilterOptionRow(pair.second, soloOnly == pair.first) { soloOnly = pair.first }
                                    }
                                }
                                TvFilterPage.Studio -> {
                                    items(
                                        (opts?.studios ?: emptyList()).filter {
                                            search.isBlank() || it.name.contains(search, true)
                                        },
                                        key = { it.id },
                                    ) { s ->
                                        TvFilterOptionRow("${s.name}（${s.workCount}）", studioId == s.id) { studioId = s.id }
                                    }
                                }
                                TvFilterPage.Series -> {
                                    items(
                                        (opts?.series ?: emptyList()).filter {
                                            search.isBlank() || it.name.contains(search, true) ||
                                                it.zhName.contains(search, true)
                                        },
                                        key = { it.id },
                                    ) { s ->
                                        TvFilterOptionRow(
                                            "${s.zhName.ifBlank { s.name }}（${s.workCount}）",
                                            seriesId == s.id,
                                        ) { seriesId = s.id }
                                    }
                                }
                                TvFilterPage.Idol -> {
                                    items(
                                        (opts?.idols ?: emptyList()).filter {
                                            search.isBlank() || it.name.contains(search, true) ||
                                                it.chineseName.contains(search, true) ||
                                                it.romanName.contains(search, true)
                                        },
                                        key = { it.id },
                                    ) { idol ->
                                        TvFilterOptionRow(
                                            "${idol.chineseName.ifBlank { idol.name }}（${idol.workCount}）",
                                            idolIds.contains(idol.id),
                                        ) {
                                            if (idolIds.contains(idol.id)) idolIds.remove(idol.id)
                                            else idolIds.add(idol.id)
                                        }
                                    }
                                }
                                TvFilterPage.Favorite -> {
                                    items(
                                        favoriteGroups.filter { search.isBlank() || it.name.contains(search, true) },
                                        key = { it.id },
                                    ) { group ->
                                        TvFilterOptionRow(
                                            "${group.name}（${group.count}）",
                                            favoriteGroupId == group.id,
                                        ) { favoriteGroupId = group.id }
                                    }
                                }
                                TvFilterPage.Tag -> {
                                    items(
                                        (opts?.tags ?: emptyList()).filter {
                                            search.isBlank() || it.name.contains(search, true) ||
                                                it.simplifiedName.contains(search, true)
                                        },
                                        key = { it.id },
                                    ) { tag ->
                                        TvFilterOptionRow(
                                            "${tag.simplifiedName.ifBlank { tag.name }}（${tag.count}）",
                                            tagIds.contains(tag.id),
                                        ) {
                                            if (tagIds.contains(tag.id)) tagIds.remove(tag.id)
                                            else tagIds.add(tag.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (options == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun TvFilterOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    TvFocusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        flat = true,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
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
                Text("✔", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
