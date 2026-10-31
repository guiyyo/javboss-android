package com.javboss.android.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.VideoRow
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.auth.LoginScreen
import com.javboss.android.ui.favorites.FavoriteGroupManageScreen
import com.javboss.android.ui.history.HistoryScreen
import com.javboss.android.ui.jav.JavDetailScreen
import com.javboss.android.ui.account.AccountScreen
import com.javboss.android.ui.jav.JavEditScreen
import com.javboss.android.ui.jav.JavListScreen
import com.javboss.android.ui.jav.PrefixScreen
import com.javboss.android.ui.library.IdolDetailScreen
import com.javboss.android.ui.library.IdolListScreen
import com.javboss.android.ui.library.SeriesListScreen
import com.javboss.android.ui.library.SeriesDetailScreen
import com.javboss.android.ui.library.StudioDetailScreen
import com.javboss.android.ui.library.StudioListScreen
import com.javboss.android.ui.player.PlaybackQueueItem
import com.javboss.android.ui.player.PlayerScreen
import com.javboss.android.ui.scrape.ScrapeScreen
import com.javboss.android.ui.screenshot.ScreenshotScreen
import com.javboss.android.ui.tags.TagManagementScreen
import com.javboss.android.ui.server.DirectoryScreen
import com.javboss.android.ui.server.DownloadScreen
import com.javboss.android.ui.server.DownloaderSettingsScreen
import com.javboss.android.ui.server.ProviderScreen
import com.javboss.android.ui.server.ResourceScreen
import com.javboss.android.ui.settings.SettingsScreen
import com.javboss.android.ui.settings.TranslationScreen
import com.javboss.android.ui.video.VideoListScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

private sealed interface AuthState {
    data object Checking : AuthState
    data object LoggedOut : AuthState
    data object LoggedIn : AuthState
}

@Composable
fun AppRoot() {
    // 界面模式：自动 / 手机 / 平板 / 电视（电视模式直接渲染 TV 版 UI）
    val modePrefs by AppGraph.settings.uiPrefs.collectAsState(initial = com.javboss.android.data.settings.UiPrefs())
    when (modePrefs.uiMode) {
        "tv" -> {
            com.javboss.android.ui.tv.TvAppRoot()
            return
        }
        "phone" -> {
            androidx.compose.runtime.CompositionLocalProvider(
                com.javboss.android.ui.common.LocalLayoutWidthOverride provides 400,
            ) { AppRootContent() }
            return
        }
        "tablet" -> {
            androidx.compose.runtime.CompositionLocalProvider(
                com.javboss.android.ui.common.LocalLayoutWidthOverride provides 900,
            ) { AppRootContent() }
            return
        }
    }
    AppRootContent()
}

