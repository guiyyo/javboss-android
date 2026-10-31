package com.javboss.android.ui.tv

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.javboss.android.ui.player.PlayerViewModel
import kotlinx.coroutines.delay

/**
 * TV 播放器：遥控器操作
 *  - OK / 播放暂停键：播放 / 暂停
 *  - ← / →：快退 / 快进 10 秒
 *  - ↑：显示信息条（5 秒后自动隐藏）
 *  - 返回键：退出
 */
@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    videoId: Long,
    locationId: Long,
    title: String,
    javId: Long,
    onBack: () -> Unit,
    vm: PlayerViewModel = viewModel(key = "tvplayer-$videoId-$locationId"),
) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var overlayVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var queueOpen by remember { mutableStateOf(false) }
    var resumeCountdown by remember { mutableStateOf(10) }
    var resumeChoice by remember { mutableStateOf(0) }   // 0=继续播放 1=从头开始
    // Kodi 风格：连按累加，停顿后再真正跳转，OSD 显示「+30 秒 · 目标 00:34:12」
    var pendingSeekMs by remember { mutableStateOf(0L) }
    var seekOsdVisible by remember { mutableStateOf(false) }
    // 字幕：自动加载已缓存字幕 + 偏移 + 显示开关
    var subCues by remember { mutableStateOf<List<com.javboss.android.data.subtitle.SubtitleCue>>(emptyList()) }
    var subName by remember { mutableStateOf<String?>(null) }
    var subOffset by remember { mutableStateOf(0L) }
    var subCode by remember { mutableStateOf("") }
    var subVisible by remember { mutableStateOf(true) }
    var subPos by remember { mutableStateOf(0L) }
    var panelOpen by remember { mutableStateOf(false) }
    var subSkipped by remember { mutableStateOf(false) }
    var seekOsdTarget by remember { mutableStateOf(0L) }
    val seekScope = androidx.compose.runtime.rememberCoroutineScope()
    var seekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(videoId, locationId, javId) {
        // 载入该作品的剧集队列：播完自动下一集
        if (javId > 0) {
            when (val detail = com.javboss.android.di.AppGraph.jav.detail(javId)) {
                is com.javboss.android.core.net.ApiResult.Success -> {
                    val items = detail.data.videos
                        .filter { it.locationId > 0 }
                        .map {
                            com.javboss.android.ui.player.PlaybackQueueItem(
                                videoId = it.id,
                                locationId = it.locationId,
                                title = it.filename.ifBlank {
                                    detail.data.zhTitle.ifBlank { detail.data.title }
                                },
                            )
                        }
                    val startIndex = items.indexOfFirst { it.videoId == videoId && it.locationId == locationId }
                        .takeIf { it >= 0 } ?: 0
                    if (items.isNotEmpty()) vm.setQueue(items, startIndex)
                }
                else -> Unit
            }
        }
        vm.prepare(context, videoId, locationId, title, restart = true)
        vm.refreshPendingScreenshots(context)
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

    LaunchedEffect(hint) {
        if (hint != null) { delay(900); hint = null }
    }

    // 续播提示 5 秒倒计时，结束后自动继续播放
    LaunchedEffect(vm.state.resumeDecided, vm.state.resumeMs) {
        if (vm.state.resumeDecided || vm.state.resumeMs <= 5000) return@LaunchedEffect
        resumeCountdown = 10
        while (resumeCountdown > 0 && !vm.state.resumeDecided) {
            delay(1000)
            if (vm.state.resumeDecided) break
            resumeCountdown--
        }
        if (!vm.state.resumeDecided) vm.acceptResume()
    }

    LaunchedEffect(seekOsdVisible, pendingSeekMs) {
        if (seekOsdVisible && pendingSeekMs == 0L) {
            delay(1200)
            seekOsdVisible = false
        }
    }

    // 进播放页：自动套用该番号已下载的字幕
    LaunchedEffect(javId) {
        if (javId <= 0) return@LaunchedEffect
        val code = runCatching {
            (com.javboss.android.di.AppGraph.jav.detail(javId) as? com.javboss.android.core.net.ApiResult.Success)?.data?.code
        }.getOrNull().orEmpty()
        if (code.isBlank()) return@LaunchedEffect
        subCode = code
        val skipped = runCatching { com.javboss.android.di.AppGraph.settings.subtitleSkipped(code) }.getOrDefault(false)
        subSkipped = skipped
        if (skipped) return@LaunchedEffect
        val inst = com.javboss.android.di.AppGraph.subtitle.current(code) ?: return@LaunchedEffect
        val f = java.io.File(inst.filePath)
        if (!f.exists()) return@LaunchedEffect
        subCues = runCatching {
            com.javboss.android.data.subtitle.parseSrtCues(f.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList())
        subName = inst.name
        subOffset = inst.offsetMs
    }

    // 字幕时钟（100ms），播片时跟随
    LaunchedEffect(subCues.size) {
        if (subCues.isEmpty()) return@LaunchedEffect
        while (true) {
            val p = vm.player?.currentPosition ?: 0L
            if (p > 0) subPos = p
            delay(100)
        }
    }

    LaunchedEffect(overlayVisible, isPlaying) {
        if (overlayVisible && isPlaying) { delay(5000); overlayVisible = false }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // 字幕键（175）或遥控器菜单键 → 打开字幕面板
                if (event.nativeKeyEvent.keyCode == 175 || event.key == Key.Menu) {
                    panelOpen = !panelOpen
                    overlayVisible = true
                    return@onKeyEvent true
                }
                if (panelOpen && event.key == Key.Back) {
                    panelOpen = false
                    return@onKeyEvent true
                }
                // 续播提示：「OK」继续播放，「←」从头开始
                val resumePrompt = !vm.state.resumeDecided && vm.state.resumeMs > 5000
                if (resumePrompt) {
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                            resumeChoice = 1 - resumeChoice
                            return@onKeyEvent true
                        }
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            if (resumeChoice == 0) vm.acceptResume() else vm.dismissResume()
                            return@onKeyEvent true
                        }
                        Key.Back -> {
                            vm.dismissResume()
                            return@onKeyEvent true
                        }
                        else -> Unit
                    }
                }
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                        if (isPlaying) vm.pause() else vm.play()
                        overlayVisible = true
                        hint = if (isPlaying) "暂停" else "播放"
                        true
                    }
                    Key.MediaPlayPause -> {
                        if (isPlaying) vm.pause() else vm.play()
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val step = if (event.key == Key.DirectionLeft) -10_000L else 10_000L
                        pendingSeekMs += step
                        seekOsdTarget = (positionMs + pendingSeekMs).coerceIn(0, if (durationMs > 0) durationMs else Long.MAX_VALUE)
                        seekOsdVisible = true
                        overlayVisible = true
                        seekJob?.cancel()
                        seekJob = seekScope.launch {
                            delay(450)
                            if (pendingSeekMs != 0L) vm.seekTo((positionMs + pendingSeekMs).coerceAtLeast(0))
                            pendingSeekMs = 0
                        }
                        true
                    }
                    Key.M, Key.Menu -> {
                        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                        val idx = speeds.indexOfFirst { kotlin.math.abs(it - vm.state.speed) < 0.01f }
                        val next = speeds[(if (idx < 0) 0 else idx + 1) % speeds.size]
                        vm.setSpeed(next)
                        overlayVisible = true
                        hint = "倍速 ${next}x"
                        true
                    }
                    Key.DirectionUp -> {
                        if (!overlayVisible && vm.state.queueSize > 1) queueOpen = true else queueOpen = false
                        overlayVisible = !overlayVisible
                        true
                    }
                    // 下键：切换播放倍速（OSD 提示）
                    Key.DirectionDown -> {
                        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                        val idx = speeds.indexOfFirst { kotlin.math.abs(it - vm.state.speed) < 0.01f }
                        val next = speeds[(if (idx < 0) 0 else idx + 1) % speeds.size]
                        vm.setSpeed(next)
                        overlayVisible = true
                        hint = "倍速 ${next}x ↓"
                        true
                    }
                    Key.Back -> {
                        vm.pause()
                        vm.saveHistory(vm.player)
                        onBack()
                        true
                    }
                    else -> false
                }
            },
    ) {
        vm.player?.let { exo ->
            AndroidView(
                factory = { ctx ->
                    // 必须用 texture_view 版（view_player.xml）：SurfaceView 会盖住 Compose 绘制的控件
                    val pv = android.view.LayoutInflater.from(ctx)
                        .inflate(com.javboss.android.R.layout.view_player, null) as PlayerView
                    pv.player = exo
                    pv
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        if (vm.state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        vm.state.error?.let { message ->
            Column(
                Modifier.fillMaxSize().padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = Color.White)
                Row(
                    modifier = Modifier.padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvFocusable(onClick = { vm.prepare(context, videoId, locationId, title) }) {
                        Text("重新播放", color = Color.White, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
                    }
                    if (vm.state.canSwitchSource) {
                        TvFocusable(onClick = { vm.switchSource() }) {
                            Text(
                                if (vm.state.usedKind == "direct") "改用 HLS" else "改用直连",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }

        // 播放中：菜单键打开「偏移 / 是否使用字幕」窗口（不含搜索下载）
        if (panelOpen) {
            TvSubtitleOffsetDialog(
                currentName = subName,
                currentOffsetMs = subOffset,
                skipped = subSkipped,
                hasCues = subCues.isNotEmpty(),
                onClose = { panelOpen = false },
                onShift = { delta ->
                    subOffset += delta
                    val code = subCode
                    val name = subName
                    if (code.isNotBlank() && name != null) {
                        kotlinx.coroutines.MainScope().launch {
                            runCatching {
                                com.javboss.android.di.AppGraph.settings.setSubtitleSelection(code, name, subOffset)
                            }
                        }
                    }
                },
                onReset = {
                    val code = subCode
                    val name = subName
                    subOffset = 0L
                    if (code.isNotBlank() && name != null) {
                        kotlinx.coroutines.MainScope().launch {
                            runCatching {
                                com.javboss.android.di.AppGraph.settings.setSubtitleSelection(code, name, 0L)
                            }
                        }
                    }
                },
                onDetach = {
                    subCues = emptyList()
                    subName = null
                    subOffset = 0L
                    kotlinx.coroutines.MainScope().launch {
                        runCatching { com.javboss.android.di.AppGraph.subtitle.clear(subCode) }
                    }
                },
                onToggleSkip = { skip ->
                    subSkipped = skip
                    if (skip) { subCues = emptyList(); subName = null }
                    kotlinx.coroutines.MainScope().launch {
                        runCatching { com.javboss.android.di.AppGraph.settings.setSubtitleSkipped(subCode, skip) }
                    }
                },
            )
        }

        // 自绘字幕（自绘层，偏移即时生效）
        if (subVisible && subCues.isNotEmpty()) {
            val t0 = subPos - subOffset
            val cue = subCues.firstOrNull { t0 >= it.startMs && t0 < it.endMs }?.text
            if (!cue.isNullOrBlank()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (overlayVisible) 150.dp else 40.dp, start = 60.dp, end = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        cue,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // 续播提示：左右切换、OK 确认，10 秒倒计时自动继续
        if (!vm.state.resumeDecided && vm.state.resumeMs > 5000) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "上次看到 ${fmt(vm.state.resumeMs)}",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ResumeOption(
                        label = "继续播放",
                        selected = resumeChoice == 0,
                        onClick = { vm.acceptResume() },
                    )
                    ResumeOption(
                        label = "从头开始",
                        selected = resumeChoice == 1,
                        onClick = { vm.dismissResume() },
                    )
                }
                Text(
                    text = "← → 切换 · OK 确认 · ${resumeCountdown} 秒后自动继续播放",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (seekOsdVisible && (pendingSeekMs != 0L)) {
            val seconds = pendingSeekMs / 1000
            val arrow = if (seconds >= 0) "»" else "«"
            val label = if (seconds >= 0) "+${seconds} 秒 »" else "−${-seconds} 秒 «"
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = fmt(seekOsdTarget),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (durationMs > 0) (seekOsdTarget.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                        },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.width(260.dp).padding(top = 8.dp),
                    )
                }
            }
        }

        hint?.let {
            Box(
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(it, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        if (queueOpen && vm.state.queueSize > 1) {
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Text(
                    "播放列表 ${vm.state.queueIndex + 1}/${vm.state.queueSize}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(modifier = Modifier.height(360.dp).width(520.dp)) {
                    itemsIndexed(vm.queueItems()) { index, item ->
                        val current = index == vm.state.queueIndex
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(
                                    if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    vm.jumpTo(index)
                                    queueOpen = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (current) "▶" else "${index + 1}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(26.dp),
                            )
                            Text(
                                text = item.title,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (overlayVisible) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(20.dp)) {
                Text(
                    text = vm.state.title.ifBlank { title },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fmt(positionMs) + " / " + fmt(durationMs),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "OK 播放/暂停 · ←→ 快进退 · ↑ 信息 · ↓ 倍速 · 菜单键 字幕",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun fmt(ms: Long): String {
    if (ms <= 0) return "00:00"
    val s = ms / 1000
    return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}
@Composable
private fun ResumeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White
    Box(
        Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp),
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
    }
}
