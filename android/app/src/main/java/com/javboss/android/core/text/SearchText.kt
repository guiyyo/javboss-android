package com.javboss.android.core.text

/**
 * 搜索输入归一化。
 *
 * 服务端 `GET /jav?search=` 的匹配**大小写不敏感、但对连字符敏感**（2026-11-06 实测）：
 *   - `snos-079` → 命中 1 条（SNOS-079）
 *   - `snos079` / `snos 079` / 全角 `ＳＮＯＳ－０７９` → 命中 0 条
 * 用户从微信/浏览器复制来的番号经常没有连字符、或是全角，所以在客户端先还原成库里的写法。
 */
object SearchText {

    /** 全角 ASCII（！～）转半角，全角空格转半角空格，各种横线统一成 '-'。 */
    fun toHalfWidth(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            sb.append(
                when (ch) {
                    in '！'..'～' -> (ch.code - 0xFEE0).toChar()
                    '　' -> ' '
                    '－', '—', '–', '‐', '‑', '─', 'ー', '一' -> '-'
                    else -> ch
                },
            )
        }
        return sb.toString()
    }

    /**
     * 把用户输入尽量还原成库里的番号写法：
     * `snos079` / `ＳＮＯＳ－０７９` / `snos 079` / `SNOS-79` → `SNOS-079`
     *
     * 不是番号形状（含中文、纯数字、太长等）时返回 null，表示「不要改写用户输入」。
     * 库里番号实测全部形如 `[A-Z0-9]+-\d{2,6}`，数字以 3 位为主（729 部里 728 部是 3 位）。
     */
    fun normalizeCode(raw: String): String? {
        val half = toHalfWidth(raw).uppercase()
        val cleaned = half.filter { it.isLetterOrDigit() || it == '-' }.trim('-')
        if (cleaned.isEmpty() || cleaned.length > 16) return null
        if (cleaned.any { it.code > 127 }) return null

        val withDash = Regex("^([A-Z0-9]*[A-Z])-(\\d{2,6})$")
        val noDash = Regex("^([A-Z0-9]*[A-Z])(\\d{2,6})$")

        for (re in listOf(withDash, noDash)) {
            val m = re.matchEntire(cleaned) ?: continue
            val prefix = m.groupValues[1]
            val raw2 = m.groupValues[2]
            val digits = if (raw2.length < 3) raw2.padStart(3, '0') else raw2
            return prefix + "-" + digits
        }
        return null
    }

    /** 提交搜索时用的文本：番号形状就归一化，否则只做全角转半角 + 去首尾空白。 */
    fun forSearch(raw: String): String = normalizeCode(raw) ?: toHalfWidth(raw).trim()
}
