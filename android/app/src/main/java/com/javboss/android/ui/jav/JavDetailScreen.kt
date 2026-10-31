package com.javboss.android.ui.jav

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.javboss.android.ui.favorites.FavoriteGroupsDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.data.model.JavItem
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.LoadingBox
import com.javboss.android.ui.common.NetworkImage
import com.javboss.android.ui.common.formatDuration
import com.javboss.android.ui.common.formatMinutes
import com.javboss.android.ui.common.formatUnixDate

@Composable
fun JavDetailScreen(
    itemId: Long,
    onPlay: (videoId: Long, locationId: Long, title: String) -> Unit,
    onOpenScreenshots: (videoId: Long) -> Unit = {},
    onEdit: () -> Unit = {},
    onOpenIdol: (Long) -> Unit = {},
    onOpenStudio: (Long) -> Unit = {},
    onOpenSeries: (Long) -> Unit = {},
    onOpenTag: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    vm: JavDetailViewModel = viewModel(),
) {
    LaunchedEffect(itemId) { vm.load(itemId) }

    val item = vm.item
    when {
        vm.loading && item == null -> LoadingBox(modifier.fillMaxSize())
        vm.error != null && item == null -> ErrorBox(vm.error!!, onRetry = { vm.load(itemId, force = true) }, modifier.fillMaxSize())
        item == null -> LoadingBox(modifier.fillMaxSize())
        else -> JavDetailContent(
            item = item,
            onPlay = onPlay,
            onOpenScreenshots = onOpenScreenshots,
            onRate = vm::rate,
            onReload = { vm.load(itemId, force = true) },
            onEdit = onEdit,
            onOpenIdol = onOpenIdol,
            onOpenStudio = onOpenStudio,
            onOpenSeries = onOpenSeries,
            onOpenTag = onOpenTag,
            modifier = modifier,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun JavDetailContent(
    item: JavItem,
    onPlay: (Long, Long, String) -> Unit,
    onOpenScreenshots: (Long) -> Unit,
    onRate: (Double) -> Unit,
    onReload: () -> Unit,
    onEdit: () -> Unit,
    onOpenIdol: (Long) -> Unit,
    onOpenStudio: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenTag: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseUrl = AppGraph.video.baseUrl().trimEnd('/')
    val spec = com.javboss.android.ui.common.rememberLayoutSpec()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var viewerOpen by remember { mutableStateOf(false) }
    var favoriteDialogOpen by remember { mutableStateOf(false) }
    val coverUrl = "$baseUrl/jav/${item.code}/cover"
    val coverFileName = "javboss_${item.code}.jpg"
    val onCoverLongPress: () -> Unit = {
        scope.launch {
            val result = com.javboss.android.core.media.MediaSaver.saveImage(context, coverUrl, coverFileName)
            android.widget.Toast.makeText(
                context,
                result.fold(
                    onSuccess = { "封面已保存到相册/Pictures/JavBoss" },
                    onFailure = { "保存失败：${it.message}" },
                ),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val coverModifier = Modifier
        // 大屏（平板/电视）左列封面只占 60% 宽，保证剧集不用滚动就能看到
        .fillMaxWidth(if (spec.isLarge) 1f else 1f)
        .then(
            Modifier.combinedClickable(
                onClick = { viewerOpen = true },
                onLongClick = onCoverLongPress,
            )
        )

    Column(
        modifier = if (spec.isLarge) {
            modifier.fillMaxSize()
        } else {
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)
        },
    ) {
        if (spec.isLarge) {
            // 大屏（平板/电视）双栏：左 = 封面 + 番号 + 剧集；右 = 信息（独立上下滚动）
            Row(
                Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // 平板/电视：整页字号统一缩 20%，压缩间距，保证剧集在首屏
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                        androidx.compose.ui.platform.LocalDensity.current.density,
                        androidx.compose.ui.platform.LocalDensity.current.fontScale * 0.8f,
                    ),
                ) {
                Column(
                    Modifier
                        .weight(0.6f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // 封面按「剩余高度」自适应放大：留出番号行/星级行/剧集标题 + 至少 3 个剧集行
                    androidx.compose.foundation.layout.BoxWithConstraints {
                        val reserve = 190.dp
                        val maxCoverH = (maxHeight - reserve).coerceAtLeast(120.dp)
                        val coverW = maxCoverH * (800f / 538f)
                        NetworkImage(
                            url = coverUrl,
                            modifier = coverModifier
                                .width(if (coverW > maxWidth) maxWidth else coverW)
                                .aspectRatio(800f / 538f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.code,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = formatMinutes(item.durationMin),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    // 日期 + 喜爱程度 + 收藏夹（同一排，与电视一致）
                    RatingAndFavoritesRow(
                        item = item,
                        onRate = onRate,
                        onOpenFavorites = { favoriteDialogOpen = true },
                    )

                    if (favoriteDialogOpen) {
                        com.javboss.android.ui.favorites.FavoriteGroupsDialog(
                            itemId = item.id,
                            onDismiss = { favoriteDialogOpen = false },
                            onSaved = onReload,
                        )
                    }

                    // 播放区已移到右列（女优上方）
                }
                androidx.compose.foundation.layout.Spacer(Modifier.width(28.dp))
                Column(
                    Modifier
                        .weight(0.4f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    JavDetailBody(
                        item = item,
                        baseUrl = baseUrl,
                        onPlay = onPlay,
                        onOpenScreenshots = onOpenScreenshots,
                        onRate = onRate,
                        onEdit = onEdit,
                        onReload = onReload,
                        onOpenIdol = onOpenIdol,
                        onOpenStudio = onOpenStudio,
                        onOpenSeries = onOpenSeries,
                        onOpenTag = onOpenTag,
                        showVideos = true,
                        showRating = false,
                        showMeta = false,
                        dense = true,
                    )
                }
            }
            }
        } else {
            NetworkImage(
                url = coverUrl,
                modifier = coverModifier.aspectRatio(16f / 9f),
            )
            JavDetailBody(
                item = item,
                baseUrl = baseUrl,
                onPlay = onPlay,
                onOpenScreenshots = onOpenScreenshots,
                onRate = onRate,
                onEdit = onEdit,
                onReload = onReload,
                onOpenIdol = onOpenIdol,
                onOpenStudio = onOpenStudio,
                onOpenSeries = onOpenSeries,
                onOpenTag = onOpenTag,
            )
        }
    }

    if (viewerOpen) {
        com.javboss.android.ui.common.ImageViewerDialog(
            url = coverUrl,
            fileName = coverFileName,
            onDismiss = { viewerOpen = false },
        )
    }
}

/** 详情页信息区（大屏放右栏、手机放在封面下方）。 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun JavDetailBody(
    item: JavItem,
    baseUrl: String,
    onPlay: (Long, Long, String) -> Unit,
    onOpenScreenshots: (Long) -> Unit,
    onRate: (Double) -> Unit,
    onEdit: () -> Unit,
    onReload: () -> Unit,
    onOpenIdol: (Long) -> Unit,
    onOpenStudio: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenTag: (Long) -> Unit,
    showVideos: Boolean = true,
    showRating: Boolean = true,
    showMeta: Boolean = true,
    dense: Boolean = false,
) {
    var favoriteDialogOpen by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = item.zhTitle.ifBlank { item.title },
            style = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (item.zhTitle.isNotBlank() && item.title.isNotBlank()) {
            Text(
                text = item.title,
                style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (showMeta) Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.code, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatUnixDate(item.releaseUnix), style = MaterialTheme.typography.bodySmall)
            Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMinutes(item.durationMin), style = MaterialTheme.typography.bodySmall)
            if (item.isUncensored == true) {
                Text(
                    "无码",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }

        // 喜爱度 + 操作按钮：窄列时自动换行（平板/电视右列较窄，避免按钮被裁成空框）
        if (showRating) Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "喜爱程度",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 2.dp),
                )
                for (index in 1..5) {
                    val filled = item.favoriteRating >= index - 0.25
                    Icon(
                        imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "$index 星",
                        tint = if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                onRate(if (item.favoriteRating >= index) index - 1.0 else index.toDouble())
                            },
                    )
                }
                Text(
                    text = "收藏 ${item.favoriteCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("编辑", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = { favoriteDialogOpen = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(" 收藏夹", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
            }
        }

        if (favoriteDialogOpen) {
            FavoriteGroupsDialog(
                itemId = item.id,
                onDismiss = { favoriteDialogOpen = false },
                onSaved = onReload,
            )
        }

        if (showVideos) {
            JavDetailVideos(item = item, onPlay = onPlay, onOpenScreenshots = onOpenScreenshots)
        }

        // 女优
        if (item.idols.isNotEmpty()) {
            Text("女优", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(item.idols, key = { it.id }) { idol ->
                    AssistChip(
                        onClick = { onOpenIdol(idol.id) },
                        label = {
                            Text(
                                text = idol.chineseName.ifBlank { idol.name },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(),
                    )
                }
            }
        }

        // 片商 / 系列
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item.studio?.let { studio ->
                AssistChip(
                    onClick = { onOpenStudio(studio.id) },
                    label = { Text("片商：${studio.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
            item.series?.let { series ->
                AssistChip(
                    onClick = { onOpenSeries(series.id) },
                    label = {
                        Text(
                            "系列：${series.zhName.ifBlank { series.name }}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }

        // 标签
        if (item.tags.isNotEmpty()) {
            Text("标签", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                item.tags.take(24).forEach { tag ->
                    AssistChip(
                        onClick = { onOpenTag(tag.id) },
                        label = {
                            Text(
                                tag.simplifiedName.ifBlank { tag.name },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(),
                    )
                }
            }
        }



        // 样品图
        if (item.sampleImages.isNotEmpty()) {
            Text("样品图", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(item.sampleImages.take(10).indices.toList()) { index ->
                    val sampleUrl = "$baseUrl/jav/items/${item.id}/sample-images/$index/thumbnail"
                    Box(
                        Modifier
                            .height(120.dp)
                            .aspectRatio(16f / 9f),
                    ) {
                        NetworkImage(url = sampleUrl, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaBlock(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        },
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** 关联视频（大屏放左栏封面下方、手机放在女优字段之前）。 */
@Composable
private fun JavDetailVideos(
    item: JavItem,
    onPlay: (Long, Long, String) -> Unit,
    onOpenScreenshots: (Long) -> Unit,
) {
    Column {
    // 关联视频
    Text(
        text = "关联视频（${item.videos.size}）",
        style = MaterialTheme.typography.titleSmall,
    )
    if (item.videos.isEmpty()) {
        Text(
            text = "暂无关联的视频文件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            item.videos.forEach { video ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPlay(video.id, video.locationId, item.zhTitle.ifBlank { item.title })
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = video.filename.ifBlank { video.path },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = buildString {
                                    append(formatDuration(video.durationSec))
                                    append(" · ")
                                    append(if (video.size > 0) "%.1f GB".format(video.size / 1024.0 / 1024.0 / 1024.0) else "—")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onOpenScreenshots(video.id) }) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = "截图")
                        }
                        androidx.compose.foundation.layout.BoxWithConstraints {
                            // 窄列（平板右栏 / 强平板模式下）只显示图标，避免挤压文件名与时长
                            if (maxWidth < 360.dp) {
                                IconButton(onClick = { onPlay(video.id, video.locationId, item.zhTitle.ifBlank { item.title }) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放", tint = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = { onPlay(video.id, video.locationId, item.zhTitle.ifBlank { item.title }) },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    Text("播放", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/** 喜爱程度 + 收藏夹（平板/电视详情左列使用）。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RatingAndFavoritesRow(
    item: JavItem,
    onRate: (Double) -> Unit,
    onOpenFavorites: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatUnixDate(item.releaseUnix),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp),
            )
            Text(
                "喜爱程度",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
            (1..5).forEach { index ->
                val filled = item.favoriteRating >= index - 0.25
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "$index 星",
                    tint = if (filled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable {
                            onRate(if (item.favoriteRating >= index) index - 1.0 else index.toDouble())
                        },
                )
            }
        }
        OutlinedButton(
            onClick = onOpenFavorites,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Filled.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                " 收藏夹 ${item.favoriteCount}",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
