package com.javboss.android.data.subtitle

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地字幕：在用户选定的文件夹（SAF）与 App 自己的字幕目录里，
 * 按「番号」匹配同名/包含番号的字幕文件。
 */
class LocalSubtitleProvider(private val context: Context) {

    private val exts = setOf("srt", "ass", "ssa", "vtt")

    suspend fun find(code: String, treeUri: String?): List<SubtitleCandidate> = withContext(Dispatchers.IO) {
        val key = normalize(code)
        if (key.isBlank()) return@withContext emptyList()
        val out = LinkedHashMap<String, SubtitleCandidate>()

        // 1) 用户选定的 SAF 文件夹
        if (!treeUri.isNullOrBlank()) {
            runCatching {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
                if (root != null) scan(root, key, out, depth = 0)
            }
        }
        // 2) App 内置字幕目录（下载缓存 + 手动放入）
        runCatching {
            appDirs().forEach { dir ->
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && matches(f.name, key)) {
                        out["file:${f.absolutePath}"] = SubtitleCandidate(
                            id = "file:${f.absolutePath}",
                            title = f.name,
                            language = guessLanguage(f.name),
                            source = "local",
                            localUri = Uri.fromFile(f).toString(),
                        )
                    }
                }
            }
        }
        out.values.sortedBy { it.title }.toList()
    }

    /** 读取本地字幕字节（content:// 或 file:// 均可）。 */
    fun readBytes(uriString: String): ByteArray? = runCatching {
        val uri = Uri.parse(uriString)
        when {
            uriString.startsWith("file:") -> java.io.File(uri.path ?: return null).readBytes()
            else -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    }.getOrNull()

    fun fileNameOf(uriString: String): String = runCatching {
        when {
            uriString.startsWith("file:") -> java.io.File(Uri.parse(uriString).path ?: "").name
            else -> DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.name ?: "subtitle"
        }
    }.getOrDefault("subtitle")

    /* ------------------------------------------------------------- 内部 */

    private fun scan(dir: DocumentFile, key: String, out: MutableMap<String, SubtitleCandidate>, depth: Int) {
        if (depth > 3) return
        dir.listFiles().forEach { f ->
            if (f.isDirectory) {
                scan(f, key, out, depth + 1)
            } else if (f.isFile && matches(f.name ?: "", key)) {
                val uri = f.uri.toString()
                out["doc:$uri"] = SubtitleCandidate(
                    id = "doc:$uri",
                    title = f.name ?: "subtitle",
                    language = guessLanguage(f.name ?: ""),
                    source = "local",
                    localUri = uri,
                )
            }
        }
    }

    fun appDirs(): List<java.io.File> = listOfNotNull(
        java.io.File(context.filesDir, "subtitles"),
        context.getExternalFilesDir("subtitles"),
    )

    private fun matches(name: String, key: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in exts) return false
        return normalize(name.substringBeforeLast('.')).contains(key)
    }

    companion object {
        /** SNOS-079 / snos_079 → SNOS079 */
        fun normalize(s: String): String = s.uppercase().filter { it.isLetterOrDigit() }
    }

    private fun guessLanguage(name: String): String {
        val t = name.lowercase()
        return when {
            t.contains("chs") || t.contains("sc") || t.contains("简体") || t.contains("zh-cn") -> "中文"
            t.contains("cht") || t.contains("tc") || t.contains("繁体") || t.contains("zh-tw") -> "中文"
            t.contains("zh") || t.contains("cn") || t.contains("中文") -> "中文"
            t.contains("ja") || t.contains("jpn") || t.contains("jp") -> "日文"
            else -> "?"
        }
    }
}
