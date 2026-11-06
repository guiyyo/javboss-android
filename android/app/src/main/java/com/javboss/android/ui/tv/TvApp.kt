package com.javboss.android.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.history.WatchHistory
import com.javboss.android.data.model.JavItem
import com.javboss.android.data.model.VideoRow
import com.javboss.android.data.repo.JavQuery
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.formatDuration
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** TV 端页面栈。 */
sealed interface TvScreen {
    data object Home : TvScreen
    data object AllJav : TvScreen
    data object History : TvScreen
    data object Settings : TvScreen
    data object Idols : TvScreen
    data object Studios : TvScreen
    data object Series : TvScreen
    data object Prefixes : TvScreen
    data class FilteredJav(val title: String, val query: JavQuery) : TvScreen
    data class Detail(val javId: Long) : TvScreen
    data class Player(
        val videoId: Long,
        val locationId: Long,
        val title: String,
        val javId: Long,
        val javCode: String = "",
    ) : TvScreen
}

private enum class TvTab(val label: String) {
    Home("首页"),
    AllJav("JAV"),
    Idols("女优"),
    Studios("片商"),
    Series("系列"),
    Prefixes("番号前缀"),
    History("观看记录"),
    Settings("设置"),
}

/** TV 入口：先做登录状态判定，未登录显示 TV 登录页。 */
@Composable
fun TvAppRoot() {
    val loginVm: com.javboss.android.ui.auth.LoginViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    var authed by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(loginVm.loggedIn, loginVm.checking) {
        authed = when {
            loginVm.loggedIn -> true
            loginVm.checking -> null
            else -> false
        }
    }

    when (authed) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        false -> TvLoginScreen(vm = loginVm, onLoggedIn = { authed = true })
        true -> {
            val prefs by AppGraph.settings.uiPrefs.collectAsState(
                initial = com.javboss.android.data.settings.UiPrefs(),
            )
            var lastActivity by remember { mutableStateOf(System.currentTimeMillis()) }
            val tvPrefs = prefs
            var pinUnlocked by remember { mutableStateOf(false) }
            // 每次进入（冷启动 / 从后台返回）都要重新解锁
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) pinUnlocked = false
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // 无操作超时自动锁屏（时间在设置里可配）
            val idleMin = prefs.tvIdleLockMinutes
            LaunchedEffect(pinUnlocked, idleMin) {
                if (!pinUnlocked || idleMin <= 0) return@LaunchedEffect
                while (true) {
                    kotlinx.coroutines.delay(5_000)
                    // 正在播放视频时不锁屏
                    if (TvGlobalNav.playing.value) {
                        lastActivity = System.currentTimeMillis()
                        continue
                    }
                    if (System.currentTimeMillis() - lastActivity >= idleMin * 60_000L) {
                        pinUnlocked = false
                        break
                    }
                }
            }
            if (prefs.tvPinEnabled && prefs.tvPinHash.isNotBlank() && !pinUnlocked) {
                TvPinScreen(onUnlocked = { pinUnlocked = true; lastActivity = System.currentTimeMillis() })
            } else {
                Box(Modifier.fillMaxSize().onPreviewKeyEvent {
                    lastActivity = System.currentTimeMillis()
                    false
                }) {
                    TvContent(onLogout = {
                        loginVm.markLoggedOut()
                        authed = false
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TvContent(onLogout: () -> Unit) {
    val stack = remember { mutableStateListOf<TvScreen>(TvScreen.Home) }
    val current = stack.last()
    LaunchedEffect(Unit) { runCatching { AppGraph.config.refresh() } }

    // 侧栏选中项：进入详情/筛选结果时保持父页面高亮（不跳回首页）
    var tab by remember { mutableStateOf(TvTab.Home) }

    // 程序化跳转后的短时间内忽略"焦点自动切换"，避免被旧焦点弹回上一页
    var navArmAt by remember { mutableStateOf(0L) }
    fun armNav(delayMs: Long = 700) { navArmAt = System.currentTimeMillis() + delayMs }

    // 各页面的滚动位置（离开页面去详情再返回时恢复）
    val scrollStates = remember {
        mutableMapOf<String, androidx.compose.foundation.lazy.grid.LazyGridState>()
    }
    fun scrollStateFor(key: String): androidx.compose.foundation.lazy.grid.LazyGridState =
        scrollStates.getOrPut(key) { androidx.compose.foundation.lazy.grid.LazyGridState() }

    fun push(screen: TvScreen) {
        stack.add(screen)
        armNav()
    }

    val navRequesters = remember { List(TvTab.entries.size) { androidx.compose.ui.focus.FocusRequester() } }
    val contentFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()


    fun switchTo(entry: TvTab) {
        val target = when (entry) {
            TvTab.Home -> TvScreen.Home
            TvTab.AllJav -> TvScreen.AllJav
            TvTab.Idols -> TvScreen.Idols
            TvTab.Studios -> TvScreen.Studios
            TvTab.Series -> TvScreen.Series
            TvTab.Prefixes -> TvScreen.Prefixes
            TvTab.History -> TvScreen.History
            TvTab.Settings -> TvScreen.Settings
        }
        if (stack.size == 1 && stack[0] == target) return
        stack.clear()
        stack.add(target)
        tab = entry
        // 切换后焦点进入内容区，遥控器可立刻操作
        scope.launch {
            kotlinx.coroutines.delay(150)
            runCatching { contentFocus.requestFocus() }
        }
    }
    LaunchedEffect(Unit) {
        TvGlobalNav.openSettings.collect { switchTo(TvTab.Settings) }
    }


    // 播放器全屏，不显示侧栏
    if (current is TvScreen.Player) {
        TvPlayerScreen(
            videoId = current.videoId,
            locationId = current.locationId,
            title = current.title,
            javId = current.javId,
            onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
        )
        return
    }

    var navHasFocus by remember { mutableStateOf(false) }
    BackHandler(enabled = stack.size > 1 || !navHasFocus) {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            tab = when (stack.last()) {
                TvScreen.Home -> TvTab.Home
                TvScreen.AllJav -> TvTab.AllJav
                is TvScreen.FilteredJav -> tab
                TvScreen.Idols -> TvTab.Idols
                TvScreen.Studios -> TvTab.Studios
                TvScreen.Series -> TvTab.Series
                TvScreen.Prefixes -> TvTab.Prefixes
                TvScreen.History -> TvTab.History
                TvScreen.Settings -> TvTab.Settings
                else -> tab
            }
        } else {
            // 焦点回侧栏当前项（再按一次返回才退出）
            runCatching { navRequesters[tab.ordinal].requestFocus() }
        }
    }
    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                // 遥控器「设置」键直达应用设置
                // 播放页里菜单键归播放器（打开字幕面板），其余页面菜单键=进设置
                val inPlayer = current is TvScreen.Player
                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                    (
                        event.key == androidx.compose.ui.input.key.Key.Settings ||
                            (event.key == androidx.compose.ui.input.key.Key.Menu && !inPlayer)
                        )
                ) {
                    switchTo(TvTab.Settings)
                    true
                } else false
            },
    ) {
        // 左侧导航
        Column(
            modifier = Modifier
                .width(148.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 18.dp)
                // 从内容区回到侧栏时，焦点恢复到上次停留的菜单项（不乱跳）
                .focusGroup(),
        ) {
            Text(
                "JavBoss",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 14.dp),
            )
            TvTab.entries.forEach { entry ->
                TvNavItem(
                    label = entry.label,
                    icon = when (entry) {
                        TvTab.Home -> Icons.Filled.Home
                        TvTab.AllJav -> Icons.Filled.VideoLibrary   // 与手机 tab 一致
                        TvTab.Idols -> Icons.Filled.Person
                        TvTab.Studios -> Icons.Filled.Workspaces     // 与手机 tab 一致
                        TvTab.Series -> Icons.Filled.PlayCircle      // 与手机 tab 一致
                        TvTab.Prefixes -> Icons.Filled.MenuBook      // 与手机「番号字典」一致
                        TvTab.History -> Icons.Filled.History
                        TvTab.Settings -> Icons.Filled.Settings
                    },
                    selected = tab == entry,
                    modifier = Modifier.focusRequester(navRequesters[entry.ordinal]),
                    onClick = { switchTo(entry) },
                    onFocus = {
                        navHasFocus = true
                    },
                )
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                // 从内容区按「左」回到侧栏时，定位到当前页面对应的菜单项
                .focusProperties { left = navRequesters[tab.ordinal] }
                .focusRequester(contentFocus)
                .onFocusChanged { navHasFocus = !it.hasFocus }
                .focusGroup(),
        ) {
            val screenKeyForFocus = when (current) {
                TvScreen.Home -> "home"
                TvScreen.AllJav -> "jav"
                is TvScreen.FilteredJav -> "jav-${current.title}"
                TvScreen.Idols -> "idols"
                TvScreen.Studios -> "studios"
                TvScreen.Series -> "series"
                TvScreen.Prefixes -> "prefixes"
                TvScreen.History -> "history"
                is TvScreen.Detail -> "detail-${current.javId}"
                else -> "other"
            }
            androidx.compose.runtime.CompositionLocalProvider(TvScreenKey provides screenKeyForFocus) {
            when (current) {
                TvScreen.Home -> TvHomeScreen(
                    state = scrollStateFor("home"),
                    onOpenAllJav = { switchTo(TvTab.AllJav) },
                    onOpenDetail = { push(TvScreen.Detail(it)) },
                    onPlayVideo = { video ->
                        push(
                            TvScreen.Player(
                                videoId = video.id,
                                locationId = video.locationId,
                                title = video.jav?.let { j -> j.zhTitle.ifBlank { j.title } } ?: video.filename,
                                javId = video.javId ?: 0L,
                            ),
                        )
                    },
                )
                TvScreen.AllJav -> TvJavGridScreen(
                    state = scrollStateFor("jav"),
                    title = "JAV",
                    query = JavQuery(
                        pageSize = 30,
                        sort = com.javboss.android.di.AppGraph.settings.let { s ->
                            kotlinx.coroutines.runBlocking { s.uiPrefs.let { it.first().javSort } }
                        },
                    ),
                    onOpenDetail = { push(TvScreen.Detail(it)) },
                )
                TvScreen.Idols -> TvIdolScreen(
                    state = scrollStateFor("idols"),
                    onOpenSettings = { switchTo(TvTab.Settings) },onOpenWorks = { id, name ->
                    push(TvScreen.FilteredJav("女优：$name", JavQuery(pageSize = 30, sort = "recent", idolIds = listOf(id))))
                })
                TvScreen.Studios -> TvStudioScreen(
                    state = scrollStateFor("studios"),
                    onOpenSettings = { switchTo(TvTab.Settings) },onOpenWorks = { id, name ->
                    push(TvScreen.FilteredJav("片商：$name", JavQuery(pageSize = 30, sort = "recent", studioId = id)))
                })
                TvScreen.Series -> TvSeriesScreen(
                    state = scrollStateFor("series"),
                    onOpenSettings = { switchTo(TvTab.Settings) },onOpenWorks = { id, name ->
                    push(TvScreen.FilteredJav("系列：$name", JavQuery(pageSize = 30, sort = "recent", seriesId = id)))
                })
                TvScreen.Prefixes -> TvPrefixScreen(onPick = { prefix ->
                    push(TvScreen.FilteredJav("番号前缀：$prefix", JavQuery(pageSize = 30, sort = "recent", prefix = prefix)))
                })
                is TvScreen.FilteredJav -> TvJavGridScreen(
                    state = scrollStateFor("jav-${current.title}"),
                    title = current.title,
                    query = current.query,
                    onOpenDetail = { push(TvScreen.Detail(it)) },
                )
                TvScreen.History -> TvHistoryScreen(
                    state = scrollStateFor("history"),
                    onPlayVideo = { video ->
                        push(
                            TvScreen.Player(
                                videoId = video.id,
                                locationId = video.locationId,
                                title = video.jav?.let { j -> j.zhTitle.ifBlank { j.title } } ?: video.filename,
                                javId = video.javId ?: 0L,
                            ),
                        )
                    },
                )
                TvScreen.Settings -> TvSettingsScreen(onLogout = onLogout)
                is TvScreen.Detail -> TvJavDetailScreen(
                    javId = current.javId,
                    onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                    onPlayVideo = { videoId, locationId, title, code ->
                        push(TvScreen.Player(videoId, locationId, title, current.javId, code))
                    },
                    onOpenIdolWorks = { id, name ->
                        push(
                            TvScreen.FilteredJav(
                                "女优：$name",
                                JavQuery(pageSize = 30, sort = "recent", idolIds = listOf(id)),
                            ),
                        )
                    },
                    onOpenFiltered = { title, q -> push(TvScreen.FilteredJav(title, q)) },
                )
                is TvScreen.Player -> Unit
            }
            }
        }
    }
}

/* ------------------------------------------------------------------ 首页 */

@Composable
private fun TvHomeScreen(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    onOpenAllJav: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onPlayVideo: (VideoRow) -> Unit,
    vm: TvHomeViewModel = viewModel(),
) {
    val baseUrl = remember { AppGraph.video.baseUrl().trimEnd('/') }
    val requesterFor = rememberTvFocusRestorer()
    val display = rememberTvDisplaySettings()
    val portrait = display.portrait
    val cardWidth = tvRowCardWidth(portrait)
    val columns = tvGridColumns(portrait)

    Box(Modifier.fillMaxSize()) {
        // 首页 = 纵向瀑布流：横滑区放最上面，最近添加直接铺成网格
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("首页", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(16.dp))
                    TvFocusable(onClick = onOpenAllJav, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            "全部作品",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            if (vm.continueWatching.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            "继续观看",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(vm.continueWatching, key = { "${it.id}-${it.locationId}" }) { video ->
                                val title = video.jav?.let { it.zhTitle.ifBlank { it.title } } ?: video.filename
                                val ratio = if (video.durationSec > 0) (video.watchedMs / 1000f) / video.durationSec else 0f
                                TvPosterCard(
                                    subtitleCode = video.jav?.code,
                                    coverUrl = coverOf(baseUrl, video),
                                    title = title,
                                    subtitle = "${formatDuration(video.watchedMs / 1000)} / ${formatDuration(video.durationSec)}",
                                    progress = ratio,
                                    cardWidth = cardWidth,
                                    portrait = portrait,
                                    cropPosition = video.jav?.coverCropPosition ?: 1.0,
                                    titleRows = display.titleRows,
                                    onClick = { onPlayVideo(video) },
                                )
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("最近入库", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            }

            items(vm.recentJav, key = { it.id }) { item ->
                TvPosterCard(
                                    subtitleCode = item.code,
                    focusKey = "${item.id}",
                    focusRequester = requesterFor("${item.id}"),
                    coverUrl = "$baseUrl/jav/${item.code}/cover",
                    title = item.zhTitle.ifBlank { item.title },
                    portrait = portrait,
                    cropPosition = item.coverCropPosition,
                    meta = tvMetaOf(item, display),
                    titleRows = display.titleRows,
                    onClick = { onOpenDetail(item.id) },
                )
            }

            }
        if (vm.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

private fun coverOf(baseUrl: String, video: VideoRow): String =
    if (!video.jav?.code.isNullOrBlank()) "$baseUrl/jav/${video.jav!!.code}/cover"
    else "$baseUrl/videos/${video.id}/thumbnail"

class TvHomeViewModel : ViewModel() {
    var continueWatching by mutableStateOf<List<VideoRow>>(emptyList())
        private set
    var recentJav by mutableStateOf<List<JavItem>>(emptyList())
        private set
    var history by mutableStateOf<List<VideoRow>>(emptyList())
        private set
    var entries by mutableStateOf<Map<String, com.javboss.android.data.history.WatchEntry>>(emptyMap())
        private set
    var loading by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            entries = runCatching { AppGraph.settings.watchHistory.first() }.getOrDefault(emptyMap())
            when (val result = AppGraph.video.list(offset = 0, limit = 30, sort = "watched", hideJav = false)) {
                is ApiResult.Success -> {
                    val watched = result.data.items.filter { it.watchedMs > 0 }
                    history = watched.take(12)
                    continueWatching = watched.filter { video ->
                        video.watchedMs > 5_000 &&
                            video.durationSec > 0 &&
                            video.watchedMs < video.durationSec * 900
                    }.take(12)
                }
                is ApiResult.Failure -> Unit
            }
            when (val recent = AppGraph.jav.list(JavQuery(pageSize = 20, sort = "recent"), offset = 0)) {
                is ApiResult.Success -> recentJav = recent.data.items.take(14)
                is ApiResult.Failure -> Unit
            }
            loading = false
        }
    }
}

/* ------------------------------------------------------------------ 作品库网格 */

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TvJavGridScreen(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    title: String,
    onOpenSettings: () -> Unit = {},
    query: JavQuery,
    onOpenDetail: (Long) -> Unit,
    vm: TvGridViewModel = viewModel(key = "grid-${query.hashCode()}"),
) {
    val gridState = state
    val requesterFor = rememberTvFocusRestorer()
    val baseUrl = remember { AppGraph.video.baseUrl().trimEnd('/') }
    var searchText by remember { mutableStateOf(query.search) }
    val display = rememberTvDisplaySettings()
    val portrait = display.portrait
    var showFilter by remember { mutableStateOf(false) }
    var subtitleFilter by remember { mutableStateOf(com.javboss.android.ui.jav.SubtitleFilter.ALL) }
    val shownItems = remember(subtitleFilter, vm.items) {
        when (subtitleFilter) {
            com.javboss.android.ui.jav.SubtitleFilter.ALL -> vm.items
            com.javboss.android.ui.jav.SubtitleFilter.HAS ->
                vm.items.filter { com.javboss.android.data.subtitle.SubtitleIndex.get(it.code)?.hasSubtitle == true }
            com.javboss.android.ui.jav.SubtitleFilter.NO_SUBTITLE ->
                vm.items.filter { com.javboss.android.data.subtitle.SubtitleIndex.get(it.code)?.noSubtitle == true }
            com.javboss.android.ui.jav.SubtitleFilter.NO_NEED ->
                vm.items.filter { com.javboss.android.data.subtitle.SubtitleIndex.get(it.code)?.noNeedSubtitle == true }
        }
    }

    LaunchedEffect(query) { vm.applyQuery(query) }

    LaunchedEffect(gridState, vm.items.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= vm.items.size - 8 }
            .collect { vm.loadMore() }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TvListHeader(
            title = title,
            total = vm.total,
            searchPlaceholder = "搜索番号 / 标题",
            searchText = searchText,
            onSearch = {
                searchText = it
                vm.applySearch(it.trim())
            },
            onFilter = { showFilter = true },
        )
        // 排序 / 观看状态（与手机端一致的常用组合）
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 时长 / 喜爱度：重复点击切换升降序
            val sorts = listOf(
                "recent" to ("recent_asc" to "最近入库"),
                "duration" to ("duration_asc" to "时长"),
                "favorite_rating" to ("favorite_rating_asc" to "喜爱度"),
                "watched" to ("watched_asc" to "观看次数"),
            )
            sorts.forEach { (descKey, pair) ->
                val (ascKey, label) = pair
                val isDesc = vm.sortValue == descKey
                val isAsc = vm.sortValue == ascKey
                val active = isDesc || isAsc
                TvFocusable(
                    onClick = { vm.applySort(if (isDesc) ascKey else descKey) },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = label + when {
                            isDesc -> " ↓"
                            isAsc -> " ↑"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            listOf(
                null to "全部",
                true to "已看",
                false to "未看",
            ).forEach { (value, label) ->
                val selected = vm.watchedOnly == value
                TvFocusable(
                    onClick = { vm.applyWatchedFilter(value) },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(tvGridColumns(portrait)),
            state = gridState,
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shownItems, key = { it.id }) { item ->
                TvPosterCard(
                                    subtitleCode = item.code,
                    focusKey = "${item.id}",
                    focusRequester = requesterFor("${item.id}"),
                    coverUrl = "$baseUrl/jav/${item.code}/cover",
                    title = item.zhTitle.ifBlank { item.title },
                    subtitle = null,
                    portrait = portrait,
                    cropPosition = item.coverCropPosition,
                    meta = tvMetaOf(item, display),
                    titleRows = display.titleRows,
                    onClick = { onOpenDetail(item.id) },
                )
            }
            if (vm.loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            }
        }
    }

        if (showFilter) {
            val counts = com.javboss.android.data.subtitle.SubtitleIndex.counts()
            TvFilterOverlay(
                initial = vm.currentQuery(),
                subtitleFilter = subtitleFilter,
                subtitleStats = listOf(counts.first, counts.second, counts.third),
                onSubtitleFilter = { subtitleFilter = it },
                onDismiss = { showFilter = false },
                onApply = { newQuery -> vm.applyQuery(newQuery) },
            )
        }
    }
}

class TvGridViewModel : ViewModel() {
    var items by mutableStateOf<List<JavItem>>(emptyList())
        private set
    var total by mutableStateOf(0L)
        private set
    var loadingMore by mutableStateOf(false)
        private set

    private var running = false
    private var endReached = false
    private var offset = 0
    private var query = JavQuery(pageSize = 30, sort = "recent")
    private var initialized = false
    private var loaded: List<JavItem> = emptyList()
    private var job: kotlinx.coroutines.Job? = null
    /** 查询代际：切换排序/搜索/筛选时自增，过期结果直接丢弃 */
    private var generation = 0

    fun applyQuery(newQuery: JavQuery) {
        if (initialized && newQuery == query) return
        query = newQuery
        initialized = true
        generation++
        job?.cancel()
        running = false
        items = emptyList()
        loaded = emptyList()
        total = 0
        offset = 0
        endReached = false
        loadMore()
    }

    fun applySearch(text: String) {
        if (query.search == text) return
        applyQuery(query.copy(search = text))
    }

    val sortValue: String get() = query.sort
    fun currentQuery(): JavQuery = query
    var watchedOnly: Boolean? by mutableStateOf(null)
        private set

    fun applySort(value: String) {
        if (query.sort == value) return
        applyQuery(query.copy(sort = value))
    }

    /** 已看/未看：服务端没有该参数，按本地条件过滤（与手机端一致）。 */
    fun applyWatchedFilter(value: Boolean?) {
        watchedOnly = value
        items = filterWatched(loaded)
        // 过滤后为空：继续往后翻，直到有数据或翻到底
        if (items.isEmpty() && !endReached) loadMore()
    }

    private fun filterWatched(list: List<JavItem>): List<JavItem> = when (watchedOnly) {
        true -> list.filter { it.watchedMs > 0 }
        false -> list.filter { it.watchedMs <= 0 }
        null -> list
    }

    fun loadMore() {
        if (running || endReached) return
        if (!initialized) applyQuery(query)
        running = true
        loadingMore = true
        val gen = generation
        job = viewModelScope.launch {
            var attempts = 0
            while (true) {
                when (val result = AppGraph.jav.list(query, offset = offset)) {
                    is ApiResult.Success -> {
                        if (gen != generation) return@launch
                        loaded = loaded + result.data.items
                        items = filterWatched(loaded)
                        total = result.data.total
                        offset += result.data.items.size
                        if (result.data.items.isEmpty() || offset >= total) endReached = true
                    }
                    is ApiResult.Failure -> {
                        if (gen == generation) endReached = true
                        else return@launch
                    }
                }
                attempts++
                // 本地过滤后仍为空时，自动多翻几页（已看/未看 + 升序时常见）
                if (items.isNotEmpty() || endReached || attempts >= 5) break
            }
            if (gen == generation) {
                running = false
                loadingMore = false
            }
        }
    }
}
/* ------------------------------------------------------------------ 观看记录 */

@Composable
private fun TvHistoryScreen(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    onPlayVideo: (VideoRow) -> Unit,
    vm: TvHomeViewModel = viewModel(),
) {
    val display = rememberTvDisplaySettings()
    val requesterFor = rememberTvFocusRestorer()
    val portrait = display.portrait
    LazyVerticalGrid(
        columns = GridCells.Fixed(tvGridColumns(portrait)),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("观看记录", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
        }
        items(vm.history, key = { "hh-${it.id}-${it.locationId}" }) { video ->
            val entry = vm.entries[WatchHistory.key(remember { AppGraph.video.baseUrl() }.trimEnd('/'), video.id, video.locationId)]
            val posSec = ((entry?.positionMs ?: video.watchedMs) / 1000)
            TvPosterCard(
                focusKey = "hh-${video.id}-${video.locationId}",
                focusRequester = requesterFor("hh-${video.id}-${video.locationId}"),
                coverUrl = coverOf(remember { AppGraph.video.baseUrl().trimEnd('/') }, video),
                title = video.jav?.let { it.zhTitle.ifBlank { it.title } } ?: video.filename,
                subtitle = "上次看到 ${formatDuration(posSec)}",
                progress = if (video.durationSec > 0) posSec.toFloat() / video.durationSec else 0f,
                portrait = portrait,
                cropPosition = video.jav?.coverCropPosition ?: 1.0,
                onClick = { onPlayVideo(video) },
            )
        }
    }
}

/* ------------------------------------------------------------------ 设置（简化） */

@Composable
private fun TvSettingsScreen(onLogout: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val prefs by AppGraph.settings.uiPrefs.collectAsState(
        initial = com.javboss.android.data.settings.UiPrefs(),
    )
    var serverUrl by remember { mutableStateOf("") }
    var accessKey by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var crashOpen by remember { mutableStateOf(false) }
    var modeOpen by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf("auto") }
    var pinSetupOpen by remember { mutableStateOf(false) }
    var tvChecking by remember { mutableStateOf(false) }
    var tvUpdate by remember { mutableStateOf<com.javboss.android.data.update.UpdateChecker.UpdateInfo?>(null) }
    var tvUpdateMsg by remember { mutableStateOf<String?>(null) }
    var tvSpeedTesting by remember { mutableStateOf(false) }
    var tvSpeedMsg by remember { mutableStateOf<String?>(null) }
    // 字幕代理弹窗（TV 端统一入口）
    var tvProxyDialogOpen by remember { mutableStateOf(false) }
    var tvFillDialogOpen by remember { mutableStateOf(false) }

    var pinConfirm by remember { mutableStateOf("") }
    var crashText by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        serverUrl = runCatching { AppGraph.settings.currentServerUrl() }.getOrDefault("")
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineSmall)
        }
        item { TvSectionHeader("连接") }
        item {
            TvInputField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                placeholder = "服务器地址",
                label = "服务器地址",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            TvInputField(
                value = accessKey,
                onValueChange = { accessKey = it },
                placeholder = "访问密钥（修改服务器时填写）",
                label = "访问密钥",
                password = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            TvFocusable(onClick = {
                scope.launch {
                    AppGraph.settings.setServerUrl(serverUrl.trim())
                    if (accessKey.isNotBlank()) {
                        when (val r = AppGraph.auth.login(serverUrl.trim(), accessKey)) {
                            is ApiResult.Success -> message = "已切换服务器并登录成功"
                            is ApiResult.Failure -> message = "登录失败：${r.message}"
                        }
                        accessKey = ""
                    } else {
                        message = "服务器地址已保存"
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "保存 / 连接",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                )
            }
        }
        item { TvSectionHeader("字幕") }
        item {
            TvSettingRow(
                "字幕代理",
                buildString {
                    val rev = prefs.subtitleProxyHost
                    val net = prefs.netProxyHost
                    if (rev.isBlank() && net.isBlank()) append("未设置（直连）")
                    if (rev.isNotBlank()) {
                        append(if (prefs.subtitleProxyHttps) "https" else "http")
                        append("://").append(rev).append(":").append(prefs.subtitleProxyPort)
                    }
                    if (net.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(prefs.netProxyType.uppercase()).append("://").append(net).append(":").append(prefs.netProxyPort)
                    }
                },
            ) { tvProxyDialogOpen = true }
        }
        item {
            TvSettingRow("导出已匹配字幕（TV）", "打包 ZIP 到 Android/data/…/files/subtitles/（文件名 番号.语言.srt）") {
                scope.launch {
                    runCatching {
                        val dir = java.io.File(context.getExternalFilesDir(null), "subtitles").apply { mkdirs() }
                        val out = java.io.File(dir, "javboss-subtitles.zip")
                        val r = com.javboss.android.data.subtitle.SubtitleExporter.exportZipTo(context, out)
                        message = "已导出 ${r.count} 条 → ${out.absolutePath}"
                    }.onFailure { message = "导出失败：${it.message}" }
                }
            }
        }
        item {
            TvSettingRow("导入已匹配字幕（TV）", "从同目录的 javboss-subtitles.zip 恢复字幕文件") {
                scope.launch {
                    runCatching {
                        val f = java.io.File(java.io.File(context.getExternalFilesDir(null), "subtitles"), "javboss-subtitles.zip")
                        if (!f.exists()) {
                            message = "找不到文件：${f.absolutePath}"
                        } else {
                            val n = com.javboss.android.data.subtitle.SubtitleExporter.importZipFrom(context, f)
                            message = "已导入 $n 条字幕"
                        }
                    }.onFailure { message = "导入失败：${it.message}" }
                }
            }
        }
        item {
            TvSettingRow("导出字幕索引（TV）", "写到 Android/data/…/files/subtitles/index-export.json") {
                scope.launch {
                    runCatching {
                        val dir = java.io.File(context.getExternalFilesDir(null), "subtitles").apply { mkdirs() }
                        val f = java.io.File(dir, "index-export.json")
                        f.writeText(com.javboss.android.data.subtitle.SubtitleIndex.exportJson())
                        message = "已导出：${f.absolutePath}"
                    }.onFailure { message = "导出失败：${it.message}" }
                }
            }
        }
        item {
            TvSettingRow("导入字幕索引（TV）", "从同一路径的 index-export.json 导入") {
                scope.launch {
                    runCatching {
                        val f = java.io.File(java.io.File(context.getExternalFilesDir(null), "subtitles"), "index-export.json")
                        if (!f.exists()) {
                            message = "找不到文件：${f.absolutePath}"
                        } else {
                            val n = com.javboss.android.data.subtitle.SubtitleIndex.importJson(f.readText())
                            message = "已导入：更新 $n 条"
                        }
                    }.onFailure { message = "导入失败：${it.message}" }
                }
            }
        }
        item {
            val fillState by com.javboss.android.data.subtitle.SubtitleFill.state.collectAsState()
            TvSettingRow(
                "批量补齐字幕",
                if (fillState.running) "补齐中 ${fillState.done}/${fillState.total}"
                else if (fillState.total > 0) "上次：成功 ${fillState.filled} · 无匹配 ${fillState.noMatch}"
                else "点开后可选择范围并开始",
            ) { tvFillDialogOpen = true }
        }
        item {
            TvSettingRow("仅中文字幕", if (prefs.subtitleChineseOnly) "开" else "关") {
                scope.launch { AppGraph.settings.setSubtitleChineseOnly(!prefs.subtitleChineseOnly) }
            }
        }
        item { TvSectionHeader("显示与外观") }
        item {
            TvSettingRow(
                "界面模式",
                when (prefs.uiMode) {
                    "tv" -> "电视"
                    "tablet" -> "平板"
                    "phone" -> "手机"
                    else -> "自动（按设备）"
                },
            ) { modeOpen = true }
        }
        item {
            TvSettingRow("播放器渲染", if (prefs.playerSurfaceView) "SurfaceView" else "TextureView（默认）") {
                scope.launch {
                    AppGraph.settings.setPlayerSurfaceView(!prefs.playerSurfaceView)
                    message = if (!prefs.playerSurfaceView) "已切到 SurfaceView（部分机型更省电）"
                    else "已切回 TextureView（更稳）"
                }
            }
        }
        item { TvSectionHeader("安全") }
        item {
            TvSettingRow("电视密码锁", if (prefs.tvPinEnabled) "已开启" else "未开启") {
                scope.launch {
                    if (prefs.tvPinEnabled) {
                        AppGraph.settings.setTvPinEnabled(false)
                        message = "已关闭电视密码锁"
                    } else {
                        pinSetupOpen = true
                    }
                }
            }
        }
        item { TvSectionHeader("更新") }
        item {
            TvSettingRow(
                "检查更新",
                tvUpdateMsg ?: if (tvChecking) "检查中…" else "当前 v" + com.javboss.android.BuildConfig.VERSION_NAME,
            ) {
                scope.launch {
                    tvChecking = true
                    tvUpdateMsg = null
                    val r = com.javboss.android.data.update.UpdateChecker
                        .check(com.javboss.android.BuildConfig.VERSION_NAME)
                    tvChecking = false
                    tvUpdateMsg = r.fold(
                        onSuccess = { info ->
                            if (!com.javboss.android.data.update.UpdateChecker.isNewer(
                                    info.latestVersion,
                                    com.javboss.android.BuildConfig.VERSION_NAME,
                                )
                            ) {
                                "已是最新版本" + (if (info.latestVersion != com.javboss.android.BuildConfig.VERSION_NAME)
                                    "（GitHub 上最新 v" + info.latestVersion + " 低于当前版本）" else "")
                            } else {
                                "发现新版本 v" + info.latestVersion + "（到手机端更新，或浏览器打开 Releases）"
                            }
                        },
                        onFailure = { "检查失败：" + (it.message ?: "") },
                    )
                }
            }
        }
        item {
            TvSettingRow(
                "下载镜像",
                com.javboss.android.data.update.UpdateMirror.PRESETS
                    .firstOrNull { it.first == prefs.updateMirror }?.second ?: "GitHub 直连",
            ) {
                scope.launch {
                    val presets = com.javboss.android.data.update.UpdateMirror.PRESETS
                    val idx = presets.indexOfFirst { it.first == prefs.updateMirror }.coerceAtLeast(0)
                    val next = presets[(idx + 1) % presets.size]
                    AppGraph.settings.setUpdateMirror(next.first)
                    message = "下载镜像：" + next.second
                }
            }
        }
        item {
            TvSettingRow(
                "镜像测速",
                tvSpeedMsg ?: if (tvSpeedTesting) "测速中…" else "自动测速并应用最快镜像",
            ) {
                scope.launch {
                    tvSpeedTesting = true
                    tvSpeedMsg = "测速中…"
                    runCatching {
                        val info = com.javboss.android.data.update.UpdateChecker
                            .check(com.javboss.android.BuildConfig.VERSION_NAME).getOrNull()
                        val apk = info?.apkUrl
                        if (apk.isNullOrBlank()) {
                            tvSpeedMsg = "暂时拿不到下载地址，稍后再试"
                        } else {
                            val results = com.javboss.android.data.update.UpdateMirror.PRESETS.map { (m, label) ->
                                com.javboss.android.data.update.UpdateMirrorTester.test(m, apk)
                                    .let { spd -> spd.copy(label = label) }
                            }.filter { it.ok }.sortedByDescending { it.kbps }
                            val best = results.firstOrNull()
                            if (best == null) {
                                tvSpeedMsg = "所有镜像都不通，建议用直连"
                            } else {
                                AppGraph.settings.setUpdateMirror(best.mirror)
                                AppGraph.settings.setUpdateMirrorLastTest(System.currentTimeMillis())
                                tvSpeedMsg = "已选：" + best.label + "（" + best.kbps + " KB/s）"
                            }
                        }
                    }.onFailure { tvSpeedMsg = "测速失败：" + (it.message ?: "") }
                    tvSpeedTesting = false
                }
            }
        }
        item {
            TvSettingRow("打开 Releases 页面", "在浏览器中下载最新版") {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/guiyyo/javboss-android/releases"),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        item { TvSectionHeader("其他") }
        item {
            TvSettingRow("诊断日志", "查看最近的崩溃信息") {
                crashText = com.javboss.android.core.log.Diagnostics.buildReport(context)
                crashOpen = true
            }
        }
        item { TvSettingRow("退出登录", "") { onLogout() } }
        item {
            TvSettingRow(
                "版本",
                "v" + com.javboss.android.BuildConfig.VERSION_NAME,
            ) { }
        }
        message?.let { msg ->
            item {
                Text(msg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (tvProxyDialogOpen) {
        TvSubtitleProxyDialog(onDismiss = { tvProxyDialogOpen = false }, prefs = prefs)
    }
    if (tvFillDialogOpen) {
        TvBatchFillDialog(onDismiss = { tvFillDialogOpen = false })
    }
    if (crashOpen) {
        TvCrashDialog(text = crashText, onDismiss = { crashOpen = false })
    }
    if (modeOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { modeOpen = false },
            title = { Text("界面模式") },
            text = {
                Column {
                    listOf(
                        "auto" to "自动（按设备）",
                        "tv" to "电视",
                        "tablet" to "平板",
                        "phone" to "手机",
                    ).forEach { pair ->
                        TvFocusable(
                            onClick = {
                                scope.launch { AppGraph.settings.setUiMode(pair.first) }
                                modeOpen = false
                                message = "界面模式：" + pair.second
                            },
                            shape = RoundedCornerShape(8.dp),
                            flat = true,
                        ) { Text(pair.second, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { modeOpen = false }) { Text("关闭") } },
        )
    }
    if (pinSetupOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pinSetupOpen = false },
            title = { Text("设置电视密码") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = pinInput,
                    onValueChange = { v -> pinInput = v.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("4~6 位数字") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput.length >= 4) {
                        scope.launch {
                            AppGraph.settings.setTvPinEnabled(true)
                            pinSetupOpen = false
                            pinInput = ""
                            message = "已开启电视密码锁"
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { pinSetupOpen = false }) { Text("取消") } },
        )
    }
    }
}

/* ---------------- TV 设置用的两个小组件 ---------------- */

@Composable
private fun TvSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun TvSettingRow(label: String, value: String, onClick: () -> Unit) {
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    TvFocusable(
        onClick = {
            onClick()
            scope.launch {
                kotlinx.coroutines.delay(80)
                runCatching { focus.requestFocus() }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus),
        shape = RoundedCornerShape(10.dp),
        outlined = true,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 字幕代理弹窗：两个开关（网络代理 / 字幕源反代） */
@Composable
private fun TvSubtitleProxyDialog(
    onDismiss: () -> Unit,
    prefs: com.javboss.android.data.settings.UiPrefs,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var netOn by remember { mutableStateOf(prefs.netProxyHost.isNotBlank()) }
    var netHost by remember { mutableStateOf(prefs.netProxyHost) }
    var netPort by remember { mutableStateOf(prefs.netProxyPort.toString()) }
    var netSocks by remember { mutableStateOf(prefs.netProxyType.equals("socks", true)) }
    var revOn by remember { mutableStateOf(prefs.subtitleProxyHost.isNotBlank()) }
    var revHost by remember { mutableStateOf(prefs.subtitleProxyHost) }
    var revPort by remember { mutableStateOf(prefs.subtitleProxyPort.toString()) }
    var revHttps by remember { mutableStateOf(prefs.subtitleProxyHttps) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(0.8f).heightIn(max = 470.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("字幕代理", style = MaterialTheme.typography.titleLarge)
                TvSettingRow("网络代理", if (netOn) "开" else "关") {
                    netOn = !netOn
                    if (netOn) revOn = false   // 二选一
                }
                if (netOn) {
                    TvInputField(
                        value = netHost,
                        onValueChange = { netHost = it },
                        placeholder = "192.168.1.1",
                        label = "IP / 主机",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TvInputField(
                        value = netPort,
                        onValueChange = { v -> netPort = v.filter { it.isDigit() }.take(5) },
                        placeholder = "7890",
                        label = "端口",
                        digitsOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TvSettingRow("类型", if (netSocks) "SOCKS5" else "HTTP") { netSocks = !netSocks }
                }
                TvSettingRow("字幕源反代", if (revOn) "开" else "关") {
                    revOn = !revOn
                    if (revOn) netOn = false   // 二选一
                }
                if (revOn) {
                    TvInputField(
                        value = revHost,
                        onValueChange = { revHost = it },
                        placeholder = "192.168.1.1",
                        label = "IP / 主机",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TvInputField(
                        value = revPort,
                        onValueChange = { v -> revPort = v.filter { it.isDigit() }.take(5) },
                        placeholder = "8899",
                        label = "端口",
                        digitsOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TvSettingRow("协议", if (revHttps) "HTTPS" else "HTTP") { revHttps = !revHttps }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f))
                    TvFocusable(onClick = onDismiss, shape = RoundedCornerShape(8.dp), flat = true) {
                        Text("取消", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                    TvFocusable(
                        onClick = {
                            scope.launch {
                                AppGraph.settings.setNetProxyHost(if (netOn) netHost.trim() else "")
                                AppGraph.settings.setNetProxyPort(netPort.toIntOrNull() ?: 7890)
                                AppGraph.settings.setNetProxyType(if (netSocks) "socks" else "http")
                                AppGraph.settings.setSubtitleProxyHost(if (revOn) revHost.trim() else "")
                                AppGraph.settings.setSubtitleProxyPort(revPort.toIntOrNull() ?: 8899)
                                AppGraph.settings.setSubtitleProxyHttps(revHttps)
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "保存",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 「批量补齐字幕」弹窗：选范围 + 开始/停止 + 进度 */
@Composable
private fun TvBatchFillDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fillState by com.javboss.android.data.subtitle.SubtitleFill.state.collectAsState()
    val manual by com.javboss.android.data.subtitle.SubtitleFill.manual.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { com.javboss.android.data.subtitle.SubtitleFill.init(context) }
    }
    var tvReviewOpen by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf(0) }
    var interval by remember { mutableStateOf(2000L) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 1500.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("批量补齐字幕", style = MaterialTheme.typography.titleLarge)
                Text(
                    "自动搜索并下载缺失字幕：已有字幕/无字幕/无需字幕会跳过；番号必须完全一致才下载；人工翻译优先。任务不会自动开始。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("范围", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20 to "前 20 部", 100 to "前 100 部", 0 to "全部").forEach { pair ->
                        TvFocusable(
                            onClick = { if (!fillState.running) limit = pair.first },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                (if (limit == pair.first) "● " else "○ ") + pair.second,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = if (limit == pair.first) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Text("请求间隔", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1500L to "1.5s", 2000L to "2s", 3000L to "3s").forEach { pair ->
                        TvFocusable(
                            onClick = { if (!fillState.running) interval = pair.first },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                (if (interval == pair.first) "● " else "○ ") + pair.second,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = if (interval == pair.first) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (fillState.total > 0) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (fillState.done.toFloat() / fillState.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(fillState.message, style = MaterialTheme.typography.bodySmall)
                Text(
                    "进度 " + fillState.done + "/" + fillState.total +
                        " · 成功 " + fillState.filled +
                        " · 无匹配 " + fillState.noMatch +
                        " · 失败 " + fillState.failed,
                    style = MaterialTheme.typography.labelSmall,
                )
                TvFocusable(
                    onClick = { if (manual.isNotEmpty()) tvReviewOpen = true },
                    shape = RoundedCornerShape(8.dp),
                    flat = true,
                ) {
                    Text(
                        "待复核 " + manual.size + " 部（点开查看）›",
                        color = if (manual.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (manual.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvFocusable(
                            onClick = {
                                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    com.javboss.android.data.subtitle.SubtitleFill.markAllManualNoSubtitle(context)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "全部标记无字幕",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TvFocusable(
                            onClick = {
                                com.javboss.android.data.subtitle.SubtitleFill.retryManual(context)
                            },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "全部重试",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f))
                    TvFocusable(onClick = onDismiss, shape = RoundedCornerShape(8.dp), flat = true) {
                        Text("关闭", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                    TvFocusable(
                        onClick = {
                            if (fillState.running) {
                                com.javboss.android.data.subtitle.SubtitleFill.stop()
                            } else {
                                com.javboss.android.data.subtitle.SubtitleFill.start(
                                    context, limit = limit, intervalMs = interval,
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            if (fillState.running) "停止" else "开始补齐",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
    if (tvReviewOpen) {
        TvReviewDialog(onDismiss = { tvReviewOpen = false })
    }
}

@Composable
private fun TvCrashDialog(text: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("诊断日志") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(
                    androidx.compose.foundation.rememberScrollState(),
                ),
            ) { Text(text, style = MaterialTheme.typography.bodySmall) }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = {
                    runCatching {
                        val ctx = com.javboss.android.di.AppGraph.contextOrNull()
                        if (ctx != null) {
                            val dir = java.io.File(ctx.getExternalFilesDir(null), "logs").apply { mkdirs() }
                            java.io.File(dir, "javboss-log.txt").writeText(text)
                        }
                    }
                }) { Text("导出到文件") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}
