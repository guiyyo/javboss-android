package com.javboss.android.ui.player

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.javboss.android.core.net.ApiClient
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.PlaybackInfo
import com.javboss.android.data.screenshot.FrameCapture
import com.javboss.android.data.screenshot.LocalScreenshotStore
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackQueueItem(
    val videoId: Long,
    val locationId: Long,
    val title: String,
)

data class PlayerUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val info: PlaybackInfo? = null,
    val usedKind: String = "",
    val startPositionMs: Long = 0,
    val message: String? = null,
    val pendingScreenshots: Int = 0,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    /** 上次观看位置（>0 时播放器显示「继续/从头」提示条） */
    val resumeMs: Long = 0,
    /** 续播提示是否已处理（处理后不再显示） */
    val resumeDecided: Boolean = false,
    /** 当前倍速 */
    val speed: Float = 1.0f,
    /** 播放失败时是否可切换另一种源 */
    val canSwitchSource: Boolean = false,
)

/**
 * 播放器（Client 模式语义）：本机 ExoPlayer 直连远端流。
 *
 * - 先 `GET /videos/{id}/streams` 选源：direct(可 seek) 优先，失败自动回落 HLS；
 * - 观看时长按服务端“会话 + 累计值”协议上报（10 秒一跳，410 重建会话）；
 * - 首次出画异步 `POST /videos/{id}/play` 计数。
 */
class PlayerViewModel : ViewModel() {
    private companion object { const val TAG = "JavBossPlayer" }

    var state by mutableStateOf(PlayerUiState())
        private set

    var player: ExoPlayer? = null
        private set

    private var videoId: Long = 0
    private var locationId: Long = 0
    private var queue: List<PlaybackQueueItem> = emptyList()
    private var queueIndex: Int = 0
    private var playCountReported = false
    private var hlsFallbackTried = false

