package com.javboss.android.data.backup

import com.javboss.android.core.backup.BackupFile
import com.javboss.android.core.backup.BackupImportResult
import com.javboss.android.core.backup.BackupMerge
import com.javboss.android.data.settings.PlaybackStore
import com.javboss.android.data.settings.SettingsStore
import com.javboss.android.data.subtitle.SubtitleIndex
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * 备份与恢复（A3）：把「设置 + 观看进度 + 字幕索引/选择 + 搜索历史」打成一个 JSON，
 * 换机/重装后导入即恢复。
 *
 * 明说不做的事：不备份会话 Cookie、不备份字幕文件本体、不备份服务端数据。
 */
class BackupManager(
    private val context: android.content.Context,
    private val settings: SettingsStore,
    private val playback: PlaybackStore,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun exportJson(): String {
        val prefs = settings.uiPrefsForBackup()
        val file = BackupFile(
            format = BackupFile.FORMAT,
            createdAt = System.currentTimeMillis(),
            appVersion = com.javboss.android.BuildConfig.VERSION_NAME,
            serverUrl = settings.currentServerUrl(),
            uiPrefs = prefs,
            watchHistory = playback.currentWatchHistory(),
            subtitlePrefs = playback.exportSubtitlePrefs(),
            subtitleIndexJson = SubtitleIndex.exportJson(),
            searchHistory = settings.searchHistory.first(),
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /**
     * 导入备份。
     * @param applySettings false = 只恢复数据（观看记录 / 字幕索引与选择 / 搜索历史），
     *        不动界面设置、服务端地址、更新镜像与代理 —— 安全审计要求用户能明确选择。
     */
    suspend fun importJson(text: String, applySettings: Boolean = true): BackupImportResult {
        validate(text)?.let { throw IllegalArgumentException(it) }
        val incoming = json.decodeFromString(BackupFile.serializer(), text)

        if (applySettings) settings.applyUiPrefs(incoming.uiPrefs)
        var serverApplied = false
        if (applySettings && incoming.serverUrl.isNotBlank() && incoming.serverUrl != settings.currentServerUrl()) {
            settings.setServerUrl(incoming.serverUrl)
            serverApplied = true
        }

        val localWatch = playback.currentWatchHistory()
        val mergedWatch = BackupMerge.mergeWatchHistory(localWatch, incoming.watchHistory)
        var watchUpdated = 0
        for ((key, entry) in mergedWatch) {
            if (localWatch[key] != entry) {
                playback.putWatchEntry(key, entry)
                watchUpdated++
            }
        }

        val localSubs = playback.exportSubtitlePrefs()
        val mergedSubs = BackupMerge.mergeSubtitlePrefs(localSubs, incoming.subtitlePrefs)
        var subPrefs = 0
        for ((key, entry) in mergedSubs) {
            if (localSubs[key] != entry) {
                playback.putSubtitlePref(key, entry)
                subPrefs++
            }
        }

        val indexUpdated = if (incoming.subtitleIndexJson.isBlank()) 0
        else SubtitleIndex.importJson(incoming.subtitleIndexJson)

        val localHistory = settings.searchHistory.first()
        val mergedHistory = BackupMerge.mergeSearchHistory(
            localHistory,
            incoming.searchHistory.take(BackupFile.MAX_SEARCH_HISTORY),
        )
        if (mergedHistory != localHistory) settings.setSearchHistoryList(mergedHistory)

        return BackupImportResult(
            watchUpdated = watchUpdated,
            subtitlePrefs = subPrefs,
            subtitleIndex = indexUpdated,
            searchHistory = (mergedHistory.size - localHistory.size).coerceAtLeast(0),
            settingsApplied = applySettings,
            serverUrlApplied = serverApplied,
        )
    }

    /**
     * 结构/体量校验：防止超大 JSON 或畸形数据把内存/磁盘打爆。
     * @return 错误文案；null 表示通过。
     */
    fun validate(text: String): String? {
        if (text.isBlank()) return "备份文件为空"
        if (text.toByteArray().size > BackupFile.MAX_TEXT_BYTES) return "备份文件过大（超过 8MB）"
        val parsed = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { return "备份文件解析失败：" + (it.message ?: "格式不正确") }
        if (parsed.format != BackupFile.FORMAT) return "这不是 JavBoss 备份文件"
        if (parsed.watchHistory.size > BackupFile.MAX_ENTRIES) return "备份里的观看记录过多（> 20000 条）"
        if (parsed.subtitlePrefs.size > BackupFile.MAX_ENTRIES) return "备份里的字幕偏好过多（> 20000 条）"
        if (parsed.subtitleIndexJson.length > BackupFile.MAX_TEXT_BYTES) return "备份里的字幕索引过大"
        return null
    }

    /** 给「导入前确认」弹窗用的一句话概览（不落盘）。 */
    fun describe(text: String): String {
        val incoming = Json { ignoreUnknownKeys = true }.decodeFromString(BackupFile.serializer(), text)
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .format(java.util.Date(incoming.createdAt))
        return buildString {
            append("备份时间：").append(stamp)
            if (incoming.appVersion.isNotBlank()) append("\n备份来自版本：v").append(incoming.appVersion)
            append("\n观看记录：").append(incoming.watchHistory.size).append(" 条")
            append("\n字幕选择：").append(incoming.subtitlePrefs.size).append(" 条")
            append("\n搜索历史：").append(incoming.searchHistory.size).append(" 条")
            append("\n\n【导入后会改动】")
            append("\n· 界面设置：按备份覆盖（含更新镜像、字幕参数等）")
            if (incoming.serverUrl.isNotBlank()) append("\n· 服务端地址 → ").append(incoming.serverUrl)
            if (incoming.uiPrefs.netProxyHost.isNotBlank()) {
                append("\n· 网络代理 → ").append(incoming.uiPrefs.netProxyType)
                    .append("://").append(incoming.uiPrefs.netProxyHost)
                    .append(":").append(incoming.uiPrefs.netProxyPort)
            }
            if (incoming.uiPrefs.updateMirror.isNotBlank()) {
                append("\n· 更新镜像 → ").append(incoming.uiPrefs.updateMirror)
            }
            append("\n\n规则：观看记录取较新；字幕索引同番号取较新。")
        }
    }
}
