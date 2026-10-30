package com.javboss.android.data.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.javboss.android.core.net.ApiClient
import com.javboss.android.core.net.toCookieHeader
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 播放中截帧（Client 模式：截图发生在手机侧）。
 *
 * 优先用 [MediaMetadataRetriever] 直接对远端的 direct 流取帧（干净、不含 UI），
 * 失败时由调用方回退到 PixelCopy 抓取画面。
 */
object FrameCapture {

    suspend fun capture(
        context: Context,
        videoId: Long,
        locationId: Long,
        positionMs: Long,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = AppGraph.settings.currentServerUrl()
            val path = buildString {
                append("/videos/").append(videoId).append("/stream")
                if (locationId > 0) append("?location_id=").append(locationId)
            }
            val url = ApiClient.absoluteUrl(baseUrl, path)
            val host = java.net.URI(url).host ?: ""
            val cookie = AppGraph.cookieStore.cookiesFor(host).toCookieHeader()
            val headers = mutableMapOf("User-Agent" to "JavBossAndroid")
            if (cookie.isNotBlank()) headers["Cookie"] = cookie

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(url, headers)
                val bitmap = retriever.getFrameAtTime(
                    positionMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ) ?: throw IllegalStateException("无法在该时间点取帧")
                LocalScreenshotStore.saveJpeg(context, videoId, bitmap)
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    /** 兜底：把已渲染的画面（含 UI 叠层）保存为截图。 */
    fun saveBitmap(context: Context, videoId: Long, bitmap: Bitmap): File =
        LocalScreenshotStore.saveJpeg(context, videoId, bitmap)
}
