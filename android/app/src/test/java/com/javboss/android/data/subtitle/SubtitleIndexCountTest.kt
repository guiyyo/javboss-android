package com.javboss.android.data.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

/** 统计口径必须与角标/筛选一致：LOCAL_FOLDER 与 EMBEDDED 也算「已配」。 */
class SubtitleIndexCountTest {

    private fun state(
        source: SubtitleSource,
        zh: Boolean,
        noSub: Boolean = false,
        noNeed: Boolean = false,
    ) = SubtitleState(code = "X", zh = zh, source = source, noSubtitle = noSub, noNeedSubtitle = noNeed)

    @Test
    fun countsIncludeLocalFolderAndEmbedded() {
        val states = mapOf(
            "A" to state(SubtitleSource.ONLINE, true),
            "B" to state(SubtitleSource.LOCAL_FOLDER, true),
            "C" to state(SubtitleSource.EMBEDDED, true),
            "D" to state(SubtitleSource.NONE, false, noSub = true),
            "E" to state(SubtitleSource.NONE, false, noNeed = true),
            "F" to SubtitleState(code = "F"),
        )
        val (configured, noSub, noNeed) = SubtitleIndex.countStates(states)
        assertEquals("已配应含在线/本地文件夹/内嵌", 3, configured)
        assertEquals(1, noSub)
        assertEquals(1, noNeed)
    }

    @Test
    fun embeddedWithoutLanguageDoesNotCountAsConfigured() {
        val states = mapOf("A" to state(SubtitleSource.EMBEDDED, false))
        assertEquals(0, SubtitleIndex.countStates(states).first)
    }

    @Test
    fun noNeedWinsOverEverything() {
        val states = mapOf(
            "A" to state(SubtitleSource.ONLINE, true, noSub = true, noNeed = true),
        )
        val (configured, noSub, noNeed) = SubtitleIndex.countStates(states)
        assertEquals(0, configured)
        assertEquals(0, noSub)
        assertEquals(1, noNeed)
    }

    @Test
    fun emptyMapGivesZeros() {
        val (a, b, c) = SubtitleIndex.countStates(emptyMap())
        assertEquals(0, a); assertEquals(0, b); assertEquals(0, c)
    }
}
