package com.javboss.android.data.subtitle

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 字幕来源（对外只呈现来源，不呈现「探测」过程） */
enum class SubtitleSource { NONE, ONLINE, LOCAL_FOLDER, EMBEDDED }

/** 番号级字幕状态：列表 / 详情 / 播放器共用同一份数据 */
@Serializable
data class SubtitleState(
    val code: String = "",
    /** 是否已有「偏好语言」字幕 */
    val zh: Boolean = false,
    val source: SubtitleSource = SubtitleSource.NONE,
    /** 内嵌字幕语言标签（如 chi / zho / und），仅 EMBEDDED 有意义 */
    val embeddedLang: String? = null,
    /** 自动判定的置信度：0.6=容器解析，1.0=播放实测 */
    val autoConfidence: Float = 0f,
    /** 站点无该番号字幕（客观事实，补齐时跳过） */
    val noSubtitle: Boolean = false,
    /** 用户手动标记「本片无需字幕」（自带硬字幕等） */
    val noNeedSubtitle: Boolean = false,
    val updatedAt: Long = 0L,
) {
    /** 该番号是否已有可用字幕（含「无需字幕」不算已配） */
    val hasSubtitle: Boolean
        get() = source != SubtitleSource.NONE && zh
}

@Serializable
private data class IndexFile(
    val version: Int = 2,
    val states: Map<String, SubtitleState> = emptyMap(),
)

/**
 * 字幕状态索引（L1）。
 *
 * - 存储：`filesDir/subtitles/index.json`，独立于 DataStore（避免高频写拖慢主配置）
 * - 内存：`Map<code, SubtitleState>`，列表滚动 O(1) 查表
 * - 写入点：下载完成 / 播放探测 / 手动标记 / 批量任务每步
 */
object SubtitleIndex {

    private const val FILE_NAME = "index.json"
    private const val FLUSH_DEBOUNCE_MS = 1500L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val _states = MutableStateFlow<Map<String, SubtitleState>>(emptyMap())
    val states: StateFlow<Map<String, SubtitleState>> = _states.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var indexFile: File? = null
    private var subtitleRoot: File? = null
    private var initialized = false
    private var flushJob: Job? = null

    /** 幂等初始化：加载 JSON + 从磁盘补种（已下载的 active.srt） */
    suspend fun init(context: Context) {
        if (initialized) return
        mutex.withLock {
            if (initialized) return
            val root = File(context.filesDir, "subtitles")
            subtitleRoot = root
            val file = File(root, FILE_NAME)
            indexFile = file
            val loadedRaw = withContext(Dispatchers.IO) {
                runCatching {
                    if (!file.exists()) IndexFile() else
                        json.decodeFromString(IndexFile.serializer(), file.readText())
                }.getOrDefault(IndexFile())
            }
            // 迁移：旧版本里由「容器/试播探测」写入的 EMBEDDED 全部丢弃（该功能已移除）
            val loaded = if (loadedRaw.version < 2) {
                loadedRaw.states.filterValues { it.source != SubtitleSource.EMBEDDED }
            } else {
                loadedRaw.states
            }
            val seeded = withContext(Dispatchers.IO) { seedFromDisk(root, loaded) }
            _states.value = seeded
            initialized = true
            if (seeded.size != loaded.size) scheduleFlush()
        }
    }

