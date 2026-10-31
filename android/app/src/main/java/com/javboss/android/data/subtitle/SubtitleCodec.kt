package com.javboss.android.data.subtitle

import java.nio.charset.Charset

/**
 * 字幕文本处理：
 *  - 编码归一（UTF-8 / UTF-16 BOM / GBK / BIG5）
 *  - ASS / SSA / VTT → SRT
 *  - 时间轴整体偏移
 *
 * 注意：这里刻意**不使用带花括号的正则**，改为手写扫描，避免不同 Android 版本
 * 对 `\\{...}` 这类模式解析不一致（Android 7 会报 “Syntax error in regexp pattern”）。
 */
object SubtitleCodec {

    private const val LF = "\n"

    /* ------------------------------------------------------------ 编码 */

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        if (isValidUtf8(bytes)) return String(bytes, Charsets.UTF_8)
        for (name in listOf("GBK", "GB18030", "BIG5")) {
            val text = runCatching { String(bytes, Charset.forName(name)) }.getOrNull()
            if (text != null && text.none { it == '\uFFFD' }) return text
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            val len = when {
                b < 0x80 -> 1
                b in 0xC2..0xDF -> 2
                b in 0xE0..0xEF -> 3
                b in 0xF0..0xF4 -> 4
                else -> return false
            }
            if (i + len > bytes.size) return false
            for (k in 1 until len) {
                val c = bytes[i + k].toInt() and 0xFF
                if (c !in 0x80..0xBF) return false
            }
            i += len
        }
        return true
    }

    /* ------------------------------------------------------------ 标记清理 */

    fun stripMarkup(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '{') {
                while (i < input.length && input[i] != '}') i++
                if (i < input.length) i++
            } else if (c == '<') {
                while (i < input.length && input[i] != '>') i++
                if (i < input.length) i++
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /* ------------------------------------------------------------ 格式转换 */

    fun toSrt(rawFileName: String, text: String): String {
        val lower = rawFileName.lowercase()
        val t = text.trimStart('\uFEFF', '\n', '\r', ' ')
        return when {
            lower.endsWith(".ass") || lower.endsWith(".ssa") || t.contains("[Events]") -> assToSrt(t)
            lower.endsWith(".vtt") || t.startsWith("WEBVTT") -> vttToSrt(t)
            else -> normalizeSrt(t)
        }
    }

    private fun assToSrt(ass: String): String {
        val sb = StringBuilder()
        var index = 1
        var inEvents = false
        for (line in ass.replace("\r\n", "\n").split("\n")) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                inEvents = trimmed.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents) continue
            if (trimmed.startsWith("Format:", ignoreCase = true)) continue
            if (!trimmed.startsWith("Dialogue:", ignoreCase = true)) continue

            val body = trimmed.substringAfter(':').trim()
            val parts = body.split(',')
            if (parts.size < 10) continue
            val start = parseAssTime(parts[1])
            val end = parseAssTime(parts[2])
            if (start < 0 || end <= start) continue
            val raw = parts.drop(9).joinToString(",").replace("\\N", "\n").replace("\\n", "\n")
            val content = stripMarkup(raw).trim()
            if (content.isBlank()) continue
            sb.append(index++).append(LF)
            sb.append(formatSrtTime(start)).append(" --> ").append(formatSrtTime(end)).append(LF)
            sb.append(content).append(LF).append(LF)
        }
        return sb.toString()
    }

    private fun parseAssTime(v: String): Long {
        val p = v.trim().split(':')
        if (p.size != 3) return -1
        val h = p[0].trim().toLongOrNull() ?: return -1
        val m = p[1].trim().toLongOrNull() ?: return -1
        val sec = p[2].replace(',', '.').toDoubleOrNull() ?: return -1
        return h * 3_600_000 + m * 60_000 + (sec * 1000).toLong()
    }

    private fun vttToSrt(vtt: String): String {
        val sb = StringBuilder()
        var index = 1
        val normalized = vtt.replace("\r\n", "\n")
        for (block in normalized.split("\n\n")) {
            val lines = block.split("\n").filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val timeLine = lines.firstOrNull { it.contains("-->") } ?: continue
            val after = lines.dropWhile { it != timeLine }.drop(1)
            if (after.isEmpty()) continue
            val times = timeLine.split("-->")
            if (times.size != 2) continue
            val start = parseVttTime(times[0].trim())
            val end = parseVttTime(times[1].trim().substringBefore(' '))
            if (start < 0 || end <= start) continue
            sb.append(index++).append(LF)
            sb.append(formatSrtTime(start)).append(" --> ").append(formatSrtTime(end)).append(LF)
            for (l in after) sb.append(stripMarkup(l)).append(LF)
            sb.append(LF)
        }
        return sb.toString()
    }

    private fun parseVttTime(v: String): Long {
        val p = v.split(':')
        return when (p.size) {
            3 -> {
                val h = p[0].toLongOrNull() ?: return -1
                val m = p[1].toLongOrNull() ?: return -1
                val s = p[2].replace(',', '.').toDoubleOrNull() ?: return -1
                h * 3_600_000 + m * 60_000 + (s * 1000).toLong()
            }
            2 -> {
                val m = p[0].toLongOrNull() ?: return -1
                val s = p[1].replace(',', '.').toDoubleOrNull() ?: return -1
                m * 60_000 + (s * 1000).toLong()
            }
            else -> -1
        }
    }

    private fun normalizeSrt(srt: String): String {
        var out = srt.replace("\r\n", "\n").replace("\r", "\n").replace("\u0000", "")
        while (out.contains("\n\n\n")) out = out.replace("\n\n\n", "\n\n")
        return out.trim() + LF
    }

    /* ------------------------------------------------------------ 偏移 */

    fun shift(srt: String, offsetMs: Long): String {
        if (offsetMs == 0L) return srt
        val sb = StringBuilder()
        for (line in srt.replace("\r\n", "\n").split("\n")) {
            if (line.contains("-->")) {
                val parts = line.split("-->")
                if (parts.size == 2) {
                    val start = parseSrtTime(parts[0].trim())
                    val end = parseSrtTime(parts[1].trim().substringBefore(' '))
                    if (start >= 0 && end >= 0) {
                        val s = (start + offsetMs).coerceAtLeast(0)
                        val e = (end + offsetMs).coerceAtLeast(s + 200)
                        sb.append(formatSrtTime(s)).append(" --> ").append(formatSrtTime(e)).append(LF)
                        continue
                    }
                }
            }
            sb.append(line).append(LF)
        }
        return sb.toString()
    }

    private fun parseSrtTime(v: String): Long {
        val p = v.replace(',', '.').split(':')
        if (p.size != 3) return -1
        val h = p[0].trim().toLongOrNull() ?: return -1
        val m = p[1].trim().toLongOrNull() ?: return -1
        val s = p[2].trim().toDoubleOrNull() ?: return -1
        return h * 3_600_000 + m * 60_000 + (s * 1000).toLong()
    }

    fun formatSrtTime(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000
        val millis = ms % 1000
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", h, m, s, millis)
    }
}
