package com.javboss.android.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceChoiceTest {

    @Test
    fun pick_honoursUserPreferenceOverServerHint() {
        // 用户开「优先直连」：即使服务端建议 hls（HEVC/MKV 场景），也要先试直连
        assertEquals("direct", SourceChoice.pick(hasDirect = true, hasHls = true, preferDirect = true))
        // 用户关掉：走 HLS
        assertEquals("hls", SourceChoice.pick(hasDirect = true, hasHls = true, preferDirect = false))
    }

    @Test
    fun pick_fallsBackWhenPreferredSourceMissing() {
        assertEquals("hls", SourceChoice.pick(hasDirect = false, hasHls = true, preferDirect = true))
        assertEquals("direct", SourceChoice.pick(hasDirect = true, hasHls = false, preferDirect = false))
        assertNull(SourceChoice.pick(hasDirect = false, hasHls = false, preferDirect = true))
    }

    @Test
    fun fallback_switchesToTheOtherSource() {
        assertEquals("hls", SourceChoice.fallback("direct", hasDirect = true, hasHls = true))
        assertEquals("direct", SourceChoice.fallback("hls", hasDirect = true, hasHls = true))
        assertNull(SourceChoice.fallback("direct", hasDirect = true, hasHls = false))
        assertNull(SourceChoice.fallback(null, hasDirect = true, hasHls = true))
    }
}
