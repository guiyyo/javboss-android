package com.javboss.android.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "javboss_settings")

/**
 * 设备本地设置（Client 模式语义：播放器与界面偏好只存本地，不写入服务器 config）。
 */
class SettingsStore(private val context: Context) {

    /** 播放相关的高频写入走独立存储（避免播放时反复触发 uiPrefs 重组） */
    private val playback = PlaybackStore(context)

    object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val SESSION_COOKIE = stringPreferencesKey("session_cookie")
        val REMEMBER_PASSWORD = booleanPreferencesKey("remember_password")
        val PLAYER_PREFER_DIRECT = booleanPreferencesKey("player_prefer_direct")
        val PLAYER_RESUME = booleanPreferencesKey("player_resume")
        val PLAYER_VOLUME = stringPreferencesKey("player_volume")
        val AUTO_UPLOAD_SCREENSHOTS = booleanPreferencesKey("auto_upload_screenshots")
        val GRID_COLUMNS = stringPreferencesKey("grid_columns")
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { it[Keys.SERVER_URL] ?: DEFAULT_SERVER_URL }

    val sessionCookie: Flow<String?> = context.dataStore.data.map { it[Keys.SESSION_COOKIE] }

    val preferDirect: Flow<Boolean> = context.dataStore.data.map { it[Keys.PLAYER_PREFER_DIRECT] ?: true }

    val resumePlayback: Flow<Boolean> = context.dataStore.data.map { it[Keys.PLAYER_RESUME] ?: true }

