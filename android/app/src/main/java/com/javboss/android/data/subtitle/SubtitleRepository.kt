package com.javboss.android.data.subtitle

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 字幕仓库：聚合「在线搜索(SubtitleCat)」与「本地文件」，负责下载 / 编码归一 / 偏移 / 缓存 / 记忆选择。
 *
 * 缓存结构：filesDir/subtitles/<CODE>/active.srt （永远转换为 SRT，方便 Media3 直接加载）
 */
class SubtitleRepository(
    private val context: Context,
    private val settings: com.javboss.android.data.settings.SettingsStore,
) {
    private val cat = SubtitleCatProvider { runCatching { settings.uiPrefs.first().subtitleCatBase }.getOrDefault("") }
    private val local = LocalSubtitleProvider(context)

    /* ------------------------------------------------------------- 搜索 */

    /**
     * 在线搜索（多源轮询）：逐个源尝试，单源 15 秒超时，超时/失败立刻换下一个；
     * 全部失败才返回失败，由 UI 提示并提供重试。
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
                kotlinx.coroutines.withTimeout(15_000) { cat.searchWithBase(base, code) }
            }
            val list = result.getOrNull()
            if (!list.isNullOrEmpty()) return Result.success(list)
            lastError = result.exceptionOrNull()?.message ?: "无结果"
            android.util.Log.i("JavBossSub", "source $base failed: $lastError")
        }
        return Result.failure(IllegalStateException("所有字幕源都失败（最后一次：$lastError）"))
    }

    suspend fun findLocal(code: String): List<SubtitleCandidate> {
        val folder = runCatching { settings.uiPrefs.first().subtitleFolder }.getOrDefault("")
        return local.findLocalSafe(code, folder)
    }

    /* ------------------------------------------------------------- 安装 */

    /** 安装选中的字幕：下载/读取 → 转 SRT → 写入缓存 → 记忆选择。 */
    suspend fun install(
        code: String,
        candidate: SubtitleCandidate,
        onProgress: ((Float?) -> Unit)? = null,
    ): Result<InstalledSubtitle> =
        withContext(Dispatchers.IO) {
            runCatching {
                android.util.Log.i("JavBossSub", "install start: ${candidate.title} src=${candidate.source}")
                val bytes = when {
                    candidate.localUri != null -> local.readBytes(candidate.localUri)
                        ?: error("读取本地字幕失败")
                    candidate.downloadUrl != null || candidate.detailUrl != null -> {
                        val url = candidate.downloadUrl ?: cat.resolveDownload(candidate)
                            ?: error("未找到字幕下载地址")
                        cat.download(url) { read, total ->
                            onProgress?.invoke(if (total > 0) (read.toFloat() / total) else null)
                        } ?: error("下载字幕失败")
                    }
                    else -> error("无效的字幕来源")
                }
                val rawName = candidate.localUri?.let { local.fileNameOf(it) }
                    ?: candidate.title.ifBlank { "subtitle.srt" }
                android.util.Log.i("JavBossSub", "downloaded ${bytes.size} bytes, convert…")
                val text = SubtitleCodec.toSrt(rawName, SubtitleCodec.decode(bytes))
                if (text.isBlank()) error("字幕内容为空或格式不支持")

                val dir = dirFor(code)
                val target = File(dir, "active.srt")
                target.writeText(text, Charsets.UTF_8)

                val offset = runCatching { settings.subtitleOffset(code) }.getOrDefault(0L)
                if (offset != 0L) target.writeText(SubtitleCodec.shift(text, offset), Charsets.UTF_8)

                settings.setSubtitleSelection(code, rawName, offset)
                android.util.Log.i("JavBossSub", "installed → ${target.absolutePath} (${target.length()}B, cues=${com.javboss.android.data.subtitle.parseSrtCues(text).size})")
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
            val dir = dirFor(code)
            val file = File(dir, "active.srt")
            if (!file.exists()) error("当前没有已安装的字幕")
            val current = runCatching { settings.subtitleOffset(code) }.getOrDefault(0L)
            val next = current + deltaMs
            // 重新从原始时间轴生成：这里以「当前文件」为基准直接平移，累加即等效
            val shifted = SubtitleCodec.shift(file.readText(Charsets.UTF_8), deltaMs)
            file.writeText(shifted, Charsets.UTF_8)
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

    suspend fun clear(code: String) = withContext(Dispatchers.IO) {
        runCatching {
            File(dirFor(code), "active.srt").delete()
            settings.setSubtitleSelection(code, "", 0L)
        }
        Unit
    }

    private fun dirFor(code: String): File {
        val safe = LocalSubtitleProvider.normalize(code).ifBlank { "unknown" }
        val dir = File(File(context.filesDir, "subtitles"), safe)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /* ------------------------------------------------------------- 扩展 */

    private suspend fun LocalSubtitleProvider.findLocalSafe(code: String, folder: String): List<SubtitleCandidate> =
        runCatching { find(code, folder) }.getOrDefault(emptyList())
}
