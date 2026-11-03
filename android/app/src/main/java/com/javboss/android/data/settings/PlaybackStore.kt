package com.javboss.android.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.javboss.android.data.history.WatchEntry
import com.javboss.android.data.history.WatchHistorySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * 播放相关的「高频写入」独立存储。
 *
 * 为什么不放在主 [SettingsStore] 里：
 *  - 主 store 的 `uiPrefs` 被 AppRoot / TvApp / 各页面 collect，**任何一次写入都会让整棵树重组**；
 *  - 播放时每 10 秒写一次观看进度、调字幕偏移时也会写 → 造成播放期间周期性卡顿。
 *
 * 拆成独立 DataStore 后，这些写入不再触发 UI 偏好流的发射。
 */
private val Context.playbackStore by preferencesDataStore(name = "javboss_playback")

class PlaybackStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val WATCH_HISTORY = stringPreferencesKey("watch_history")

    private fun subSelKey(code: String) =
        stringPreferencesKey("sub_sel_" + code.filter { it.isLetterOrDigit() }.uppercase())

    private fun subOffKey(code: String) =
        longPreferencesKey("sub_off_" + code.filter { it.isLetterOrDigit() }.uppercase())

    private fun subSkipKey(code: String) =
        booleanPreferencesKey("sub_skip_" + code.filter { it.isLetterOrDigit() }.uppercase())

    /* ------------------------------------------------------- 观看记录 */

    val watchHistory: Flow<Map<String, WatchEntry>> =
        context.playbackStore.data.map { prefs -> decode(prefs[WATCH_HISTORY]) }

    suspend fun watchEntry(key: String): WatchEntry? = watchHistory.first()[key]

    suspend fun recordWatch(key: String, entry: WatchEntry) {
        context.playbackStore.edit { prefs ->
            val map = decode(prefs[WATCH_HISTORY]).toMutableMap()
            map[key] = entry
            val trimmed = if (map.size > 500) {
                map.entries.sortedByDescending { it.value.at }.take(500).associate { it.key to it.value }
            } else {
                map
            }
            prefs[WATCH_HISTORY] = json.encodeToString(
                WatchHistorySnapshot.serializer(),
                WatchHistorySnapshot(trimmed),
            )
        }
    }

    private fun decode(raw: String?): Map<String, WatchEntry> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString(WatchHistorySnapshot.serializer(), raw).entries
        }.getOrDefault(emptyMap())
    }

    /* --------------------------------------------------- 每部作品字幕 */

    suspend fun subtitleSelection(code: String): String? =
        context.playbackStore.data.first()[subSelKey(code)]?.takeIf { it.isNotBlank() }

    suspend fun subtitleOffset(code: String): Long =
        context.playbackStore.data.first()[subOffKey(code)] ?: 0L

    suspend fun setSubtitleSelection(code: String, name: String, offsetMs: Long) {
        context.playbackStore.edit {
            it[subSelKey(code)] = name
            it[subOffKey(code)] = offsetMs
        }
    }

    suspend fun subtitleSkipped(code: String): Boolean =
        context.playbackStore.data.first()[subSkipKey(code)] ?: false

    suspend fun setSubtitleSkipped(code: String, value: Boolean) {
        context.playbackStore.edit { it[subSkipKey(code)] = value }
    }
}
