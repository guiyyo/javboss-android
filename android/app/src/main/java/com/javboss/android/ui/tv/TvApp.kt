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
    data class Player(val videoId: Long, val locationId: Long, val title: String, val javId: Long) : TvScreen
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
                    onPlayVideo = { videoId, locationId, title ->
                        push(TvScreen.Player(videoId, locationId, title, current.javId))
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
            items(vm.items, key = { it.id }) { item ->
                TvPosterCard(
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
            TvFilterOverlay(
                initial = vm.currentQuery(),
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
        item { TvSectionHeader("更新") }
        item {
            TvSettingRow(
                "检查更新",
                when {
                    tvChecking -> "检查中…"
                    tvUpdate != null -> "发现新版本 v${tvUpdate!!.latestVersion}"
                    tvUpdateMsg != null -> tvUpdateMsg!!
                    else -> "v${com.javboss.android.BuildConfig.VERSION_NAME}"
                },
            ) {
                if (tvChecking) return@TvSettingRow
                tvChecking = true
                tvUpdateMsg = null
                scope.launch {
                    com.javboss.android.data.update.UpdateChecker
                        .check(com.javboss.android.BuildConfig.VERSION_NAME)
                        .onSuccess { info ->
                            if (info.hasUpdate) {
                                tvUpdate = info
                            } else {
                                tvUpdateMsg = "已是最新版本"
                                message = "已是最新版本"
                            }
                        }
                        .onFailure { tvUpdateMsg = "检查失败：${it.message}"; message = "检查失败：${it.message}" }
                    tvChecking = false
                }
            }
        }
        item {
            TvSettingRow(
                "下载镜像",
                com.javboss.android.data.update.UpdateMirror.label(prefs.updateMirror),
            ) {
                val list = com.javboss.android.data.update.UpdateMirror.PRESETS
                val idx = list.indexOfFirst { it.first == prefs.updateMirror }
                val next = list[(if (idx < 0) 0 else idx + 1) % list.size]
                scope.launch { AppGraph.settings.setUpdateMirror(next.first) }
                message = "下载镜像：${next.second}"
            }
        }
        item {
            TvSettingRow("镜像测速", if (tvSpeedMsg != null) tvSpeedMsg!! else "自动测速并应用最快") {
                if (tvSpeedTesting) return@TvSettingRow
                tvSpeedTesting = true
                tvSpeedMsg = "测速中…"
                scope.launch {
                    val apk = com.javboss.android.data.update.UpdateChecker
                        .check(com.javboss.android.BuildConfig.VERSION_NAME)
                        .getOrNull()?.apkUrl
                    if (apk.isNullOrBlank()) {
                        tvSpeedMsg = "拿不到 APK 地址"
                        tvSpeedTesting = false
                        return@launch
                    }
                    var best: com.javboss.android.data.update.MirrorSpeed? = null
                    coroutineScope {
                        com.javboss.android.data.update.UpdateMirror.PRESETS.map { it.first }.chunked(4).forEach { g ->
                            g.map { m ->
                                async {
                                    com.javboss.android.data.update.UpdateMirrorTester.test(m, apk)
                                }
                            }.map { it.await() }.forEach { r ->
                                if (r.ok && r.kbps > 0 && (best == null || r.kbps > best!!.kbps)) best = r
                            }
                        }
                    }
                    val chosen = best
                    if (chosen != null) {
                        AppGraph.settings.setUpdateMirror(chosen.mirror)
                        tvSpeedMsg = "${chosen.label} · ${chosen.kbps} KB/s"
                        message = "已应用最快镜像：${chosen.label}（${chosen.kbps} KB/s）"
                    } else {
                        tvSpeedMsg = "全部失败"
                    }
                    tvSpeedTesting = false
                }
            }
        }

        item { TvSectionHeader("显示与外观") }
        item {
            TvSettingRow(
                "界面模式",
                when (prefs.uiMode) {
                    "phone" -> "手机"
                    "tablet" -> "平板"
                    "tv" -> "电视"
                    else -> "自动"
                },
            ) {
                pendingMode = prefs.uiMode
                modeOpen = true
            }
        }
        item { TvSectionHeader("播放") }
        item {
            TvSettingRow(
                "默认排序",
                when (prefs.javSort) {
                    "duration" -> "时长↓"
                    "favorite_rating" -> "喜爱度↓"
                    "watched" -> "观看最多"
                    else -> "最近入库"
                },
            ) {
                val order = listOf("recent", "duration", "favorite_rating", "watched")
                val next = order[(order.indexOf(prefs.javSort).coerceAtLeast(0) + 1) % order.size]
                scope.launch { AppGraph.settings.setJavSort(next) }
            }
        }

        item {
            TvSettingRow(
                "每行卡片数",
                if (prefs.tvColumns == 0) "自动（按屏幕）" else "${prefs.tvColumns} 个",
            ) {
                val order = listOf(0, 3, 4, 5, 6)
                val next = order[(order.indexOf(prefs.tvColumns).coerceAtLeast(0) + 1) % order.size]
                scope.launch { AppGraph.settings.setTvColumns(next) }
            }
        }
        item { TvSectionHeader("安全") }
        item {
            TvSettingRow(
                "密码锁",
                if (prefs.tvPinEnabled && prefs.tvPinHash.isNotBlank()) "开" else "关",
            ) {
                if (prefs.tvPinEnabled) {
                    // 关闭密码锁（不需要再输密码）
                    scope.launch {
                        AppGraph.settings.setTvPinEnabled(false)
                        message = "密码锁已关闭"
                    }
                } else {
                    // 打开前先弹出「设置密码（两次输入确认）」
                    pinSetupOpen = true
                    pinInput = ""
                    pinConfirm = ""
                }
            }
        }
        if (prefs.tvPinEnabled && prefs.tvPinHash.isNotBlank()) {
        item {
            TvSettingRow(
                "超时锁定",
                if (prefs.tvIdleLockMinutes <= 0) "关闭" else "${prefs.tvIdleLockMinutes} 分钟无操作后锁屏",
            ) {
                scope.launch {
                    val order = listOf(0, 5, 10, 15, 30, 60)
                    val next = order[(order.indexOf(prefs.tvIdleLockMinutes).coerceAtLeast(0) + 1) % order.size]
                    AppGraph.settings.setTvIdleLockMinutes(next)
                    message = if (next == 0) "已关闭超时锁定" else "已设为 ${next} 分钟无操作后锁屏"
                }
            }
        }
        }
        if (prefs.tvPinEnabled) {
            item {
                TvSettingRow("修改密码", "打开设置密码窗口") {
                    pinSetupOpen = true
                    pinInput = ""
                    pinConfirm = ""
                }
            }
        }
        item {
            TvSettingRow(
                "封面样式",
                when (prefs.coverStyle) {
                    "portrait" -> "竖版（裁切填充）"
                    "landscape" -> "横版（完整显示）"
                    else -> "跟随网页"
                },
            ) {
                scope.launch {
                    val next = when (prefs.coverStyle) {
                        "" -> "portrait"
                        "portrait" -> "landscape"
                        else -> ""
                    }
                    AppGraph.settings.setCoverStyle(next)
                }
            }
        }
        item {
            TvSettingRow("优先直连播放", if (prefs.preferDirect) "开" else "关") {
                scope.launch { AppGraph.settings.setPreferDirect(!prefs.preferDirect) }
            }
        }
        item {
            TvSettingRow("默认倍速", "${prefs.playbackSpeed}x") {
                val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                val next = speeds[(speeds.indexOfFirst { it == prefs.playbackSpeed }.coerceAtLeast(0) + 1) % speeds.size]
                scope.launch { AppGraph.settings.setPlaybackSpeed(next) }
            }
        }
        item {
            TvSettingRow("退出登录", "") { onLogout() }
        }
        message?.let { text ->
            item {
                Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { TvSectionHeader("其他") }
        item {
            TvFocusable(onClick = {
                crashText = com.javboss.android.core.log.CrashLogger.readLast(context) ?: "暂无崩溃记录"
                crashOpen = true
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("诊断日志", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Text("查看", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Text(
                "JavBoss Android TV v${com.javboss.android.BuildConfig.VERSION_NAME} · " +
                    "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
        if (crashOpen) {
            TvCrashOverlay(crashText) { crashOpen = false }
        }
        tvUpdate?.let { info ->
            com.javboss.android.ui.update.UpdateDialog(
                info = info,
                currentVersion = com.javboss.android.BuildConfig.VERSION_NAME,
                mirror = prefs.updateMirror,
                onDismiss = { tvUpdate = null },
                tvMode = true,
            )
        }
        if (pinSetupOpen) {
            TvPinSetupOverlay(
                pin = pinInput,
                confirm = pinConfirm,
                onPinChange = { pinInput = it },
                onConfirmChange = { pinConfirm = it },
                onDismiss = { pinSetupOpen = false },
                onSave = {
                    when {
                        pinInput.length < 4 -> message = "密码至少 4 位数字"
                        pinInput != pinConfirm -> message = "两次输入的密码不一致"
                        else -> {
                            scope.launch {
                                AppGraph.settings.setTvPin(pinInput)
                                message = "密码已保存，密码锁已开启"
                                pinSetupOpen = false
                                pinInput = ""
                                pinConfirm = ""
                            }
                        }
                    }
                },
            )
        }
        if (modeOpen) {
            TvModeOverlay(
                current = pendingMode,
                onPick = { pendingMode = it },
                onDismiss = { modeOpen = false },
                onConfirm = {
                    modeOpen = false
                    scope.launch {
                        AppGraph.settings.setUiMode(pendingMode)
                        message = "已切换，重启应用后生效"
                    }
                },
            )
        }
    }
}
@Composable
private fun TvCrashOverlay(text: String, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler { onClose() }
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                    event.key == androidx.compose.ui.input.key.Key.Back
                ) { onClose(); true } else false
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(0.8f).widthIn(max = 900.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("诊断日志", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TvFocusable(onClick = onClose, shape = RoundedCornerShape(8.dp), flat = true) {
                        Text("关闭", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // 二维码内容 = 本机临时网页地址（微信/QQ 扫码会直接打开网页显示日志文字）
                    val url = androidx.compose.runtime.remember(text) {
                        com.javboss.android.core.log.LocalLogServer.publish(text)
                    }
                    val qr = androidx.compose.runtime.remember(url, text) {
                        url?.let { com.javboss.android.core.log.QrCodes.bitmap(it, maxChars = 300) }
                            // 兜底：本机服务不可用时，退回「文本二维码」
                            ?: com.javboss.android.core.log.QrCodes.bitmap(text, sizePx = 720, maxChars = 900)
                    }
                    if (qr != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.foundation.Image(
                                bitmap = qr,
                                contentDescription = "诊断日志二维码",
                                modifier = Modifier.size(240.dp),
                            )
                            Text(
                                url ?: "（本机服务未启动，二维码为日志文本）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 3,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                if (url != null) "微信 / QQ 扫码后在浏览器打开即可看到日志全文"
                                else "扫码可读取日志文本（如扫不出请直接看右侧）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.weight(1f).heightIn(max = 420.dp).verticalScroll(
                            androidx.compose.foundation.rememberScrollState(),
                        ),
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun TvSettingRow(label: String, value: String, onClick: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    TvFocusable(
        onClick = {
            onClick()
            // 值改变后把焦点放回该项，避免光标丢失
            scope.launch {
                kotlinx.coroutines.delay(80)
                runCatching { focus.requestFocus() }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).focusRequester(focus),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TvModeOverlay(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(0.7f),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("选择界面模式", style = MaterialTheme.typography.titleLarge)
                listOf(
                    "auto" to "自动（按屏幕大小）",
                    "phone" to "手机",
                    "tablet" to "平板",
                    "tv" to "电视（遥控器界面）",
                ).forEach { (value, label) ->
                    TvFocusable(
                        onClick = { onPick(value) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        flat = true,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (current == value) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (current == value) Text("✔", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    TvFocusable(onClick = onDismiss, shape = RoundedCornerShape(8.dp), flat = true) {
                        Text("取消", maxLines = 1, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                    TvFocusable(onClick = onConfirm, shape = RoundedCornerShape(8.dp), flat = true) {
                        Text(
                            "确定",
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun TvPinSetupOverlay(
    pin: String,
    confirm: String,
    onPinChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    // 两步：先输密码，再用数字键盘二次确认（TV 不依赖系统输入法）
    var step by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(max = 620.dp),
        ) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("设置密码锁", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (step == 0) "输入 4–6 位数字密码" else "请再次输入以确认",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TvPinPad(
                    pin = if (step == 0) pin else confirm,
                    onPinChange = { v -> if (step == 0) onPinChange(v) else onConfirmChange(v) },
                    actionLabel = if (step == 0) "下一步" else "保存",
                    onAction = {
                        if (step == 0) {
                            if (pin.length < 4) message = "密码至少 4 位数字" else { message = null; step = 1 }
                        } else {
                            when {
                                confirm.length < 4 -> message = "密码至少 4 位数字"
                                confirm != pin -> message = "两次输入的密码不一致"
                                else -> onSave()
                            }
                        }
                    },
                )
                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                TvFocusable(onClick = onDismiss, shape = RoundedCornerShape(8.dp), flat = true) {
                    Text(
                        "取消",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** 设置页分组标题。 */
@Composable
private fun TvSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}
