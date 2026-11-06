package com.javboss.android.data.subtitle

import android.content.Context
import com.javboss.android.core.net.ApiResult
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 批量补齐（S7 + S8）。
 *
 * 硬性规则（用户要求）：
 * 1. **已有字幕一律跳过**：在线外挂（ONLINE）、内嵌（EMBEDDED）、已标记「无需字幕」都跳过；
 * 2. **番号必须完全对上才下载**：候选标题/文件名里的「字母+数字」必须与番号完全一致
 *    （`JUR-897` 不会匹配到 `JUR-8970`），否则不下载，进入「待人工处理」；
 * 3. **限速**：每条之间可配置间隔（默认 2 秒），避免字幕源风控/封 IP；
 * 4. 未找到的条目进入「待人工处理」列表，可批量标记「无需字幕」或重试。
 */
object SubtitleFill {

    data class State(
        val running: Boolean = false,
        val total: Int = 0,
        val done: Int = 0,
        val filled: Int = 0,
        val skipped: Int = 0,
        val noMatch: Int = 0,
        val failed: Int = 0,
        val currentCode: String = "",
        val startedAt: Long = 0L,
        val finishedAt: Long = 0L,
        val message: String = "尚未开始",
        val intervalMs: Long = 2000L,
    ) {
        val elapsedMs: Long
            get() = if (startedAt == 0L) 0 else (if (finishedAt > 0) finishedAt else System.currentTimeMillis()) - startedAt
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** 待人工处理的番号（持久化） */
    private val _manual = MutableStateFlow<List<String>>(emptyList())
    val manual: StateFlow<List<String>> = _manual.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private lateinit var appContext: Context
    private var manualFile: File? = null
    private var stateFile: File? = null

    private fun saveState() {
        val f = stateFile ?: return
        val st = _state.value
        scope.launch {
            runCatching {
                f.parentFile?.mkdirs()
                val obj = kotlinx.serialization.json.buildJsonObject {
                    put("running", kotlinx.serialization.json.JsonPrimitive(st.running))
                    put("total", kotlinx.serialization.json.JsonPrimitive(st.total))
                    put("done", kotlinx.serialization.json.JsonPrimitive(st.done))
                    put("filled", kotlinx.serialization.json.JsonPrimitive(st.filled))
                    put("noMatch", kotlinx.serialization.json.JsonPrimitive(st.noMatch))
                    put("failed", kotlinx.serialization.json.JsonPrimitive(st.failed))
                    put("intervalMs", kotlinx.serialization.json.JsonPrimitive(st.intervalMs))
                }
                f.writeText(obj.toString())
            }
        }
    }

    /** 进程被杀后自动续跑（上次 running=true） */
    fun autoResume(context: Context) {
        val f = java.io.File(java.io.File(context.filesDir, "subtitles"), "fill.json")
        stateFile = f
        if (!f.exists()) return
        val wasRunning = runCatching {
            val o = kotlinx.serialization.json.Json.parseToJsonElement(f.readText())
                as kotlinx.serialization.json.JsonObject
            (o["running"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBoolean() ?: false
        }.getOrDefault(false)
        if (wasRunning && !_state.value.running) {
            com.javboss.android.core.log.AppLog.i("JavBossFill", "auto-resume fill after process restart")
            start(context.applicationContext, limit = 0, intervalMs = 2000L)
        }
    }

    @Volatile private var cancelRequested = false

    // ---------------- 番号严格匹配 ----------------

    /** 全角→半角 + 只保留字母/数字，其余当分隔符 */
    private fun tokens(s: String): List<String> {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            val half = when {
                ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar() // 全角 ASCII
                ch.code == 0x3000 -> ' '
                else -> ch
            }
            sb.append(if (half.isLetterOrDigit()) half.uppercaseChar() else ' ')
        }
        return sb.split(' ').filter { it.isNotBlank() }
    }

    /** 去掉分隔符后的番号（`JUR-897` → `JUR897`） */
    private fun compactCode(code: String): String = tokens(code).joinToString("")

    /** 语言/噪声词：这些 token 不参与番号拼接，避免 `CEMD-908.zh-cn` 因紧邻字母被误拒 */
    private val NOISE_TOKENS = setOf(
        "ZH", "CN", "CHS", "CHT", "CHI", "ZHO", "TW", "HK", "SG",
        "EN", "ENG", "JA", "JPN", "JP", "KO", "KOR",
        "SUB", "SUBS", "SUBTITLE", "SRT", "ASS", "VTT", "SSA",
        "BY", "TRANS", "TRANSBY", "TRANSCODE", "HARDSUB", "SOFTSUB",
        "HD", "FHD", "UHD", "4K", "8K", "1080P", "720P", "2160P",
        "UNCENSORED", "CENSORED", "LEAK", "MIDE", "AI",
    )

    /**
     * 候选名称是否与番号匹配（用户要求的归一化规则）：
     * 1. 全角转半角，**只保留字母/数字**，符号与分隔符作分词；
     * 2. 相邻 token 依次拼接，与「番号压缩串」完全相等才算命中
     *    → `CEMD-908` / `CEMD 908` / `CEMD908` / `CEMD-908.zh-cn(by transub)` 都命中；
     *    → `CEMD-9080`、`2019_Ausser Kontrolle - Halt` 都不命中；
     * 3. 纯语言/噪声 token（ZH、CN、EN、SUB、BY…）在拼接时跳过。
     */
    fun matchesCode(text: String, code: String): Boolean {
        val target = compactCode(code)
        if (target.length < 4) return false
        val ts = tokens(text)
        for (i in ts.indices) {
            val sb = StringBuilder()
            var j = i
            while (j < ts.size) {
                val tk = ts[j]
                if (tk !in NOISE_TOKENS) sb.append(tk)
                val cur = sb.toString()
                if (cur == target) return true
                if (cur.length >= target.length || sb.length > target.length) break
                j++
            }
        }
        return false
    }

    /** 从名称里提取语言（供打分用） */
    fun languageOf(text: String): String? =
        tokens(text).firstNotNullOfOrNull { SubtitleLang.normalize(it) }

    // ---------------- 状态持久化 ----------------

    @Volatile private var manualLoaded = false

    suspend fun init(context: Context) {
        appContext = context.applicationContext
        stateFile = File(File(context.filesDir, "subtitles"), "fill.json")
        val f = File(File(context.filesDir, "subtitles"), "manual.json")
        manualFile = f
        if (manualLoaded) return
        if (f.exists()) {
            runCatching {
                val arr = kotlinx.serialization.json.Json.parseToJsonElement(f.readText())
                    as kotlinx.serialization.json.JsonArray
                _manual.value = arr.mapNotNull {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
            }
            manualLoaded = true
        }
    }

    private fun saveManual() {
        val f = manualFile ?: return
        val snapshot = _manual.value
        scope.launch {
            runCatching {
                f.parentFile?.mkdirs()
                val arr = kotlinx.serialization.json.JsonArray(
                    snapshot.map { kotlinx.serialization.json.JsonPrimitive(it) },
                )
                f.writeText(arr.toString())
            }
        }
    }

    fun removeManual(code: String) {
        _manual.value = _manual.value - code
        saveManual()
    }

    fun clearManual() {
        _manual.value = emptyList()
        saveManual()
    }

    /** 手动把某条标记为「本片无需字幕」并从待人工列表移除 */
    /** 标记「本片无需字幕」：保留在待复核列表（只是状态变了），便于回看 */
    suspend fun markNoNeed(code: String) {
        runCatching { SubtitleIndex.setNoNeed(code, true) }
    }

    /** 把「待人工处理」里的全部条目标记为「本片无需字幕」并清空列表 */
    suspend fun markAllManualNoSubtitle(context: Context): Int {
        init(context)          // 先把 manual.json 读进内存
        SubtitleIndex.init(context)
        val list = _manual.value.toList()
        com.javboss.android.core.log.AppLog.i("JavBossFill", "markAllManualNoNeed: " + list.size + " items")
        var n = 0
        for (code in list) {
            runCatching {
                SubtitleIndex.setNoSubtitle(code, true)
                n++
            }
        }
        return n
    }

    /** 重试「待人工处理」里的全部条目（成功的会从列表移除） */
    fun retryManual(context: Context, intervalMs: Long = 2000L) {
        if (_state.value.running) return
        appContext = context.applicationContext
        cancelRequested = false
        job = scope.launch {
            runCatching {
                SubtitleIndex.init(context)
                init(context)
                val list = _manual.value.toList()
                _state.value = State(
                    running = true,
                    total = list.size,
                    startedAt = System.currentTimeMillis(),
                    intervalMs = intervalMs,
                    message = "重试待人工 ${list.size} 条…",
                )
                com.javboss.android.core.task.TaskForegroundService.start(
                    appContext, "重试失败字幕", "正在重新搜索…",
                )
                for (code in list) {
                    if (cancelRequested) break
                    _state.value = _state.value.copy(currentCode = code)
                    val before = _state.value.filled
                    fillOne(code)
                    if (_state.value.filled > before) {
                        _manual.value = _manual.value - code
                        saveManual()
                    }
                    saveState()
                    delay(intervalMs)
                }
                _state.value = _state.value.copy(
                    running = false,
                    finishedAt = System.currentTimeMillis(),
                    message = "重试完成",
                )
                saveState()
                com.javboss.android.core.task.TaskForegroundService.stop(appContext)
            }.onFailure { e ->
                val msg = if (e is kotlinx.coroutines.CancellationException) "已取消"
                else "重试失败：" + (e.message ?: "未知错误")
                _state.value = _state.value.copy(running = false, message = msg)
            }
        }
    }

    /**
     * 检查并升级机翻字幕：已安装的字幕若为机翻，且能匹配到人工翻译且番号一致 → 替换（旧文件备份为 active.srt.machine.bak）。
     */
    fun upgradeMachine(context: Context, intervalMs: Long = 2000L) {
        if (_state.value.running) return
        appContext = context.applicationContext
        cancelRequested = false
        job = scope.launch {
            runCatching {
                SubtitleIndex.init(context)
                init(context)
                val codes = _statesInstalled()
                _state.value = State(
                    running = true,
                    total = codes.size,
                    startedAt = System.currentTimeMillis(),
                    intervalMs = intervalMs,
                    message = "检查机翻字幕 ${codes.size} 条…",
                )
                com.javboss.android.core.task.TaskForegroundService.start(
                    appContext, "升级机翻字幕", "正在寻找更好的人工翻译…",
                )
                for (code in codes) {
                    if (cancelRequested) break
                    _state.value = _state.value.copy(currentCode = code)
                    if (lastInstalledMachine(code) == true) {
                        val res = AppGraph.subtitle.searchOnline(code)
                        val human = res.getOrNull()
                            ?.filter { matchesCode(it.title, code) && isHumanTranslated(it.title) }
                            ?.maxByOrNull { score(it, code) }
                        if (human != null) {
                            val dir = java.io.File(java.io.File(appContext.filesDir, "subtitles"), code)
                            runCatching {
                                val cur = java.io.File(dir, "active.srt")
                                if (cur.exists()) cur.copyTo(java.io.File(dir, "active.srt.machine.bak"), overwrite = true)
                            }
                            val r = AppGraph.subtitle.install(code, human)
                            if (r.isSuccess) {
                                recordHistory(code, human.title, false)
                                _state.value = _state.value.copy(filled = _state.value.filled + 1)
                            }
                        }
                    }
                    _state.value = _state.value.copy(done = _state.value.done + 1)
                    saveState()
                    delay(intervalMs)
                }
                _state.value = _state.value.copy(
                    running = false,
                    finishedAt = System.currentTimeMillis(),
                    message = "升级检查完成",
                )
                saveState()
                com.javboss.android.core.task.TaskForegroundService.stop(appContext)
            }.onFailure { e ->
                _state.value = _state.value.copy(running = false, message = "升级失败：${e.message}")
            }
        }
    }

    /** 已安装字幕的番号列表（按目录里是否有 active.srt） */
    private suspend fun _statesInstalled(): List<String> {
        val root = java.io.File(appContext.filesDir, "subtitles")
        return runCatching {
            root.listFiles()?.filter { it.isDirectory && java.io.File(it, "active.srt").exists() }
                ?.map { it.name } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun stop() {
        cancelRequested = true
        job?.cancel()
        _state.value = _state.value.copy(running = false, finishedAt = System.currentTimeMillis(), message = "已取消")
        saveState()
        com.javboss.android.core.task.TaskForegroundService.stop(appContext)
    }

    // ---------------- 主流程 ----------------

    /**
     * @param limit 0=全部；>0 仅处理前 N 部
     * @param intervalMs 每条之间的间隔（限速）
     */
    /** 匹配规则自检（debug 输出，方便回归） */
    private fun selfTest() {
        val cases = listOf(
            "CEMD-908" to "CEMD-908.zh-cn(by transub)",
            "CEMD-908" to "CEMD 908 chs",
            "CEMD-908" to "CEMD908.srt",
            "CEMD-908" to "CEMD-9080.zh.srt",
            "HALT-072" to "2019_Ausser Kontrolle - Halt",
            "SNOS-079" to "ＳＮＯＳ－０７９ 简体中文",
        )
        for ((code, name) in cases) {
            com.javboss.android.core.log.AppLog.i(
                "JavBossMatch",
                code + " vs [" + name + "] → " + matchesCode(name, code),
            )
        }
    }

    fun start(context: Context, limit: Int = 0, intervalMs: Long = 2000L) {
        com.javboss.android.core.log.AppLog.i("JavBossFill", "start() entered, running=" + _state.value.running)
        if (_state.value.running) {
            com.javboss.android.core.log.AppLog.w("JavBossFill", "start() ignored: already running")
            return
        }
        appContext = context.applicationContext
        cancelRequested = false
        job = scope.launch {
            _state.value = State(
                running = true,
                startedAt = System.currentTimeMillis(),
                intervalMs = intervalMs,
                message = "正在收集待补条目…",
            )
            com.javboss.android.core.log.AppLog.i("JavBossFill", "coroutine started")
            saveState()
            if (com.javboss.android.BuildConfig.DEBUG) selfTest()
            runCatching {
                com.javboss.android.core.task.TaskForegroundService.start(
                    appContext, "批量补齐字幕", "正在搜索并下载字幕…",
                )
                com.javboss.android.core.log.AppLog.i("JavBossFill", "foreground service requested")
                SubtitleIndex.init(context)
                init(context)

                // 1) 收集：只留「没有字幕且未标记无需」的番号
                val targets = mutableListOf<String>()
                val seen = mutableSetOf<String>()
                var offset = 0
                val pageSize = 100
                var page = 1
                while (!cancelRequested) {
                    val res = AppGraph.jav.list(
                        com.javboss.android.data.repo.JavQuery(pageSize = pageSize), offset,
                    )
                    if (res is ApiResult.Failure) break
                    val items = (res as? ApiResult.Success)?.data?.items ?: emptyList()
                    if (items.isEmpty()) break
                    for (item in items) {
                        val code = item.code.trim()
                        if (code.isBlank()) continue
                        if (!seen.add(code)) continue
                        val st = SubtitleIndex.get(code)
                        // 唯一跳过条件：本机确实已安装过字幕文件，或用户标记了「无需字幕」
                        val installed = runCatching { AppGraph.subtitle.current(code) != null }
                            .getOrDefault(false)
                        val localHit = runCatching {
                            com.javboss.android.di.AppGraph.subtitle.findLocal(code).isNotEmpty()
                        }.getOrDefault(false)
                        // 「无字幕」的条目仍保留在待复核列表里，方便随时回看重试
                        if (st?.noSubtitle == true) addManual(code)
                        if (installed || localHit || st?.noNeedSubtitle == true || st?.noSubtitle == true) continue
                        targets.add(code)
                    }
                    if (limit > 0 && targets.size >= limit) break
                    if (items.size < pageSize) break
                    offset += pageSize
                    page++
                    if (page > 60) break
                }

                val work = if (limit > 0) targets.take(limit) else targets
                com.javboss.android.core.log.AppLog.i("JavBossFill", "targets=${targets.size} work=${work.size}")
                _state.value = _state.value.copy(
                    total = work.size,
                    message = if (work.isEmpty()) "没有需要补齐的条目" else "开始补齐 ${work.size} 部（间隔 ${intervalMs / 1000}s）",
                )

                for (code in work) {
                    if (cancelRequested) break
                    _state.value = _state.value.copy(currentCode = code)
                    com.javboss.android.core.log.AppLog.i("JavBossFill", "processing $code")
                    fillOne(code)
                    saveState()
                    // 限速：避免字幕源风控
                    delay(intervalMs)
                }

                _state.value = _state.value.copy(
                    running = false,
                    finishedAt = System.currentTimeMillis(),
                    message = if (cancelRequested) "已停止" else "补齐完成",
                )
                saveState()
                com.javboss.android.core.task.TaskForegroundService.stop(appContext)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) {
                    _state.value = _state.value.copy(
                        running = false,
                        finishedAt = System.currentTimeMillis(),
                        message = "已取消",
                    )
                } else {
                    com.javboss.android.core.log.AppLog.e("JavBossFill", "fill exception: " + e.message, e)
                    _state.value = _state.value.copy(
                        running = false,
                        finishedAt = System.currentTimeMillis(),
                        message = "补齐失败：" + (e.message ?: "未知错误"),
                    )
                }
            }
        }
    }

    private suspend fun fillOne(code: String) {
        val search = AppGraph.subtitle.searchOnline(code)
        val found = search.getOrNull()
        if (found == null) {
            com.javboss.android.core.log.AppLog.w(
                "JavBossFill",
                "search failed for " + code + ": " + (search.exceptionOrNull()?.message ?: "unknown"),
            )
            _state.value = _state.value.copy(done = _state.value.done + 1, failed = _state.value.failed + 1)
            return
        }
        // 严格匹配：番号的字母+数字必须完全一致
        val matched = found.filter { c -> matchesCode(c.title, code) }
        com.javboss.android.core.log.AppLog.i(
            "JavBossFill",
            code + " candidates=" + found.size + " matched=" + matched.size +
                " titles=" + found.take(3).map { it.title.take(28) },
        )
        if (matched.isEmpty()) {
            addManual(code)
            runCatching { SubtitleIndex.setNoSubtitle(code, true) }
            _state.value = _state.value.copy(done = _state.value.done + 1, noMatch = _state.value.noMatch + 1)
            return
        }
        // 打分：优先中文、行数/时长比合理的
        val best = matched.maxByOrNull { score(it, code) } ?: matched.first()
        val install = AppGraph.subtitle.install(code, best)
        if (install.isSuccess) {
            recordHistory(code, best.title, isMachineTranslated(best.title))
            runCatching { SubtitleIndex.markOnline(code) }
            _state.value = _state.value.copy(done = _state.value.done + 1, filled = _state.value.filled + 1)
        } else {
            com.javboss.android.core.log.AppLog.w(
                "JavBossFill",
                "install failed for " + code + ": " + (install.exceptionOrNull()?.message ?: "unknown"),
            )
            addManual(code)
            runCatching { SubtitleIndex.setNoSubtitle(code, true) }
            _state.value = _state.value.copy(done = _state.value.done + 1, failed = _state.value.failed + 1)
        }
    }

    /** 机翻/AI 字幕特征词（命中则视为机翻） */
    private val MACHINE_HINTS = listOf(
        "机翻", "机器翻译", "ai翻译", "ai 翻译", "by transub", "transub",
        "whisper", "whisperjav", "gpt", "deepseek", "auto-translate", "autotranslate",
        " mt ", "mt)", "machine",
    )

    /** 人工翻译特征词（命中则视为人工，优先） */
    private val HUMAN_HINTS = listOf(
        "精翻", "人工", "手翻", "官中", "官方", "字幕组", "简日", "繁日", "特效",
    )

    fun isMachineTranslated(title: String): Boolean {
        val t = title.lowercase()
        return MACHINE_HINTS.any { t.contains(it) }
    }

    private fun isHumanTranslated(title: String): Boolean {
        val t = title.lowercase()
        return HUMAN_HINTS.any { t.contains(it) } && !isMachineTranslated(title)
    }

    /** 记录字幕历史（来源/是否机翻/时间），用于「升级替换」判断 */
    private fun recordHistory(code: String, title: String, machine: Boolean) {
        scope.launch {
            runCatching {
                val dir = java.io.File(java.io.File(appContext.filesDir, "subtitles"), code)
                dir.mkdirs()
                val f = java.io.File(dir, "history.json")
                val arr = if (f.exists()) {
                    (kotlinx.serialization.json.Json.parseToJsonElement(f.readText())
                        as? kotlinx.serialization.json.JsonArray)?.toMutableList() ?: mutableListOf()
                } else mutableListOf()
                arr.add(
                    kotlinx.serialization.json.buildJsonObject {
                        put("title", kotlinx.serialization.json.JsonPrimitive(title))
                        put("machine", kotlinx.serialization.json.JsonPrimitive(machine))
                        put("at", kotlinx.serialization.json.JsonPrimitive(System.currentTimeMillis()))
                    },
                )
                f.writeText(kotlinx.serialization.json.JsonArray(arr).toString())
            }
        }
    }

    /** 最近一次安装的字幕是否机翻 */
    private fun lastInstalledMachine(code: String): Boolean? {
        return runCatching {
            val f = java.io.File(java.io.File(appContext.filesDir, "subtitles"), "$code/history.json")
            if (!f.exists()) return null
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(f.readText())
                as? kotlinx.serialization.json.JsonArray ?: return null
            val last = arr.lastOrNull() as? kotlinx.serialization.json.JsonObject ?: return null
            (last["machine"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBoolean()
        }.getOrNull()
    }

    private fun score(c: SubtitleCandidate, code: String): Int {
        var s = 0
        val lang = languageOf(c.title) ?: c.language
        if (SubtitleLang.isPreferred(lang)) s += 40
        if (matchesCode(c.title, code)) s += 30
        // 人工翻译优先（+40），机翻降级（-30）
        if (isHumanTranslated(c.title)) s += 40 else if (isMachineTranslated(c.title)) s -= 30
        if (c.title.contains("HD") || c.title.contains("4K")) s -= 15
        return s
    }

    private fun addManual(code: String) {
        if (code in _manual.value) return
        _manual.value = _manual.value + code
        saveManual()
    }
}
