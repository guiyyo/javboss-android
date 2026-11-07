package com.javboss.android.core.log

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactorTest {

    @Test
    fun masksIpAndPort() {
        val out = LogRedactor.redact("connect 192.168.1.1:8655 failed")
        assertFalse(out.contains("192.168.1.1"))
        assertFalse(out.contains("8655"))
        assertTrue(out.contains("192.168.***.***"))
    }

    @Test
    fun masksCookiesAndTokens() {
        val out = LogRedactor.redact("Cookie: session=abc123 token=xyz789 Authorization: Bearer QWERTY")
        assertFalse(out.contains("abc123"))
        assertFalse(out.contains("xyz789"))
        assertFalse(out.contains("QWERTY"))
    }

    @Test
    fun masksWindowsUserAndEmail() {
        val out = LogRedactor.redact("path C:\\Users\\jack\\javbus\\android")
        assertFalse(out.contains("jack"))
        assertTrue(out.contains("<user>"))
        val mail = LogRedactor.redact("contact me@example.com now")
        assertFalse(mail.contains("me@example.com"))
        assertTrue(mail.contains("<mail>"))
    }

    @Test
    fun masksMacAddress() {
        assertFalse(LogRedactor.redact("mac 3c:52:82:11:aa:bb up").contains("3c:52:82:11:aa:bb"))
    }

    @Test
    fun keepsDebuggingEvidence() {
        val line = "W/JavBossFill: search failed for SNOS-079: 所有字幕源都失败（最后一次：timeout 28000ms）"
        val out = LogRedactor.redact(line)
        assertTrue(out.contains("SNOS-079"))
        assertTrue(out.contains("28000ms"))
        assertTrue(out.contains("所有字幕源都失败"))
        assertTrue(out.contains("W/JavBossFill"))
    }

    @Test
    fun emptyStaysEmpty() {
        assertTrue(LogRedactor.redact("").isEmpty())
    }
    @Test
    fun masksBasicAuthAndCookieHeader() {
        val basic = LogRedactor.redact("Authorization: Basic dXNlcjpwYXNz")
        assertFalse(basic.contains("dXNlcjpwYXNz"))
        val cookie = LogRedactor.redact("Cookie: a=1; b=2; javboss_session_x=deadbeef")
        assertFalse(cookie.contains("deadbeef"))
        assertFalse(cookie.contains("a=1"))
    }

    @Test
    fun masksTokenInQueryString() {
        val out = LogRedactor.redact("GET /x?token=SECRET123&page=2 HTTP/1.1")
        assertFalse(out.contains("SECRET123"))
        assertTrue(out.contains("page=2"))
    }

    @Test
    fun masksProxyHostField() {
        val out = LogRedactor.redact("net proxy_host=my-nas.local proxy=8080")
        assertFalse(out.contains("my-nas.local"))
    }
    @Test
    fun masksCustomSecretHeadersAndPrivateHosts() {
        val h = LogRedactor.redact("X-Api-Key: abc123def X-Auth-Token: tok999")
        assertFalse(h.contains("abc123def"))
        assertFalse(h.contains("tok999"))
        val b = LogRedactor.redact("Authorization: Bearer abc=def==")
        assertFalse(b.contains("abc=def"))
        val host = LogRedactor.redact("hit http://my-nas.local:8899/?u=x")
        assertFalse(host.contains("my-nas.local"))
    }
}
