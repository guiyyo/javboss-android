package com.javboss.android.data.repo

import com.javboss.android.core.net.ApiClient
import com.javboss.android.core.net.ApiResult
import com.javboss.android.core.net.apiCall
import com.javboss.android.data.model.Page
import com.javboss.android.data.model.PlaybackInfo
import com.javboss.android.data.model.ScreenshotInfo
import com.javboss.android.data.remote.ScreenshotListResponse
import com.javboss.android.data.model.VideoRow
import com.javboss.android.data.model.WatchedTimeSnapshot
import com.javboss.android.data.remote.CoverRequest
import com.javboss.android.data.remote.JavBossApi
import com.javboss.android.data.remote.PlaybackSessionRequest
import com.javboss.android.data.remote.ScreenshotCreateRequest
import com.javboss.android.data.remote.WatchedRequest
import com.javboss.android.data.settings.SettingsStore
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class VideoRepository(private val settings: SettingsStore) {

    private fun api(): JavBossApi =
        ApiClient.createApi(settings.currentServerUrlBlocking(), JavBossApi::class.java)

    suspend fun list(
        offset: Int,
        limit: Int = 30,
        search: String = "",
        sort: String = "recent",
        tags: List<String> = emptyList(),
        hideJav: Boolean = false,
    ): ApiResult<Page<VideoRow>> = apiCall {
        api().videos(
            limit = limit,
            offset = offset,
            sort = sort.takeIf { it.isNotBlank() },
            search = search.takeIf { it.isNotBlank() },
            tags = tags.takeIf { it.isNotEmpty() }?.joinToString(","),
            hideJav = if (hideJav) true else null,
        )
    }

    suspend fun detail(id: Long): ApiResult<VideoRow> = apiCall { api().video(id) }

    suspend fun streams(videoId: Long, locationId: Long? = null): ApiResult<PlaybackInfo> =
        apiCall { api().videoStreams(videoId, locationId?.takeIf { it > 0 }) }

    suspend fun incrementPlayCount(videoId: Long): ApiResult<Unit> = apiCall {
        runCatching { api().incrementPlayCount(videoId) }
        Unit
    }

    suspend fun createPlaybackSession(videoId: Long, locationId: Long): ApiResult<String> = apiCall {
        api().createPlaybackSession(videoId, PlaybackSessionRequest(locationId)).sessionId
    }

    /** 上报累计观看毫秒；返回 410 时调用方需要重建会话。 */
    suspend fun reportPlayback(videoId: Long, sessionId: String, watchedMs: Long): ApiResult<Unit> = apiCall {
        api().reportPlaybackSession(videoId, sessionId, WatchedRequest(watchedMs))
        Unit
    }

    suspend fun watchedTime(videoIds: List<Long> = emptyList(), javIds: List<Long> = emptyList()): ApiResult<WatchedTimeSnapshot> =
        apiCall {
            api().watchedTime(
                videoIds = videoIds.takeIf { it.isNotEmpty() }?.joinToString(","),
                javIds = javIds.takeIf { it.isNotEmpty() }?.joinToString(","),
            )
        }

    suspend fun screenshots(videoId: Long): ApiResult<List<ScreenshotInfo>> = apiCall {
        api().screenshots(videoId).items
    }

    suspend fun createScreenshot(videoId: Long, second: Double): ApiResult<ScreenshotInfo> =
        apiCall { api().createScreenshot(videoId, ScreenshotCreateRequest(second)) }

    suspend fun deleteScreenshot(videoId: Long, name: String): ApiResult<Unit> =
        apiCall { api().deleteScreenshot(videoId, name); Unit }

    /** 上传本地截帧（裸二进制，扩展名决定 Content-Type）。 */
    suspend fun uploadScreenshot(videoId: Long, name: String, file: java.io.File): ApiResult<ScreenshotInfo> =
        apiCall {
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            val body = okhttp3.RequestBody.create(mime.toMediaTypeOrNull(), file)
            api().uploadScreenshot(videoId, name, body)
        }

    suspend fun setCover(videoId: Long, screenshotName: String): ApiResult<VideoRow> =
        apiCall { api().setCover(videoId, CoverRequest(screenshotName)) }

    suspend fun resetCover(videoId: Long): ApiResult<VideoRow> = apiCall { api().resetCover(videoId) }

    /** 服务端缩略图/封面的绝对地址（Coil 直接加载，Cookie 由共享 OkHttpClient 提供）。 */
    fun thumbnailUrl(videoId: Long): String = ApiClient.absoluteUrl(
        runBlockingUrl(),
        "/videos/$videoId/thumbnail",
    )

    fun javCoverUrl(code: String): String = ApiClient.absoluteUrl(
        runBlockingUrl(),
        "/jav/$code/cover",
    )

    fun screenshotUrl(videoId: Long, name: String): String = ApiClient.absoluteUrl(
        runBlockingUrl(),
        "/videos/$videoId/screenshots/$name",
    )

    fun baseUrl(): String = runBlockingUrl()

    private fun runBlockingUrl(): String =
        kotlinx.coroutines.runBlocking { settings.currentServerUrl() }
}

@Suppress("unused")
private fun ScreenshotListResponse.size(): Int = items.size
