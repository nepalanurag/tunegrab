package com.tunegrab.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the pure library helpers (no Android runtime needed). */
class LibraryLogicTest {

    private data class FakeSong(
        override val title: String,
        override val artist: String,
        override val album: String,
        override val durationMs: Long,
        override val dateAddedSec: Long,
        override val year: Int
    ) : SongSortable

    private fun sampleSongs() = listOf(
        FakeSong("b Song", "Zed", "Alpha", 200_000, 100, 2020),
        FakeSong("A song", "Amy", "Beta", 60_000, 300, 2024),
        FakeSong("C tune", "Amy", "Alpha", 120_000, 200, 2021)
    )

    // ---------- sortSongs ----------

    @Test
    fun `sort by title is case insensitive`() {
        val titles = sortSongs(sampleSongs(), SortBy.TITLE).map { it.title }
        assertEquals(listOf("A song", "b Song", "C tune"), titles)
    }

    @Test
    fun `sort by artist groups then orders by album then title`() {
        val songs = sampleSongs()
        val result = sortSongs(songs, SortBy.ARTIST)
        assertEquals("Amy", result[0].artist)
        assertEquals("Amy", result[1].artist)
        // Same artist: Alpha before Beta.
        assertEquals("Alpha", result[0].album)
        assertEquals("Beta", result[1].album)
        assertEquals("Zed", result[2].artist)
    }

    @Test
    fun `sort by album orders by album then title`() {
        val albums = sortSongs(sampleSongs(), SortBy.ALBUM).map { it.album }
        assertEquals(listOf("Alpha", "Alpha", "Beta"), albums)
        val alphaTitles = sortSongs(sampleSongs(), SortBy.ALBUM)
            .take(2).map { it.title }
        assertEquals(listOf("b Song", "C tune"), alphaTitles)
    }

    @Test
    fun `sort by duration is shortest first`() {
        val durations = sortSongs(sampleSongs(), SortBy.DURATION).map { it.durationMs }
        assertEquals(listOf(60_000L, 120_000L, 200_000L), durations)
    }

    @Test
    fun `sort by date added is newest first`() {
        val dates = sortSongs(sampleSongs(), SortBy.DATE_ADDED).map { it.dateAddedSec }
        assertEquals(listOf(300L, 200L, 100L), dates)
    }

    @Test
    fun `sort by year is newest first with title tiebreak`() {
        val result = sortSongs(sampleSongs(), SortBy.YEAR)
        assertEquals(listOf(2024, 2021, 2020), result.map { it.year })
        val ties = listOf(
            FakeSong("Zebra", "A", "A", 1, 1, 2020),
            FakeSong("apple", "A", "A", 1, 1, 2020)
        )
        assertEquals(
            listOf("apple", "Zebra"),
            sortSongs(ties, SortBy.YEAR).map { it.title }
        )
    }

    @Test
    fun `sort empty list returns empty`() {
        assertTrue(sortSongs(emptyList(), SortBy.TITLE).isEmpty())
    }

    // ---------- diffSongs ----------

    @Test
    fun `diff finds added and removed ids`() {
        val diff = diffSongs(old = listOf(1L, 2L, 3L), new = listOf(2L, 3L, 4L))
        assertEquals(listOf(4L), diff.added)
        assertEquals(listOf(1L), diff.removed)
    }

    @Test
    fun `diff preserves new order for added and old order for removed`() {
        val diff = diffSongs(old = listOf(5L, 1L, 9L), new = listOf(7L, 9L, 3L))
        assertEquals(listOf(7L, 3L), diff.added)
        assertEquals(listOf(5L, 1L), diff.removed)
    }

    @Test
    fun `diff of identical lists is empty`() {
        val ids = listOf(1L, 2L, 3L)
        val diff = diffSongs(ids, ids)
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
    }

    @Test
    fun `diff from empty old marks everything added`() {
        val diff = diffSongs(emptyList(), listOf(1L, 2L))
        assertEquals(listOf(1L, 2L), diff.added)
        assertTrue(diff.removed.isEmpty())
    }

    @Test
    fun `diff to empty new marks everything removed`() {
        val diff = diffSongs(listOf(1L, 2L), emptyList())
        assertTrue(diff.added.isEmpty())
        assertEquals(listOf(1L, 2L), diff.removed)
    }

    // ---------- folderDisplayName ----------

    @Test
    fun `folder display name takes last segment`() {
        assertEquals("Music", folderDisplayName("/storage/emulated/0/Music"))
    }

    @Test
    fun `folder display name strips trailing slash`() {
        assertEquals("Music", folderDisplayName("/storage/emulated/0/Music/"))
    }

    @Test
    fun `folder display name of root stays root`() {
        assertEquals("/", folderDisplayName("/"))
    }

    @Test
    fun `folder display name of empty string stays empty`() {
        assertEquals("", folderDisplayName(""))
    }

    @Test
    fun `folder display name trims surrounding whitespace`() {
        assertEquals("Downloads", folderDisplayName("  /sdcard/Downloads  "))
    }
}
