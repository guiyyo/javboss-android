package com.javboss.android.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 大屏适配规格：按屏幕宽度均匀分列。
 *  - Compact(<600dp)：手机，JAV 1 列大卡、视频/女优 2 列；
 *  - Medium(600–839dp)：小尺寸平板，JAV 2 列；
 *  - Expanded(>=840dp)：横屏平板/大平板，JAV 3 列、详情双栏、左侧导航栏。
 */
data class LayoutSpec(
    val javColumns: Int,
    val videoColumns: Int,
    val idolColumns: Int,
    val libraryColumns: Int,
    val twoPane: Boolean,
    val useRail: Boolean,
    val isLarge: Boolean,
)

/** 由「界面模式」设置强制布局宽度（null = 用真实屏宽） */
val LocalLayoutWidthOverride = androidx.compose.runtime.staticCompositionLocalOf<Int?> { null }

@Composable
fun rememberLayoutSpec(): LayoutSpec {
    val widthDp = LocalLayoutWidthOverride.current ?: LocalConfiguration.current.screenWidthDp
    return when {
        widthDp >= 840 -> LayoutSpec(3, 3, 4, 3, twoPane = true, useRail = true, isLarge = true)
        widthDp >= 600 -> LayoutSpec(2, 3, 3, 2, twoPane = false, useRail = true, isLarge = true)
        else -> LayoutSpec(1, 2, 2, 1, twoPane = false, useRail = false, isLarge = false)
    }
}

/** 表单/设置类页面：大屏限制内容宽度并居中，避免超长行。 */
@Composable
fun CenteredContent(
    maxWidth: Dp = 760.dp,
    content: @Composable () -> Unit,
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    if (widthDp > maxWidth.value) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = maxWidth)) { content() }
        }
    } else {
        content()
    }
}