    val autoUploadScreenshots: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_UPLOAD_SCREENSHOTS] ?: false }

    suspend fun currentServerUrl(): String = serverUrl.first()

    suspend fun currentCookie(): String? = sessionCookie.first()

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = url }
    }

    suspend fun setSessionCookie(cookie: String?) {
        context.dataStore.edit { prefs ->
            if (cookie.isNullOrBlank()) prefs.remove(Keys.SESSION_COOKIE) else prefs[Keys.SESSION_COOKIE] = cookie
        }
    }

    suspend fun setPreferDirect(value: Boolean) {
        context.dataStore.edit { it[Keys.PLAYER_PREFER_DIRECT] = value }
    }

    suspend fun setResumePlayback(value: Boolean) {
        context.dataStore.edit { it[Keys.PLAYER_RESUME] = value }
    }

    suspend fun setAutoUploadScreenshots(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_UPLOAD_SCREENSHOTS] = value }
    }

    /* ---------------- 本地界面偏好（只影响 App） ---------------- */

    val uiPrefs: Flow<UiPrefs> = context.dataStore.data.map { p ->
        UiPrefs(
            initialTab = p[UiPrefsKeys.INITIAL_TAB] ?: "jav",
            javSort = p[UiPrefsKeys.JAV_SORT] ?: "recent",
            videoSort = p[UiPrefsKeys.VIDEO_SORT] ?: "recent",
            titleRows = p[UiPrefsKeys.TITLE_ROWS] ?: 2,
            tagRows = p[UiPrefsKeys.TAG_ROWS] ?: 2,
            hideTags = p[UiPrefsKeys.HIDE_TAGS] ?: false,
            hideIdols = p[UiPrefsKeys.HIDE_IDOLS] ?: false,
            hideStudio = p[UiPrefsKeys.HIDE_STUDIO] ?: false,
            hideSeries = p[UiPrefsKeys.HIDE_SERIES] ?: false,
            tagSimplified = p[UiPrefsKeys.TAG_SIMPLIFIED] ?: true,
            idolPreferChinese = p[UiPrefsKeys.IDOL_PREFER_CHINESE] ?: true,
            preferDirect = p[Keys.PLAYER_PREFER_DIRECT] ?: true,
            resumePlayback = p[Keys.PLAYER_RESUME] ?: true,
            autoUploadScreenshots = p[Keys.AUTO_UPLOAD_SCREENSHOTS] ?: false,
            playbackSpeed = p[UiPrefsKeys.PLAYBACK_SPEED]?.toFloatOrNull() ?: 1.0f,
            autoPictureInPicture = p[UiPrefsKeys.AUTO_PIP] ?: false,
            appLockEnabled = p[UiPrefsKeys.APP_LOCK] ?: false,
            privacyMode = p[UiPrefsKeys.PRIVACY_MODE] ?: true,
            uiMode = p[UiPrefsKeys.UI_MODE] ?: "auto",
            coverStyle = p[UiPrefsKeys.COVER_STYLE] ?: "",
            tvColumns = p[UiPrefsKeys.TV_COLUMNS]?.toIntOrNull() ?: 0,
            tvPinEnabled = p[UiPrefsKeys.TV_PIN_ENABLED] ?: false,
            tvPinHash = p[UiPrefsKeys.TV_PIN_HASH] ?: "",
            tvPinLength = p[UiPrefsKeys.TV_PIN_LENGTH] ?: 0,
            tvIdleLockMinutes = p[UiPrefsKeys.TV_IDLE_LOCK_MIN] ?: 0,
            subtitleFolder = p[UiPrefsKeys.SUBTITLE_FOLDER] ?: "",
            subtitleCatBase = p[UiPrefsKeys.SUBTITLE_CAT_BASE] ?: "https://subtitlecat.com",
            subtitleProxy = p[UiPrefsKeys.SUBTITLE_PROXY] ?: "",
            subtitleProxyHost = p[UiPrefsKeys.SUBTITLE_PROXY_HOST] ?: "",
            subtitleProxyPort = p[UiPrefsKeys.SUBTITLE_PROXY_PORT] ?: 8899,
            subtitleProxyHttps = p[UiPrefsKeys.SUBTITLE_PROXY_HTTPS] ?: false,
            netProxyHost = p[UiPrefsKeys.NET_PROXY_HOST] ?: "",
            netProxyPort = p[UiPrefsKeys.NET_PROXY_PORT] ?: 7890,
            netProxyType = p[UiPrefsKeys.NET_PROXY_TYPE] ?: "http",
            subtitleChineseOnly = p[UiPrefsKeys.SUBTITLE_CHINESE_ONLY] ?: true,
            autoCheckUpdate = p[UiPrefsKeys.AUTO_CHECK_UPDATE] ?: true,
            updateMirror = p[UiPrefsKeys.UPDATE_MIRROR] ?: "",
            updateMirrorLastTest = p[UiPrefsKeys.UPDATE_MIRROR_LAST_TEST] ?: 0L,
            playerSurfaceView = p[UiPrefsKeys.PLAYER_SURFACE_VIEW] ?: false,
        )
    }

    suspend fun setPlaybackSpeed(value: Float) =
        context.dataStore.edit { it[UiPrefsKeys.PLAYBACK_SPEED] = value.toString() }

    suspend fun setAutoPictureInPicture(value: Boolean) =
        context.dataStore.edit { it[UiPrefsKeys.AUTO_PIP] = value }

    suspend fun setAppLockEnabled(value: Boolean) =
        context.dataStore.edit { it[UiPrefsKeys.APP_LOCK] = value }

    suspend fun setPrivacyMode(value: Boolean) =
        context.dataStore.edit { it[UiPrefsKeys.PRIVACY_MODE] = value }

    suspend fun setUiMode(value: String) =
        context.dataStore.edit { it[UiPrefsKeys.UI_MODE] = value }

    suspend fun setCoverStyle(value: String) =
        context.dataStore.edit { it[UiPrefsKeys.COVER_STYLE] = value }

    suspend fun setTvColumns(value: Int) =
        context.dataStore.edit { it[UiPrefsKeys.TV_COLUMNS] = value.toString() }

    suspend fun setSubtitleFolder(value: String) =
        context.dataStore.edit { it[UiPrefsKeys.SUBTITLE_FOLDER] = value }

    suspend fun setPlayerSurfaceView(value: Boolean) =
        context.dataStore.edit { it[UiPrefsKeys.PLAYER_SURFACE_VIEW] = value }

    suspend fun setUpdateMirrorLastTest(value: Long) =
        context.dataStore.edit { it[UiPrefsKeys.UPDATE_MIRROR_LAST_TEST] = value }

    suspend fun setUpdateMirror(value: String) =
        context.dataStore.edit { it[UiPrefsKeys.UPDATE_MIRROR] = value }

    suspend fun setAutoCheckUpdate(value: Boolean) =
        context.dataStore.edit { it[UiPrefsKeys.AUTO_CHECK_UPDATE] = value }

    suspend fun updateSkippedVersion(): String? =
        context.dataStore.data.first()[UiPrefsKeys.UPDATE_SKIPPED_VERSION]

    suspend fun setUpdateSkippedVersion(value: String) =
        context.dataStore.edit { it[UiPrefsKeys.UPDATE_SKIPPED_VERSION] = value }

    suspend fun setSubtitleChineseOnly(value: Boolean) =
        context.dataStore.edit { it[UiPrefsKeys.SUBTITLE_CHINESE_ONLY] = value }

    suspend fun setSubtitleProxy(value: String) =
        context.dataStore.edit { it[UiPrefsKeys.SUBTITLE_PROXY] = value }

    suspend fun setSubtitleProxyHost(value: String) =
        context.dataStore.edit { it[UiPrefsKeys.SUBTITLE_PROXY_HOST] = value }

    suspend fun setSubtitleProxyPort(value: Int) =
        context.dataStore.edit { it[UiPrefsKeys.SUBTITLE_PROXY_PORT] = value }

    suspend fun setSubtitleProxyHttps(value: Boolean) =
        context.dataStore.edit { it[UiPrefsKeys.SUBTITLE_PROXY_HTTPS] = value }

    suspend fun setNetProxyHost(value: String) =
        context.dataStore.edit { it[UiPrefsKeys.NET_PROXY_HOST] = value }

    suspend fun setNetProxyPort(value: Int) =
        context.dataStore.edit { it[UiPrefsKeys.NET_PROXY_PORT] = value }

    suspend fun setNetProxyType(value: String) =
        context.dataStore.edit { it[UiPrefsKeys.NET_PROXY_TYPE] = value }

    /** 网络代理地址（host:port），未配置返回 null */
    suspend fun netProxy(): java.net.Proxy? {
        val prefs = uiPrefs.first()
        val host = prefs.netProxyHost.trim()
        if (host.isBlank()) return null
        val port = prefs.netProxyPort.takeIf { it in 1..65535 } ?: 7890
        val type = if (prefs.netProxyType.equals("socks", true)) java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP
        return runCatching { java.net.Proxy(type, java.net.InetSocketAddress(host, port)) }.getOrNull()
    }

    /**
     * 实际使用的代理前缀：
     *  - 填了 Host → 自动拼 `http://host:port/?u={url}`
     *  - 否则用高级字段 subtitleProxy（完整前缀 / {url} 模板），都为空则直连
     */
    suspend fun subtitleProxyUrl(): String {
        val prefs = uiPrefs.first()
        val raw = prefs.subtitleProxyHost.trim()
        if (raw.isNotBlank()) {
            // 支持 IP、域名，也支持用户直接粘完整地址（带 https:// 或已带端口）
            val scheme = when {
                raw.startsWith("https://", true) -> "https"
                raw.startsWith("http://", true) -> "http"
                prefs.subtitleProxyHttps -> "https"
                else -> "http"
            }
            var h = raw.removePrefix("https://").removePrefix("http://")
                .removePrefix("HTTPS://").removePrefix("HTTP://")
                .trim()
                .substringBefore('/')          // 丢掉可能带的路径
            h = h.trimEnd('/')
            val port = prefs.subtitleProxyPort.takeIf { it in 1..65535 } ?: 8899
            val hostPort = if (h.contains(':')) h else "$h:$port"
            return "$scheme://$hostPort/?u={url}"
        }
        return prefs.subtitleProxy.trim()
    }

    suspend fun setSubtitleCatBase(value: String) =
        context.dataStore.edit { it[UiPrefsKeys.SUBTITLE_CAT_BASE] = value }

    /* ---------------- 每部作品的字幕选择（按番号记忆） ---------------- */


    suspend fun subtitleSelection(code: String): String? = playback.subtitleSelection(code)

    suspend fun subtitleOffset(code: String): Long = playback.subtitleOffset(code)

    suspend fun setSubtitleSelection(code: String, name: String, offsetMs: Long) =
        playback.setSubtitleSelection(code, name, offsetMs)

    suspend fun setTvIdleLockMinutes(value: Int) =
        context.dataStore.edit { it[UiPrefsKeys.TV_IDLE_LOCK_MIN] = value }

    /** 本片是否标记为「无需字幕」（硬字幕/自带） */

    suspend fun subtitleSkipped(code: String): Boolean = playback.subtitleSkipped(code)

    suspend fun setSubtitleSkipped(code: String, value: Boolean) =
        playback.setSubtitleSkipped(code, value)

    suspend fun setTvPinEnabled(value: Boolean) =
        context.dataStore.edit { it[UiPrefsKeys.TV_PIN_ENABLED] = value }

    /** 设置/修改 TV 密码（入参是明文 4-6 位数字，内部存 SHA-256）。 */
    suspend fun setTvPin(pin: String) {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
        context.dataStore.edit {
            it[UiPrefsKeys.TV_PIN_HASH] = hash
                it[UiPrefsKeys.TV_PIN_LENGTH] = pin.length
            it[UiPrefsKeys.TV_PIN_ENABLED] = true
        }
    }

    suspend fun verifyTvPin(pin: String): Boolean {
        val expected = uiPrefs.first().tvPinHash
        if (expected.isBlank()) return true
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return hash == expected
    }

    /** 上次列表的筛选/搜索快照（JSON），用于「下次打开回到原处」。 */
    val javQuerySnapshot: Flow<String?> = context.dataStore.data.map { it[UiPrefsKeys.JAV_QUERY_SNAPSHOT] }

    /** 本地观看记录（存放在独立 playback store，写入不再触发 uiPrefs 发射）。 */
    val watchHistory: Flow<Map<String, com.javboss.android.data.history.WatchEntry>> =
        playback.watchHistory

    private fun String?.toWatchMap(): Map<String, com.javboss.android.data.history.WatchEntry> {
        if (this.isNullOrBlank()) return emptyMap()
        return runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(
                    com.javboss.android.data.history.WatchHistorySnapshot.serializer(),
                    this,
                ).entries
        }.getOrDefault(emptyMap())
    }

    suspend fun recordWatch(key: String, entry: com.javboss.android.data.history.WatchEntry) =
        playback.recordWatch(key, entry)

    suspend fun watchEntry(key: String): com.javboss.android.data.history.WatchEntry? =
        playback.watchEntry(key)

    suspend fun setJavQuerySnapshot(json: String?) {
        context.dataStore.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(UiPrefsKeys.JAV_QUERY_SNAPSHOT) else prefs[UiPrefsKeys.JAV_QUERY_SNAPSHOT] = json
        }
    }



    suspend fun setInitialTab(value: String) = context.dataStore.edit { it[UiPrefsKeys.INITIAL_TAB] = value }
    suspend fun setJavSort(value: String) = context.dataStore.edit { it[UiPrefsKeys.JAV_SORT] = value }
    suspend fun setVideoSort(value: String) = context.dataStore.edit { it[UiPrefsKeys.VIDEO_SORT] = value }
    suspend fun setTitleRows(value: Int) = context.dataStore.edit { it[UiPrefsKeys.TITLE_ROWS] = value }
    suspend fun setTagRows(value: Int) = context.dataStore.edit { it[UiPrefsKeys.TAG_ROWS] = value }
    suspend fun setHideTags(value: Boolean) = context.dataStore.edit { it[UiPrefsKeys.HIDE_TAGS] = value }
    suspend fun setHideIdols(value: Boolean) = context.dataStore.edit { it[UiPrefsKeys.HIDE_IDOLS] = value }
    suspend fun setHideStudio(value: Boolean) = context.dataStore.edit { it[UiPrefsKeys.HIDE_STUDIO] = value }
    suspend fun setHideSeries(value: Boolean) = context.dataStore.edit { it[UiPrefsKeys.HIDE_SERIES] = value }
    suspend fun setTagSimplified(value: Boolean) = context.dataStore.edit { it[UiPrefsKeys.TAG_SIMPLIFIED] = value }
    suspend fun setIdolPreferChinese(value: Boolean) =
        context.dataStore.edit { it[UiPrefsKeys.IDOL_PREFER_CHINESE] = value }

    companion object {
        const val DEFAULT_SERVER_URL = "http://192.168.1.1:8655"
    }
}
