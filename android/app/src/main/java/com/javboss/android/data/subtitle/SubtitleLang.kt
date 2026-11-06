package com.javboss.android.data.subtitle

/**
 * 字幕语言偏好（S11）。
 *
 * 偏好是**有序列表**，靠前优先；默认 `zh-Hans,zh-Hant,zh`（简中 > 繁中 > 其它中文）。
 * 匹配规则覆盖：ISO 639-1/2/3（zh/zho/chi）、脚本标签（zh-Hans/zh-Hant）、
 * 以及中文字幕文件常见的「中文 / 简体 / 繁体 / 简体中文」等命名。
 */
object SubtitleLang {

    const val DEFAULT_ORDER = "zh-Hans,zh-Hant,zh"

    /** 英文/日文等其它语言的粗略归一，只在偏好里显式写了才命中 */
    private val ALIASES = mapOf(
        "zh-hans" to listOf("zh", "zho", "chi", "chs", "简", "简体", "简体中文", "中文"),
        "zh-hant" to listOf("zh", "zho", "chi", "cht", "繁", "繁体", "繁體", "繁体中文", "中文"),
        "zh" to listOf("zho", "chi", "chs", "cht", "中文", "简", "繁", "简体", "繁体"),
        "en" to listOf("eng", "english", "英文", "英语"),
        "ja" to listOf("jpn", "japanese", "日文", "日语", "日本語"),
        "ko" to listOf("kor", "korean", "韩文", "韩语"),
    )

    fun parseOrder(raw: String?): List<String> =
        (raw ?: DEFAULT_ORDER)
            .split(',', '，', ' ')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .ifEmpty { DEFAULT_ORDER.split(',').map { it.lowercase() } }

    /** 把任意标签/文件名片段归一成语言键（命不中返回 null） */
    fun normalize(tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        val t = tag.trim().lowercase().replace('_', '-')
        ALIASES.forEach { (key, list) ->
            if (t == key || t.startsWith("$key-")) return key
            if (list.any { it == t }) return key
        }
        // 语言标签里含中文关键词的兜底（如 "Chinese (Simplified)"、"中文字幕"）
        return when {
            t.contains("chi") || t.contains("zho") || t.contains("中文") || t.contains("简") ||
                t.contains("繁") -> "zh"
            t.contains("english") || t == "en" || t.contains("英文") -> "en"
            t.contains("japanese") || t == "ja" || t.contains("日文") -> "ja"
            else -> null
        }
    }

    /** 是否命中偏好（任一档即可） */
    fun isPreferred(tag: String?, order: List<String> = parseOrder(null)): Boolean {
        val n = normalize(tag) ?: return false
        return order.any { pref ->
            val p = pref.lowercase()
            n == p || (p.startsWith(n) && n.isNotEmpty()) || (n == "zh" && p.startsWith("zh"))
        }
    }

    /** 偏好排序得分：越靠前越高；未命中返回 -1 */
    fun rank(tag: String?, order: List<String> = parseOrder(null)): Int {
        val n = normalize(tag) ?: return -1
        order.forEachIndexed { idx, pref ->
            val p = pref.lowercase()
            if (n == p || (n == "zh" && p.startsWith("zh"))) return idx
        }
        return -1
    }

    /** 给界面显示用：把偏好键转中文名 */
    fun label(key: String): String = when (key.lowercase()) {
        "zh-hans" -> "简体中文"
        "zh-hant" -> "繁体中文"
        "zh" -> "其它中文"
        "en" -> "英文"
        "ja" -> "日文"
        "ko" -> "韩文"
        else -> key
    }
}
