package com.javboss.android.data.subtitle

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 已匹配字幕批量导出：打包为 ZIP，条目名 `<番号>.<语言>.srt`。
 *
 * 语言来源优先级：字幕索引（在线/内嵌）→ 已选字幕文件名里的语言关键词 → 兜底 `zh`。
 */
object SubtitleExporter {

    data class Result(val zip: File, val count: Int)

    suspend fun exportZip(context: Context): Result {
        val root = File(context.filesDir, "subtitles")
        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val zip = File(outDir, "javboss-subtitles-$stamp.zip")
        var count = 0
        ZipOutputStream(zip.outputStream().buffered()).use { zos ->
            val dirs = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
            for (dir in dirs) {
                val code = dir.name
                val srt = File(dir, "active.srt")
                if (!srt.exists() || srt.length() < 16) continue
                val state = SubtitleIndex.get(code)
                if (state?.noNeedSubtitle == true) continue
                val lang = guessLanguage(code, state)
                val entryName = "$code.$lang.srt"
                runCatching {
                    zos.putNextEntry(ZipEntry(entryName))
                    srt.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    count++
                }
            }
        }
        return Result(zip, count)
    }

    /** 导出到指定文件（TV 端用固定路径） */
    suspend fun exportZipTo(context: Context, out: java.io.File): Result {
        val tmp = exportZip(context)
        out.parentFile?.mkdirs()
        tmp.zip.copyTo(out, overwrite = true)
        runCatching { tmp.zip.delete() }
        return Result(out, tmp.count)
    }

    /** 从 ZIP 恢复字幕：条目名 `<番号>.<语言>.srt` → subtitles/<番号>/active.srt */
    suspend fun importZipFrom(context: Context, zip: java.io.File): Int {
        var n = 0
        val root = java.io.File(context.filesDir, "subtitles")
        java.util.zip.ZipFile(zip).use { zf ->
            zf.entries().asSequence().forEach { e ->
                if (e.isDirectory) return@forEach
                val name = e.name
                val code = name.substringBefore('.')
                if (code.isBlank()) return@forEach
                val dir = java.io.File(root, code).apply { mkdirs() }
                val target = java.io.File(dir, "active.srt")
                if (target.exists()) {
                    runCatching { target.copyTo(java.io.File(dir, "active.srt.bak"), overwrite = true) }
                }
                runCatching {
                    zf.getInputStream(e).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    n++
                }
            }
        }
        return n
    }

    private fun guessLanguage(code: String, state: SubtitleState?): String {
        // 1) 已保存的字幕选择里带语言关键词（如 "JUR-897.chs.srt"）
        val selected = runCatching {
            kotlinx.coroutines.runBlocking {
                com.javboss.android.di.AppGraph.settings.subtitleSelection(code)
            }
        }.getOrNull()
        if (!selected.isNullOrBlank()) {
            val n = SubtitleLang.normalize(selected)
            if (n != null) return n
        }
        // 2) 内嵌字幕带语言标签
        state?.embeddedLang?.let { SubtitleLang.normalize(it) }?.let { return it }
        // 3) 兜底
        return "zh"
    }
}
