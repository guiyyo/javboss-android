package com.javboss.android.core.net

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 会话 Cookie 存储：按 host 隔离，落盘为 JSON（同步写，量极小）。
 *
 * JavBoss 服务端只接受 Cookie 会话（见 docs/api-contract.md §2），且 Cookie 名可能带实例后缀
 * （javboss_session_<hash>），因此这里不硬编码名称，只做 host -> (name -> value) 的映射。
 */
class CookieStore(context: Context) {

    private val file = File(context.filesDir, "session-cookies.json")
    private val lock = Any()
    private var cache: MutableMap<String, MutableMap<String, String>> = load()

    private fun load(): MutableMap<String, MutableMap<String, String>> {
        if (!file.exists()) return mutableMapOf()
        return runCatching {
            val text = file.readText(Charsets.UTF_8)
            val root = Json.parseToJsonElement(text) as JsonObject
            val result = mutableMapOf<String, MutableMap<String, String>>()
            root.forEach { (host, value) ->
                val cookieObj = value as JsonObject
                val map = mutableMapOf<String, String>()
                cookieObj.forEach { (name, v) -> map[name] = v.jsonPrimitive.content }
                result[host] = map
            }
            result
        }.getOrElse { mutableMapOf() }
    }

    private fun persist() {
        val root = JsonObject(
            cache.mapValues { (_, cookies) ->
                JsonObject(cookies.mapValues { (_, v) -> kotlinx.serialization.json.JsonPrimitive(v) })
            }
        )
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(root.toString(), Charsets.UTF_8)
        }
    }

    fun cookiesFor(host: String): Map<String, String> = synchronized(lock) {
        cache[host]?.toMap() ?: emptyMap()
    }

    fun put(host: String, name: String, value: String) = synchronized(lock) {
        cache.getOrPut(host) { mutableMapOf() }[name] = value
        persist()
    }

    fun remove(host: String, name: String) = synchronized(lock) {
        cache[host]?.remove(name)
        if (cache[host]?.isEmpty() == true) cache.remove(host)
        persist()
    }

    fun clear(host: String? = null) = synchronized(lock) {
        if (host == null) cache.clear() else cache.remove(host)
        persist()
    }

    /** 供调试/“退出登录”判断使用。 */
    fun hasSession(host: String): Boolean = synchronized(lock) {
        cache[host]?.isNotEmpty() == true
    }
}