@Composable
private fun AppRootContent() {
    val locked by AppGraph.lock.locked.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        AppGraph.lock.onColdStart()
    }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                kotlinx.coroutines.MainScope().launch { AppGraph.lock.onForeground() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var authState by remember { mutableStateOf<AuthState>(AuthState.Checking) }

    // 启动鉴权：带超时，网络异常也不会一直转圈（超时按未登录处理）
    LaunchedEffect(Unit) {
        val status = kotlinx.coroutines.withTimeoutOrNull(10_000) {
            runCatching { AppGraph.auth.status() }.getOrNull()
        }
        authState = if (status is com.javboss.android.core.net.ApiResult.Success && status.data.authenticated) {
            AuthState.LoggedIn
        } else {
            AuthState.LoggedOut
        }
        android.util.Log.i("JavBossAuth", "startup status=$status → $authState")
    }


    when (authState) {
        AuthState.Checking -> SplashScreen()
        AuthState.LoggedOut -> LoginScreen(onLoggedIn = { authState = AuthState.LoggedIn })
        AuthState.LoggedIn -> {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                MainScaffold(onLoggedOut = { authState = AuthState.LoggedOut })
                // 进入主界面后在后台静默检查更新（不阻塞、不转圈）
                com.javboss.android.ui.update.UpdateCheckerHost()
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec("jav", "JAV", Icons.Filled.VideoLibrary),
    TabSpec("video", "视频", Icons.Filled.Movie),
    TabSpec("idols", "女优", Icons.Filled.Person),
    TabSpec("studios", "片商", Icons.Filled.Workspaces),
    TabSpec("series", "系列", Icons.Filled.PlayCircle),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(onLoggedOut: () -> Unit) {
    val navController = rememberNavController()
    val uiPrefs by AppGraph.settings.uiPrefs.collectAsState(initial = com.javboss.android.data.settings.UiPrefs())
    LaunchedEffect(uiPrefs.initialTab) {
        if (uiPrefs.initialTab == "video") {
            navController.navigate("video") {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(com.javboss.android.data.settings.LocalUiPrefs provides uiPrefs) {
        MainScaffoldInner(navController, onLoggedOut)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffoldInner(navController: androidx.navigation.NavHostController, onLoggedOut: () -> Unit) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    // 路由可能带查询参数（如 jav?studioId={studioId}），比较时只用路径部分
    val currentRoute = (backStackEntry?.destination?.route ?: "jav").substringBefore("?")
    val rootRoutes = tabs.map { it.route }.toSet() + "settings"
    val showBottomBar = currentRoute in tabs.map { it.route }.toSet()

    Scaffold(
        topBar = {
            // 列表页把「标题 / 搜索 / 筛选 / 排序 / 设置」合并到页面内一行，此处不再占位
            if (currentRoute == "__none__") {
                TopAppBar(
                    title = {
                        Text(
                            text = tabs.firstOrNull { it.route == currentRoute }?.label
                                ?: if (currentRoute == "settings") "设置" else "JavBoss",
                        )
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "jav",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(
                route = "jav?studioId={studioId}&seriesId={seriesId}&idolIds={idolIds}&prefix={prefix}&tagIds={tagIds}&r={r}",
                arguments = listOf(
                    navArgument("studioId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("seriesId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("idolIds") { type = NavType.StringType; defaultValue = "" },
                    navArgument("prefix") { type = NavType.StringType; defaultValue = "" },
                    navArgument("tagIds") { type = NavType.StringType; defaultValue = "" },
                    navArgument("r") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val studioId = entry.arguments?.getLong("studioId")?.takeIf { it > 0 }
                val seriesId = entry.arguments?.getLong("seriesId")?.takeIf { it > 0 }
                val idolIds = entry.arguments?.getString("idolIds").orEmpty()
                    .split(",")
                    .mapNotNull { it.toLongOrNull() }
                JavListScreen(
                    initialStudioId = studioId,
                    initialSeriesId = seriesId,
                    initialIdolIds = idolIds,
                    initialPrefix = entry.arguments?.getString("prefix")?.takeIf { it.isNotBlank() },
                    initialTagIds = entry.arguments?.getString("tagIds").orEmpty()
                        .split(",").mapNotNull { it.toLongOrNull() },
                    onOpenItem = { id -> navController.navigate("javDetail/$id") },
                    onOpenPrefixes = { navController.navigate("prefixes") },
                    onOpenHistory = { navController.navigate("history") },
                    onOpenSettings = { navController.navigate("settings") },
                    onPlayVideo = { video ->
                        val t = video.jav?.let { it.zhTitle.ifBlank { it.title } } ?: video.filename
                        navController.navigate(
                            "player/${video.id}?locationId=${video.locationId}&title=${encode(t)}&javId=${video.javId ?: 0L}",
                        )
                    },
                )
            }
            composable("history") {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { video ->
                        val t = video.jav?.let { it.zhTitle.ifBlank { it.title } } ?: video.filename
                        navController.navigate(
                            "player/${video.id}?locationId=${video.locationId}&title=${encode(t)}&javId=${video.javId ?: 0L}",
                        )
                    },
                )
            }
            composable("javDetail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                JavDetailScreen(
                    itemId = id,
                    onOpenIdol = { navController.navigate("jav?idolIds=$it&r=${System.currentTimeMillis()}") },
                    onOpenStudio = { navController.navigate("jav?studioId=$it&r=${System.currentTimeMillis()}") },
                    onOpenSeries = { navController.navigate("jav?seriesId=$it&r=${System.currentTimeMillis()}") },
                    onOpenTag = { navController.navigate("jav?tagIds=$it") },
                    onPlay = { videoId, locationId, title ->
                        navController.navigate("player/$videoId?locationId=$locationId&title=${encode(title)}&javId=$id")
                    },
                    onOpenScreenshots = { videoId ->
                        navController.navigate("screenshots/$videoId")
                    },
                    onEdit = { navController.navigate("javEdit/$id") },
                )
            }
            composable("video") {
                VideoListScreen(
                    onPlay = { video: VideoRow ->
                        val title = video.jav?.let { it.zhTitle.ifBlank { it.title } } ?: video.filename
                        navController.navigate("player/${video.id}?locationId=${video.locationId}&title=${encode(title)}&javId=${video.javId ?: 0L}")
                    },
                    onOpenScrape = { video: VideoRow -> navController.navigate("scrape/${video.id}") },
                    onOpenSettings = { navController.navigate("settings") },
                    onPlayQueue = { videos: List<VideoRow> ->
                        if (videos.isNotEmpty()) {
                            val first = videos.first()
                            val queue = videos.joinToString(",") { "${it.id}:${it.locationId}" }
                            val title = first.jav?.let { it.zhTitle.ifBlank { it.title } } ?: first.filename
                            navController.navigate(
                                "player/${first.id}?locationId=${first.locationId}&title=${encode(title)}&javId=0&queue=${encode(queue)}",
                            )
                        }
                    },
                )
            }
            composable("player/{videoId}?locationId={locationId}&title={title}&javId={javId}&queue={queue}",
                arguments = listOf(
                    navArgument("videoId") { type = NavType.LongType },
                    navArgument("locationId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("javId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("queue") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val javId = entry.arguments?.getLong("javId") ?: 0L
                val queueRaw = decode(entry.arguments?.getString("queue").orEmpty())
                val initialQueue = queueRaw.split(",")
                    .mapNotNull { token ->
                        val parts = token.split(":")
                        val vid = parts.getOrNull(0)?.toLongOrNull()
                        val loc = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                        if (vid != null && vid > 0) PlaybackQueueItem(vid, loc, "Video #$vid") else null
                    }
                val videoId = entry.arguments?.getLong("videoId") ?: 0L
                val locationId = entry.arguments?.getLong("locationId") ?: 0L
                val title = decode(entry.arguments?.getString("title").orEmpty())
                PlayerScreen(
                    videoId = videoId,
                    locationId = locationId,
                    title = title,
                    onBack = { navController.popBackStack() },
                    onOpenScreenshots = { navController.navigate("screenshots/$videoId") },
                    javId = javId,
                    initialQueue = initialQueue,
                )
            }
            composable("screenshots/{videoId}", arguments = listOf(navArgument("videoId") { type = NavType.LongType })) { entry ->
                val videoId = entry.arguments?.getLong("videoId") ?: 0L
                ScreenshotScreen(videoId = videoId, onBack = { navController.popBackStack() })
            }
            composable("idols") {
                IdolListScreen(
                    onOpenIdol = { idolId ->
                        navController.navigate("jav?idolIds=$idolId&r=${System.currentTimeMillis()}")
                    },
                    onOpenSettings = { navController.navigate("settings") },
                )
            }
            composable("idol/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val idolId = entry.arguments?.getLong("id") ?: 0L
                IdolDetailScreen(
                    idolId = idolId,
                    onBack = { navController.popBackStack() },
                    onOpenWorks = { id ->
                        navController.navigate("jav?idolIds=$id") {
                            popUpTo("idols") { inclusive = false }
                        }
                    },
                )
            }
            composable("account") { AccountScreen(onBack = { navController.popBackStack() }) }
            composable("studios") {
                StudioListScreen(
                    onOpenStudio = { studio ->
                        navController.navigate("jav?studioId=${studio.id}&r=${System.currentTimeMillis()}")
                    },
                    onOpenSettings = { navController.navigate("settings") },
                )
            }
            composable("studio/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val studioId = entry.arguments?.getLong("id") ?: 0L
                StudioDetailScreen(
                    studioId = studioId,
                    onBack = { navController.popBackStack() },
                    onOpenWorks = { id -> navController.navigate("jav?studioId=$id") },
                )
            }
            composable("series") {
                SeriesListScreen(
                    onOpenSeries = { series ->
                        navController.navigate("jav?seriesId=${series.id}&r=${System.currentTimeMillis()}")
                    },
                    onOpenSettings = { navController.navigate("settings") },
                )
            }
            composable("seriesDetail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val seriesId = entry.arguments?.getLong("id") ?: 0L
                SeriesDetailScreen(
                    seriesId = seriesId,
                    onBack = { navController.popBackStack() },
                    onOpenWorks = { id -> navController.navigate("jav?seriesId=$id") },
                )
            }
            composable("settings") {
                SettingsScreen(
                    onLoggedOut = onLoggedOut,
                    onBack = { navController.popBackStack() },
                    onOpenDirectories = { navController.navigate("directories") },
            onOpenServerConfig = { navController.navigate("serverconfig") },
                    onOpenDownloads = { navController.navigate("downloads") },
                    onOpenResources = { navController.navigate("resources") },
                    onOpenProviders = { navController.navigate("providers") },
                    onOpenDownloader = { navController.navigate("downloader") },
                    onOpenTranslation = { navController.navigate("translation") },
                    onOpenTags = { navController.navigate("tags") },
                    onOpenFavorites = { navController.navigate("favorites") },
                    onOpenAccount = { navController.navigate("account") },

                )
            }
            composable("javEdit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                JavEditScreen(
                    itemId = id,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable("scrape/{videoId}", arguments = listOf(navArgument("videoId") { type = NavType.LongType })) { entry ->
                val videoId = entry.arguments?.getLong("videoId") ?: 0L
                ScrapeScreen(videoId = videoId, onBack = { navController.popBackStack() })
            }
            composable("tags") { TagManagementScreen(onBack = { navController.popBackStack() }) }
            composable("favorites") { FavoriteGroupManageScreen(onBack = { navController.popBackStack() }) }
            composable("prefixes") {
                PrefixScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPrefix = { code -> navController.navigate("jav?prefix=$code") },
                )
            }
            composable("translation") { TranslationScreen(onBack = { navController.popBackStack() }) }

            composable("directories") { DirectoryScreen(onBack = { navController.popBackStack() }) }
        composable("serverconfig") {
            com.javboss.android.ui.server.ServerConfigScreen(onBack = { navController.popBackStack() })
        }
            composable("downloads") { DownloadScreen(onBack = { navController.popBackStack() }) }
            composable("resources") { ResourceScreen(onBack = { navController.popBackStack() }) }
            composable("providers") { ProviderScreen(onBack = { navController.popBackStack() }) }
            composable("downloader") { DownloaderSettingsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun encode(value: String): String =
    // 截断 + 去掉会破坏 NavController 路由解析的字符，避免进程恢复时解析失败
    URLEncoder.encode(
        value.replace("/", " ")
            .replace("?", " ")
            .replace("#", " ")
            .replace("%", "％")
            .take(80),
        "UTF-8",
    )
private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
