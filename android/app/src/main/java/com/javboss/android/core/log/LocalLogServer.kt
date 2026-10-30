package com.javboss.android.core.log

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * 极简本机 HTTP 服务：把日志文本挂在 http://<本机局域网IP>:<port>/ 上，
 * 二维码只放这个 URL —— 微信/QQ 扫码会直接打开网页显示文字（它们不支持纯文本二维码）。
 */
object LocalLogServer {

    private val PORTS = listOf(8899, 8901, 8903, 8905, 8907, 8909)
    private var boundPort = 0
    private var socket: ServerSocket? = null
    @Volatile private var content: String = ""

    /** 启动（幂等）并更新内容；返回可访问的 URL，失败返回 null。 */
    fun publish(text: String): String? {
        content = text
        if (socket == null) {
            synchronized(this) {
                if (socket == null) {
                    var created: ServerSocket? = null
                    for (p in PORTS) {
                        created = runCatching { ServerSocket(p).apply { reuseAddress = true } }.getOrNull()
                        if (created != null) { boundPort = p; break }
                    }
                    if (created == null) return null
                    socket = created
                    thread(isDaemon = true, name = "log-http") {
                        while (!created!!.isClosed) {
                            val client = runCatching { created!!.accept() }.getOrNull() ?: continue
                            runCatching {
                                client.use { c ->
                                    c.soTimeout = 3000
                                    val reader = BufferedReader(InputStreamReader(c.getInputStream()))
                                    // 读掉请求头
                                    while (true) {
                                        val line = reader.readLine() ?: break
                                        if (line.isEmpty()) break
                                    }
                                    val body = content.toByteArray(Charsets.UTF_8)
                                    val out: OutputStream = c.getOutputStream()
                                    val header = buildString {
                                        append("HTTP/1.1 200 OK\r\n")
                                        append("Content-Type: text/plain; charset=utf-8\r\n")
                                        append("Content-Length: ${body.size}\r\n")
                                        append("Cache-Control: no-store\r\n")
                                        append("Connection: close\r\n\r\n")
                                    }
                                    out.write(header.toByteArray(Charsets.UTF_8))
                                    out.write(body)
                                    out.flush()
                                }
                            }
                        }
                    }
                }
            }
        }
        val ip = lanIpv4() ?: return null
        return "http://$ip:$boundPort/"
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    /** 取本机局域网 IPv4（优先 192.168/10./172.16-31 网段）。 */
    fun lanIpv4(): String? {
        val candidates = mutableListOf<String>()
        runCatching {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        candidates += addr.hostAddress ?: continue
                    }
                }
            }
        }
        return candidates.firstOrNull { it.startsWith("192.168.") }
            ?: candidates.firstOrNull { it.startsWith("10.") }
            ?: candidates.firstOrNull { it.startsWith("172.") }
            ?: candidates.firstOrNull()
    }
}