package com.javboss.android.core.net

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * 会话 Cookie 存储：按 host 隔离，落盘为 JSON（同步写，量极小）。
 *
 * JavBoss 服务端只接受 Cookie 会话（见 docs/api-contract.md §2），且 Cookie 名可能带实例后缀
 * （javboss_session_<hash>），因此这里不硬编码名称，只做 host -> (name -> cookie) 的映射。
 *
 * 安全审计 A-2 修复：以前只存 name=value，`Secure / HttpOnly / Max-Age / Path` 全丢，
 * 导致 https 下发的 Secure Cookie 会被回放到 http（降级重放）。现在按属性存储并原样回放。
 * 旧格式（纯字符串值）仍可读，按非 Secure 处理。
 */
class CookieStore(context: Context) {

    data class StoredCookie(
        val value: String,
        val secure: Boolean = false,
        val httpOnly: Boolean = false,
        val path: String = "/",
        /** -1 = 会话 Cookie */
        val expiresAt: Long = -1L,
    ) {
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
            expiresAt > 0 && expiresAt <= now
    }

    private val file = File(context.filesDir, "session-cookies.json")
    private val lock = Any()
    private var cache: MutableMap<String, MutableMap<String, StoredCookie>> = load()

    private fun load(): MutableMap<String, MutableMap<String, StoredCookie>> {
        if (!file.exists()) return mutableMapOf()
        return runCatching {
            val text = file.readText(Charsets.UTF_8)
            val root = Json.parseToJsonElement(text).jsonObject
            val result = mutableMapOf<String, MutableMap<String, StoredCookie>>()
            root.forEach { (host, value) ->
                val cookieObj = value.jsonObject
                val map = mutableMapOf<String, StoredCookie>()
                cookieObj.forEach { (name, v) ->
                    map[name] = runCatching {
                        val obj = v.jsonObject
                        StoredCookie(
                            value = obj["value"]?.jsonPrimitive?.content.orEmpty(),
                            secure = obj["secure"]?.jsonPrimitive?.booleanOrNull ?: false,
                            httpOnly = obj["httpOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
                            path = obj["path"]?.jsonPrimitive?.content.orEmpty().ifBlank { "/" },
                            expiresAt = obj["expiresAt"]?.jsonPrimitive?.longOrNull ?: -1L,
                        )
                    }.getOrElse {
                        // 旧格式：host -> { name: "value" }
                        StoredCookie(value = v.jsonPrimitive.content)
                    }
                }
                result[host] = map
            }
            result
        }.getOrElse { mutableMapOf() }
    }

    private fun persist() {
        val root = JsonObject(
            cache.mapValues { (_, cookies) ->
                JsonObject(
                    cookies.mapValues { (_, c) ->
                        JsonObject(
                            mapOf(
                                "value" to JsonPrimitive(c.value),
                                "secure" to JsonPrimitive(c.secure),
                                "httpOnly" to JsonPrimitive(c.httpOnly),
                                "path" to JsonPrimitive(c.path),
                                "expiresAt" to JsonPrimitive(c.expiresAt),
                            ),
                        )
                    },
                )
            },
        )
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(root.toString(), Charsets.UTF_8)
            // 会话凭据只给本进程读（同应用私有目录，属纵深防御）
            runCatching { file.setReadable(false, false); file.setReadable(true, true) }
        }
    }

    fun cookiesFor(host: String): Map<String, StoredCookie> = synchronized(lock) {
        cache[host]?.toMap() ?: emptyMap()
    }

    /** 兼容旧调用方：只要 name -> value 的视图（不参与 Secure 判断，勿用于网络请求）。 */
    fun valuesFor(host: String): Map<String, String> = synchronized(lock) {
        cache[host]?.mapValues { it.value.value } ?: emptyMap()
    }

    fun put(host: String, name: String, cookie: StoredCookie) = synchronized(lock) {
        cache.getOrPut(host) { mutableMapOf() }[name] = cookie
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

    /** 供调试 / 退出登录判断使用。 */
    fun hasSession(host: String): Boolean = synchronized(lock) {
        cache[host]?.isNotEmpty() == true
    }
}
