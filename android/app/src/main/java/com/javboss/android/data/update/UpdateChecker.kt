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
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 独立 client：短超时，避免 GitHub 不可达时长时间等待 */
    private val client by lazy {
        com.javboss.android.core.net.ApiClient.okHttp.newBuilder()
            .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    suspend fun check(currentVersion: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "JavBoss-Android")
                .build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("GitHub 返回 ${resp.code}")
                resp.body?.string() ?: error("响应为空")
            }
            val obj: JsonObject = json.parseToJsonElement(body).jsonObject
            val tag = stringOf(obj, "tag_name")
            val notes = stringOf(obj, "body")
            val page = stringOf(obj, "html_url").ifBlank { "$RELEASES_PAGE/tag/$tag" }
            val apk = runCatching {
                obj["assets"]?.jsonArray
                    ?.mapNotNull { a -> runCatching { a.jsonObject["browser_download_url"]?.jsonPrimitive?.content }.getOrNull() }
                    ?.firstOrNull { it.endsWith(".apk", ignoreCase = true) }
            }.getOrNull()
            val latest = tag.trim().removePrefix("v").removePrefix("V")
            UpdateInfo(
                latestVersion = latest.ifBlank { tag },
                notes = notes.trim(),
                apkUrl = apk,
                pageUrl = page,
                hasUpdate = isNewer(latest, currentVersion),
            )
        }
    }

    private fun stringOf(obj: JsonObject, key: String): String =
        runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull().orEmpty()

    /** 语义化版本比较：1.2.10 > 1.2.9 */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate)
        val b = parse(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parse(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
