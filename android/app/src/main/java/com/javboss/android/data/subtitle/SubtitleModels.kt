package com.javboss.android.data.subtitle

/** 一条可用的字幕候选（在线搜索结果 / 本地文件 / 手动直链）。 */
data class SubtitleCandidate(
    /** 稳定标识：source + 唯一路径或地址 */
    val id: String,
    val title: String,
    /** 展示用语言标签，如「中文」「日文」「?」 */
    val language: String,
    /** subtitlecat / local / url */
    val source: String,
    /** 需要二次解析的详情页（subtitlecat） */
    val detailUrl: String? = null,
    /** 可直接下载的地址 */
    val downloadUrl: String? = null,
    /** 本地文件 uri（content:// 或 file://） */
    val localUri: String? = null,
)

/** 已安装的字幕文件与偏移。 */
data class InstalledSubtitle(
    val code: String,
    val name: String,
    val filePath: String,
    val offsetMs: Long = 0L,
)

/** 一条字幕（用于自绘渲染，改偏移不需要重建播放器）。 */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/** 解析 SRT 文本为 cue 列表。 */
fun parseSrtCues(srt: String): List<SubtitleCue> {
    val cues = ArrayList<SubtitleCue>()
    val timeRe = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})")
    val blocks = srt.replace("\r\n", "\n").split("\n\n")
    for (block in blocks) {
        val lines = block.trim().lines()
        if (lines.isEmpty()) continue
        val timeLine = lines.firstOrNull { it.contains("-->") } ?: continue
        val m = timeRe.find(timeLine) ?: continue
        val g = m.groupValues
        fun toMs(h: String, mi: String, s: String, ms: String): Long {
            val milli = ms.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            return h.toLong() * 3_600_000 + mi.toLong() * 60_000 + s.toLong() * 1000 + milli
        }
        val start = toMs(g[1], g[2], g[3], g[4])
        val end = toMs(g[5], g[6], g[7], g[8])
        if (end <= start) continue
        val text = lines.dropWhile { it != timeLine }.drop(1)
            .joinToString("\n")
            .let { SubtitleCodec.stripMarkup(it) }
            .trim()
        if (text.isNotBlank()) cues.add(SubtitleCue(start, end, text))
    }
    return cues
}
