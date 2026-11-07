package com.javboss.android.core.backup

import com.javboss.android.data.history.WatchEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {

    @Test
    fun watchHistory_keepsNewerLocal() {
        val local = mapOf("a" to WatchEntry(at = 200, positionMs = 5000))
        val incoming = mapOf("a" to WatchEntry(at = 100, positionMs = 999), "b" to WatchEntry(at = 300))
        val merged = BackupMerge.mergeWatchHistory(local, incoming)
        assertEquals(5000L, merged["a"]!!.positionMs)
        assertEquals(300L, merged["b"]!!.at)
        assertEquals(2, merged.size)
    }

    @Test
    fun watchHistory_incomingWinsWhenNewer() {
        val local = mapOf("a" to WatchEntry(at = 100, positionMs = 1))
        val incoming = mapOf("a" to WatchEntry(at = 200, positionMs = 2))
        assertEquals(2L, BackupMerge.mergeWatchHistory(local, incoming)["a"]!!.positionMs)
    }

    @Test
    fun subtitlePrefs_importOverrides() {
        val local = mapOf("SNOS079" to SubtitlePrefEntry(name = "local.srt", offsetMs = 0))
        val incoming = mapOf("SNOS079" to SubtitlePrefEntry(name = "backup.srt", offsetMs = 500))
        val merged = BackupMerge.mergeSubtitlePrefs(local, incoming)
        assertEquals("backup.srt", merged["SNOS079"]!!.name)
        assertEquals(500L, merged["SNOS079"]!!.offsetMs)
    }

    @Test
    fun searchHistory_dedupIgnoreCaseAndCap() {
        val merged = BackupMerge.mergeSearchHistory(
            local = listOf("snos-079", "MIUM-1487"),
            incoming = listOf("SNOS-079", "人气"),
        )
        assertEquals(listOf("SNOS-079", "人气", "MIUM-1487"), merged)
        val many = (1..20).map { "q" + it }
        assertEquals(12, BackupMerge.mergeSearchHistory(emptyList(), many).size)
    }

    @Test
    fun searchHistory_skipsBlank() {
        assertTrue(BackupMerge.mergeSearchHistory(listOf("  "), listOf("")).isEmpty())
    }
}
