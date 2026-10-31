package com.javboss.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ------------------------------------------------------------------ 通用 */

@Serializable
data class Page<T>(
    val items: List<T> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class StatusResponse(
    val status: String = "",
)

@Serializable
data class AuthStatus(
    val authenticated: Boolean = false,
)

/* ------------------------------------------------------------------ JAV 域 */

@Serializable
data class JavSampleImage(
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    @SerialName("detail_url") val detailUrl: String = "",
)

@Serializable
data class StudioRef(
    val id: Long = 0,
    val name: String = "",
)

@Serializable
data class SeriesRef(
    val id: Long = 0,
    val name: String = "",
    @SerialName("zh_name") val zhName: String = "",
    @SerialName("studio_id") val studioId: Long? = null,
)

@Serializable
data class JavTagCategoryRef(
    val id: Long = 0,
    val name: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class JavTagRef(
    val id: Long = 0,
    val name: String = "",
    @SerialName("simplified_name") val simplifiedName: String = "",
    @SerialName("is_user") val isUser: Boolean = false,
    val provider: Int = 0,
    @SerialName("category_id") val categoryId: Long? = null,
    val category: JavTagCategoryRef? = null,
)

@Serializable
data class JavTagCount(
    val id: Long = 0,
    val name: String = "",
    @SerialName("simplified_name") val simplifiedName: String = "",
    @SerialName("category_id") val categoryId: Long? = null,
    /** 服务端返回分类名（字符串），不是对象 */
    val category: String = "",
    val provider: Int = 0,
    val count: Long = 0,
)

@Serializable
data class IdolRef(
    val id: Long = 0,
    val name: String = "",
    val aliases: List<String> = emptyList(),
    @SerialName("roman_name") val romanName: String = "",
    @SerialName("japanese_name") val japaneseName: String = "",
    @SerialName("chinese_name") val chineseName: String = "",
    @SerialName("height_cm") val heightCm: Int? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    val bust: Int? = null,
    val waist: Int? = null,
    val hips: Int? = null,
    val cup: Int? = null,
    @SerialName("work_count") val workCount: Long = 0,
    @SerialName("cover_jav_id") val coverJavId: Long? = null,
    @SerialName("cover_code") val coverCode: String = "",
    @SerialName("cover_crop_left") val coverCropLeft: Double = 0.53,
    @SerialName("favorite_count") val favoriteCount: Long = 0,
)

@Serializable
data class DirectoryRef(
    val id: Long = 0,
    val path: String = "",
    val missing: Boolean = false,
    @SerialName("is_delete") val isDelete: Boolean = false,
    val enabled: Boolean = true,
    @SerialName("scanned_video_count") val scannedVideoCount: Long = 0,
    @SerialName("scraped_video_count") val scrapedVideoCount: Long = 0,
    @SerialName("auto_scan_enabled") val autoScanEnabled: Boolean = false,
    @SerialName("auto_scan_interval_minutes") val autoScanIntervalMinutes: Int = 0,
    @SerialName("is_scanning") val isScanning: Boolean = false,
    @SerialName("work_status") val workStatus: String = "",
    @SerialName("scan_elapsed_ms") val scanElapsedMs: Long = 0,
    @SerialName("scanned_file_count") val scannedFileCount: Long = 0,
    val lastScanSummary: LastScanSummary? = null,
)

@Serializable
data class LastScanSummary(
    @SerialName("files_seen") val filesSeen: Long = 0,
    val inserted: Long = 0,
    val updated: Long = 0,
    val removed: Long = 0,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("finished_at_unix_ms") val finishedAtUnixMs: Long = 0,
)

@Serializable
data class VideoLocationRef(
    val id: Long = 0,
    @SerialName("video_id") val videoId: Long = 0,
    @SerialName("directory_id") val directoryId: Long = 0,
    @SerialName("relative_path") val relativePath: String = "",
    val filename: String = "",
    @SerialName("jav_id") val javId: Long? = null,
    val directory: DirectoryRef? = null,
)

@Serializable
data class VideoRow(
    val id: Long = 0,
    @SerialName("location_id") val locationId: Long = 0,
    @SerialName("directory_id") val directoryId: Long = 0,
    val path: String = "",
    val filename: String = "",
    val size: Long = 0,
    @SerialName("modified_at") val modifiedAt: String = "",
    val fingerprint: String = "",
    @SerialName("duration_sec") val durationSec: Long = 0,
    @SerialName("play_count") val playCount: Long = 0,
    @SerialName("jav_scrape_override") val javScrapeOverride: String = "",
    @SerialName("cover_screenshot_name") val coverScreenshotName: String = "",
    @SerialName("watched_ms") val watchedMs: Long = 0,
    @SerialName("jav_id") val javId: Long? = null,
    val jav: JavItem? = null,
    val directory: DirectoryRef? = null,
    val locations: List<VideoLocationRef> = emptyList(),
    val tags: List<TagCount> = emptyList(),
    val hidden: Boolean = false,
)

@Serializable
data class JavItem(
    val id: Long = 0,
    val code: String = "",
    val title: String = "",
    @SerialName("studio_id") val studioId: Long? = null,
    val studio: StudioRef? = null,
    @SerialName("series_id") val seriesId: Long? = null,
    val series: SeriesRef? = null,
    @SerialName("release_unix") val releaseUnix: Long = 0,
    @SerialName("duration_min") val durationMin: Int = 0,
    @SerialName("is_uncensored") val isUncensored: Boolean? = null,
    @SerialName("zh_title") val zhTitle: String = "",
    @SerialName("favorite_rating") val favoriteRating: Double = 0.0,
    @SerialName("favorite_count") val favoriteCount: Long = 0,
    @SerialName("watched_ms") val watchedMs: Long = 0,
    @SerialName("sample_images") val sampleImages: List<JavSampleImage> = emptyList(),
    @SerialName("cover_crop_position") val coverCropPosition: Double = 1.0,
    val tags: List<JavTagRef> = emptyList(),
    val idols: List<IdolRef> = emptyList(),
    val videos: List<VideoRow> = emptyList(),
    @SerialName("fetched_at") val fetchedAt: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class JavPrefixSummary(
    val prefix: String = "",
    @SerialName("studio_id") val studioId: Long? = null,
    @SerialName("studio_name") val studioName: String = "",
    @SerialName("is_uncensored") val isUncensored: Boolean? = null,
    @SerialName("work_count") val workCount: Long = 0,
    @SerialName("sample_code") val sampleCode: String = "",
)

@Serializable
data class JavStudioSummary(
    val id: Long = 0,
    val name: String = "",
    val aliases: List<String> = emptyList(),
    @SerialName("work_count") val workCount: Long = 0,
    @SerialName("series_count") val seriesCount: Long = 0,
    @SerialName("favorite_count") val favoriteCount: Long = 0,
    @SerialName("sample_code") val sampleCode: String = "",
    val prefixes: List<String> = emptyList(),
)

@Serializable
data class JavSeriesSummary(
    val id: Long = 0,
    val name: String = "",
    @SerialName("zh_name") val zhName: String = "",
    @SerialName("studio_id") val studioId: Long? = null,
    @SerialName("studio_name") val studioName: String = "",
    @SerialName("work_count") val workCount: Long = 0,
    @SerialName("sample_code") val sampleCode: String = "",
    @SerialName("favorite_count") val favoriteCount: Long = 0,
)

/* ------------------------------------------------------------------ 视频标签 */

@Serializable
data class TagCount(
    val id: Long = 0,
    val name: String = "",
    @SerialName("category_id") val categoryId: Long? = null,
    val category: JavTagCategoryRef? = null,
    val count: Long = 0,
)

@Serializable
data class TagCategory(
    val id: Long = 0,
    val name: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
)

/* ------------------------------------------------------------------ 播放 */

@Serializable
data class PlaybackSource(
    val kind: String = "",
    val src: String = "",
    @SerialName("mime_type") val mimeType: String = "",
    val label: String = "",
)

@Serializable
data class PlaybackInfo(
    @SerialName("video_id") val videoId: Long = 0,
    @SerialName("location_id") val locationId: Long = 0,
    @SerialName("preferred_kind") val preferredKind: String = "hls",
    val sources: List<PlaybackSource> = emptyList(),
    @SerialName("video_codec") val videoCodec: String = "",
    @SerialName("audio_codec") val audioCodec: String = "",
)

@Serializable
data class PlaybackSession(
    @SerialName("session_id") val sessionId: String = "",
)

@Serializable
data class WatchedTimeEntry(
    val id: Long = 0,
    @SerialName("watched_ms") val watchedMs: Long = 0,
)

@Serializable
data class WatchedTimeSnapshot(
    val videos: List<WatchedTimeEntry> = emptyList(),
    val javs: List<WatchedTimeEntry> = emptyList(),
)

@Serializable
data class ScreenshotInfo(
    @SerialName("video_id") val videoId: Long = 0,
    val name: String = "",
    val url: String = "",
    val size: Long = 0,
    @SerialName("modified_at") val modifiedAt: String = "",
    @SerialName("is_cover") val isCover: Boolean = false,
)

/* ------------------------------------------------------------------ 收藏分组 */

@Serializable
data class FavoriteGroup(
    val id: Long = 0,
    @SerialName("entity_type") val entityType: String = "",
    val name: String = "",
    val count: Long = 0,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

/* ------------------------------------------------------------------ 下载 / 系统 */

@Serializable
data class DownloadJob(
    val id: Long = 0,
    val status: String = "",
    val name: String = "",
    val progress: Double = 0.0,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class ApiError(
    @SerialName("error_zh") val messageZh: String = "",
    @SerialName("error_en") val messageEn: String = "",
) {
    val display: String get() = messageZh.ifBlank { messageEn }
}

/* ------------------------------------------------------------------ 筛选与收藏 */

@Serializable
data class JavFilterOptions(
    val total: Long = 0,
    @SerialName("solo_count") val soloCount: Long = 0,
    val prefixes: List<JavPrefixSummary> = emptyList(),
    val idols: List<IdolRef> = emptyList(),
    val tags: List<JavTagCount> = emptyList(),
    val studios: List<JavStudioSummary> = emptyList(),
    val series: List<JavSeriesSummary> = emptyList(),
)

@Serializable
data class FavoriteSelection(
    @SerialName("selected_group_ids") val selectedGroupIds: List<Long> = emptyList(),
)

@Serializable
data class FavoriteGroupCountsResponse(
    val items: List<FavoriteGroup> = emptyList(),
)
/* ------------------------------------------------------------------ 服务器：下载 / 资源 */

@Serializable
data class DownloadJobItem(
    val id: Long = 0,
    @SerialName("directory_path") val directoryPath: String = "",
    val provider: String = "",
    @SerialName("info_hash") val infoHash: String = "",
    @SerialName("magnet_name") val magnetName: String = "",
    @SerialName("remote_folder") val remoteFolder: String = "",
    val status: String = "",
    @SerialName("bytes_total") val bytesTotal: Long = 0,
    @SerialName("bytes_downloaded") val bytesDownloaded: Long = 0,
    @SerialName("error_message") val errorMessage: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("completed_at") val completedAt: String? = null,
) {
    val progress: Float
        get() = if (bytesTotal > 0) (bytesDownloaded.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
}

@Serializable
data class DownloadCounts(
    val active: Long = 0,
    val completed: Long = 0,
    val failed: Long = 0,
)

@Serializable
data class DownloadsPage(
    val items: List<DownloadJobItem> = emptyList(),
    val total: Long = 0,
    val counts: DownloadCounts = DownloadCounts(),
)

@Serializable
data class ResourceMemory(
    @SerialName("total_bytes") val totalBytes: Long = 0,
    @SerialName("used_bytes") val usedBytes: Long = 0,
    @SerialName("used_percent") val usedPercent: Double = 0.0,
)

@Serializable
data class ResourceDisk(
    val path: String = "",
    @SerialName("total_bytes") val totalBytes: Long = 0,
    @SerialName("free_bytes") val freeBytes: Long = 0,
    @SerialName("used_percent") val usedPercent: Double = 0.0,
)

@Serializable
data class ResourceProcess(
    @SerialName("rss_bytes") val rssBytes: Long = 0,
    val goroutines: Long = 0,
    @SerialName("uptime_seconds") val uptimeSeconds: Long = 0,
)

@Serializable
data class ResourceSnapshot(
    val os: String = "",
    @SerialName("logical_cpus") val logicalCpus: Int = 0,
    val container: Boolean = false,
    @SerialName("system_cpu_percent") val systemCpuPercent: Double? = null,
    @SerialName("system_memory") val systemMemory: ResourceMemory? = null,
    @SerialName("data_disk") val dataDisk: ResourceDisk? = null,
    val process: ResourceProcess? = null,
    val unavailable: List<String> = emptyList(),
)

@Serializable
data class FfmpegStatus(
    val name: String = "",
    val installed: Boolean = false,
    val version: String = "",
    val path: String = "",
    val supported: Boolean = false,
)

@Serializable
data class ToolsResponse(
    val ffmpeg: FfmpegStatus? = null,
)
/* ------------------------------------------------------------------ 翻译 */

@Serializable
data class TranslationResult(
    val id: Long = 0,
    val title: String = "",
    @SerialName("zh_title") val zhTitle: String = "",
    val name: String = "",
    @SerialName("zh_name") val zhName: String = "",
)

@Serializable
data class TranslationBatchStatus(
    val stopping: Boolean = false,
    val scope: String = "",
    val running: Boolean = false,
    val total: Int = 0,
    val processed: Int = 0,
    val translated: Int = 0,
    val skipped: Int = 0,
    @SerialName("error_zh") val errorZh: String = "",
    @SerialName("error_en") val errorEn: String = "",
)
/* ------------------------------------------------------------------ 账号 */

@Serializable
data class ExtensionToken(
    val id: Long = 0,
    val name: String = "",
    val token: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("expires_at") val expiresAt: String = "",
    @SerialName("last_used_at") val lastUsedAt: String? = null,
)
/* ------------------------------------------------------------------ 数据源可用性 */

@Serializable
data class AvailabilityResult(
    val provider: Int = 0,
    val status: String = "",
    @SerialName("http_status") val httpStatus: Int? = null,
    @SerialName("elapsed_ms") val elapsedMs: Long = 0,
    @SerialName("checked_at") val checkedAt: String = "",
)

@Serializable
data class AvailabilityProviderItem(
    val id: Int = 0,
    val name: String = "",
    val domain: String = "",
    val sample: String = "",
    @SerialName("last_result") val lastResult: AvailabilityResult? = null,
)
/* ------------------------------------------------------------------ 下载设置 */

@Serializable
data class DownloaderSettings(
    @SerialName("download_directory") val downloadDirectory: String = "",
    @SerialName("local_concurrency") val localConcurrency: Int = 2,
    @SerialName("min_video_size_mb") val minVideoSizeMb: Long = 50,
    val address: String = "",
    @SerialName("remote_folder") val remoteFolder: String = "",
    @SerialName("token_configured") val tokenConfigured: Boolean = false,
)