package com.javboss.android.data.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCueTest {

    private val sample = """
        1
        00:00:01,000 --> 00:00:04,000
        测试字幕第一句

        2
        00:00:05,500 --> 00:00:08,250
        第二句
        跨行内容
    """.trimIndent()

    @Test
    fun parse_returns_cues_with_correct_timing() {
        val cues = parseSrtCues(sample)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(4000L, cues[0].endMs)
        assertEquals("测试字幕第一句", cues[0].text)
        assertEquals(5500L, cues[1].startMs)
        assertEquals(8250L, cues[1].endMs)
        assertTrue(cues[1].text.contains("跨行内容"))
    }

    @Test
    fun offset_moves_lookup_window() {
        val cues = parseSrtCues(sample)
        val at6s = cues.firstOrNull { t -> t.startMs <= 6000 && t.endMs > 6000 }?.text
        assertEquals("第二句\n跨行内容", at6s)

        // 偏移 +2000ms：视频 8s 时应命中原本 6s 的那条字幕
        val offset = 2000L
        val shifted = cues.firstOrNull { t -> t.startMs <= 8000 - offset && t.endMs > 8000 - offset }?.text
        assertEquals("第二句\n跨行内容", shifted)
    }

    @Test
    fun ass_and_vtt_convert_to_srt() {
        val ass = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,{\an8}你好世界
        """.trimIndent()
        val srt = SubtitleCodec.toSrt("x.ass", ass)
        assertTrue(srt.contains("-->"))
        assertTrue(srt.contains("你好世界"))
        assertEquals(1, parseSrtCues(srt).size)

        val vtt = """
            WEBVTT

            00:00:02.000 --> 00:00:04.000
            字幕内容
        """.trimIndent()
        val srt2 = SubtitleCodec.toSrt("x.vtt", vtt)
        assertEquals(1, parseSrtCues(srt2).size)
    }
}