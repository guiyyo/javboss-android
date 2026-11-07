package com.javboss.android.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTextTest {

    @Test
    fun normalizeCode_insertsDashAndPadsDigits() {
        assertEquals("SNOS-079", SearchText.normalizeCode("snos079"))
        assertEquals("SNOS-079", SearchText.normalizeCode("SNOS-79"))
        assertEquals("SNOS-079", SearchText.normalizeCode("SNOS-079"))
        assertEquals("SNOS-079", SearchText.normalizeCode(" snos 079 "))
        assertEquals("SNOS-079", SearchText.normalizeCode("snos_079"))
    }

    @Test
    fun normalizeCode_handlesFullWidth() {
        assertEquals("SNOS-079", SearchText.normalizeCode("ＳＮＯＳ－０７９"))
        assertEquals("SNOS-079", SearchText.normalizeCode("ｓｎｏｓ０７９"))
    }

    @Test
    fun normalizeCode_keepsLongerDigitGroups() {
        assertEquals("MIUM-1487", SearchText.normalizeCode("MIUM1487"))
        assertEquals("390JAC-120", SearchText.normalizeCode("390JAC-120"))
    }

    @Test
    fun normalizeCode_returnsNullForNonCodes() {
        assertNull(SearchText.normalizeCode("中文标题"))
        assertNull(SearchText.normalizeCode(""))
        assertNull(SearchText.normalizeCode("   "))
        assertNull(SearchText.normalizeCode("079"))
        assertNull(SearchText.normalizeCode("SNOS"))
    }

    @Test
    fun forSearch_fallsBackToHalfWidthTrim() {
        assertEquals("SNOS-079", SearchText.forSearch("ＳＮＯＳ－０７９"))
        assertEquals("abc", SearchText.forSearch("abc"))
    }
}
