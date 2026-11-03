package com.javboss.android.data.subtitle

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 字幕仓库：聚合「在线搜索(SubtitleCat)」与「本地文件」，负责下载 / 编码归一 / 偏移 / 缓存 / 记忆选择。
 *
 * 缓存结构：
 *  - filesDir/subtitles/<CODE>/active.srt      当前字幕（永远转换为 SRT）
 *  - filesDir/subtitles/<CODE>/last_search.tsv 上次搜索结果（避免每次重开面板都要重新等）
 */
class SubtitleRepository(
    private val context: Context,
    private val settings: com.javboss.android.data.settings.SettingsStore,
) {
    private val cat = SubtitleCatProvider(
        baseUrlProvider = { runCatching { settings.uiPrefs.first().subtitleCatBase }.getOrDefault("") },
        proxyProvider = { runCatching { settings.subtitleProxyUrl() }.getOrDefault("") },
        netProxyProvider = { runCatching { settings.netProxy() }.getOrNull() },
    )
    private val local = LocalSubtitleProvider(context)

    /* ------------------------------------------------------------- 搜索 */

    /**
     * 在线搜索（多源轮询）：逐个源尝试，单源 28 秒超时（实测站方响应 10~20s），超时/失败换下一个；
     * 全部失败才返回失败，由 UI 提示并提供重试。成功后会把结果缓存下来。
     */
    suspend fun searchOnline(
        code: String,
        onAttempt: ((attempt: Int, total: Int, source: String) -> Unit)? = null,
    ): Result<List<SubtitleCandidate>> {
        val sources = buildList {
            val configured = runCatching { settings.uiPrefs.first().subtitleCatBase }.getOrDefault("")
                .trim().trimEnd('/')
            if (configured.isNotBlank()) add(configured)
            addAll(SubtitleCatProvider.DEFAULT_SOURCES)
        }.distinct()

        var lastError: String? = null
        for ((index, base) in sources.withIndex()) {
            onAttempt?.invoke(index + 1, sources.size, base)
            val result = runCatching {
                kotlinx.coroutines.withTimeout(28_000) { cat.searchWithBase(base, code) }
            }
            var list = result.getOrNull()
            if (!list.isNullOrEmpty()) {
                // 中文优先排序；开启「仅中文字幕」时只保留中文（避免白等非中文的详情页）
                list = list.sortedChineseFirst()
                val chineseOnly = runCatching { settings.uiPrefs.first().subtitleChineseOnly }.getOrDefault(true)
                if (chineseOnly) {
                    val zh = list.filter { isChineseSubtitle(it) }
                    if (zh.isNotEmpty()) list = zh
                }
                runCatching { saveSearchCache(code, list) }
                // 后台预热第 1 个候选的下载地址（详情页很慢，提前解析好，用户点「加载」就不用等）
                prewarm(code, list)
                return Result.success(list)
            }
            lastError = result.exceptionOrNull()?.message ?: "无结果"
            android.util.Log.i("JavBossSub", "source $base failed: $lastError")
        }
        return Result.failure(IllegalStateException("所有字幕源都失败（最后一次：$lastError）"))
    }

    suspend fun findLocal(code: String): List<SubtitleCandidate> {
        val folder = runCatching { settings.uiPrefs.first().subtitleFolder }.getOrDefault("")
        return local.find(code, folder)
    }

    /* --------------------------------------------------------- 搜索缓存 */

    private fun cacheFile(code: String) = File(dirFor(code), "last_search.tsv")

    private val TAB = 9.toChar().toString()
    private val NL = 10.toChar().toString()

    /** 上次搜索结果（无缓存返回空表）。 */
    fun cachedSearch(code: String): List<SubtitleCandidate> = runCatching {
        val f = cacheFile(code)
        if (!f.exists()) return emptyList()
        f.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val c = line.split(TAB)
                if (c.size < 4) return@mapNotNull null
                SubtitleCandidate(
                    id = c[0],
                    title = c[1],
                    language = c[2],
                    source = c[3],
                    detailUrl = c.getOrNull(4)?.takeIf { it.isNotBlank() && it != "-" },
                    downloadUrl = c.getOrNull(5)?.takeIf { it.isNotBlank() && it != "-" },
                    localUri = c.getOrNull(6)?.takeIf { it.isNotBlank() && it != "-" },
                )
            }
    }.getOrDefault(emptyList())

    private fun saveSearchCache(code: String, list: List<SubtitleCandidate>) {
        runCatching {
            val f = cacheFile(code)
            f.parentFile?.mkdirs()
            fun clean(v: String?): String =
                (v ?: "").replace(TAB, " ").replace(NL, " ").ifBlank { "-" }
            f.writeText(
                list.take(80).joinToString(NL) { c ->
                    listOf(
                        c.id,
                        c.title,
                        c.language,
                        c.source,
                        clean(c.detailUrl),
                        clean(c.downloadUrl),
                        clean(c.localUri),
                    ).joinToString(TAB)
                },
                Charsets.UTF_8,
            )
        }
    }

    /* --------------------------------------------------- 安装任务（与 UI 解耦） */
    //
    // 关键点：下载/转换在仓库自带的 IO 作用域里跑。
    // 弹窗关闭、页面重组、协程被取消都不会中断任务，状态留在 installState 里，
    // 下次打开面板还能看到「成功/失败 + 原因」。

    data class InstallProgress(
        val code: String,
        val candidateId: String,
        val title: String,
        val stage: String,
        val progress: Float? = null,
        val done: Boolean = false,
        val error: String? = null,
        val installed: InstalledSubtitle? = null,
    )

    private val _installState = MutableStateFlow<InstallProgress?>(null)
    val installState: StateFlow<InstallProgress?> = _installState.asStateFlow()

    private val taskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun installAsync(code: String, candidate: SubtitleCandidate) {
        // 失败自动换下一条候选（源站详情页经常超时，换一条成功率明显更高）
        val fallbacks = runCatching {
            cachedSearch(code).filter { it.id != candidate.id }.take(2)
        }.getOrDefault(emptyList())
        val queue = listOf(candidate) + fallbacks

        _installState.value = InstallProgress(
            code = code,
            candidateId = candidate.id,
            title = candidate.title,
            stage = "准备下载…",
        )
        taskScope.launch {
            var lastError: String? = null
            queue.forEachIndexed { index, c ->
                val cur = _installState.value
                if (cur != null && cur.code != code) return@launch // 已经换成别的番号了
                _installState.value = InstallProgress(
                    code = code,
                    candidateId = c.id,
                    title = c.title,
                    stage = if (index == 0) "准备下载…" else "第 ${index + 1} 个候选…",
                )
                val result = runCatching {
                    install(code, c) { p ->
                        val st = _installState.value
                        if (st != null && !st.done) {
                            _installState.value = st.copy(stage = "下载中…", progress = p)
                        }
                    }
                }.getOrElse { e -> Result.failure(e) }

                if (result.isSuccess) {
                    val inst = result.getOrNull()!!
                    _installState.value = InstallProgress(
                        code = code,
                        candidateId = c.id,
                        title = inst.name,
                        stage = "✔ 已加载",
                        progress = 1f,
                        done = true,
                        installed = inst,
                    )
                    android.util.Log.i("JavBossSub", "installAsync ok: ${inst.name}")
                    return@launch
                }
                lastError = result.exceptionOrNull()?.message ?: "未知错误"
                android.util.Log.w("JavBossSub", "candidate ${c.id} failed: $lastError")
            }
            _installState.value = InstallProgress(
                code = code,
                candidateId = candidate.id,
                title = candidate.title,
                stage = "✖ 加载失败",
                done = true,
                error = lastError ?: "全部候选都失败",
            )
        }
    }

    fun clearInstallState() {
        _installState.value = null
    }

    /* --------------------------------------------- 已解析下载地址缓存 */

    private fun resolvedFile(code: String) = File(dirFor(code), "resolved.tsv")

    fun cachedResolved(code: String, candidateId: String): String? = runCatching {
        val f = resolvedFile(code)
        if (!f.exists()) return null
        f.readLines(Charsets.UTF_8)
            .firstOrNull { it.startsWith(candidateId + TAB) }
            ?.substringAfter(TAB)
            ?.takeIf { it.startsWith("http") }
    }.getOrNull()

    /** 后台预解析下载地址（详情页响应可能 30~60s，提前跑掉）。 */
    private fun prewarm(code: String, list: List<SubtitleCandidate>) {
        val target = list.firstOrNull { it.downloadUrl == null && it.detailUrl != null } ?: return
        if (cachedResolved(code, target.id) != null) return
        taskScope.launch {
            runCatching {
                val url = cat.resolveDownload(target)
                if (!url.isNullOrBlank()) saveResolved(code, target.id, url)
                android.util.Log.i("JavBossSub", "prewarm ${target.id}: ${url ?: "failed"}")
            }
        }
    }

    private fun saveResolved(code: String, candidateId: String, url: String) {
        runCatching {
            val f = resolvedFile(code)
            f.parentFile?.mkdirs()
            val kept = if (f.exists()) {
                f.readLines(Charsets.UTF_8).filter { !it.startsWith(candidateId + TAB) }
            } else emptyList()
            f.writeText((kept + (candidateId + TAB + url)).joinToString(NL), Charsets.UTF_8)
        }
    }

    /* ------------------------------------------------------------- 安装 */

    /** 安装选中的字幕：下载/读取 → 转 SRT → 写入缓存 → 记忆选择。 */
    suspend fun install(
        code: String,
        candidate: SubtitleCandidate,
        onProgress: ((Float?) -> Unit)? = null,
    ): Result<InstalledSubtitle> = withContext(Dispatchers.IO) {
        runCatching {
            android.util.Log.i("JavBossSub", "install start: ${candidate.title} src=${candidate.source}")
            val bytes = when {
                candidate.localUri != null -> local.readBytes(candidate.localUri)
                    ?: error("读取本地字幕失败")
                candidate.downloadUrl != null || candidate.detailUrl != null -> {
                    val url = candidate.downloadUrl
                        ?: cachedResolved(code, candidate.id)
                        ?: runCatching { cat.resolveDownload(candidate) }.getOrNull()
                        ?: error("解析下载地址失败（详情页没取到 .srt 链接，可能是源站限流）")
                    runCatching { saveResolved(code, candidate.id, url) }
                    android.util.Log.i("JavBossSub", "download url: $url")
                    runCatching {
                        cat.download(url) { read, total ->
                            onProgress?.invoke(if (total > 0) (read.toFloat() / total) else null)
                        }
                    }.getOrNull() ?: error("下载失败：${url.takeLast(60)}")
                }
                else -> error("无效的字幕来源")
            }
            val rawName = candidate.localUri?.let { local.fileNameOf(it) }
                ?: candidate.title.ifBlank { "subtitle.srt" }
            android.util.Log.i("JavBossSub", "downloaded ${bytes.size} bytes, convert…")
            val text = runCatching {
                SubtitleCodec.toSrt(rawName, SubtitleCodec.decode(bytes))
            }.getOrElse { error("字幕解析失败：${it.message}") }
            if (text.isBlank()) error("字幕内容为空或格式不支持（可能下载到了网页而不是字幕）")

            val dir = dirFor(code)
            val target = File(dir, "active.srt")
            target.writeText(text, Charsets.UTF_8)

            val offset = runCatching { settings.subtitleOffset(code) }.getOrDefault(0L)
            if (offset != 0L) target.writeText(SubtitleCodec.shift(text, offset), Charsets.UTF_8)

            settings.setSubtitleSelection(code, rawName, offset)
            android.util.Log.i(
                "JavBossSub",
                "installed → ${target.absolutePath} (${target.length()}B, cues=${parseSrtCues(text).size})",
            )
            InstalledSubtitle(code = code, name = rawName, filePath = target.absolutePath, offsetMs = offset)
        }
    }

    /** 手动直链安装。 */
    suspend fun installFromUrl(code: String, url: String): Result<InstalledSubtitle> =
        install(
            code,
            SubtitleCandidate(
                id = "url:$url",
                title = url.substringAfterLast('/'),
                language = "?",
                source = "url",
                downloadUrl = url,
            ),
        )

    /* ------------------------------------------------------------- 偏移 */

    /** 调整当前字幕偏移（毫秒步进），返回更新后的文件。 */
    suspend fun shift(code: String, deltaMs: Long): Result<InstalledSubtitle> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(dirFor(code), "active.srt")
            if (!file.exists()) error("当前没有已安装的字幕")
            val current = runCatching { settings.subtitleOffset(code) }.getOrDefault(0L)
            val next = current + deltaMs
            file.writeText(SubtitleCodec.shift(file.readText(Charsets.UTF_8), deltaMs), Charsets.UTF_8)
            val name = runCatching { settings.subtitleSelection(code) }.getOrNull() ?: "subtitle.srt"
            settings.setSubtitleSelection(code, name, next)
            InstalledSubtitle(code = code, name = name, filePath = file.absolutePath, offsetMs = next)
        }
    }

    /* ------------------------------------------------------------- 读取 */

    /** 当前已安装字幕（存在才返回）。 */
    suspend fun current(code: String): InstalledSubtitle? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(dirFor(code), "active.srt")
            if (!file.exists()) return@runCatching null
            val name = settings.subtitleSelection(code) ?: "subtitle.srt"
            val offset = settings.subtitleOffset(code)
            InstalledSubtitle(code = code, name = name, filePath = file.absolutePath, offsetMs = offset)
        }.getOrNull()
    }

    /** 删除该番号的字幕缓存与记忆。 */
    suspend fun clear(code: String) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = dirFor(code)
            File(dir, "active.srt").delete()
            runCatching { settings.setSubtitleSelection(code, "", 0L) }
        }
        Unit
    }

    /* ------------------------------------------------------------- 内部 */

    private fun dirFor(code: String): File {
        val safe = code.trim().uppercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "UNKNOWN" }
        return File(File(context.filesDir, "subtitles"), safe).apply { mkdirs() }
    }
}
