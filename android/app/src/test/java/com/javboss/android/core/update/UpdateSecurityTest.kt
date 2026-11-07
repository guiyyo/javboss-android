package com.javboss.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSecurityTest {

    private val good = "5fbe8a35b603d09a49a2fe5a7108aa7c4ef3e082e4c6f078adb55f65c0edaf07"

    @Test
    fun normalizeDigest_acceptsCommonForms() {
        assertEquals(good, UpdateSecurity.normalizeDigest(good))
        assertEquals(good, UpdateSecurity.normalizeDigest(good.uppercase()))
        assertEquals(good, UpdateSecurity.normalizeDigest("sha256:" + good))
        assertEquals(good, UpdateSecurity.normalizeDigest("  SHA256: " + good + "  "))
    }

    @Test
    fun normalizeDigest_rejectsGarbage() {
        assertNull(UpdateSecurity.normalizeDigest(null))
        assertNull(UpdateSecurity.normalizeDigest(""))
        assertNull(UpdateSecurity.normalizeDigest("abc"))
        assertNull(UpdateSecurity.normalizeDigest(good.dropLast(1)))
        assertNull(UpdateSecurity.normalizeDigest("z".repeat(64)))
    }

    @Test
    fun digestsMatch_requiresBothValid() {
        assertTrue(UpdateSecurity.digestsMatch(good, "sha256:" + good.uppercase()))
        assertFalse(UpdateSecurity.digestsMatch(good, null))
        assertFalse(UpdateSecurity.digestsMatch(good, "deadbeef"))
    }

    @Test
    fun sanitizeMirror_allowsHttpAndAutoPrefixesBareHost() {
        // 产品决定：https 不强制，只挡非法格式
        assertEquals("", UpdateSecurity.sanitizeMirror(null))
        assertEquals("", UpdateSecurity.sanitizeMirror(""))
        assertEquals("http://ghfast.top/", UpdateSecurity.sanitizeMirror("http://ghfast.top/"))
        assertEquals("https://ghfast.top/", UpdateSecurity.sanitizeMirror(" https://ghfast.top/ "))
        assertEquals("https://ghfast.top/", UpdateSecurity.sanitizeMirror("ghfast.top/"))
        assertEquals("http://192.168.1.1:8899/", UpdateSecurity.sanitizeMirror("http://192.168.1.1:8899/"))
        assertEquals("", UpdateSecurity.sanitizeMirror("ftp://x.com/"))
        assertEquals("", UpdateSecurity.sanitizeMirror("file:///sdcard/a"))
        assertEquals("", UpdateSecurity.sanitizeMirror("localhost/"))
        assertEquals("https://x.com/{url}", UpdateSecurity.sanitizeMirror("https://x.com/{url}"))
    }
}
