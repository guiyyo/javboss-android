package com.javboss.android.core.lock

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * TV PIN 哈希（F2）。
 *
 * 历史：0.36.13 及以前是**裸 SHA-256**，而且设置密码的对话框只翻了开关、根本没存哈希（等于没锁）。
 * 现在：随机盐 + 迭代 SHA-256，纯 JVM 实现（minSdk 22 上 PBKDF2 的算法名在 API 26 前后不一致，
 * 自己迭代更可控；6000 轮在手机上约 10~20ms）。
 *
 * 兼容：旧的无盐哈希仍能通过 [verifyLegacy]，校验成功后由调用方换成加盐值（见 SettingsStore.verifyTvPin）。
 */
object PinHasher {

    const val ITERATIONS = 20000
    private const val SALT_BYTES = 16
    private const val HEX = "0123456789abcdef"

    fun randomSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    /** 加盐迭代哈希：h0 = SHA-256(salt||pin)，之后 h = SHA-256(h||salt)，共 [iterations] 轮。 */
    fun hash(pin: String, saltHex: String, iterations: Int = ITERATIONS): String {
        val rounds = if (iterations in 1..200_000) iterations else ITERATIONS
        val md = MessageDigest.getInstance("SHA-256")
        md.update(saltHex.toByteArray(Charsets.UTF_8))
        md.update(pin.toByteArray(Charsets.UTF_8))
        var h = md.digest()
        val saltBytes = saltHex.toByteArray(Charsets.UTF_8)
        repeat(rounds - 1) {
            md.reset()
            md.update(h)
            md.update(saltBytes)
            h = md.digest()
        }
        return h.toHex()
    }

    /** 旧算法：无盐 SHA-256（仅用于识别存量数据并迁移）。 */
    fun legacyHash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8)).toHex()

    fun verifyLegacy(pin: String, expectedHex: String): Boolean =
        expectedHex.isNotBlank() && constantTimeEquals(legacyHash(pin), expectedHex)

    fun verify(pin: String, saltHex: String, iterations: Int, expectedHex: String): Boolean {
        if (saltHex.isBlank() || expectedHex.isBlank()) return false
        return constantTimeEquals(hash(pin, saltHex, iterations), expectedHex)
    }

    /** 定长比较，避免按字节提前返回带来的时序差异。 */
    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }
}
