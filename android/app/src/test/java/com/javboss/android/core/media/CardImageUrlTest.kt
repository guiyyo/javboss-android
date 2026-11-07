package com.javboss.android.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class CardImageUrlTest {

    @Test
    fun prefersJavCoverWhenCodePresent() {
        assertEquals(
            "http://host:8655/jav/SNOS-079/cover",
            CardImageUrl.forVideo("http://host:8655", "SNOS-079", 1974),
        )
    }

    @Test
    fun fallsBackToThumbnailWhenNoCode() {
        assertEquals(
            "http://host:8655/videos/1974/thumbnail",
            CardImageUrl.forVideo("http://host:8655", null, 1974),
        )
        assertEquals(
            "http://host:8655/videos/1974/thumbnail",
            CardImageUrl.forVideo("http://host:8655", "   ", 1974),
        )
    }

    @Test
    fun trimsTrailingSlash() {
        assertEquals(
            "http://host:8655/jav/JUR-897/cover",
            CardImageUrl.forVideo("http://host:8655/", "JUR-897", 1966),
        )
        assertEquals(
            "http://host:8655/videos/1966/thumbnail",
            CardImageUrl.forVideo("http://host:8655//", null, 1966),
        )
    }

    @Test
    fun keepsCodeAsIs() {
        assertEquals("http://h/jav/390JAC-120/cover", CardImageUrl.javCover("http://h", "390JAC-120"))
    }
}
