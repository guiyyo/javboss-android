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
    /**
     * 从 ZIP 恢复字幕。
     *
     * R5：加限额与白名单（经典 Zip Slip 本来不成立，但仍可被塞入大量条目/超大文件/任意名字耗尽磁盘或覆盖无关番号）：
     *  - 条目数 ≤ 20000；单条解压后 ≤ 20MB；累计 ≤ 300MB；压缩比 > 500:1 视为异常跳过；
     *  - 只接受 <番号>.<语言>.srt 形式，番号必须过 [A-Za-z0-9_-]{2,32} 白名单。
     */
    /** ZIP 导入结果：导入了多少条、因不合规跳过多少条。 */
    data class ZipImportResult(val imported: Int, val skipped: Int)

    /** 兼容旧调用方：只关心导入条数。 */
    suspend fun importZipFrom(context: Context, zip: java.io.File): Int = importZipDetailed(context, zip).imported

    suspend fun importZipDetailed(context: Context, zip: java.io.File): ZipImportResult {
        val maxEntries = 20_000
        val maxEntryBytes = 20L * 1024 * 1024
        val maxTotalBytes = 300L * 1024 * 1024
        val safeCode = Regex("^[A-Za-z0-9_-]{2,32}$")
        var n = 0
        var skipped = 0
        var total = 0L
        var entries = 0
        val root = java.io.File(context.filesDir, "subtitles")
        java.util.zip.ZipFile(zip).use { zf ->
            zf.entries().asSequence().forEach { e ->
                if (e.isDirectory) return@forEach
                if (++entries > maxEntries) { skipped++; return@use }
                val name = e.name.substringAfterLast('/')
                if (!name.endsWith(".srt", ignoreCase = true)) { skipped++; return@forEach }
                val code = name.substringBefore('.')
                if (!safeCode.matches(code)) { skipped++; return@forEach }
                if (e.size > maxEntryBytes) { skipped++; return@forEach }
                if (e.compressedSize > 0 && e.size / e.compressedSize > 500) { skipped++; return@forEach }
                val dir = java.io.File(root, code).apply { mkdirs() }
                val target = java.io.File(dir, "active.srt")
                if (target.exists()) {
                    runCatching { target.copyTo(java.io.File(dir, "active.srt.bak"), overwrite = true) }
                }
                runCatching {
                    var written = 0L
                    zf.getInputStream(e).use { input ->
                        target.outputStream().use { output ->
                            val buf = ByteArray(1 shl 16)
                            while (true) {
                                val r = input.read(buf)
                                if (r <= 0) break
                                written += r
                                if (written > maxEntryBytes) error("字幕条目过大")
                                total += r
                                if (total > maxTotalBytes) error("压缩包解压体积超限")
                                output.write(buf, 0, r)
                            }
                        }
                    }
                    n++
                }.onFailure { skipped++ }
            }
        }
        return ZipImportResult(imported = n, skipped = skipped)
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
