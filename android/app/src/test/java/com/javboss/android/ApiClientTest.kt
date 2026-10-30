package com.javboss.android

import com.javboss.android.core.net.ApiClient
import com.javboss.android.data.repo.JavQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientTest {

    @Test
    fun normalizeBaseUrl_addsSchemeAndTrailingSlash() {
        assertEquals("http://192.168.1.1:8655/", ApiClient.normalizeBaseUrl("192.168.1.1:8655"))
        assertEquals("http://192.168.1.1:8655/", ApiClient.normalizeBaseUrl("http://192.168.1.1:8655"))
        assertEquals("https://example.com/", ApiClient.normalizeBaseUrl("https://example.com/"))
        assertEquals("http://192.168.1.1:8655/", ApiClient.normalizeBaseUrl("  "))
    }

    @Test
    fun absoluteUrl_joinsRelativePaths() {
        val base = "http://192.168.1.1:8655"
        assertEquals(
            "http://192.168.1.1:8655/videos/12/stream",
            ApiClient.absoluteUrl(base, "/videos/12/stream"),
        )
        assertEquals(
            "http://192.168.1.1:8655/videos/12/stream",
            ApiClient.absoluteUrl("$base/", "/videos/12/stream"),
        )
        assertEquals(
            "https://cdn.example.com/a.jpg",
            ApiClient.absoluteUrl(base, "https://cdn.example.com/a.jpg"),
        )
    }

    @Test
    fun parseErrorMessage_readsLocalizedBody() {
        val (zh, en) = ApiClient.parseErrorMessage("""{"error_zh":"需要登录后才能继续","error_en":"Authentication is required"}""")
        assertEquals("需要登录后才能继续", zh)
        assertEquals("Authentication is required", en)
    }

    @Test
    fun parseErrorMessage_handlesNonJsonBody() {
        val (zh, en) = ApiClient.parseErrorMessage("boom")
        assertEquals("boom", zh)
        assertEquals("", en)
        val (emptyZh, emptyEn) = ApiClient.parseErrorMessage(null)
        assertEquals("", emptyZh)
        assertEquals("", emptyEn)
    }

    @Test
    fun javQuery_hasFilterDetectsActiveFilters() {
        assertFalse(JavQuery().hasFilter())
        assertTrue(JavQuery(search = "abc").hasFilter())
        assertTrue(JavQuery(studioId = 3).hasFilter())
        assertTrue(JavQuery(idolIds = listOf(1)).hasFilter())
        assertTrue(JavQuery(tagIds = listOf(1, 2)).hasFilter())
        assertTrue(JavQuery(soloOnly = true).hasFilter())
        assertTrue(JavQuery(ratingMin = 2.0, ratingMax = 4.0).hasFilter())
        assertTrue(JavQuery(favoriteGroupId = 9).hasFilter())
        assertTrue(JavQuery(prefix = "SNOS").hasFilter())
    }
}
