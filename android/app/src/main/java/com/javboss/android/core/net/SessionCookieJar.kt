package com.javboss.android.core.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.HttpCookie

/**
 * 持久化 CookieJar：所有请求（含 Coil 图片、ExoPlayer 视频流）只要复用同一个 OkHttpClient，
 * 就会自动携带会话 Cookie。
 *
 * 安全审计 A-2：按 host 存的是带属性的 Cookie（Secure/HttpOnly/Path/ExpiresAt）。
 *  - Secure Cookie **只**在 https 请求上回放（以前无条件回放，等于允许降级）；
 *  - 过期时间已到的 Cookie 不再发出并顺手清掉。
 */
class SessionCookieJar(private val store: CookieStore) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val now = System.currentTimeMillis()
        val out = ArrayList<Cookie>()
        for ((name, stored) in store.cookiesFor(host)) {
            if (stored.value.isEmpty()) continue
            if (stored.isExpired(now)) {
                store.remove(host, name)
                continue
            }
            if (stored.secure && !url.isHttps) continue
            val builder = Cookie.Builder()
                .name(name)
                .value(stored.value)
                .domain(host)
                .path(stored.path.ifBlank { "/" })
            if (stored.secure) builder.secure()
            if (stored.httpOnly) builder.httpOnly()
            if (stored.expiresAt > 0) builder.expiresAt(stored.expiresAt)
            runCatching { out.add(builder.build()) }
        }
        return out
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val now = System.currentTimeMillis()
        for (cookie in cookies) {
            if (cookie.value.isEmpty() || cookie.expiresAt <= now) {
                store.remove(host, cookie.name)
            } else {
                store.put(
                    host,
                    cookie.name,
                    CookieStore.StoredCookie(
                        value = cookie.value,
                        secure = cookie.secure,
                        httpOnly = cookie.httpOnly,
                        path = cookie.path.ifBlank { "/" },
                        expiresAt = if (cookie.persistent) cookie.expiresAt else -1L,
                    ),
                )
            }
        }
    }
}

/** 便捷方法：把 Cookie 集合渲染为 Cookie: 请求头（给不支持 CookieJar 的组件用）。 */
fun Map<String, String>.toCookieHeader(): String =
    entries.joinToString("; ") { (name, value) -> "$name=$value" }

/** 带属性的渲染（FrameCapture 等需要显式带 Cookie 头的地方用）。 */
fun Map<String, CookieStore.StoredCookie>.toCookieHeaderFor(url: HttpUrl?): String {
    val https = url?.isHttps ?: true
    val now = System.currentTimeMillis()
    return entries
        .filter { (_, c) -> c.value.isNotEmpty() && !c.isExpired(now) && (!c.secure || https) }
        .joinToString("; ") { (name, c) -> "$name=" + c.value }
}

@Suppress("unused")
internal fun parseSetCookieHeader(header: String): HttpCookie? =
    runCatching { HttpCookie.parse(header).firstOrNull() }.getOrNull()
