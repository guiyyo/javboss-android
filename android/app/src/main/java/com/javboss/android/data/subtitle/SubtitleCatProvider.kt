package com.javboss.android.data.subtitle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * SubtitleCat 在线字幕搜索（按番号）。
 *
 * 实测流程（2026-10）：
 *  1. 搜索：GET {base}/index.php?search=SNOS-079
 *     结果条目形如 <a href="subs/1375/SNOS-079.html">SNOS-079</a>（**相对路径、无前导斜杠**）
 *  2. 详情：GET {base}/subs/1375/SNOS-079.html
 *     页面里列出各语言直链：/subs/1376/SNOS-079-zh-CN.srt、-zh-TW、-en …
 *  3. 下载：GET {base}/subs/1376/SNOS-079-zh-CN.srt （纯 SRT 文本）
 *
 * 注意：实测 `www.subtitlecat.com` 不通，裸域 `subtitlecat.com` 可用（默认值即裸域）。
 */
class SubtitleCatProvider(private val baseUrlProvider: suspend () -> String) {

    // 字幕源专用 client：短超时，避免单源卡住一分钟（OkHttp 阻塞调用不响应协程取消）
    private val client by lazy {
        com.javboss.android.core.net.ApiClient.okHttp.newBuilder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /** 上次成功的域名（同一个站点，www 与裸域可互换） */
    @Volatile private var workingBase: String? = null

    private suspend fun base(): String = workingBase ?: baseUrlProvider().ifBlank { DEFAULT_BASE }.trimEnd('/')

    /** 候选域名：配置值 + 自动互换 www / 裸域。 */
    private suspend fun candidateBases(): List<String> {
        val configured = baseUrlProvider().ifBlank { DEFAULT_BASE }.trimEnd('/')
        val alt = when {
            configured.contains("://www.") -> configured.replace("://www.", "://")
            else -> configured.replace("://", "://www.")
        }
        return listOf(configured, alt).distinct()
    }

    /* ------------------------------------------------------------- 搜索 */

    suspend fun search(query: String): List<SubtitleCandidate> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        val enc = java.net.URLEncoder.encode(q, "UTF-8")
        for (b in candidateBases()) {
            val page = fetch("$b/index.php?search=$enc", b) ?: continue
            if (!page.contains("subs/")) continue
            workingBase = b
            return@withContext parseSearch(page, b)
        }
        return@withContext emptyList()
    }

    /** 用指定域名搜索（供多源轮询使用）；失败抛异常以便上层换源。 */
    suspend fun searchWithBase(base: String, query: String): List<SubtitleCandidate> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isBlank()) return@withContext emptyList()
            val b = base.trimEnd('/')
            val enc = java.net.URLEncoder.encode(q, "UTF-8")
            val page = fetch("$b/index.php?search=$enc", b) ?: error("连接失败：$b")
            if (!page.contains("subs/") && !page.contains("subtitlecat")) error("响应异常：$b")
            workingBase = b
            parseSearch(page, b)
        }



    /**
     * 解析真正可下载的字幕地址：优先中文（zh-CN → zh-TW → 其他 zh），否则退到英文/第一个。
     */
    suspend fun resolveDownload(candidate: SubtitleCandidate, preferChinese: Boolean = true): String? =
        withContext(Dispatchers.IO) {
            candidate.downloadUrl?.let { return@withContext it }
            val b = base()
            val detail = candidate.detailUrl ?: return@withContext null
            val html = fetch(detail, b) ?: return@withContext null
            val links = Regex("href=\"([^\"]+\\.srt)\"", RegexOption.IGNORE_CASE)
                .findAll(html)
                .map { it.groupValues[1] }
                .distinct()
                .toList()
            if (links.isEmpty()) return@withContext null
            val picked = if (preferChinese) {
                links.firstOrNull { it.contains("-zh-CN", true) }
                    ?: links.firstOrNull { it.contains("-zh-TW", true) || it.contains("-zh-Hans", true) }
                    ?: links.firstOrNull { it.contains("-zh", true) }
                    ?: links.firstOrNull { it.contains("-en", true) }
                    ?: links.first()
            } else links.first()
            absolutize(picked, b)
        }

    /** 下载字幕，带字节进度回调（total 可能为 -1）。 */
    suspend fun download(
        url: String,
        onProgress: (read: Long, total: Long) -> Unit = { _, _ -> },
    ): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val b = base()
            val abs = if (url.startsWith("http")) url else absolutize(url, b)
            val req = Request.Builder()
                .url(abs)
                .header("User-Agent", UA)
                .header("Referer", "$b/")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body ?: return@use null
                val total = body.contentLength()
                val input = body.byteStream()
                val out = java.io.ByteArrayOutputStream(total.takeIf { it > 0 }?.toInt() ?: 8192)
                val buf = ByteArray(16 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    read += n
                    onProgress(read, total)
                }
                out.toByteArray()
            }
        }.getOrNull()
    }

    /* ------------------------------------------------------------- 解析 */

    private fun parseSearch(html: String, base: String): List<SubtitleCandidate> {
        val out = LinkedHashMap<String, SubtitleCandidate>()
        Regex(
            "<a[^>]+href=\"([^\"]*?subs/\\d+/[^\"]+\\.html)\"[^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(html).forEach { m ->
            val rawPath = m.groupValues[1]
            val title = m.groupValues[2]
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { rawPath.substringAfterLast('/').removeSuffix(".html") }
            if (title.length < 2) return@forEach
            val url = absolutize(rawPath, base)
            out[url] = SubtitleCandidate(
                id = "subtitlecat:$url",
                title = title,
                language = guessLanguage(rawPath),
                source = "subtitlecat",
                detailUrl = url,
            )
        }
        if (out.isEmpty()) {
            Regex("href=\"([^\"]*?subs/\\d+/[^\"]+\\.html)\"", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
                val rawPath = m.groupValues[1]
                val url = absolutize(rawPath, base)
                out[url] = SubtitleCandidate(
                    id = "subtitlecat:$url",
                    title = rawPath.substringAfterLast('/').removeSuffix(".html"),
                    language = guessLanguage(rawPath),
                    source = "subtitlecat",
                    detailUrl = url,
                )
            }
        }
        return out.values.take(30).toList()
    }

    private fun guessLanguage(path: String): String {
        val p = path.lowercase()
        return when {
            p.contains("-zh-cn") -> "中文(简)"
            p.contains("-zh-tw") || p.contains("-zh-hans") -> "中文(繁)"
            p.contains("-zh") || p.contains("繁中") -> "中文"
            p.contains("-ja") || p.contains("-jp") -> "日文"
            p.contains("-en") -> "英文"
            p.contains("-vi") -> "越南语"
            p.contains("-ko") -> "韩文"
            else -> "原文"
        }
    }

    private fun absolutize(path: String, base: String): String {
        if (path.startsWith("http")) return path
        val clean = path.trimStart('.')
        return if (clean.startsWith("/")) "$base$clean" else "$base/${clean.trimStart('/')}"
    }

    private fun fetch(url: String, base: String): String? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", "$base/")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val bytes = resp.body?.bytes() ?: return@use null
            SubtitleCodec.decode(bytes)
        }
    }.getOrNull()

    companion object {
        const val DEFAULT_BASE = "https://subtitlecat.com"

        /** 默认字幕源（同一站点的多个入口，用于超时换源） */
        val DEFAULT_SOURCES = listOf(
            "https://subtitlecat.com",
            "https://www.subtitlecat.com",
            "http://subtitlecat.com",
            "http://www.subtitlecat.com",
        )
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/122.0 Safari/537.36"
    }
}
