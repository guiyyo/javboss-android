package com.javboss.android.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.javboss.android.core.net.ApiResult
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * 播放页：
 *  - 画面中心无按钮；点屏幕 → 底部控制条（左：时间/倍速/队列；右：快退10s、播放暂停、快进10s）；
 *  - 手势：双击左/右 1/3 快退/快进 10s，双击中间暂停，长按 2 倍速，上下滑左半屏调亮度、右半屏调音量；
 *  - 续播提示条；右上角竖排：横竖屏 / 截帧 / 截图页 / 画中画 / 队列；
 *  - 切后台或锁屏直接暂停；开启「自动画中画」时改为进入 PiP。
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoId: Long,
    locationId: Long,
    title: String,
    onBack: () -> Unit,
    onOpenScreenshots: () -> Unit = {},
    javId: Long = 0,
    initialQueue: List<PlaybackQueueItem> = emptyList(),
    modifier: Modifier = Modifier,
    vm: PlayerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val spec = com.javboss.android.ui.common.rememberLayoutSpec()

    var landscape by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var showSubtitle by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var subtitlePosMs by remember { mutableStateOf(0L) }
    var skippedMarked by remember { mutableStateOf(false) }
    LaunchedEffect(showSubtitle, javId) {
        if (showSubtitle && vm.state.javCode.isNotBlank()) {
            skippedMarked = runCatching {
                AppGraph.settings.subtitleSkipped(vm.state.javCode)
            }.getOrDefault(false)
        }
    }
    var autoPiP by remember { mutableStateOf(false) }
    var resumeCountdown by remember { mutableStateOf(5) }

    LaunchedEffect(Unit) {
        autoPiP = AppGraph.settings.uiPrefs.first().autoPictureInPicture
    }
    LaunchedEffect(javId) { vm.bindJav(javId) }

    LaunchedEffect(videoId, locationId, javId, initialQueue) {
        if (initialQueue.isNotEmpty()) {
            val startIndex = initialQueue.indexOfFirst { it.videoId == videoId && it.locationId == locationId }
                .takeIf { it >= 0 } ?: 0
            vm.setQueue(initialQueue, startIndex)
        } else if (javId > 0) {
            val detail = AppGraph.jav.detail(javId)
            if (detail is ApiResult.Success) {
                val items = detail.data.videos
                    .filter { it.locationId > 0 }
                    .map {
                        PlaybackQueueItem(
                            videoId = it.id,
                            locationId = it.locationId,
                            title = it.filename.ifBlank { detail.data.zhTitle.ifBlank { detail.data.title } },
                        )
                    }
                val startIndex = items.indexOfFirst { it.videoId == videoId && it.locationId == locationId }
                    .takeIf { it >= 0 } ?: 0
                if (items.isNotEmpty()) vm.setQueue(items, startIndex)
            }
        }
        vm.prepare(context, videoId, locationId, title)
        vm.refreshPendingScreenshots(context)
    }

    // 拖动/快进后立即对齐字幕时钟，避免字幕“消失”
    LaunchedEffect(vm.state.subtitleCues.size, vm.player) {
        if (vm.state.subtitleCues.isEmpty()) return@LaunchedEffect
        while (true) {
            val p = vm.player?.currentPosition ?: 0L
            // 跳变（seek）时立刻同步，缓冲期间保持上一次值
            if (p > 0) subtitlePosMs = p
            delay(100)
        }
    }

    // 拖动进度条 / 手势快进后，4.00 秒主时钟跳变时立刻把字幕时钟对齐
    LaunchedEffect(positionMs) {
        if (kotlin.math.abs(positionMs - subtitlePosMs) > 800) subtitlePosMs = positionMs
    }

    LaunchedEffect(vm.player) {
        while (true) {
            vm.player?.let { exo ->
                positionMs = exo.currentPosition.coerceAtLeast(0)
                val d = exo.duration
                durationMs = if (d > 0) d else 0
                isPlaying = exo.isPlaying
            }
            delay(400)
        }
    }

    LaunchedEffect(controlsVisible, positionMs, isPlaying) {
        if (controlsVisible && isPlaying && !showQueue && !speedMenuOpen) {
            delay(3500)
            controlsVisible = false
        }
    }

    LaunchedEffect(gestureHint) {
        if (gestureHint != null) {
            delay(900)
            gestureHint = null
        }
    }

    // 续播提示 5 秒倒计时，结束后自动继续播放
    LaunchedEffect(vm.state.resumeDecided, vm.state.resumeMs) {
        if (vm.state.resumeDecided || vm.state.resumeMs <= 5000) return@LaunchedEffect
        resumeCountdown = 5
        while (resumeCountdown > 0 && !vm.state.resumeDecided) {
            delay(1000)
            if (vm.state.resumeDecided) break
            resumeCountdown--
        }
        if (!vm.state.resumeDecided) vm.acceptResume()
    }

    LaunchedEffect(vm.state.message) {
        vm.state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearToast()
        }
    }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val activity = context as? android.app.Activity
                val inPip = activity != null &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
                    activity.isInPictureInPictureMode
                if (!inPip) {
                    if (autoPiP && isPlaying && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val params = android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(android.util.Rational(16, 9))
                            .build()
                        runCatching { activity?.enterPictureInPictureMode(params) }
                    } else {
                        vm.pause()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) vm.pause()
            }
        }
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? android.app.Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (activity != null) {
                WindowCompat.getInsetsController(activity.window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler { onBack() }

    fun toggleOrientation() {
        val activity = context as? android.app.Activity ?: return
        landscape = !landscape
        activity.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (landscape) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val exo = vm.player
        if (exo != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exo
                        useController = false
                        subtitleView?.apply {
                            visibility = android.view.View.VISIBLE
                            setFractionalTextSize(0.055f)
                            setStyle(
                                androidx.media3.ui.CaptionStyleCompat(
                                    android.graphics.Color.WHITE,
                                    android.graphics.Color.parseColor("#B3000000"),
                                    android.graphics.Color.TRANSPARENT,
                                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    android.graphics.Color.BLACK,
                                    null,
                                ),
                            )
                        }
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 自绘字幕：偏移即时生效，不重建播放器（不会黑屏）
        // 控制条可见时字幕上移（在进度条上方），隐藏时贴底
        val subtitleBottom by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (controlsVisible) 104.dp else 22.dp,
            animationSpec = androidx.compose.animation.core.tween(180),
            label = "subtitleBottom",
        )
        run {
            val cues = vm.state.subtitleCues
            if (cues.isNotEmpty()) {
                val t = subtitlePosMs - vm.state.subtitleOffsetMs
                val text = cues.firstOrNull { t >= it.startMs && t < it.endMs }?.text.orEmpty()
                androidx.compose.runtime.LaunchedEffect(text) {
                    if (text.isNotBlank()) android.util.Log.i("JavBossSub", "cue=" + text.replace("\n", " / "))
                }
                if (text.isNotBlank()) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = subtitleBottom, start = 32.dp, end = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text,
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val w = size.width
                            when {
                                offset.x < w / 3f -> {
                                    positionMs = (positionMs - 10_000).coerceAtLeast(0)
                                    vm.seekTo(positionMs)
                                    gestureHint = "<< 10s"
                                }
                                offset.x > w * 2f / 3f -> {
                                    positionMs += 10_000
                                    vm.seekTo(positionMs)
                                    gestureHint = "10s >>"
                                }
                                else -> {
                                    if (isPlaying) vm.pause() else vm.play()
                                }
                            }
                        },
                        onPress = {
                            val before = vm.state.speed
                            tryAwaitRelease()
                            if (abs(vm.state.speed - 2f) < 0.01f) vm.setSpeed(before)
                        },
                        onLongPress = {
                            vm.setSpeed(2f)
                            gestureHint = "2x"
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { },
                        onDragEnd = { },
                        onDragCancel = { },
                    ) { change, dragAmount ->
                        val isLeft = change.position.x < size.width / 2f
                        val delta = -dragAmount / size.height
                        if (isLeft) {
                            val activity = context as? android.app.Activity
                            val attrs = activity?.window?.attributes ?: return@detectVerticalDragGestures
                            val current = if (attrs.screenBrightness < 0f) 0.5f else attrs.screenBrightness
                            val next = (current + delta).coerceIn(0.05f, 1f)
                            attrs.screenBrightness = next
                            activity.window.attributes = attrs
                            gestureHint = "亮度 ${(next * 100).toInt()}%"
                        } else {
                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val step = (delta * max).toInt()
                            if (step != 0) {
                                val next = (current + step).coerceIn(0, max)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                gestureHint = "音量 ${(next * 100 / max)}%"
                            }
                        }
                    }
                },
        )

        if (vm.state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        vm.state.error?.let { message ->
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    TextButton(onClick = { vm.prepare(context, videoId, locationId, title) }) { Text("重试") }
                    if (vm.state.canSwitchSource) {
                        TextButton(onClick = { vm.switchSource() }) {
                            Text(if (vm.state.usedKind == "direct") "改用 HLS" else "改用直连")
                        }
                    }
                }
            }
        }

        gestureHint?.let {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(it, color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                        }
                        Text(
                            text = vm.state.title.ifBlank { title },
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp),
                        )
                        Text(
                            text = when (vm.state.usedKind) {
                                "direct" -> "直连"
                                "hls" -> "HLS"
                                else -> ""
                            },
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }

                    Spacer(Modifier.size(8.dp))

                    Column(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!spec.isLarge) {
                            PlayerAction("横竖屏", Icons.Filled.ScreenRotation) { toggleOrientation() }
                        }
                        PlayerAction("截帧", Icons.Filled.PhotoCamera) { vm.captureFrame(context) }
                        PlayerAction("截图页", Icons.Filled.Collections) { onOpenScreenshots() }
                        PlayerAction("字幕", Icons.Filled.Subtitles) { showSubtitle = true }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            PlayerAction("画中画", Icons.Filled.PictureInPictureAlt) {
                                val activity = context as? android.app.Activity ?: return@PlayerAction
                                val params = android.app.PictureInPictureParams.Builder()
                                    .setAspectRatio(android.util.Rational(16, 9))
                                    .build()
                                runCatching { activity.enterPictureInPictureMode(params) }
                            }
                        }
                        if (vm.state.queueSize > 1) {
                            PlayerAction("播放列表", Icons.Filled.PlaylistPlay) { showQueue = true }
                            PlayerAction("上一集", Icons.Filled.SkipPrevious) { vm.previous() }
                            Text(
                                "${vm.state.queueIndex + 1}/${vm.state.queueSize}",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            PlayerAction("下一集", Icons.Filled.SkipNext) { vm.next() }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (!vm.state.resumeDecided && vm.state.resumeMs > 5000) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "上次看到 ${format(vm.state.resumeMs)} · ${resumeCountdown} 秒后自动继续",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.acceptResume() }) { Text("继续播放（${resumeCountdown}s）") }
                        TextButton(onClick = { vm.dismissResume() }) { Text("从头") }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Slider(
                        value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                        onValueChange = { fraction ->
                            if (durationMs > 0) {
                                val target = (durationMs * fraction).toLong()
                                positionMs = target
                                vm.seekTo(target)
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "${format(positionMs)} / ${format(durationMs)}",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Box {
                            TextButton(onClick = { speedMenuOpen = true }) {
                                Text(
                                    text = speedLabel(vm.state.speed),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                                    DropdownMenuItem(
                                        text = { Text(speedLabel(value)) },
                                        onClick = {
                                            vm.setSpeed(value)
                                            speedMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        if (vm.state.queueSize > 1) {
                            IconButton(onClick = { showQueue = true }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Filled.PlaylistPlay,
                                    contentDescription = "播放列表",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.seekTo((positionMs - 10_000).coerceAtLeast(0)) }) {
                            Text("−10s", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = { if (isPlaying) vm.pause() else vm.play() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                            )
                        }
                        TextButton(onClick = { vm.seekTo(positionMs + 10_000) }) {
                            Text("+10s", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }


        if (showSubtitle) {
            Box(Modifier.fillMaxSize()) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        ) { showSubtitle = false },
                )
                SubtitlePanel(
                    code = vm.state.javCode,
                    currentName = vm.state.subtitleName,
                    currentOffsetMs = vm.state.subtitleOffsetMs,
                    embeddedCount = vm.state.embeddedSubtitleCount,
                    usingEmbedded = vm.state.usingEmbeddedSubtitle,
                    skipped = skippedMarked,
                    onDismiss = { showSubtitle = false },
                    onShift = { delta -> vm.shiftSubtitleOffset(delta) },
                    onResetOffset = { vm.resetSubtitleOffset() },
                    onDetach = {
                        scope.launch {
                            AppGraph.subtitle.clear(vm.state.javCode)
                            vm.detachSubtitle()
                        }
                    },
                    onUseEmbedded = { vm.useEmbeddedSubtitle() },
                    onToggleSkip = { skip ->
                        scope.launch {
                            AppGraph.settings.setSubtitleSkipped(vm.state.javCode, skip)
                            if (skip) {
                                AppGraph.subtitle.clear(vm.state.javCode)
                                vm.detachSubtitle()
                            }
                        }
                    },
                    onOpenPicker = { showSubtitle = false; showPicker = true },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                )
            }
        }

        if (showPicker) {
            SubtitlePickerSheet(
                code = vm.state.javCode,
                activeName = vm.state.subtitleName,
                onDismiss = { showPicker = false },
                onLoaded = { inst ->
                    vm.attachSubtitle(inst.name, java.io.File(inst.filePath), inst.offsetMs)
                    vm.toast("字幕已加载：${inst.name}")
                },
            )
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }


    if (showQueue) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showQueue = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    "播放列表（${vm.state.queueIndex + 1}/${vm.state.queueSize}）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(vm.queueItems()) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.jumpTo(index)
                                    showQueue = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (index == vm.state.queueIndex) "▶" else "${index + 1}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (vm.state.queueSize > 1) {
                                IconButton(onClick = { vm.removeFromQueue(index) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "从列表移除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x" else "${speed}x"

private fun format(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

@Composable
private fun PlayerAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}
