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
import kotlinx.coroutines.launch
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

    val serverUrl: Flow<String> = context.dataStore.data.map { it[Keys.SERVER_URL] ?: "" }

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

    /** 内存快照：后台收集时更新，供「必须在同步上下文里读设置」的地方使用（见 uiPrefsSnapshot）。 */
    @Volatile private var cachedUiPrefs: UiPrefs? = null
    @Volatile private var cachedServerUrl: String? = null

    private val warmScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    init {
        warmScope.launch { runCatching { uiPrefs.collect { cachedUiPrefs = it } } }
        warmScope.launch { runCatching { serverUrl.collect { cachedServerUrl = it } } }
    }

    /**
     * 同步读设置快照（不阻塞主线程）。
     *
     * 安全/性能审计 A：MainActivity/TvMainActivity 的 onCreate 以前用 runBlocking 读 DataStore，
     * 慢设备上冷启动首帧会卡住甚至 ANR。现在后台常驻收集，这里直接读内存；
     * 只有「进程刚起、快照还没热」时才回落到一次 runBlocking。
     */
    fun uiPrefsSnapshot(): UiPrefs = cachedUiPrefs ?: kotlinx.coroutines.runBlocking { uiPrefs.first() }

    /** 同步读服务端地址（同上）。 */
    fun currentServerUrlCached(): String =
        cachedServerUrl ?: kotlinx.coroutines.runBlocking { currentServerUrl() }

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
            tvPinLockUntil = p[UiPrefsKeys.TV_PIN_LOCK_UNTIL] ?: 0L,
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
            subtitleLangOrder = p[UiPrefsKeys.SUBTITLE_LANG_ORDER] ?: "zh-Hans,zh-Hant,zh",
            autoCheckUpdate = p[UiPrefsKeys.AUTO_CHECK_UPDATE] ?: true,
            updateMirror = com.javboss.android.core.update.UpdateSecurity
                .sanitizeMirror(p[UiPrefsKeys.UPDATE_MIRROR] ?: ""),
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

    /** R2：存储层也做校验 —— 非 https / 裸域名一律存成空串（= GitHub 直连），避免配置里留非法值。 */
    suspend fun setUpdateMirror(value: String) =
        context.dataStore.edit {
            it[UiPrefsKeys.UPDATE_MIRROR] =
                com.javboss.android.core.update.UpdateSecurity.sanitizeMirror(value)
        }

    suspend fun setAutoCheckUpdate(value: Boolean) =
        context.dataStore.edit { it[UiPrefsKeys.AUTO_CHECK_UPDATE] = value }

    suspend fun updateSkippedVersion(): String? =
        context.dataStore.data.first()[UiPrefsKeys.UPDATE_SKIPPED_VERSION]

    suspend fun setUpdateSkippedVersion(value: String) =
        context.dataStore.edit { it[UiPrefsKeys.UPDATE_SKIPPED_VERSION] = value }

    suspend fun setSubtitleLangOrder(value: String) =
        context.dataStore.edit { it[UiPrefsKeys.SUBTITLE_LANG_ORDER] = value }

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

    suspend fun setSubtitleSkipped(code: String, value: Boolean) {
        playback.setSubtitleSkipped(code, value)
        runCatching {
            com.javboss.android.data.subtitle.SubtitleIndex.setNoNeed(code, value)
        }
    }

    suspend fun setTvPinEnabled(value: Boolean) =
        context.dataStore.edit { it[UiPrefsKeys.TV_PIN_ENABLED] = value }

    /**
     * 设置/修改 TV 密码（明文 4~6 位数字）。
     *
     * F2：随机盐 + 6000 轮迭代 SHA-256（以前是裸 SHA-256）。
     * 注意：0.36.13 之前设置密码的对话框只调了 setTvPinEnabled(true)，从没调过本方法，
     * 所以存量用户「开着锁但没有密码」，升级后需要重新设置一次。
     */
    suspend fun setTvPin(pin: String) {
        val salt = com.javboss.android.core.lock.PinHasher.randomSalt()
        val iterations = com.javboss.android.core.lock.PinHasher.ITERATIONS
        val hash = com.javboss.android.core.lock.PinHasher.hash(pin, salt, iterations)
        context.dataStore.edit {
            it[UiPrefsKeys.TV_PIN_HASH] = hash
            it[UiPrefsKeys.TV_PIN_SALT] = salt
            it[UiPrefsKeys.TV_PIN_ITER] = iterations
            it[UiPrefsKeys.TV_PIN_LENGTH] = pin.length
            it[UiPrefsKeys.TV_PIN_ENABLED] = true
            it[UiPrefsKeys.TV_PIN_FAIL] = 0
            it[UiPrefsKeys.TV_PIN_LOCK_UNTIL] = 0L
        }
    }

    /** 关闭密码锁并清掉旧哈希（避免下次开启时残留一个谁都不知道的密码）。 */
    suspend fun clearTvPin() {
        context.dataStore.edit {
            it[UiPrefsKeys.TV_PIN_ENABLED] = false
            it.remove(UiPrefsKeys.TV_PIN_HASH)
            it.remove(UiPrefsKeys.TV_PIN_SALT)
            it.remove(UiPrefsKeys.TV_PIN_ITER)
            it.remove(UiPrefsKeys.TV_PIN_LENGTH)
            it.remove(UiPrefsKeys.TV_PIN_FAIL)
            it.remove(UiPrefsKeys.TV_PIN_LOCK_UNTIL)
        }
    }

    /**
     * 校验 TV 密码。失败次数与锁定截止时间**落盘**（以前只存在页面内存里，退出重进就归零）。
     * 退避：5 次 → 30 秒，10 次 → 1 分钟，15 次 → 5 分钟。
     */
    suspend fun verifyTvPin(pin: String): Boolean {
        val p = context.dataStore.data.first()
        val expected = p[UiPrefsKeys.TV_PIN_HASH] ?: ""
        if (expected.isBlank()) return true
        val now = System.currentTimeMillis()
        if ((p[UiPrefsKeys.TV_PIN_LOCK_UNTIL] ?: 0L) > now) return false
        val salt = p[UiPrefsKeys.TV_PIN_SALT] ?: ""
        val iterations = p[UiPrefsKeys.TV_PIN_ITER] ?: com.javboss.android.core.lock.PinHasher.ITERATIONS
        val ok = if (salt.isBlank()) {
            com.javboss.android.core.lock.PinHasher.verifyLegacy(pin, expected)
        } else {
            com.javboss.android.core.lock.PinHasher.verify(pin, salt, iterations, expected)
        }
        context.dataStore.edit { prefs ->
            if (ok) {
                prefs[UiPrefsKeys.TV_PIN_FAIL] = 0
                prefs[UiPrefsKeys.TV_PIN_LOCK_UNTIL] = 0L
                if (salt.isBlank()) {
                    // 老的无盐哈希：校验通过时顺手换成加盐值，用户无感
                    val newSalt = com.javboss.android.core.lock.PinHasher.randomSalt()
                    prefs[UiPrefsKeys.TV_PIN_SALT] = newSalt
                    prefs[UiPrefsKeys.TV_PIN_ITER] = com.javboss.android.core.lock.PinHasher.ITERATIONS
                    prefs[UiPrefsKeys.TV_PIN_HASH] = com.javboss.android.core.lock.PinHasher.hash(
                        pin,
                        newSalt,
                        com.javboss.android.core.lock.PinHasher.ITERATIONS,
                    )
                }
            } else {
                val fails = (prefs[UiPrefsKeys.TV_PIN_FAIL] ?: 0) + 1
                prefs[UiPrefsKeys.TV_PIN_FAIL] = fails
                val backoff = when {
                    fails >= 15 -> 300_000L
                    fails >= 10 -> 60_000L
                    fails >= 5 -> 30_000L
                    else -> 0L
                }
                if (backoff > 0) prefs[UiPrefsKeys.TV_PIN_LOCK_UNTIL] = now + backoff
                if (fails > 15) prefs[UiPrefsKeys.TV_PIN_FAIL] = 15
            }
        }
        return ok
    }

    /** 剩余锁定毫秒（0 = 未锁定）。 */
    suspend fun tvPinLockRemainingMs(): Long {
        val p = context.dataStore.data.first()
        val until = p[UiPrefsKeys.TV_PIN_LOCK_UNTIL] ?: 0L
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** 已失败次数（用于提示「还可尝试 N 次」）。 */
    suspend fun tvPinFailCount(): Int = context.dataStore.data.first()[UiPrefsKeys.TV_PIN_FAIL] ?: 0

    /* ---------- 搜索历史（F1 快赢包：搜索番号归一化 + 历史） ---------- */

    val searchHistory: kotlinx.coroutines.flow.Flow<List<String>> = context.dataStore.data.map { p ->
        (p[UiPrefsKeys.SEARCH_HISTORY] ?: "")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** 记一条搜索历史：去重（忽略大小写）、最近在前、最多 12 条。 */
    suspend fun addSearchHistory(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        context.dataStore.edit { prefs ->
            val old = (prefs[UiPrefsKeys.SEARCH_HISTORY] ?: "")
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val next = (listOf(q) + old.filter { !it.equals(q, ignoreCase = true) }).take(12)
            prefs[UiPrefsKeys.SEARCH_HISTORY] = next.joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it.remove(UiPrefsKeys.SEARCH_HISTORY) }
    }

    /** 备份恢复用：整份写回搜索历史。 */
    suspend fun setSearchHistoryList(list: List<String>) {
        context.dataStore.edit { prefs ->
            val cleaned = list.map { it.trim() }.filter { it.isNotEmpty() }.take(12)
            if (cleaned.isEmpty()) prefs.remove(UiPrefsKeys.SEARCH_HISTORY)
            else prefs[UiPrefsKeys.SEARCH_HISTORY] = cleaned.joinToString("\n")
        }
    }

    /**
     * 备份恢复（A3）：整份写回 UiPrefs。
     *
     * **故意不写 TV 密码相关字段**（tvPinEnabled / tvPinHash / tvPinLength / tvPinLockUntil）：
     * 把别人的密码哈希带到这台机器上，只会造成「开着锁但不知道密码」。
     */
    suspend fun applyUiPrefs(p: UiPrefs) {
        context.dataStore.edit { prefs ->
            prefs[UiPrefsKeys.INITIAL_TAB] = p.initialTab
            prefs[UiPrefsKeys.JAV_SORT] = p.javSort
            prefs[UiPrefsKeys.VIDEO_SORT] = p.videoSort
            prefs[UiPrefsKeys.TITLE_ROWS] = p.titleRows
            prefs[UiPrefsKeys.TAG_ROWS] = p.tagRows
            prefs[UiPrefsKeys.HIDE_TAGS] = p.hideTags
            prefs[UiPrefsKeys.HIDE_IDOLS] = p.hideIdols
            prefs[UiPrefsKeys.HIDE_STUDIO] = p.hideStudio
            prefs[UiPrefsKeys.HIDE_SERIES] = p.hideSeries
            prefs[UiPrefsKeys.TAG_SIMPLIFIED] = p.tagSimplified
            prefs[UiPrefsKeys.IDOL_PREFER_CHINESE] = p.idolPreferChinese
            prefs[Keys.PLAYER_PREFER_DIRECT] = p.preferDirect
            prefs[Keys.PLAYER_RESUME] = p.resumePlayback
            prefs[Keys.AUTO_UPLOAD_SCREENSHOTS] = p.autoUploadScreenshots
            prefs[UiPrefsKeys.PLAYBACK_SPEED] = p.playbackSpeed.toString()
            prefs[UiPrefsKeys.AUTO_PIP] = p.autoPictureInPicture
            prefs[UiPrefsKeys.APP_LOCK] = p.appLockEnabled
            prefs[UiPrefsKeys.PRIVACY_MODE] = p.privacyMode
            prefs[UiPrefsKeys.UI_MODE] = p.uiMode
            prefs[UiPrefsKeys.COVER_STYLE] = p.coverStyle
            prefs[UiPrefsKeys.TV_COLUMNS] = p.tvColumns.toString()
            prefs[UiPrefsKeys.TV_IDLE_LOCK_MIN] = p.tvIdleLockMinutes
            prefs[UiPrefsKeys.SUBTITLE_FOLDER] = p.subtitleFolder
            prefs[UiPrefsKeys.SUBTITLE_CAT_BASE] = p.subtitleCatBase
            prefs[UiPrefsKeys.SUBTITLE_PROXY] = p.subtitleProxy
            prefs[UiPrefsKeys.SUBTITLE_PROXY_HOST] = p.subtitleProxyHost
            prefs[UiPrefsKeys.SUBTITLE_PROXY_PORT] = p.subtitleProxyPort
            prefs[UiPrefsKeys.SUBTITLE_PROXY_HTTPS] = p.subtitleProxyHttps
            prefs[UiPrefsKeys.NET_PROXY_HOST] = p.netProxyHost
            prefs[UiPrefsKeys.NET_PROXY_PORT] = p.netProxyPort
            prefs[UiPrefsKeys.NET_PROXY_TYPE] = p.netProxyType
            prefs[UiPrefsKeys.SUBTITLE_CHINESE_ONLY] = p.subtitleChineseOnly
            prefs[UiPrefsKeys.SUBTITLE_LANG_ORDER] = p.subtitleLangOrder
            prefs[UiPrefsKeys.AUTO_CHECK_UPDATE] = p.autoCheckUpdate
            prefs[UiPrefsKeys.UPDATE_MIRROR] =
                com.javboss.android.core.update.UpdateSecurity.sanitizeMirror(p.updateMirror)
            prefs[UiPrefsKeys.UPDATE_MIRROR_LAST_TEST] = p.updateMirrorLastTest
            prefs[UiPrefsKeys.PLAYER_SURFACE_VIEW] = p.playerSurfaceView
        }
    }

    /** 设置里 TV 密码锁状态（备份时置空，避免把别人机器的锁带过来）。 */
    suspend fun uiPrefsForBackup(): UiPrefs = uiPrefs.first().copy(
        tvPinEnabled = false,
        tvPinHash = "",
        tvPinLength = 0,
        tvPinLockUntil = 0L,
    )

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
        /** 仅用于界面示例提示，不作为默认值（本项目是自建服务端，地址因人而异） */
        const val EXAMPLE_SERVER_URL = "http://192.168.1.1:8655"
    }
}
