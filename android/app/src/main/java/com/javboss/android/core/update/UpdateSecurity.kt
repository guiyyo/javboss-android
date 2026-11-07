package com.javboss.android.core.update

/**
 * 更新链路的安全约束（纯函数，便于单测）。
 *
 * 背景（A-L4）：以前 SHA-256 校验被 runCatching 包住，任何失败都只记一句
 * 「sha256 校验跳过」然后照常安装 —— 校验形同虚设；镜像前缀也是纯字符串拼接，
 * 允许 http 与任意主机。这里把「什么样的摘要可信」「什么样的镜像可用」固化成规则。
 */
object UpdateSecurity {

    /**
     * 归一化摘要：接受 sha256:xxxx、SHA256: xxxx、纯 64 位 hex（大小写不敏感）。
     * 不是 64 位 hex 一律返回 null，调用方必须中止安装，不能跳过校验。
     */
    fun normalizeDigest(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .removePrefix("sha256:")
            .removePrefix("SHA256:")
            .filter { it.isLetterOrDigit() }
            .lowercase()
        if (cleaned.length != 64) return null
        if (!cleaned.all { it in "0123456789abcdef" }) return null
        return cleaned
    }

    /** 两个摘要是否一致（都必须能归一化成 64 位 hex）。 */
    fun digestsMatch(a: String?, b: String?): Boolean {
        val x = normalizeDigest(a) ?: return false
        val y = normalizeDigest(b) ?: return false
        return x == y
    }

    /**
     * 校验并规范化镜像前缀。
     *
     * 产品决定（用户 2026-11-07）：**https 不强制**，让用户自己判断；这里只挡明显非法的输入：
     *  - 空串 = GitHub 直连；
     *  - 不带 scheme 的写法（如 ghfast.top/）自动补 https://；
     *  - 其它 scheme（file:// ftp:// 等）、无点且非 IP 的主机名 → 返回空串（视为无效，回落直连）。
     */
    fun sanitizeMirror(mirror: String?): String {
        var m = mirror?.trim().orEmpty()
        if (m.isEmpty()) return ""
        if (!m.contains("://")) m = "https://" + m
        val lower = m.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return ""
        val hostPart = m.substringAfter("://").substringBefore("/")
        if (hostPart.isBlank()) return ""
        if (!hostPart.contains(".")) return ""
        return m
    }
}
