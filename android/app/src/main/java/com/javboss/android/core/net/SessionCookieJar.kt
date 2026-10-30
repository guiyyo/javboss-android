package com.javboss.android.core.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.HttpCookie

/**
 * 持久化 CookieJar：所有请求（含 Coil 图片、ExoPlayer 视频流）只要复用同一个 OkHttpClient，
 * 就会自动携带会话 Cookie。
 */
class SessionCookieJar(private val store: CookieStore) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        return store.cookiesFor(host).map { (name, value) ->
            Cookie.Builder()
                .name(name)
                .value(value)
                .domain(host)
                .path("/")
                .build()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        for (cookie in cookies) {
            val expired = !cookie.persistent && cookie.expiresAt < System.currentTimeMillis()
            if (expired || cookie.value.isEmpty()) {
                store.remove(host, cookie.name)
            } else {
                store.put(host, cookie.name, cookie.value)
            }
        }
    }
}

/** 便捷方法：把 Cookie 集合渲染为 `Cookie:` 请求头（给不支持 CookieJar 的组件用）。 */
fun Map<String, String>.toCookieHeader(): String =
    entries.joinToString("; ") { (name, value) -> "$name=$value" }

@Suppress("unused")
internal fun parseSetCookieHeader(header: String): HttpCookie? =
    runCatching { HttpCookie.parse(header).firstOrNull() }.getOrNull()