    /** 从 `subtitles/<CODE>/active.srt` 补种 ONLINE 状态（老用户升级后立即可用） */
    private fun seedFromDisk(root: File, base: Map<String, SubtitleState>): Map<String, SubtitleState> {
        val result = base.toMutableMap()
        val dirs = runCatching { root.listFiles() }.getOrNull() ?: return result
        for (dir in dirs) {
            if (!dir.isDirectory) continue
            val code = dir.name
            if (code.isBlank() || code == FILE_NAME) continue
            val active = File(dir, "active.srt")
            if (!active.exists() || active.length() < 32) continue
            val cur = result[code]
            if (cur == null || cur.source == SubtitleSource.NONE) {
                result[code] = SubtitleState(
                    code = code,
                    zh = true,
                    source = SubtitleSource.ONLINE,
                    autoConfidence = 1f,
                    noNeedSubtitle = cur?.noNeedSubtitle ?: false,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
        return result
    }

    fun get(code: String?): SubtitleState? = code?.let { _states.value[it.uppercase()] }

    /** 在线字幕下载并应用成功 */
    suspend fun markOnline(code: String) = mutate(code) { cur ->
        cur.copy(
            zh = true,
            source = SubtitleSource.ONLINE,
            autoConfidence = 1f,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /** 播放时实测到内嵌字幕（ground truth，覆盖容器解析结果） */
    suspend fun markEmbedded(code: String, lang: String?, preferLanguageHit: Boolean = true) = mutate(code) { cur ->
        // 已有外挂字幕时不降级为内嵌
        if (cur.source == SubtitleSource.ONLINE) return@mutate cur
        cur.copy(
            zh = preferLanguageHit,
            source = SubtitleSource.EMBEDDED,
            embeddedLang = lang ?: cur.embeddedLang,
            autoConfidence = 1f,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /** 手动标记「本片无需字幕」（硬字幕唯一兜底） */
    suspend fun setNoNeed(code: String, value: Boolean) = mutate(code) { cur ->
        cur.copy(noNeedSubtitle = value, updatedAt = System.currentTimeMillis())
    }

    /** 标记「无字幕」：站点没有该番号（补齐时跳过，但仍可在「无字幕」筛选里看到并重试） */
    suspend fun setNoSubtitle(code: String, value: Boolean) = mutate(code) { cur ->
        cur.copy(noSubtitle = value, updatedAt = System.currentTimeMillis())
    }

    /** 字幕被移除 / 片源删除 */
    suspend fun remove(code: String) {
        val key = code.uppercase()
        _states.value = _states.value - key
        scheduleFlush()
    }

    private suspend fun mutate(code: String, block: (SubtitleState) -> SubtitleState) {
        val key = code.trim().uppercase()
        if (key.isBlank()) return
        val cur = _states.value[key] ?: SubtitleState(code = key)
        val next = block(cur)
        if (next == cur) return
        _states.value = _states.value + (key to next)
        scheduleFlush()
    }

    private fun scheduleFlush() {
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(FLUSH_DEBOUNCE_MS)
            flushNow()
        }
    }

    /** 立即落盘（测试/退出时可手动调用） */
    suspend fun flushNow() {
        val file = indexFile ?: return
        val snapshot = _states.value
        withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                tmp.writeText(
                    json.encodeToString(
                        IndexFile.serializer(),
                        IndexFile(version = 2, states = snapshot),
                    ),
                )
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }
        }
    }

    /** 供界面判断角标内容 */
    fun badge(code: String?): Badge? {
        val st = get(code) ?: return null
        if (st.noNeedSubtitle) return Badge(type = BadgeType.NO_NEED, text = "")
        return when {
            st.source == SubtitleSource.ONLINE -> Badge(BadgeType.ONLINE, "中")
            st.source == SubtitleSource.LOCAL_FOLDER -> Badge(BadgeType.LOCAL, "中")
            st.source == SubtitleSource.EMBEDDED && st.zh -> Badge(BadgeType.EMBEDDED, "中·嵌")
            st.source == SubtitleSource.EMBEDDED && !st.zh -> Badge(BadgeType.UNKNOWN, "中?")
            else -> null
        }
    }

    enum class BadgeType { ONLINE, LOCAL, EMBEDDED, UNKNOWN, NO_NEED }

    data class Badge(val type: BadgeType, val text: String)

    /** 测试用：重置内存与磁盘（仅 debug 调用） */
    suspend fun clearAllForTest() {
        _states.value = emptyMap()
        flushNow()
    }

    /** 全库口径统计：已配 / 无字幕 / 无需字幕 */
    fun counts(): Triple<Int, Int, Int> {
        var online = 0; var noSub = 0; var noNeed = 0
        for (v in _states.value.values) {
            when {
                v.noNeedSubtitle -> noNeed++
                v.noSubtitle -> noSub++
                v.source == SubtitleSource.ONLINE -> online++
            }
        }
        return Triple(online, noSub, noNeed)
    }

    /** 清掉所有 EMBEDDED 判定（迁移/手动清理用） */
    suspend fun purgeEmbedded(): Int {
        val before = _states.value
        val after = before.filterValues { it.source != SubtitleSource.EMBEDDED }
        val removed = before.size - after.size
        if (removed > 0) {
            _states.value = after
            flushNow()
        }
        return removed
    }

    /** 导出索引 JSON（用于在其它设备导入，避免每台都全库扫描） */
    fun exportJson(): String {
        val snapshot = _states.value
        return json.encodeToString(IndexFile.serializer(), IndexFile(states = snapshot))
    }

    /**
     * 导入索引（合并策略：同一番号取 updatedAt 较新的那条）。
     * @return 导入/更新的条目数
     */
    suspend fun importJson(text: String): Int {
        val incoming = runCatching {
            json.decodeFromString(IndexFile.serializer(), text).states
        }.getOrDefault(emptyMap())
        if (incoming.isEmpty()) return 0
        val merged = _states.value.toMutableMap()
        var changed = 0
        for ((code, st) in incoming) {
            val cur = merged[code]
            if (cur == null || st.updatedAt >= cur.updatedAt) {
                merged[code] = st
                changed++
            }
        }
        _states.value = merged
        flushNow()
        return changed
    }
}
