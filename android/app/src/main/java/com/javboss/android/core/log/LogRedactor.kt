package com.javboss.android.core.log

/**
 * 诊断日志脱敏（F3）。
 *
 * 诊断报告会经微信/QQ 发出去，里面常带局域网地址、会话 Cookie、本机用户名。
 * 这里只抹掉**个人信息**，错误码、堆栈、耗时等排障依据一律保留。
 *
 * 纯字符串处理，不依赖 Android，可直接单测。
 */
object LogRedactor {

    private val IPV4 = Regex("""\b(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(?::(\d{1,5}))?\b""")
    private val MAC = Regex("""\b(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}\b""")
    private val SECRET = Regex(
        """(?i)\b(session|sessionid|cookie|token|access_token|authorization|bearer|api_key|apikey|password|passwd|pwd|secret|proxy_host|proxy|mirror)(\s*[=:]\s*)([^\s;,'"&]+)""",
    )
    private val WIN_USER = Regex("""(?i)([A-Z]:\\Users\\)([^\\\s"']+)""")
    private val EMAIL = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")
    private val BEARER = Regex("""(?i)\b(bearer)\s+(\S{4,})""")
    private val URL_CREDS = Regex("""(?i)(https?://)([^\s/@]+)@""")
    /** Authorization: Basic xxx / Digest xxx（原来只处理 Bearer）。 */
    private val AUTH_BASIC = Regex("""(?i)\b(authorization\s*:\s*)(basic|digest)\s+([A-Za-z0-9+/=\-._~]+)""")
    /** 整行 Cookie 头（可能有多条 name=value）。 */
    private val COOKIE_HEADER = Regex("""(?i)\b(cookie\s*:\s*)([^\r\n]+)""")
    /** URL 查询串里的凭据类参数：token / key / sig / apikey / access_token。 */
    private val QUERY_SECRET = Regex("""(?i)([?&](?:token|access_token|api_key|apikey|key|sig|signature|auth)=)([^&\s"']+)""")
    /** 自定义凭据头：x-api-key / x-auth-token / x-secret 等（整行值打码）。 */
    private val SECRET_HEADER = Regex("""(?im)\b((?:x-[\w-]*(?:key|token|secret|auth|sign)[\w-]*)\s*:\s*)([^\r\n]+)""")
    /** 内网域名（.local / .lan / .home / .internal / .corp）——NAS 主机名不应出现在分享出去的日志里。 */
    private val PRIVATE_HOST = Regex("""(?i)\b([\w-]+\.(?:local|lan|home|internal|corp))\b""")

    /** 保留前两段网段（排障要区分局域网/公网），主机位与端口打码。 */
    fun maskIp(ip: String, port: String?): String {
        val parts = ip.split('.')
        val masked = if (parts.size != 4) "***" else parts[0] + "." + parts[1] + ".***.***"
        return if (port.isNullOrBlank()) masked else masked + ":****"
    }

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        out = IPV4.replace(out) { m ->
            val ip = listOf(1, 2, 3, 4).joinToString(".") { m.groupValues[it] }
            maskIp(ip, m.groupValues[5].takeIf { it.isNotBlank() })
        }
        out = MAC.replace(out, "**:**:**:**:**:**")
        out = URL_CREDS.replace(out) { m -> m.groupValues[1] + "***@" }
        out = BEARER.replace(out) { m -> m.groupValues[1] + " ***" }
        out = AUTH_BASIC.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + " ***" }
        out = COOKIE_HEADER.replace(out) { m -> m.groupValues[1] + "***" }
        out = QUERY_SECRET.replace(out) { m -> m.groupValues[1] + "***" }
        out = SECRET_HEADER.replace(out) { m -> m.groupValues[1] + "***" }
        out = PRIVATE_HOST.replace(out, "<host>")
        out = SECRET.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + "***" }
        out = WIN_USER.replace(out) { m -> m.groupValues[1] + "<user>" }
        out = EMAIL.replace(out, "<mail>")
        return out
    }

    /** 供「复制为 Issue 模板」用：脱敏 + Markdown 代码块。 */
    fun redactedTemplate(text: String, appVersion: String): String {
        val fence = "```"
        return buildString {
            appendLine("### 环境")
            appendLine("- App 版本：v" + appVersion)
            appendLine()
            appendLine("### 日志（已脱敏：IP / 端口 / Cookie / 用户名 / 邮箱已替换）")
            appendLine(fence)
            appendLine(redact(text).trim())
            appendLine(fence)
        }
    }
}
