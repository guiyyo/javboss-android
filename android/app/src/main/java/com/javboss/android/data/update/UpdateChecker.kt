package com.javboss.android.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * 从 GitHub Releases 检查新版本（仓库：guiyyo/javboss-android）。
 * 公开仓库无需 token（每小时 60 次 / IP）。
 */
object UpdateChecker {

    const val REPO = "guiyyo/javboss-android"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"

    data class UpdateInfo(
        val latestVersion: String,
        val notes: String,
        val apkUrl: String?,
        val pageUrl: String,
        val hasUpdate: Boolean,
        /** GitHub 为附件生成的 sha256（形如 `sha256:...`，取冒号后的部分） */
        val sha256: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 独立 client：短超时 + 可选网络代理 */
    private suspend fun client(): okhttp3.OkHttpClient =
        com.javboss.android.core.net.NetProxySupport.build(
            proxy = com.javboss.android.core.net.NetProxySupport.current(),
            connectSeconds = 10,
            readSeconds = 20,
            callSeconds = 40,
        )

    suspend fun check(currentVersion: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "JavBoss-Android")
                .build()
            val body = client().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("GitHub 返回 ${resp.code}")
                resp.body?.string() ?: error("响应为空")
            }
            val obj: JsonObject = json.parseToJsonElement(body).jsonObject
            val tag = stringOf(obj, "tag_name")
            val notes = stringOf(obj, "body")
            val page = stringOf(obj, "html_url").ifBlank { "$RELEASES_PAGE/tag/$tag" }
            var apk: String? = null
            var sha256: String? = null
            runCatching {
                val assets = obj["assets"]?.jsonArray ?: return@runCatching
                for (a in assets) {
                    val o = a.jsonObject
                    val url = o["browser_download_url"]?.jsonPrimitive?.content ?: continue
                    if (!url.endsWith(".apk", ignoreCase = true)) continue
                    apk = url
                    // R3：与 ApkInstaller 用同一套归一化规则（64 位 hex），不再各写一套
                    sha256 = com.javboss.android.core.update.UpdateSecurity.normalizeDigest(
                        o["digest"]?.jsonPrimitive?.content,
                    )
                    break
                }
            }
            val latest = tag.trim().removePrefix("v").removePrefix("V")
            com.javboss.android.core.log.AppLog.i(
                "JavBossUpdate",
                "release check: current=$currentVersion latest=$tag hasUpdate=${isNewer(latest, currentVersion)}",
            )
            UpdateInfo(
                latestVersion = latest.ifBlank { tag },
                notes = notes.trim(),
                apkUrl = apk,
                pageUrl = page,
                hasUpdate = isNewer(latest, currentVersion),
                sha256 = sha256,
            )
        }
    }

    private fun stringOf(obj: JsonObject, key: String): String =
        runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull().orEmpty()

    /** 语义化版本比较：1.2.10 > 1.2.9 */
    fun isNewer(candidate: String, current: String): Boolean {
        val (a, aPre) = parseWithPreRelease(candidate)
        val (b, bPre) = parseWithPreRelease(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        // 数字段相同：正式版高于预发布版（1.2.3 > 1.2.3-beta）
        if (aPre != bPre) return !aPre
        return false
    }

    /** 数字段 + 是否预发布（-alpha/-beta/-rc/-pre/-dev 后缀）。 */
    private fun parseWithPreRelease(v: String): Pair<List<Int>, Boolean> {
        val cleaned = v.trim().removePrefix("v").removePrefix("V")
        val lower = cleaned.lowercase()
        val pre = cleaned.contains("-") && listOf("alpha", "beta", "rc", "pre", "dev").any { lower.contains(it) }
        val base = cleaned.split("-")[0].split("+")[0]
        val numbers = base.split(".").map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        return numbers to pre
    }

    private fun parse(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