    private var sessionId: String? = null
    private var accumulatedMs: Long = 0
    /** 本地观看记录 key（按服务器地址隔离） */
    private var historyKey: String? = null
    private var acknowledgedMs: Long = 0
    private var progressJob: Job? = null
    private var tickerJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && !playCountReported) {
                playCountReported = true
                viewModelScope.launch { AppGraph.video.incrementPlayCount(videoId) }
            }
            if (playbackState == Player.STATE_ENDED && queue.size > 1 && queueIndex < queue.size - 1) {
                playIndex(queueIndex + 1)
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            // direct 播放失败 → 回落 HLS（与网页端策略一致，只回落一次）
            val info = state.info
            if (!hlsFallbackTried && state.usedKind == "direct" && info != null) {
                val hls = info.sources.firstOrNull { it.kind == "hls" }
                if (hls != null) {
                    hlsFallbackTried = true
                    val position = player?.currentPosition ?: 0
                    viewModelScope.launch {
                        startSource(info, hls, positionMs = position, autoPlay = true)
                    }
                    return
                }
            }
            state = state.copy(
                loading = false,
                error = "播放失败：${error.errorCodeName}",
                canSwitchSource = info != null && info.sources.size > 1,
            )
        }
    }

    fun attach(context: Context) {
        if (player != null) return
        val dataSourceFactory = OkHttpDataSource.Factory(ApiClient.okHttp)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                addListener(listener)
                playWhenReady = false
            }
        player = exo
    }

    fun prepare(context: Context, videoId: Long, locationId: Long, title: String, restart: Boolean = false) {
        if (restart) {
            // 重新进入播放页：清掉上次的会话状态，重新读取续播位置
            this.videoId = 0
            accumulatedMs = 0
            acknowledgedMs = 0
            sessionId = null
            state = state.copy(resumeDecided = false, resumeMs = 0, error = null)
            runCatching { player?.stop() }
        }
        if (this.videoId == videoId && this.locationId == locationId && player?.mediaItemCount ?: 0 > 0) return
        if (queue.isEmpty() || queue.none { it.videoId == videoId && it.locationId == locationId }) {
            queue = listOf(PlaybackQueueItem(videoId, locationId, title))
            queueIndex = 0
            state = state.copy(queueIndex = 0, queueSize = 1)
        }
        this.videoId = videoId
        this.locationId = locationId
        state = state.copy(loading = true, error = null, title = title)
        attach(context)

        viewModelScope.launch {
            val streams = AppGraph.video.streams(videoId, locationId)
            if (streams is ApiResult.Failure) {
                state = state.copy(loading = false, error = streams.message)
                return@launch
            }
            val info = (streams as ApiResult.Success).data

            // 续播位置：优先本地记录的精确播放头，其次服务端累计时长
            val baseUrl = AppGraph.video.baseUrl()
            val key = com.javboss.android.data.history.WatchHistory.key(baseUrl, videoId, locationId)
            historyKey = key
            val local = AppGraph.settings.watchEntry(key)
            var serverResumeMs = 0L
            val watched = AppGraph.video.watchedTime(videoIds = listOf(videoId))
            if (watched is ApiResult.Success) {
                serverResumeMs = watched.data.videos.firstOrNull { it.id == videoId }?.watchedMs ?: 0
            }
            val localPos = local?.positionMs ?: 0
            val localDuration = local?.durationMs ?: 0
            // 本地位置更准；但若已接近片尾（>=98%）则认为看完，从服务端逻辑走
            var resumeMs = if (localPos > 3_000 && (localDuration == 0L || localPos < localDuration * 98 / 100)) {
                localPos
            } else {
                serverResumeMs
            }
            // 已接近结尾则从头播放（服务端未直接给时长，保守起见超过 3 小时不续播）
            if (resumeMs > 3 * 60 * 60 * 1000L && resumeMs > 0) resumeMs = 0

            val speed = AppGraph.settings.uiPrefs.first().playbackSpeed
            state = state.copy(
                info = info,
                startPositionMs = resumeMs,
                loading = false,
                resumeMs = if (resumeMs > 5000) resumeMs else 0,
                resumeDecided = resumeMs <= 5000,
                speed = speed,
            )
            val preferDirect = AppGraph.settings.preferDirect.first()
            // 先从头开始播出画面，再由「继续播放」把进度跳到上次位置（避免黑屏等待）
            startSource(info, chooseSource(info, preferDirect), 0, autoPlay = true)
        }
    }

    private fun chooseSource(
        info: PlaybackInfo,
        preferDirect: Boolean = true,
    ): com.javboss.android.data.model.PlaybackSource? {
        val direct = info.sources.firstOrNull { it.kind == "direct" }
        val hls = info.sources.firstOrNull { it.kind == "hls" }
        if (!preferDirect) return hls ?: direct
        return if (info.preferredKind == "direct" && direct != null) direct else hls ?: direct
    }

    private fun startSource(
        info: PlaybackInfo,
        source: com.javboss.android.data.model.PlaybackSource?,
        positionMs: Long,
        autoPlay: Boolean,
    ) {
        val exo = player ?: return
        if (source == null) {
            state = state.copy(loading = false, error = "服务端未提供可播放的媒体源")
            return
        }
        val baseUrl = AppGraph.video.baseUrl()
        val url = ApiClient.absoluteUrl(baseUrl, source.src)
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .apply { if (source.kind == "hls") setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        val mediaSource: MediaSource = if (source.kind == "hls") {
            val factory = OkHttpDataSource.Factory(ApiClient.okHttp)
            HlsMediaSource.Factory(factory).createMediaSource(mediaItem)
        } else {
            val factory = OkHttpDataSource.Factory(ApiClient.okHttp)
            ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
        }
        state = state.copy(usedKind = source.kind, loading = false, error = null)
        exo.setMediaSource(mediaSource, positionMs.coerceAtLeast(0))
        exo.prepare()
        exo.playWhenReady = autoPlay
        exo.setPlaybackSpeed(state.speed)
        beginWatchedTracking()
    }

    /* -------------------------------------------------- 观看时长上报 */

    private fun beginWatchedTracking() {
        progressJob?.cancel()
        tickerJob?.cancel()
        sessionId = null
        accumulatedMs = 0
        acknowledgedMs = 0

        progressJob = viewModelScope.launch {
            val result = AppGraph.video.createPlaybackSession(videoId, locationId)
            when (result) {
                is ApiResult.Success -> { sessionId = result.data; Log.d(TAG, "session created ${result.data}") }
                is ApiResult.Failure -> Log.w(TAG, "session create failed: ${result.status} ${result.message}")
            }
        }

        tickerJob = viewModelScope.launch {
            var lastPosition = player?.currentPosition ?: 0L
            while (isActive) {
                delay(500)
                val exo = player ?: break
                val position = exo.currentPosition
                if (exo.isPlaying) {
                    val delta = position - lastPosition
                    if (delta in 1..2_000) accumulatedMs += delta
                }
                lastPosition = position
                if (accumulatedMs - acknowledgedMs >= 10_000) {
                    flush(final = false)
                    saveHistory(exo)
                }
            }
        }
    }

    private suspend fun flush(final: Boolean) {
        val payload = accumulatedMs
        if (payload <= acknowledgedMs) return
        var sid = sessionId
        if (sid == null) {
            val created = AppGraph.video.createPlaybackSession(videoId, locationId)
            if (created is ApiResult.Success) {
                sessionId = created.data
                sid = created.data
            } else {
                return
            }
        }
        val result = AppGraph.video.reportPlayback(videoId, sid, payload)
        when (result) {
            is ApiResult.Success -> {
                acknowledgedMs = payload
                Log.d(TAG, "reported watched=${payload}ms")
            }
            is ApiResult.Failure -> {
                if (result.status == 410) {
                    // 会话失效：重建会话并以当前累计值作为新基线
                    Log.w(TAG, "session expired, recreating")
                    sessionId = null
                    val recreated = AppGraph.video.createPlaybackSession(videoId, locationId)
                    if (recreated is ApiResult.Success) {
                        sessionId = recreated.data
                        val retry = AppGraph.video.reportPlayback(videoId, recreated.data, payload)
                        if (retry is ApiResult.Success) acknowledgedMs = payload
                    }
                } else {
                    Log.w(TAG, "report failed: ${result.status} ${result.message}")
                }
            }
        }
    }

    fun pause() {
        player?.let { saveHistory(it) }
        player?.pause()
    }

    fun play() {
        player?.play()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun watchedMs(): Long = accumulatedMs

    /** 记录本地观看时间与精确播放位置（服务端只有累计时长）。 */
    fun saveHistory(exo: androidx.media3.exoplayer.ExoPlayer? = player) {
        val key = historyKey ?: return
        val position = exo?.currentPosition?.coerceAtLeast(0) ?: return
        if (position <= 0) return
        val duration = exo.duration.let { if (it > 0) it else 0L }
        WatchHistoryFlusher.save(
            key = key,
            entry = com.javboss.android.data.history.WatchEntry(
                at = System.currentTimeMillis(),
                positionMs = position,
                durationMs = duration,
                title = state.title,
            ),
        )
    }

    /* ---------------- 倍速 / 续播 / 切源 / 队列 ---------------- */

    fun setSpeed(speed: Float) {
        state = state.copy(speed = speed)
        player?.setPlaybackSpeed(speed)
        viewModelScope.launch { AppGraph.settings.setPlaybackSpeed(speed) }
    }

    fun acceptResume() {
        val target = state.resumeMs
        state = state.copy(resumeDecided = true)
        if (target > 0) player?.seekTo(target)
    }

    fun dismissResume() {
        state = state.copy(resumeDecided = true, resumeMs = 0)
        player?.seekTo(0)
    }

    /** 直连失败切 HLS（或反向），保留当前进度。 */
    fun switchSource() {
        val info = state.info ?: return
        val current = state.usedKind
        val target = if (current == "direct") {
            info.sources.firstOrNull { it.kind == "hls" }
        } else {
            info.sources.firstOrNull { it.kind == "direct" }
        } ?: return
        val position = player?.currentPosition ?: 0
        hlsFallbackTried = true
        state = state.copy(error = null, loading = true)
        startSource(info, target, position, autoPlay = true)
    }

    fun queueItems(): List<PlaybackQueueItem> = queue

    fun jumpTo(index: Int) {
        if (index in queue.indices) playIndex(index)
    }

    fun removeFromQueue(index: Int) {
        if (index !in queue.indices || queue.size <= 1) return
        val currentVideo = queue.getOrNull(queueIndex)
        queue = queue.filterIndexed { i, _ -> i != index }
        val newIndex = queue.indexOfFirst { it.videoId == currentVideo?.videoId }
            .takeIf { it >= 0 } ?: 0
        queueIndex = newIndex
        state = state.copy(queueIndex = newIndex, queueSize = queue.size)
    }

    fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val currentVideo = queue.getOrNull(queueIndex)
        val list = queue.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        queue = list
        val newIndex = queue.indexOfFirst { it.videoId == currentVideo?.videoId }
            .takeIf { it >= 0 } ?: 0
        queueIndex = newIndex
        state = state.copy(queueIndex = newIndex, queueSize = queue.size)
    }

    /** 设置播放队列（Client 模式：队列与连播都在本机）。 */
    fun setQueue(items: List<PlaybackQueueItem>, startIndex: Int = 0) {
        queue = items
        queueIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        state = state.copy(queueIndex = queueIndex, queueSize = items.size)
    }

    fun playIndex(index: Int) {
        if (index !in queue.indices) return
        queueIndex = index
        state = state.copy(queueIndex = index, queueSize = queue.size)
        val item = queue[index]
        flushFinal()
        playCountReported = false
        hlsFallbackTried = false
        videoId = item.videoId
        locationId = item.locationId
        state = state.copy(title = item.title, loading = true, error = null, usedKind = "")
        viewModelScope.launch {
            val streams = AppGraph.video.streams(item.videoId, item.locationId)
            if (streams is ApiResult.Failure) {
                state = state.copy(loading = false, error = streams.message)
                return@launch
            }
            val info = (streams as ApiResult.Success).data
            state = state.copy(info = info, loading = false)
            val preferDirect = AppGraph.settings.preferDirect.first()
            startSource(info, chooseSource(info, preferDirect), 0, autoPlay = true)
        }
    }

    fun next() {
        if (queueIndex < queue.size - 1) playIndex(queueIndex + 1)
    }

    fun previous() {
        if (queueIndex > 0) playIndex(queueIndex - 1)
    }

    private fun flushFinal() {
        if (accumulatedMs > acknowledgedMs) {
            PlaybackFlusher.flush(videoId, locationId, accumulatedMs, sessionId)
        }
        tickerJob?.cancel()
        progressJob?.cancel()
        sessionId = null
        accumulatedMs = 0
        acknowledgedMs = 0
    }

    /** 截帧：保存到本机；自动上传开启时立即上传。 */
    fun captureFrame(context: android.content.Context) {
        val position = player?.currentPosition ?: return
        viewModelScope.launch {
            val autoUpload = AppGraph.settings.autoUploadScreenshots.first()
            val result = FrameCapture.capture(context, videoId, locationId, position)
            if (result.isSuccess) {
                if (autoUpload) {
                    when (val uploaded = LocalScreenshotStore.uploadAll(context, videoId)) {
                        is ApiResult.Success -> state = state.copy(
                            message = "已截帧并上传 ${uploaded.data} 张",
                            pendingScreenshots = LocalScreenshotStore.pendingCount(context, videoId),
                        )
                        is ApiResult.Failure -> state = state.copy(
                            message = "截帧已保存，上传失败：${uploaded.message}",
                            pendingScreenshots = LocalScreenshotStore.pendingCount(context, videoId),
                        )
                    }
                } else {
                    state = state.copy(
                        message = "已保存到本机（可在截图页手动上传）",
                        pendingScreenshots = LocalScreenshotStore.pendingCount(context, videoId),
                    )
                }
            } else {
                state = state.copy(message = "截帧失败：${result.exceptionOrNull()?.message ?: "未知原因"}")
            }
        }
    }

    fun refreshPendingScreenshots(context: android.content.Context) {
        state = state.copy(pendingScreenshots = LocalScreenshotStore.pendingCount(context, videoId))
    }

    override fun onCleared() {
        tickerJob?.cancel()
        progressJob?.cancel()
        player?.release()
        player = null
        if (accumulatedMs > acknowledgedMs) {
            // 退出时尽力补一次最终进度（不阻塞 UI；失败由下次播放会话覆盖）
            PlaybackFlusher.flush(videoId, locationId, accumulatedMs, sessionId)
        }
        super.onCleared()
    }
}

/** 脱离 ViewModel 生命周期的收尾上报（Client 模式：进度必须落库）。 */
object PlaybackFlusher {
    fun flush(videoId: Long, locationId: Long, watchedMs: Long, sessionId: String?) {
        if (videoId <= 0 || watchedMs <= 0) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            var sid = sessionId
            if (sid == null) {
                val created = AppGraph.video.createPlaybackSession(videoId, locationId)
                sid = (created as? ApiResult.Success)?.data ?: return@launch
            }
            val result = AppGraph.video.reportPlayback(videoId, sid, watchedMs)
            if (result is ApiResult.Failure && result.status == 410) {
                val recreated = AppGraph.video.createPlaybackSession(videoId, locationId)
                if (recreated is ApiResult.Success) {
                    AppGraph.video.reportPlayback(videoId, recreated.data, watchedMs)
                }
            }
        }
    }
}

/** 本地观看记录写入器（不依赖 ViewModel 生命周期）。 */
object WatchHistoryFlusher {
    fun save(key: String, entry: com.javboss.android.data.history.WatchEntry) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { AppGraph.settings.recordWatch(key, entry) }
        }
    }
}