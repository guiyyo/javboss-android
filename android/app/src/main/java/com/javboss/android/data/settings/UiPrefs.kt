package com.javboss.android.data.settings

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * App 本地界面偏好（**只影响 App，不写入服务器 config**）。
 */
data class UiPrefs(
    val initialTab: String = "jav",
    val javSort: String = "recent",
    val videoSort: String = "recent",
    val titleRows: Int = 2,
    val tagRows: Int = 2,
    val hideTags: Boolean = false,
    val hideIdols: Boolean = false,
    val hideStudio: Boolean = false,
    val hideSeries: Boolean = false,
    val tagSimplified: Boolean = true,
    val idolPreferChinese: Boolean = true,
    val preferDirect: Boolean = true,
    val resumePlayback: Boolean = true,
    val autoUploadScreenshots: Boolean = false,
    /** 播放倍速（本机记忆） */
    val playbackSpeed: Float = 1.0f,
    /** 返回桌面自动进入画中画 */
    val autoPictureInPicture: Boolean = false,
    /** 冷启动 / 设备锁屏后需要指纹解锁 */
    val appLockEnabled: Boolean = false,
    /** 隐私模式：禁止截屏与任务预览（FLAG_SECURE） */
    val privacyMode: Boolean = true,
    /** 界面模式：auto / phone / tablet / tv */
    val uiMode: String = "auto",
    /** 封面样式：landscape 横版（完整显示）/ portrait 竖版（裁切） */
    val coverStyle: String = "",   // ""=跟随网页 jav_card_portrait；landscape/portrait=本机覆盖
    /** TV 每行卡片数：0 = 按屏幕自动，其它为固定列数（默认 3） */
    val tvColumns: Int = 0,   // 0 = 按封面样式给默认（竖版 4 / 横版 3）
    /** TV 密码锁开关 */
    val tvPinEnabled: Boolean = false,
    /** TV 密码（SHA-256 十六进制），空表示未设置 */
    val tvPinHash: String = "",
    /** 已设置密码的位数（用于输满即自动解锁） */
    val tvPinLength: Int = 0,
    /** TV 无操作自动锁屏时间（分钟，0 = 关闭） */
    val tvIdleLockMinutes: Int = 0,
)

val LocalUiPrefs = staticCompositionLocalOf { UiPrefs() }

object UiPrefsKeys {
    val INITIAL_TAB = stringPreferencesKey("ui_initial_tab")
    val JAV_SORT = stringPreferencesKey("ui_jav_sort")
    val VIDEO_SORT = stringPreferencesKey("ui_video_sort")
    val TITLE_ROWS = intPreferencesKey("ui_title_rows")
    val TAG_ROWS = intPreferencesKey("ui_tag_rows")
    val HIDE_TAGS = booleanPreferencesKey("ui_hide_tags")
    val HIDE_IDOLS = booleanPreferencesKey("ui_hide_idols")
    val HIDE_STUDIO = booleanPreferencesKey("ui_hide_studio")
    val HIDE_SERIES = booleanPreferencesKey("ui_hide_series")
    val TAG_SIMPLIFIED = booleanPreferencesKey("ui_tag_simplified")
    val IDOL_PREFER_CHINESE = booleanPreferencesKey("ui_idol_prefer_chinese")
    val PLAYBACK_SPEED = stringPreferencesKey("ui_playback_speed")
    val AUTO_PIP = booleanPreferencesKey("ui_auto_pip")
    val APP_LOCK = booleanPreferencesKey("ui_app_lock")
    val PRIVACY_MODE = booleanPreferencesKey("ui_privacy_mode")
    val UI_MODE = stringPreferencesKey("ui_mode")
    val JAV_QUERY_SNAPSHOT = stringPreferencesKey("ui_jav_query_snapshot")
    val WATCH_HISTORY = stringPreferencesKey("ui_watch_history")
    val COVER_STYLE = stringPreferencesKey("ui_cover_style")
    val TV_COLUMNS = stringPreferencesKey("ui_tv_columns")
    val TV_PIN_ENABLED = booleanPreferencesKey("ui_tv_pin_enabled")
    val TV_PIN_HASH = stringPreferencesKey("ui_tv_pin_hash")
    val TV_PIN_LENGTH = intPreferencesKey("ui_tv_pin_length")
    val TV_IDLE_LOCK_MIN = intPreferencesKey("ui_tv_idle_lock_min")
}
