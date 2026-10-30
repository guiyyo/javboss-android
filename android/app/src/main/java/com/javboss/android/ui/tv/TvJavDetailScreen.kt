package com.javboss.android.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.javboss.android.data.repo.JavQuery
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.JavItem
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.NetworkImage
import com.javboss.android.ui.common.formatDuration

/** TV 详情页：左封面 + 右信息 + 剧集列表（全部可遥控器聚焦）。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TvJavDetailScreen(
    javId: Long,
    onBack: () -> Unit,
    onPlayVideo: (videoId: Long, locationId: Long, title: String) -> Unit,
    onOpenIdolWorks: (Long, String) -> Unit,
    /** 详情页内筛选/跳转统一走这里：标题 + 查询条件 */
    onOpenFiltered: (String, JavQuery) -> Unit,
) {
    val baseUrl = remember { AppGraph.video.baseUrl().trimEnd('/') }
    var item by remember { mutableStateOf<JavItem?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var reloadKey by remember { mutableStateOf(0) }
    val firstVideoFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val requesterFor = rememberTvFocusRestorer()
    var favoriteDialogOpen by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(javId, reloadKey) {
        when (val result = AppGraph.jav.detail(javId)) {
            is ApiResult.Success -> item = result.data
            is ApiResult.Failure -> error = result.message
        }
    }

    // 进入详情后默认聚焦到「播放」按钮
    LaunchedEffect(item?.id) {
        if (item != null) {
            kotlinx.coroutines.delay(300)
            runCatching { firstVideoFocus.requestFocus() }
        }
    }

    val detail = item
    if (detail == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    Row(Modifier.fillMaxSize().padding(28.dp), verticalAlignment = Alignment.Top) {
        // 左：封面 + 番号 + 剧集（宽度按比例，给右侧留出空间）
        // 左列：高度随内容自适应；只有内容超出屏幕时才可滚动
        Column(
            Modifier
                .weight(0.6f)
                .then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())),
        ) {
            NetworkImage(
                url = "$baseUrl/jav/${detail.code}/cover",
                modifier = Modifier.fillMaxWidth().aspectRatio(800f / 538f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = detail.code,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                val minutes: Int = if (detail.durationMin > 0) detail.durationMin.toInt()
                else (((detail.videos.maxOfOrNull { it.durationSec } ?: 0L) / 60L).toInt())
                if (minutes > 0) {
                    Text(
                        text = com.javboss.android.ui.common.formatMinutesHm(minutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            // 日期 + 喜爱程度 + 收藏夹：同一排（番号下方）
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (detail.releaseUnix > 0) {
                    Text(
                        text = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date(detail.releaseUnix * 1000)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "喜爱程度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                detail.favoriteRating.let { current ->
                    (1..5).forEach { index ->
                        val filled = current >= index - 0.25
                        TvFocusable(
                            onClick = {
                                val rating = if (current >= index) index - 1.0 else index.toDouble()
                                scope.launch {
                                    runCatching { AppGraph.jav.rate(detail.id, rating) }
                                    reloadKey++
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            flat = true,
                        ) {
                            Icon(
                                imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "$index 星",
                                tint = if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(4.dp).size(20.dp),
                            )
                        }
                    }
                }
                TvFocusable(
                    onClick = { favoriteDialogOpen = true },
                    shape = RoundedCornerShape(8.dp),
                    outlined = true,
                ) {
                    Text(
                        "收藏夹 ${detail.favoriteCount}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            if (favoriteDialogOpen) {
                com.javboss.android.ui.favorites.FavoriteGroupsDialog(
                    itemId = detail.id,
                    onDismiss = { favoriteDialogOpen = false },
                    onSaved = { reloadKey++ },
                )
            }

            val title = detail.zhTitle.ifBlank { detail.title }
            Text(
                text = "剧集（${detail.videos.size}）",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                detail.videos.forEachIndexed { index, video ->
                    TvFocusable(onClick = {
                        onPlayVideo(video.id, video.locationId, title)
                    }, modifier = Modifier.fillMaxWidth().then(
                        if (index == 0) Modifier.focusRequester(firstVideoFocus) else Modifier,
                    ).then(rememberTvFocusModifier("ep-${video.id}-${video.locationId}", requesterFor))) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("▶", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = video.filename.ifBlank { video.path },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                            )
                            Text(
                                text = formatDuration(video.durationSec),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(24.dp))

        // 右：信息（高度随内容自适应，超出时才滚动）
        Column(
            Modifier
                .weight(0.4f)
                .then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())),
        ) {
            Text(
                text = detail.zhTitle.ifBlank { detail.title },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )

            val meta = buildList {
                detail.studio?.let { add("片商：${it.name}") }
                detail.series?.let { add("系列：${it.zhName.ifBlank { it.name }}") }
                if (detail.idols.isNotEmpty()) add("女优：" + detail.idols.joinToString("、") { it.chineseName.ifBlank { it.name } })
                if (detail.tags.isNotEmpty()) add("标签：" + detail.tags.take(6).joinToString("、") { it.simplifiedName.ifBlank { it.name } })
            }
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                detail.studio?.let { studio ->
                    TvFocusable(
                        onClick = {
                            onOpenFiltered(
                                "片商：${studio.name}",
                                JavQuery(pageSize = 30, sort = "recent", studioId = studio.id),
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        outlined = true,
                    ) {
                        Text(
                            "片商：${studio.name}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
                detail.series?.let { series ->
                    val name = series.zhName.ifBlank { series.name }
                    TvFocusable(
                        onClick = {
                            onOpenFiltered(
                                "系列：$name",
                                JavQuery(pageSize = 30, sort = "recent", seriesId = series.id),
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        outlined = true,
                    ) {
                        Text(
                            "系列：$name",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
                detail.idols.take(3).forEach { idol ->
                    val name = idol.chineseName.ifBlank { idol.name }
                    TvFocusable(
                        onClick = { onOpenIdolWorks(idol.id, name) },
                        shape = RoundedCornerShape(10.dp),
                        outlined = true,
                    ) {
                        Text(
                            "女优：$name",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
            }
                }


            // 标签单独一栏（与手机/平板一致）
            if (detail.tags.isNotEmpty()) {
                Text(
                    "标签",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    detail.tags.take(12).forEach { tag ->
                        val name = tag.simplifiedName.ifBlank { tag.name }
                        TvFocusable(
                            onClick = {
                                onOpenFiltered(
                                    "标签：$name",
                                    JavQuery(pageSize = 30, sort = "recent", tagIds = listOf(tag.id)),
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            outlined = true,
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }



        }
    }
}
