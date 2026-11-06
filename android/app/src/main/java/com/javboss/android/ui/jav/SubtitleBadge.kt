package com.javboss.android.ui.jav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.javboss.android.data.subtitle.SubtitleIndex

/**
 * 字幕角标（S5）：只标「有」，不标「无」。
 *
 * - 外挂 / 本地 / 在线 → 「中」
 * - 内嵌且命中偏好   → 「中·嵌」
 * - 内嵌但语言未知   → 「中?」
 * - 已声明无需字幕   → 虚线空圈
 */
@Composable
fun SubtitleBadge(
    code: String?,
    modifier: Modifier = Modifier,
    /** 小尺寸卡片（如「继续观看」）用 compact 样式 */
    dense: Boolean = false,
) {
    val states by SubtitleIndex.states.collectAsState()
    val badge = SubtitleIndex.badge(code)
    // 订阅 states 以触发重组（badge() 读的是同一份内存表）
    @Suppress("UNUSED_EXPRESSION") states

    val b = badge ?: return
    val scheme = MaterialTheme.colorScheme
    when (b.type) {
        SubtitleIndex.BadgeType.ONLINE -> BadgeCapsule(
            text = b.text,
            background = Color.Black.copy(alpha = 0.62f),
            contentColor = Color(0xFF7CFFB2),
            modifier = modifier,
            dense = dense,
        )
        SubtitleIndex.BadgeType.LOCAL -> BadgeCapsule(
            text = b.text,
            background = Color.Black.copy(alpha = 0.62f),
            contentColor = Color(0xFF9BD1FF),
            modifier = modifier,
            dense = dense,
        )
        SubtitleIndex.BadgeType.EMBEDDED -> BadgeCapsule(
            text = b.text,
            background = scheme.primary.copy(alpha = 0.82f),
            contentColor = Color.White,
            modifier = modifier,
            dense = dense,
        )
        SubtitleIndex.BadgeType.UNKNOWN -> BadgeCapsule(
            text = b.text,
            background = Color.Black.copy(alpha = 0.42f),
            contentColor = Color.White.copy(alpha = 0.72f),
            modifier = modifier,
            dense = dense,
        )
        SubtitleIndex.BadgeType.NO_NEED -> Box(
            modifier
                .padding(2.dp)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                "无",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun BadgeCapsule(
    text: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    Box(
        modifier
            .background(background, RoundedCornerShape(999.dp))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .padding(
                horizontal = if (dense) 7.dp else 12.dp,
                vertical = if (dense) 2.dp else 5.dp,
            ),
    ) {
        Text(
            text,
            style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = contentColor,
        )
    }
}
