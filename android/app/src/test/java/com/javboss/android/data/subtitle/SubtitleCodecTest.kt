package com.javboss.android.data.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 字幕编解码：编码识别 / VTT·ASS 转 SRT / 偏移 / 时间格式。 */
class SubtitleCodecTest {

    private val srt = """
        1
        00:00:01,000 --> 00:00:02,500
        <i>斜体</i>与{\an8}标记

        2
        00:00:03,000 --> 00:00:04,000
        第二行

    """.trimIndent()

    @Test
    fun decode_utf8_with_bom() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val text = "中文字幕".toByteArray(Charsets.UTF_8)
        val out = SubtitleCodec.decode(bom + text)
        assertTrue(out.startsWith("中文字幕"))
    }

    @Test
    fun decode_utf8_plain() {
        assertEquals("Hello 字幕", SubtitleCodec.decode("Hello 字幕".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun decode_gbk_falls_back() {
        // GBK 的「中文」= D6 D0 CE C4，不是合法 UTF-8
        val gbk = byteArrayOf(0xD6.toByte(), 0xD0.toByte(), 0xCE.toByte(), 0xC4.toByte())
        val out = SubtitleCodec.decode(gbk)
        assertTrue("GBK 应能解出「中文」，实际=$out", out.contains("中"))
    }

    @Test
    fun stripMarkup_removes_tags_and_braces() {
        val out = SubtitleCodec.stripMarkup("<i>斜体</i>与{\\an8}标记")
        assertFalse(out.contains("<i>"))
        assertFalse("标记应被清除", out.contains("an8"))
        assertTrue(out.contains("斜体"))
        assertTrue(out.contains("标记"))
    }

    @Test
    fun toSrt_keeps_srt_and_strips_markup() {
        val out = SubtitleCodec.toSrt("a.srt", srt)
        assertTrue(out.contains("-->"))
        assertTrue(out.contains("斜体"))
        assertFalse(out.contains("<i>"))
    }

    @Test
    fun toSrt_converts_vtt() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            vtt 第一句

            00:01:03.500 --> 00:01:04.000
            vtt 第二句
        """.trimIndent()
        val out = SubtitleCodec.toSrt("a.vtt", vtt)
        assertTrue(out.contains("00:00:01,000 --> 00:00:02,000"))
        assertTrue(out.contains("vtt 第一句"))
        assertTrue(out.contains("00:01:03,500"))
    }

    @Test
    fun toSrt_converts_ass() {
        val ass = """
            [Script Info]
            ScriptType: v4.00+

            [V4+ Styles]
            Format: Name, Fontname
            Style: Default,Arial

            [Events]
            Format: Layer, Start, End, Style, Text
            Dialogue: 0,0:00:01.00,0:00:02.50,Default,第一句
            Dialogue: 0,0:01:03.00,0:01:04.00,Default,第二句
        """.trimIndent()
        val out = SubtitleCodec.toSrt("a.ass", ass)
        assertTrue(out.contains("00:00:01,000 --> 00:00:02,500"))
        assertTrue(out.contains("第一句"))
        assertTrue(out.contains("00:01:03,000"))
    }

    @Test
    fun shift_moves_all_timestamps() {
        val shifted = SubtitleCodec.shift(srt, 1000)
        assertTrue(shifted.contains("00:00:02,000 --> 00:00:03,500"))
        assertTrue(shifted.contains("00:00:04,000 --> 00:00:05,000"))
        val cues = parseSrtCues(shifted)
        assertEquals(2000L, cues[0].startMs)
    }

    @Test
    fun shift_backwards_clamps_at_zero() {
        val shifted = SubtitleCodec.shift(srt, -60_000)
        val cues = parseSrtCues(shifted)
        assertTrue(cues.isNotEmpty())
        assertTrue(cues.all { it.startMs >= 0 })
        assertEquals(0L, cues[0].startMs)
    }

    @Test
    fun formatSrtTime_formats_hours_minutes_millis() {
        assertEquals("00:00:00,000", SubtitleCodec.formatSrtTime(0))
        assertEquals("00:00:01,234", SubtitleCodec.formatSrtTime(1234))
        assertEquals("01:02:03,004", SubtitleCodec.formatSrtTime(3_723_004))
    }

    @Test
    fun parseSrtCues_handles_crlf_and_extra_blank_lines() {
        val crlf = "1\r\n00:00:01,000 --> 00:00:02,000\r\nA\r\n\r\n\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\nB\r\n"
        val cues = parseSrtCues(crlf)
        assertEquals(2, cues.size)
        assertEquals("A", cues[0].text)
        assertEquals("B", cues[1].text)
    }

    @Test
    fun toSrt_converts_ass_standard_10_fields() {
        val ass = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,{\an8}第一句
            Dialogue: 0,0:01:03.00,0:01:04.00,Default,,0,0,0,,第二句\N换行
        """.trimIndent()
        val out = SubtitleCodec.toSrt("a.ass", ass)
        assertTrue(out.contains("00:00:01,000 --> 00:00:02,500"))
        assertTrue(out.contains("第一句"))
        assertFalse("ASS 覆盖层标记应被清除", out.contains("an8"))
        assertTrue(out.contains("00:01:03,000"))
    }
}
