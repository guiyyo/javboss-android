package com.javboss.android.data.history

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** 单条本地观看记录（服务端没有时间戳/精确位置，用本地补齐）。 */
@Serializable
data class WatchEntry(
    /** 最后一次观看时间（epoch millis），用于「最近看的排最前」 */
    val at: Long = 0,
    /** 播放头最后位置（毫秒），用于精确续播 */
    val positionMs: Long = 0,
    /** 视频总时长（毫秒），用于判断是否已看完 */
    val durationMs: Long = 0,
    val title: String = "",
)

/** 序列化包装（避免 Map<K,V> 泛型序列化器的类型推断问题）。 */
@Serializable
data class WatchHistorySnapshot(
    val entries: Map<String, WatchEntry> = emptyMap(),
)

object WatchHistory {
    /** 按服务器地址隔离（不同服务器同名视频不互相污染）。 */
    fun key(baseUrl: String, videoId: Long, locationId: Long): String {
        val host = runCatching {
            MessageDigest.getInstance("SHA-256")
                .digest(baseUrl.trimEnd('/').toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(8)
        }.getOrDefault(baseUrl.trimEnd('/').hashCode().toString())
        return "$host|$videoId|$locationId"
    }
}