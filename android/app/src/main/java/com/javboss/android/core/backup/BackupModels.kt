package com.javboss.android.core.backup

import com.javboss.android.data.history.WatchEntry
import com.javboss.android.data.settings.UiPrefs
import kotlinx.serialization.Serializable

/** 单部作品的字幕选择 / 偏移 / 「本片无需字幕」标记（key 是去掉连字符后的大写番号，与 PlaybackStore 一致）。 */
@Serializable
data class SubtitlePrefEntry(
    val name: String = "",
    val offsetMs: Long = 0,
    val skipped: Boolean = false,
)

/**
 * 备份文件（A3）。
 *
 * 设计原则：
 *  - 只备本机数据，不备服务器会话 Cookie（换服务器也不该带走凭据）；
 *  - 字幕本体不备份（体积大且可重新下载），只备份「索引 + 每部选的是哪条/偏移/免字幕」；
 *  - 版本化：新增字段一律带默认值，导入时 ignoreUnknownKeys，旧备份仍可读。
 */
@Serializable
data class BackupFile(
    /** 必填（无默认值）：旧版这里带默认值，导致「随便一个 JSON」也能通过格式校验。 */
    val format: String,
    val version: Int = VERSION,
    val createdAt: Long = 0L,
    val appVersion: String = "",
    /** 服务端地址（换机后能直接对上；空则保留本机现有地址） */
    val serverUrl: String = "",
    val uiPrefs: UiPrefs = UiPrefs(),
    val watchHistory: Map<String, WatchEntry> = emptyMap(),
    val subtitlePrefs: Map<String, SubtitlePrefEntry> = emptyMap(),
    /** 字幕状态索引（原样嵌套 SubtitleIndex 的 JSON，导入时按 updatedAt 合并） */
    val subtitleIndexJson: String = "",
    val searchHistory: List<String> = emptyList(),
) {
    companion object {
        const val FORMAT = "javboss-backup"
        const val VERSION = 1
        /** 导入上限：文本 8MB、观看记录/字幕偏好各 20000 条、搜索历史 50 条。 */
        const val MAX_TEXT_BYTES = 8 * 1024 * 1024
        const val MAX_ENTRIES = 20_000
        const val MAX_SEARCH_HISTORY = 50
    }
}

/** 导入结果（用于给用户一句话汇总）。 */
data class BackupImportResult(
    val watchUpdated: Int = 0,
    val subtitlePrefs: Int = 0,
    val subtitleIndex: Int = 0,
    val searchHistory: Int = 0,
    val settingsApplied: Boolean = false,
    val serverUrlApplied: Boolean = false,
) {
    fun summary(): String = buildString {
        append(if (settingsApplied) "已恢复设置" else "设置无变化")
        append("、观看记录 ").append(watchUpdated).append(" 条")
        append("、字幕选择 ").append(subtitlePrefs).append(" 条")
        append("、字幕索引 ").append(subtitleIndex).append(" 条")
        if (searchHistory > 0) append("、搜索历史 ").append(searchHistory).append(" 条")
        if (serverUrlApplied) append("、服务端地址")
    }
}
