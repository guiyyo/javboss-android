package com.javboss.android.core.player

/**
 * 播放源选择规则（纯逻辑，便于单测）。
 *
 * 背景（2026-11-07 定位）：服务端 \`/videos/{id}/streams\` 的 \`preferred_kind\` 只是它的**保守判断**——
 * \`internal/util/playback.go\` 里只有 mp4+h264+aac（或 webm+vp8/vp9+opus/vorbis）才判 direct，
 * MKV / HEVC / AC3 一律判 hls；但同一次响应里**仍然会把 direct 源发给客户端**（服务端注释：
 * 「由浏览器决定自己的解码器能不能试」）。
 *
 * 以前的实现是 \`if (preferredKind == "direct") direct else hls\`，等于把用户的「优先直连」开关废掉了：
 * 只要片子是 HEVC/MKV，就永远从 HLS 起播（服务端实时转码，慢且容易失败），用户手点「改用直连」反而能播。
 * 现在：**用户的设置说了算**，服务端 preferred_kind 不再覆盖它。
 */
object SourceChoice {

    /** 起播优先用哪个源。 */
    fun pick(hasDirect: Boolean, hasHls: Boolean, preferDirect: Boolean): String? = when {
        preferDirect && hasDirect -> "direct"
        hasHls -> "hls"
        hasDirect -> "direct"
        else -> null
    }

    /** 播放失败后自动回落到哪个源（另一路存在才有值；只回落一次，避免来回弹）。 */
    fun fallback(current: String?, hasDirect: Boolean, hasHls: Boolean): String? = when (current) {
        "direct" -> if (hasHls) "hls" else null
        "hls" -> if (hasDirect) "direct" else null
        else -> null
    }
}
