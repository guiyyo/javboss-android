package com.javboss.android.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 版本比较 / 镜像 URL 拼接：更新功能的纯逻辑部分。 */
class UpdateCheckerTest {

    @Test
    fun isNewer_basicSemver() {
        assertTrue(UpdateChecker.isNewer("0.33.1", "0.33.0"))
        assertFalse(UpdateChecker.isNewer("0.33.0", "0.33.0"))
        assertFalse(UpdateChecker.isNewer("0.32.9", "0.33.0"))
    }

    @Test
    fun isNewer_handlesMultiDigitSegments() {
        assertTrue(UpdateChecker.isNewer("0.34.10", "0.34.9"))
        assertFalse(UpdateChecker.isNewer("0.34.9", "0.34.10"))
    }

    @Test
    fun isNewer_ignoresPrefixAndBuildMetadata() {
        assertTrue(UpdateChecker.isNewer("v0.34.1", "0.34.0"))
        assertFalse(UpdateChecker.isNewer("0.34.0+abc", "0.34.0"))
        assertFalse(UpdateChecker.isNewer("0.34.0-beta", "0.34.0"))
    }

    @Test
    fun isNewer_treatsMissingSegmentsAsZero() {
        assertTrue(UpdateChecker.isNewer("1.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewer("1", "1.0.0"))
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0"))
    }

    @Test
    fun isNewer_neverTrueForGarbageCurrent() {
        // 当前版本为空/异常时不应误报（parse 会退化成 0）
        assertTrue(UpdateChecker.isNewer("0.1.0", ""))
        assertFalse(UpdateChecker.isNewer("", ""))
    }
}

class UpdateMirrorTest {

    @Test
    fun wrap_prefixStyle() {
        val url = "https://github.com/a/b/releases/download/v1/app.apk"
        assertEquals("https://ghfast.top/$url", UpdateMirror.wrap(url, "https://ghfast.top/"))
        assertEquals("https://ghfast.top/$url", UpdateMirror.wrap(url, "https://ghfast.top"))
    }

    @Test
    fun wrap_placeholderStyle() {
        val url = "https://github.com/a/b/c.apk"
        assertEquals(
            "https://hub.gitmirror.com/https://github.com/a/b/c.apk",
            UpdateMirror.wrap(url, "https://hub.gitmirror.com/{url}"),
        )
    }

    @Test
    fun wrap_emptyMirrorReturnsOriginal() {
        val url = "https://github.com/a/b/c.apk"
        assertEquals(url, UpdateMirror.wrap(url, ""))
        assertEquals(url, UpdateMirror.wrap(url, "   "))
    }

    @Test
    fun wrap_isIdempotent() {
        val url = "https://github.com/a/b/c.apk"
        val wrapped = UpdateMirror.wrap(url, "https://ghfast.top/")
        assertEquals(wrapped, UpdateMirror.wrap(wrapped, "https://ghfast.top/"))
    }

    @Test
    fun label_resolvesPresets() {
        assertEquals("GitHub 直连", UpdateMirror.label(""))
        assertTrue(UpdateMirror.label("https://ghfast.top/").contains("ghfast"))
        assertEquals("https://custom.example/", UpdateMirror.label("https://custom.example/"))
    }
}
