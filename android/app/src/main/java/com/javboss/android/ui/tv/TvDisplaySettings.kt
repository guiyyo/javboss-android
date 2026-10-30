package com.javboss.android.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.javboss.android.data.model.JavItem
import com.javboss.android.di.AppGraph

/** 网页端「显示设置」在 TV 端的镜像（直接读服务端 config，改网页 TV 同步）。 */
data class TvDisplaySettings(
    val portrait: Boolean = false,
    val titleRows: Int = 2,
    val hideStudio: Boolean = false,
    val hideSeries: Boolean = false,
    val hideIdols: Boolean = false,
    val hideTags: Boolean = false,
    val gridColumns: Int = 0,
)

@Composable
fun rememberTvDisplaySettings(): TvDisplaySettings {
    val config by AppGraph.config.config.collectAsState()
    val prefs by AppGraph.settings.uiPrefs.collectAsState(
        initial = com.javboss.android.data.settings.UiPrefs(),
    )
    fun flag(key: String, default: Boolean = false): Boolean =
        (config[key] ?: "").let { if (it.isBlank()) default else it == "true" || it == "1" }

    // 封面样式：本地设置优先，否则跟随网页 jav_card_portrait
    val portrait = when (prefs.coverStyle) {
        "portrait" -> true
        "landscape" -> false
        else -> flag("jav_card_portrait", false)
    }
    val webColumns = (config["jav_grid_columns"] ?: "").toIntOrNull() ?: 0
    return TvDisplaySettings(
        portrait = portrait,
        titleRows = (config["jav_title_max_rows"] ?: "").toIntOrNull()?.coerceIn(1, 3) ?: 2,
        hideStudio = flag("jav_hide_studio"),
        hideSeries = flag("jav_hide_series"),
        hideIdols = flag("jav_hide_idols"),
        hideTags = flag("jav_hide_tags"),
        gridColumns = webColumns,
    )
}

/** 拼卡片的副标题：番号 · 片商 · 女优（按网页显示设置取舍）。 */
fun tvMetaOf(item: JavItem, s: TvDisplaySettings): String {
    val parts = mutableListOf<String>()
    if (item.code.isNotBlank()) parts += item.code
    if (!s.hideStudio) item.studio?.name?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (!s.hideSeries) item.series?.let { parts += it.zhName.ifBlank { it.name } }
    if (!s.hideIdols && item.idols.isNotEmpty()) {
        parts += item.idols.take(2).joinToString("、") { it.chineseName.ifBlank { it.name } }
    }
    return parts.joinToString(" · ")
}