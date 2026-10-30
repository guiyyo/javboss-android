package com.javboss.android.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javboss.android.ui.common.NetworkImage

/** TV 焦点容器：遥控器移上来时放大 + 主色描边。 */
@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    /** true = 无底色（用于列表行，避免出现一格格的浅色块），聚焦时才高亮 */
    flat: Boolean = false,
    /** true = 常态也画描边（用于「可点击」的 chip，告诉用户这里能点） */
    outlined: Boolean = false,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "tv-focus-scale")
    val bg = when {
        focused -> MaterialTheme.colorScheme.surfaceVariant
        flat -> Color.Transparent
        else -> MaterialTheme.colorScheme.surface
    }
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(bg)
            .border(
                BorderStroke(
                    if (focused) 2.dp else 1.dp,
                    when {
                        focused -> MaterialTheme.colorScheme.primary
                        outlined -> MaterialTheme.colorScheme.outline
                        else -> Color.Transparent
                    },
                ),
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        content(focused)
    }
}

/**
 * TV 大卡片：与网页端一致的两种封面样式
 *  - 横版（landscape）：800:538 等比**完整显示**（object-contain 顶部对齐）
 *  - 竖版（portrait）：源图裁出右侧 47% 的窄条（与网页 COVER_VISIBLE_RATIO=0.47 一致），
 *    比例为 376:538；裁切位置取 item.cover_crop_position（默认 1.0 = 最右侧）
 */
@Composable
fun TvPosterCard(
    coverUrl: String,
    title: String,
    subtitle: String? = null,
    progress: Float? = null,
    /** 传 null 表示填满网格单元格 */
    cardWidth: androidx.compose.ui.unit.Dp? = null,
    portrait: Boolean = false,
    cropPosition: Double = 1.0,
    /** 副标题（番号 + 片商/女优等，跟随网页显示设置拼好再传进来） */
    meta: String? = null,
    /** 标题最大行数（跟随网页 jav_title_max_rows） */
    titleRows: Int = 2,
    /** 焦点记忆：返回该页时把焦点还原到这一项 */
    focusKey: String? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onClick: () -> Unit,
) {
    // 竖版：可见宽度 47%，高度不变 → 宽高比 376:538
    val screenKey = TvScreenKey.current
    val aspect = if (portrait) 376f / 538f else 800f / 538f
    val coverModifier = if (cardWidth != null) {
        Modifier.fillMaxWidth().height(cardWidth / aspect)
    } else {
        Modifier.fillMaxWidth().aspectRatio(aspect)
    }
    val scale = if (portrait) {
        androidx.compose.ui.layout.ContentScale.Crop
    } else {
        androidx.compose.ui.layout.ContentScale.Fit
    }
    val alignment = if (portrait) {
        androidx.compose.ui.BiasAlignment(cropPosition.toFloat() * 2f - 1f, 0f)
    } else {
        Alignment.TopCenter
    }

    TvFocusable(
        onClick = onClick,
        modifier = (if (cardWidth != null) Modifier.width(cardWidth) else Modifier.fillMaxWidth())
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            )
            .onFocusChanged {
                if (it.isFocused && focusKey != null) {
                    TvFocusMemory.put(screenKey, focusKey)
                }
            },
    ) {
        Column {
            Box(Modifier.fillMaxWidth().background(Color.Black)) {
                NetworkImage(
                    url = coverUrl,
                    modifier = coverModifier,
                    contentScale = scale,
                    alignment = alignment,
                )
                if (progress != null && progress > 0f) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    )
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            val rows = titleRows.coerceIn(1, 3)
            val titleLineHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                MaterialTheme.typography.bodySmall.lineHeight.toDp()
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = rows,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(titleLineHeight * rows + 10.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            if (!meta.isNullOrBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/** TV 侧栏与页面内边距占用的宽度（dp）。 */
private const val TV_RAIL_AND_PADDING_DP = 188

@Composable
private fun tvAvailableWidthDp(): Int {
    val widthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    return (widthDp - TV_RAIL_AND_PADDING_DP).coerceAtLeast(320)
}

/**
 * 每行列数：默认 3（设置里可改：自动 / 3 / 4 / 5 / 6）。
 * 「自动」按可用宽度和卡片最小宽度算。
 */
@Composable
fun tvGridColumns(portrait: Boolean): Int {
    val prefs by com.javboss.android.di.AppGraph.settings.uiPrefs.collectAsState(
        initial = com.javboss.android.data.settings.UiPrefs(),
    )
    if (prefs.tvColumns > 0) return prefs.tvColumns      // 本机显式设置优先
    // 未显式设置：按封面样式给默认（竖版 4 / 横版 3）
    if (portrait) return 4
    val webColumns by com.javboss.android.di.AppGraph.config.config.collectAsState()
    (webColumns["jav_grid_columns"] ?: "").toIntOrNull()?.takeIf { it in 2..12 }?.let { return it }
    return 3
}

/** 首页横滑行的卡片宽度：按列数均分可用宽度。 */
@Composable
fun tvRowCardWidth(portrait: Boolean): androidx.compose.ui.unit.Dp {
    val available = tvAvailableWidthDp()
    val columns = tvGridColumns(portrait).coerceAtLeast(2)
    return (available / columns).coerceIn(120, 420).dp
}
/** TV 横滑行（带标题）。 */
@Composable
fun <T> TvRow(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items, key = key) { item -> itemContent(item) }
        }
    }
}

/** TV 侧边导航项。 */
@Composable
fun TvNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 遥控器焦点移到该项时立刻触发（TV 习惯：选中即切换） */
    onFocus: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 9.dp),
            maxLines = 1,
        )
    }
}
/**
 * TV 列表页统一页头：标题（headlineSmall）+ 共 N + [可选筛选] + 搜索框，全部同一行同一规格。
 * JAV / 女优 / 片商 / 系列 / 番号前缀 都用它，避免各页字号与排版不一致。
 */
