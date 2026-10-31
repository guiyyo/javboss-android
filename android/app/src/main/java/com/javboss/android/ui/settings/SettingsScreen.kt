package com.javboss.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.setValue
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.data.settings.UiPrefs
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.AppCard
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * 设置页（**只影响 App**）：
 *  - 界面 / 浏览 / 播放器偏好写本地 DataStore，不修改服务器 config，网页端不受影响；
 *  - 「服务器管理」与「账号」属于服务器侧操作，按需保留；
 *  - 列表统一瀑布流（无限滚动），不再提供分页 / 每页数量设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    onOpenDirectories: () -> Unit = {},
    onOpenServerConfig: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenResources: () -> Unit = {},
    onOpenProviders: () -> Unit = {},
    onOpenTranslation: () -> Unit = {},
    onOpenDisplay: () -> Unit = {},
    onOpenTags: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onOpenDownloader: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel(),
) {
    val prefs by AppGraph.settings.uiPrefs.collectAsState(initial = UiPrefs())
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var crashLogOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var modeDialogOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var subtitleBaseDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var subtitleBaseInput by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var subtitleSelfTestDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var subtitleSelfTestInput by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var subtitleSelfTestResult by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var subtitleSelfTestRunning by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var checkingUpdate by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var updateResult by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var updateInfo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.javboss.android.data.update.UpdateChecker.UpdateInfo?>(null) }
    var mirrorDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var mirrorInput by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var testingMirror by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var mirrorEditing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var autoApplied by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var testError by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var speedResults by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<Map<String, com.javboss.android.data.update.MirrorSpeed>>(emptyMap())
    }
    var pendingMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("auto") }
    var crashText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val biometricAvailable = androidx.compose.runtime.remember {
        com.javboss.android.core.lock.BiometricGate.isAvailable(context)
    }

    LaunchedEffect(Unit) { AppGraph.config.refresh() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("设置", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard("连接") {
                OutlinedTextField(
                    value = vm.serverUrl,
                    onValueChange = vm::onServerUrlInput,
                    label = { Text("服务器地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = vm::saveServerUrl) { Text("保存地址") }
                    Text(
                        "服务端 ${AppGraph.config.string("app_version", "—")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard("浏览（仅本机）") {
                Text(
                    "以下设置只影响 App，不会改动网页端配置；列表统一瀑布流加载，不分页。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChoiceRow(
                    title = "启动页",
                    options = listOf("jav" to "JAV", "video" to "视频"),
                    selected = prefs.initialTab,
                    onSelect = { scope.launch { AppGraph.settings.setInitialTab(it) } },
                )
                ChoiceRow(
                    title = "JAV 默认排序",
                    options = listOf(
                        "recent" to "最近",
                        "release" to "发行",
                        "code" to "番号",
                        "favorite_rating" to "喜爱度",
                    ),
                    selected = prefs.javSort,
                    onSelect = { scope.launch { AppGraph.settings.setJavSort(it) } },
                )
                ChoiceRow(
                    title = "视频默认排序",
                    options = listOf("recent" to "最近", "release" to "发行", "code" to "番号"),
                    selected = prefs.videoSort,
                    onSelect = { scope.launch { AppGraph.settings.setVideoSort(it) } },
                )
            }

            SectionCard("界面模式（仅本机）") {
                NavRow(
                    "当前模式",
                    when (prefs.uiMode) {
                        "phone" -> "手机"
                        "tablet" -> "平板"
                        "tv" -> "电视"
                        else -> "自动（按屏幕）"
                    },
                ) {
                    pendingMode = prefs.uiMode
                    modeDialogOpen = true
                }
                if (modeDialogOpen) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { modeDialogOpen = false },
                        title = { Text("选择界面模式") },
                        text = {
                            Column {
                                listOf(
                                    "auto" to "自动（按屏幕大小）",
                                    "phone" to "手机",
                                    "tablet" to "平板",
                                    "tv" to "电视（遥控器界面）",
                                ).forEach { (value, label) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { pendingMode = value }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = pendingMode == value,
                                            onClick = { pendingMode = value },
                                        )
                                        Text(label, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                modeDialogOpen = false
                                scope.launch { AppGraph.settings.setUiMode(pendingMode) }
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(onClick = { modeDialogOpen = false }) { Text("取消") }
                        },
                    )
                }
                Text(
                    "电视模式会切换到遥控器/焦点导航界面；手机/平板模式强制对应布局宽度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (subtitleSelfTestDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { subtitleSelfTestDialog = false },
                    title = { Text("在线字幕源自检") },
                    text = {
                        Column {
                            androidx.compose.material3.OutlinedTextField(
                                value = subtitleSelfTestInput,
                                onValueChange = { subtitleSelfTestInput = it },
                                label = { Text("番号") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (subtitleSelfTestRunning) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    Modifier.fillMaxWidth().padding(top = 10.dp),
                                )
                            }
                            if (subtitleSelfTestResult.isNotBlank()) {
                                Text(
                                    subtitleSelfTestResult,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !subtitleSelfTestRunning,
                            onClick = {
                                val code = subtitleSelfTestInput.trim()
                                subtitleSelfTestRunning = true
                                subtitleSelfTestResult = "搜索中…"
                                scope.launch {
                                    AppGraph.subtitle.searchOnline(code)
                                        .onSuccess { list ->
                                            subtitleSelfTestResult =
                                                "搜索到 ${list.size} 条\n" +
                                                    list.take(3).joinToString("\n") { "· ${it.title} (${it.language})" }
                                            val first = list.firstOrNull()
                                            if (first != null) {
                                                subtitleSelfTestResult += "\n下载中…"
                                                AppGraph.subtitle.install(code, first)
                                                    .onSuccess { inst ->
                                                        val kb = java.io.File(inst.filePath).length() / 1024
                                                        subtitleSelfTestResult += "\n✔ ${inst.name} · ${kb}KB"
                                                    }
                                                    .onFailure { subtitleSelfTestResult += "\n✘ 安装失败：${it.message}" }
                                            }
                                        }
                                        .onFailure { subtitleSelfTestResult = "✘ 搜索失败：${it.message}" }
                                    subtitleSelfTestRunning = false
                                }
                            },
                        ) { Text("开始") }
                    },
                    dismissButton = {
                        TextButton(onClick = { subtitleSelfTestDialog = false }) { Text("关闭") }
                    },
                )
            }

            if (subtitleBaseDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { subtitleBaseDialog = false },
                    title = { Text("在线字幕源地址") },
                    text = {
                        Column {
                            androidx.compose.material3.OutlinedTextField(
                                value = subtitleBaseInput,
                                onValueChange = { subtitleBaseInput = it },
                                placeholder = { Text("https://subtitlecat.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "镜像站地址可自行替换；留空则使用默认 SubtitleCat。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            subtitleBaseDialog = false
                            scope.launch { AppGraph.settings.setSubtitleCatBase(subtitleBaseInput.trim()) }
                        }) { Text("保存") }
                    },
                    dismissButton = {
                        TextButton(onClick = { subtitleBaseDialog = false }) { Text("取消") }
                    },
                )
            }

            SectionCard("卡片显示（仅本机）") {
                val config by com.javboss.android.di.AppGraph.config.config.collectAsState()
                val webPortrait = (config["jav_card_portrait"] ?: "").let { it == "true" || it == "1" }
                NavRow(
                    "封面样式",
                    when (prefs.coverStyle) {
                        "portrait" -> "竖版（取右侧 47% 裁切）"
                        "landscape" -> "横版（完整显示 800:538）"
                        else -> "跟随网页（当前：${if (webPortrait) "竖版" else "横版"}）"
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
                NavRow(
                    "每行卡片数",
                    if (prefs.tvColumns == 0) "自动（按屏幕）" else "${prefs.tvColumns} 个",
                ) {
                    scope.launch {
                        val order = listOf(0, 1, 2, 3, 4, 5)
                        val next = order[(order.indexOf(prefs.tvColumns).coerceAtLeast(0) + 1) % order.size]
                        AppGraph.settings.setTvColumns(next)
                    }
                }
                StepperRow("标题最多行数", prefs.titleRows, listOf(1, 2, 3)) {
                    scope.launch { AppGraph.settings.setTitleRows(it) }
                }
                SwitchRow("隐藏标签", "卡片不显示标签行", prefs.hideTags) {
                    scope.launch { AppGraph.settings.setHideTags(it) }
                }
                SwitchRow("隐藏女优", "卡片不显示女优", prefs.hideIdols) {
                    scope.launch { AppGraph.settings.setHideIdols(it) }
                }
                SwitchRow("隐藏片商", "卡片不显示片商", prefs.hideStudio) {
                    scope.launch { AppGraph.settings.setHideStudio(it) }
                }
                SwitchRow("标签显示简体", "抓取标签用简体显示", prefs.tagSimplified) {
                    scope.launch { AppGraph.settings.setTagSimplified(it) }
                }
                SwitchRow("女优优先中文名", "有中文名时优先显示", prefs.idolPreferChinese) {
                    scope.launch { AppGraph.settings.setIdolPreferChinese(it) }
                }
            }

            // 字幕
            SectionCard("字幕（仅本机）") {
                val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    if (uri != null) {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                        scope.launch { AppGraph.settings.setSubtitleFolder(uri.toString()) }
                    }
                }
                NavRow(
                    "本地字幕文件夹",
                    if (prefs.subtitleFolder.isBlank()) "未选择（选一个放 .srt/.ass 的文件夹）"
                    else "已选择（按番号自动匹配）",
                ) { folderPicker.launch(null) }
                if (prefs.subtitleFolder.isNotBlank()) {
                    NavRow("清除字幕文件夹", "") {
                        scope.launch { AppGraph.settings.setSubtitleFolder("") }
                    }
                }
                NavRow(
                    "在线源自检",
                    subtitleSelfTestResult.ifBlank { "测试搜索 + 下载是否可用" },
                ) {
                    subtitleSelfTestInput = "SNOS-079"
                    subtitleSelfTestResult = ""
                    subtitleSelfTestDialog = true
                }
                NavRow("在线字幕源", prefs.subtitleCatBase) {
                    subtitleBaseInput = prefs.subtitleCatBase
                    subtitleBaseDialog = true
                }
                Text(
                    "播放时点「字幕」按钮即可搜索/加载；加载后按番号记忆，下次自动套用。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("隐私与锁") {
                SwitchRow(
                    "指纹锁",
                    if (biometricAvailable) "冷启动与设备锁屏后重新进入需要解锁" else "当前设备未设置指纹/锁屏密码",
                    prefs.appLockEnabled && biometricAvailable,
                ) { enabled ->
                    if (!biometricAvailable) return@SwitchRow
                    scope.launch {
                        AppGraph.settings.setAppLockEnabled(enabled)
                        AppGraph.lock.syncWithPreference(enabled)
                    }
                }
                SwitchRow(
                    "隐私模式",
                    "开启后禁止系统截屏与任务预览（播放器内「截帧」不受影响）",
                    prefs.privacyMode,
                ) { enabled ->
                    scope.launch {
                        AppGraph.settings.setPrivacyMode(enabled)
                        // 立即生效（下次进入 Activity 也会按设置应用）
                        (context as? android.app.Activity)?.let { activity ->
                            if (enabled) {
                                activity.window.setFlags(
                                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                )
                            } else {
                                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                            }
                        }
                    }
                }
            }

            SectionCard("播放器（仅本机）") {
                SwitchRow("优先直连播放", "直连可任意拖动；不支持时自动回落 HLS", prefs.preferDirect) {
                    scope.launch { AppGraph.settings.setPreferDirect(it) }
                }
                StepperSpeedRow(prefs.playbackSpeed) { speed ->
                    scope.launch { AppGraph.settings.setPlaybackSpeed(speed) }
                }
                SwitchRow("返回桌面自动画中画", "关闭时切后台会直接暂停播放", prefs.autoPictureInPicture) {
                    scope.launch { AppGraph.settings.setAutoPictureInPicture(it) }
                }
                SwitchRow("断点续播", "从上次观看位置继续", prefs.resumePlayback) {
                    scope.launch { AppGraph.settings.setResumePlayback(it) }
                }
                SwitchRow("截帧自动上传", "关闭时只存手机，可在截图页手动上传", prefs.autoUploadScreenshots) {
                    scope.launch { AppGraph.settings.setAutoUploadScreenshots(it) }
                }
            }

            SectionCard("服务器管理（会修改服务器）") {
                NavRow("目录管理", "启用开关、扫描、统计", onOpenDirectories)
                NavRow("服务器配置", "与 /config 对照（只读）", onOpenServerConfig)
                NavRow("下载中心", "任务列表、重试/取消/删除、新建磁力", onOpenDownloads)
                NavRow("下载设置", "并发数、最小体积、CloudDrive2", onOpenDownloader)
                NavRow("服务器状态", "内存/磁盘、FFmpeg、运行信息", onOpenResources)
                NavRow("数据源可用性", "各刮削数据源的连通性与延迟", onOpenProviders)
                NavRow("标签管理", "视频标签与分类的增删", onOpenTags)
                NavRow("收藏夹管理", "四类实体的收藏夹增删与排序", onOpenFavorites)
                NavRow("AI 批量翻译", "批量翻译任务：开始/停止/进度", onOpenTranslation)
            }


            SectionCard("关于 / 更新") {
                NavRow("当前版本", "v${com.javboss.android.BuildConfig.VERSION_NAME}（${com.javboss.android.BuildConfig.VERSION_CODE}）") {}
                NavRow(
                    "检查更新",
                    when {
                        checkingUpdate -> "检查中…"
                        updateResult != null -> updateResult!!
                        else -> "从 GitHub Releases 获取最新版本"
                    },
                ) {
                    if (checkingUpdate) return@NavRow
                    checkingUpdate = true
                    updateResult = null
                    scope.launch {
                        com.javboss.android.data.update.UpdateChecker
                            .check(com.javboss.android.BuildConfig.VERSION_NAME)
                            .onSuccess { info ->
                                if (info.hasUpdate) {
                                    updateInfo = info
                                    updateResult = "发现新版本 v${info.latestVersion}"
                                } else {
                                    updateResult = "已是最新版本"
                                }
                            }
                            .onFailure { updateResult = "检查失败：${it.message}" }
                        checkingUpdate = false
                    }
                }
                NavRow(
                    "下载镜像",
                    com.javboss.android.data.update.UpdateMirror.label(prefs.updateMirror),
                ) {
                    mirrorInput = ""
                    mirrorEditing = false
                    speedResults = emptyMap()
                    autoApplied = null
                    testError = null
                    mirrorDialog = true
                }
                NavRow("打开 Releases 页面", com.javboss.android.data.update.UpdateChecker.RELEASES_PAGE) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(com.javboss.android.data.update.UpdateChecker.RELEASES_PAGE),
                            ),
                        )
                    }
                }
            }

            SectionCard("账号") {
                NavRow("账号与安全", "修改访问密码、查看扩展令牌", onOpenAccount)
            }

            vm.message?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
                onClick = { vm.logout(onLoggedOut) },
                enabled = !vm.loggingOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text(if (vm.loggingOut) "退出中…" else "退出登录") }

            TextButton(
                onClick = {
                    crashText = com.javboss.android.core.log.CrashLogger.readLast(context) ?: "暂无崩溃记录 🎉"
                    crashLogOpen = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("诊断日志") }

            Text(
                "JavBoss Android v${com.javboss.android.BuildConfig.VERSION_NAME} · Client 模式 · App 设置与网页端互不影响",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }
    }

    updateInfo?.let { info ->
        com.javboss.android.ui.update.UpdateDialog(
            info = info,
            currentVersion = com.javboss.android.BuildConfig.VERSION_NAME,
            mirror = prefs.updateMirror,
            onDismiss = { updateInfo = null },
        )
    }

    if (mirrorDialog) {
        // 测速完成后按速度降序：可用优先、KB/s 高的在前，失败的排最后
        val presetsOrdered = com.javboss.android.data.update.UpdateMirror.PRESETS.sortedWith(
            compareByDescending<Pair<String, String>> { speedResults[it.first]?.ok == true }
                .thenByDescending { speedResults[it.first]?.kbps ?: -1 },
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!testingMirror) mirrorDialog = false },
            title = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("更新下载镜像", modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(
                        enabled = !testingMirror,
                        onClick = {
                            testingMirror = true
                            testError = null
                            speedResults = emptyMap()
                            scope.launch {
                                val info = com.javboss.android.data.update.UpdateChecker
                                    .check(com.javboss.android.BuildConfig.VERSION_NAME)
                                    .getOrNull()
                                val apk = info?.apkUrl
                                if (apk.isNullOrBlank()) {
                                    testError = "拿不到 APK 地址（GitHub API 不可达？）"
                                    testingMirror = false
                                    return@launch
                                }
                                val targets = com.javboss.android.data.update.UpdateMirror.PRESETS
                                    .map { it.first }
                                    .toMutableList()
                                val custom = mirrorInput.trim()
                                if (custom.isNotBlank() && !targets.contains(custom)) targets.add(custom)
                                // 并发测速（每组 4 个）
                                kotlinx.coroutines.coroutineScope {
                                    targets.chunked(4).forEach { group ->
                                        group.map { m ->
                                            async {
                                                m to com.javboss.android.data.update.UpdateMirrorTester.test(m, apk)
                                            }
                                        }.map { it.await() }.forEach { (m, r) ->
                                            speedResults = speedResults + (m to r)
                                        }
                                    }
                                }
                                // 自动应用最快的可用镜像
                                val best = speedResults.entries
                                    .filter { it.value.ok && it.value.kbps > 0 }
                                    .maxByOrNull { it.value.kbps }
                                if (best != null) {
                                    runCatching { AppGraph.settings.setUpdateMirror(best.key) }
                                    autoApplied = "${best.value.label}（${best.value.kbps} KB/s）"
                                } else {
                                    autoApplied = null
                                }
                                testingMirror = false
                            }
                        },
                    ) { Text(if (testingMirror) "测速中…" else "测速") }
                }
            },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) {
                    presetsOrdered.forEach { (value, label) ->
                        val speed = speedResults[value]
                        val s = speed
                        val speedText = when {
                            testingMirror && s == null -> "测速中…"
                            s == null -> ""
                            !s.ok -> "失败"
                            s.kbps <= 0 -> "超时（${s.ttfbMs}ms）"
                            else -> "${s.kbps} KB/s · ${s.ttfbMs}ms"
                        }
                        val speedColor = when {
                            s == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            !s.ok -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch { AppGraph.settings.setUpdateMirror(value) }
                                mirrorDialog = false
                            }.padding(vertical = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                (if (value == prefs.updateMirror) "✔ " else "") + label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (value == prefs.updateMirror) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(speedText, style = MaterialTheme.typography.labelSmall, color = speedColor)
                        }
                    }

                    // 自定义前缀：默认不聚焦、不弹键盘，点一下才编辑
                    val focusRequester = androidx.compose.runtime.remember {
                        androidx.compose.ui.focus.FocusRequester()
                    }
                    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                    androidx.compose.runtime.LaunchedEffect(mirrorEditing) {
                        if (mirrorEditing) {
                            kotlinx.coroutines.delay(60)
                            runCatching { focusRequester.requestFocus() }
                            keyboard?.show()
                        }
                    }
                    if (!mirrorEditing) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                .clickable { mirrorEditing = true }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                mirrorInput.ifBlank { "自定义前缀（可选，点这里输入）" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (mirrorInput.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        androidx.compose.material3.OutlinedTextField(
                            value = mirrorInput,
                            onValueChange = { mirrorInput = it },
                            label = { Text("自定义前缀（可选，留空=直连）") },
                            placeholder = { Text("https://ghfast.top/") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .focusRequester(focusRequester),
                        )
                    }
                    if (testError != null) {
                        Text(
                            testError!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Text(
                        "当前使用：${com.javboss.android.data.update.UpdateMirror.label(prefs.updateMirror)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    autoApplied?.let { best ->
                        Text(
                            "✔ 已自动应用最快镜像：$best",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Text(
                        "点某项可手动切换；测速后按速度降序排列（KB/s 越高越快）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // 只有填了自定义前缀才覆盖；否则保留测速自动选中的镜像
                    val custom = mirrorInput.trim()
                    if (custom.isNotBlank()) {
                        scope.launch { AppGraph.settings.setUpdateMirror(custom) }
                    }
                    mirrorDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(enabled = !testingMirror, onClick = { mirrorDialog = false }) { Text("取消") }
            },
        )
    }
    if (crashLogOpen) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { crashLogOpen = false }) {
            androidx.compose.material3.Card {
                androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                    Text("崩溃日志", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = crashText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .heightIn(max = 460.dp)
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(top = 8.dp),
                    )
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    val file = com.javboss.android.core.log.Diagnostics.buildReport(context)
                                    com.javboss.android.core.log.Diagnostics.shareFile(context, file)
                                }
                            }
                        }) { Text("分享到 QQ / 微信") }
                        TextButton(onClick = {
                            com.javboss.android.core.log.CrashLogger.clear(context)
                            crashLogOpen = false
                        }) { Text("清空", maxLines = 1, softWrap = false) }
                        TextButton(onClick = { crashLogOpen = false }) { Text("关闭", maxLines = 1, softWrap = false) }
                    }
                }
            }
        }
    }
}
/* ------------------------------------------------------------------ 组件 */

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun StepperRow(title: String, current: Int, options: List<Int>, onSelect: (Int) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            options.forEach { value ->
                FilterChip(
                    selected = value == current,
                    onClick = { onSelect(value) },
                    label = { Text(value.toString()) },
                )
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepperSpeedRow(current: Float, onSelect: (Float) -> Unit) {
    Column {
        Text("默认倍速", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                FilterChip(
                    selected = kotlin.math.abs(current - value) < 0.01f,
                    onClick = { onSelect(value) },
                    label = { Text(if (value == value.toInt().toFloat()) "${value.toInt()}.0x" else "${value}x") },
                )
            }
        }
    }
}
