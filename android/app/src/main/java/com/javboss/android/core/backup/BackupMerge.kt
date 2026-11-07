package com.javboss.android.core.backup

import com.javboss.android.data.history.WatchEntry

/**
 * 备份合并规则（纯函数，便于单测）。
 *
 * - 观看记录：同一 key 取 at 较新者（本地刚看的不会被旧备份覆盖）；
 * - 字幕选择/偏移：导入方优先（用户主动恢复，期望看到备份时的样子）；
 * - 搜索历史：合并去重（忽略大小写），备份里的排前面，最多 12 条。
 */
object BackupMerge {

    fun mergeWatchHistory(
        local: Map<String, WatchEntry>,
        incoming: Map<String, WatchEntry>,
    ): Map<String, WatchEntry> {
        if (incoming.isEmpty()) return local
        val out = local.toMutableMap()
        for ((key, entry) in incoming) {
            val cur = out[key]
            if (cur == null || entry.at >= cur.at) out[key] = entry
        }
        return out
    }

    fun mergeSubtitlePrefs(
        local: Map<String, SubtitlePrefEntry>,
        incoming: Map<String, SubtitlePrefEntry>,
    ): Map<String, SubtitlePrefEntry> {
        if (incoming.isEmpty()) return local
        return local + incoming
    }

    fun mergeSearchHistory(
        local: List<String>,
        incoming: List<String>,
        max: Int = 12,
    ): List<String> {
        val out = ArrayList<String>(local.size + incoming.size)
        for (item in incoming + local) {
            val text = item.trim()
            if (text.isEmpty()) continue
            if (out.any { it.equals(text, ignoreCase = true) }) continue
            out.add(text)
        }
        return out.take(max)
    }
}
