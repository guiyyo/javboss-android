package com.javboss.android.core.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun hash_isDeterministicPerSalt() {
        val salt = PinHasher.randomSalt()
        assertEquals(PinHasher.hash("1234", salt, 100), PinHasher.hash("1234", salt, 100))
    }

    @Test
    fun hash_differsAcrossSaltsAndPins() {
        val s1 = PinHasher.randomSalt()
        val s2 = PinHasher.randomSalt()
        assertNotEquals(s1, s2)
        assertNotEquals(PinHasher.hash("1234", s1, 100), PinHasher.hash("1234", s2, 100))
        assertNotEquals(PinHasher.hash("1234", s1, 100), PinHasher.hash("1235", s1, 100))
    }

    @Test
    fun verify_acceptsCorrectPinOnly() {
        val salt = PinHasher.randomSalt()
        val h = PinHasher.hash("246810", salt, 200)
        assertTrue(PinHasher.verify("246810", salt, 200, h))
        assertFalse(PinHasher.verify("246811", salt, 200, h))
        assertFalse(PinHasher.verify("246810", "", 200, h))
        assertFalse(PinHasher.verify("246810", salt, 200, ""))
    }

    @Test
    fun legacyHash_matchesPlainSha256() {
        // 已知向量：SHA-256("1234")
        assertEquals(
            "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
            PinHasher.legacyHash("1234"),
        )
    }

    @Test
    fun salt_isHexAndLongEnough() {
        val salt = PinHasher.randomSalt()
        assertEquals(32, salt.length)
        assertTrue(salt.all { it in "0123456789abcdef" })
    }

    @Test
    fun verifyLegacy_acceptsOldUnsaltedHash() {
        // 升级兼容：0.36.13 及以前若真的存过裸 SHA-256，同一个密码必须仍能解开
        val legacy = PinHasher.legacyHash("1234")
        assertTrue(PinHasher.verifyLegacy("1234", legacy))
        assertFalse(PinHasher.verifyLegacy("12345", legacy))
        assertFalse(PinHasher.verifyLegacy("1234", ""))
    }

    @Test
    fun legacyAndSaltedHashes_doNotCollide() {
        // 同密码：裸哈希 ≠ 加盐哈希，所以「有 salt 走新算法、无 salt 走老算法」不会互相误判
        val salt = PinHasher.randomSalt()
        assertNotEquals(PinHasher.legacyHash("1234"), PinHasher.hash("1234", salt, 100))
        assertFalse(PinHasher.verify("1234", salt, 100, PinHasher.legacyHash("1234")))
        assertFalse(PinHasher.verifyLegacy("1234", PinHasher.hash("1234", salt, 100)))
    }

    @Test
    fun constantTimeEquals_behavesLikeEquals() {
        assertTrue(PinHasher.constantTimeEquals("abc", "abc"))
        assertFalse(PinHasher.constantTimeEquals("abc", "abd"))
        assertFalse(PinHasher.constantTimeEquals("abc", "abcd"))
    }
}
