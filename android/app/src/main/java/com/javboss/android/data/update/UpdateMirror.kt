package com.javboss.android.data.update

/**
 * 下载镜像：GitHub 在国内常被墙，允许把下载地址套一层代理前缀。
 *
 * 支持两种写法：
 *  - 前缀式：`https://ghfast.top/` → `https://ghfast.top/https://github.com/...`
 *  - 占位符：`https://hub.gitmirror.com/{url}` → 把 {url} 替换为原地址
 */
object UpdateMirror {

    /** 常用镜像预设（value 为空表示直连） */
    /**
     * 常用 GitHub 加速镜像（可用性随网络环境差异很大，所以这里**不做删除**，
     * 统一列出知名镜像，用户点「测速」自行选择最快的一个）。
     */
    val PRESETS = listOf(
        "" to "GitHub 直连",
        "https://ghfast.top/" to "ghfast.top",
        "https://gh-proxy.com/" to "gh-proxy.com",
        "https://ghproxy.net/" to "ghproxy.net",
        "https://ghproxy.cc/" to "ghproxy.cc",
        "https://gh.llkk.cc/" to "gh.llkk.cc",
        "https://github.moeyy.xyz/" to "github.moeyy.xyz",
        "https://hub.gitmirror.com/" to "hub.gitmirror.com",
        "https://ghps.cc/" to "ghps.cc",
        "https://ghproxy.homeboyc.cn/" to "ghproxy.homeboyc.cn",
        "https://gh.ddlc.top/" to "gh.ddlc.top",
        "https://gh-proxy.net/" to "gh-proxy.net",
        "https://ghproxy.cfd/" to "ghproxy.cfd",
    )

    fun wrap(url: String, mirror: String): String {
        val m = mirror.trim()
        if (m.isEmpty()) return url
        if (url.isBlank()) return url
        // 已经是镜像化地址就不重复套
        if (url.startsWith(m)) return url
        return if (m.contains("{url}")) {
            m.replace("{url}", url)
        } else if (m.endsWith("/")) {
            m + url
        } else {
            "$m/$url"
        }
    }

    fun label(mirror: String): String {
        val m = mirror.trim()
        if (m.isEmpty()) return "GitHub 直连"
        return PRESETS.firstOrNull { it.first == m }?.second ?: m
    }
}
