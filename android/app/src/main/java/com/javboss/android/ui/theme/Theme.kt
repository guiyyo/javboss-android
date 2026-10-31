package com.javboss.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 现代化配色：
 *  - 深色为默认气质（媒体类应用主流），近黑背景 + 分层表面 + 珊瑚红点缀；
 *  - 浅色保持同一色相，避免两套观感割裂。
 */
private val Accent = Color(0xFFFF4D67)
private val AccentDim = Color(0xFFFF7A8C)
private val AccentContainerDark = Color(0xFF4A1620)

private val DarkColors = darkColorScheme(
    primary = AccentDim,
    onPrimary = Color(0xFF36060F),
    primaryContainer = AccentContainerDark,
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFF9AA4B2),
    onSecondary = Color(0xFF11151B),
    tertiary = Color(0xFF7BD0FF),
    background = Color(0xFF0B0C0F),
    onBackground = Color(0xFFECEDF1),
    surface = Color(0xFF14161B),
    onSurface = Color(0xFFECEDF1),
    surfaceVariant = Color(0xFF1E2127),
    onSurfaceVariant = Color(0xFFA7ADB9),
    outline = Color(0xFF2C3038),
    outlineVariant = Color(0xFF20242B),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1E5),
    onPrimaryContainer = Color(0xFF3E0010),
    secondary = Color(0xFF5A6472),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF12141A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12141A),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Color(0xFF5C6472),
    outline = Color(0xFFD6DAE1),
    outlineVariant = Color(0xFFE6E9EE),
)

@Composable
fun JavBossTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    // 页面自适应：按屏宽放大字号（手机 1.0 / 小平板 1.15 / 平板+电视 1.3 / 超大屏 1.45）
    // 若用户强制了「界面模式」（手机/平板/电视），字号也跟随该模式，避免布局与字号不匹配
    val widthDp = com.javboss.android.ui.common.LocalLayoutWidthOverride.current
        ?: androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val scale = when {
        widthDp >= 1280 -> 1.5f
        widthDp >= 960 -> 1.35f
        widthDp >= 840 -> 1.28f
        widthDp >= 600 -> 1.14f
        else -> 1f
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = AppShapes,
        typography = if (scale == 1f) AppTypography else AppTypography.scaleFonts(scale),
        content = content,
    )
}

/** 按比例放大整套排版（含行高），用于平板/电视的自适应。 */
private fun androidx.compose.material3.Typography.scaleFonts(factor: Float): androidx.compose.material3.Typography {
    fun androidx.compose.ui.text.TextStyle.s(): androidx.compose.ui.text.TextStyle =
        copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)
    return androidx.compose.material3.Typography(
        displayLarge = displayLarge.s(),
        displayMedium = displayMedium.s(),
        displaySmall = displaySmall.s(),
        headlineLarge = headlineLarge.s(),
        headlineMedium = headlineMedium.s(),
        headlineSmall = headlineSmall.s(),
        titleLarge = titleLarge.s(),
        titleMedium = titleMedium.s(),
        titleSmall = titleSmall.s(),
        bodyLarge = bodyLarge.s(),
        bodyMedium = bodyMedium.s(),
        bodySmall = bodySmall.s(),
        labelLarge = labelLarge.s(),
        labelMedium = labelMedium.s(),
        labelSmall = labelSmall.s(),
    )
}