@Composable
fun TvListHeader(
    title: String,
    total: Long? = null,
    searchPlaceholder: String,
    searchText: String,
    onSearch: (String) -> Unit,
    onFilter: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (total != null) {
            Text(
                "共 $total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        if (onFilter != null) {
            TvFocusable(
                onClick = onFilter,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(56.dp),
            ) {
                Box(Modifier.height(56.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "筛选",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        TvInputField(
            value = searchText,
            onValueChange = onSearch,
            placeholder = searchPlaceholder,
            modifier = Modifier.width(300.dp),
        )
        if (onOpenSettings != null) {
            TvFocusable(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(56.dp),
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
/**
 * TV 输入框：焦点经过时**不弹键盘**，必须按 OK 才进入输入状态。
 * 失焦或按返回自动退出输入状态。
 */
@Composable
fun TvInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    password: Boolean = false,
    label: String? = null,
    digitsOnly: Boolean = false,
    maxLength: Int = 64,
) {
    var editing by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // 进入编辑态：反复尝试拿焦点 + 弹软键盘（TV 上 IME 有时慢）
    LaunchedEffect(editing) {
        if (!editing) return@LaunchedEffect
        repeat(6) {
            kotlinx.coroutines.delay(90)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    if (editing) {
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                val filtered = if (digitsOnly) input.filter { it.isDigit() } else input
                onValueChange(filtered.take(maxLength))
            },
            label = label?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodySmall,
            visualTransformation = if (password) {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else androidx.compose.ui.text.input.VisualTransformation.None,
            shape = RoundedCornerShape(10.dp),
            modifier = modifier
                .focusRequester(focus)
                // 遥控器数字键/退格直接输入（TV 上软键盘不一定弹得出来）
                .onPreviewKeyEvent { e ->
                    if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    val digit = keyToChar(e.key)
                    when {
                        digit != null -> {
                            if (digitsOnly || true) {
                                onValueChange((value + digit).take(maxLength))
                            }
                            true
                        }
                        e.key == androidx.compose.ui.input.key.Key.Backspace -> {
                            onValueChange(value.dropLast(1))
                            true
                        }
                        e.key == androidx.compose.ui.input.key.Key.Back -> {
                            editing = false
                            keyboard?.hide()
                            true
                        }
                        else -> false
                    }
                },
        )
    } else {
        TvFocusable(
            onClick = {
                editing = true
            },
            shape = RoundedCornerShape(10.dp),
            outlined = true,
            modifier = modifier,
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        value.isBlank() -> placeholder
                        password -> "•".repeat(value.length.coerceAtMost(12))
                        else -> value
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "OK 输入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun keyToChar(key: androidx.compose.ui.input.key.Key): String? = when (key) {
    androidx.compose.ui.input.key.Key.Zero -> "0"
    androidx.compose.ui.input.key.Key.One -> "1"
    androidx.compose.ui.input.key.Key.Two -> "2"
    androidx.compose.ui.input.key.Key.Three -> "3"
    androidx.compose.ui.input.key.Key.Four -> "4"
    androidx.compose.ui.input.key.Key.Five -> "5"
    androidx.compose.ui.input.key.Key.Six -> "6"
    androidx.compose.ui.input.key.Key.Seven -> "7"
    androidx.compose.ui.input.key.Key.Eight -> "8"
    androidx.compose.ui.input.key.Key.Nine -> "9"
    else -> null
}

/**
 * TV 数字密码键盘：完全不依赖系统输入法，遥控器方向键 + OK 即可输入。
 * 用于密码锁解锁、设置密码（含二次确认）。
 */
@Composable
fun TvPinPad(
    pin: String,
    onPinChange: (String) -> Unit,
    maxLen: Int = 6,
    actionLabel: String = "确定",
    onAction: () -> Unit,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 密码点阵
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(maxLen) { i ->
                val filled = i < pin.length
                Box(
                    Modifier
                        .size(18.dp)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(9.dp),
                        ),
                )
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("删除", "0", actionLabel),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { key ->
                        TvFocusable(
                            onClick = {
                                if (!enabled) return@TvFocusable
                                when (key) {
                                    "删除" -> if (pin.isNotEmpty()) onPinChange(pin.dropLast(1))
                                    actionLabel -> onAction()
                                    else -> if (pin.length < maxLen) onPinChange(pin + key)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Box(
                                Modifier.width(96.dp).height(56.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    key,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (key == actionLabel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
/** 记住每个页面最后一次聚焦的条目，返回时还原焦点。 */
object TvFocusMemory {
    private val map = mutableMapOf<String, String>()
    fun put(screen: String, key: String) {
        if (screen.isNotBlank()) map[screen] = key
    }
    fun get(screen: String): String? = map[screen]
}

/** 当前页面标识（用于焦点记忆）。 */
val TvScreenKey = androidx.compose.runtime.staticCompositionLocalOf { "" }

/**
 * 焦点还原器：按当前页面（[TvScreenKey]）记忆最后一次聚焦的条目 key，
 * 页面重新组合（如从详情返回）时自动把焦点放回那个条目。
 */
@Composable
fun rememberTvFocusRestorer(): (String) -> androidx.compose.ui.focus.FocusRequester {
    val screenKey = TvScreenKey.current
    val requesters = remember { mutableMapOf<String, androidx.compose.ui.focus.FocusRequester>() }
    LaunchedEffect(screenKey) {
        kotlinx.coroutines.delay(220)
        TvFocusMemory.get(screenKey)?.let { key ->
            runCatching { requesters[key]?.requestFocus() }
        }
    }
    return remember(screenKey) {
        { key: String ->
            requesters.getOrPut(key) { androidx.compose.ui.focus.FocusRequester() }
        }
    }
}
/** 给任意可聚焦控件加上「页面内焦点记忆」。 */
@Composable
fun rememberTvFocusModifier(
    key: String,
    requesterFor: (String) -> androidx.compose.ui.focus.FocusRequester,
): Modifier {
    val screenKey = TvScreenKey.current
    val requester = requesterFor(key)
    return Modifier
        .focusRequester(requester)
        .onFocusChanged { if (it.isFocused) TvFocusMemory.put(screenKey, key) }
}