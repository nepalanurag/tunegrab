package com.tunegrab.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStatsTest {

    private fun stat(
        key: String,
        artist: String = "Artist",
        plays: Int = 1,
        lastPlayed: Long = 1000L,
    ) = PlayStat(
        key = key,
        title = "Title $key",
        artist = artist,
        artworkKey = null,
        playCount = plays,
        lastPlayedMs = lastPlayed,
    )

    @Test
    fun topSongsOrdersByPlayCountDesc() {
        val stats = listOf(
            stat("a", plays = 2),
            stat("b", plays = 5),
            stat("c", plays = 3),
        )
        val top = ListeningStats.topSongs(stats)
        assertEquals(listOf("b", "c", "a"), top.map { it.key })
    }

    @Test
    fun topSongsBreaksTiesByRecency() {
        val stats = listOf(
            stat("a", plays = 3, lastPlayed = 1000L),
            stat("b", plays = 3, lastPlayed = 2000L),
        )
        val top = ListeningStats.topSongs(stats)
        assertEquals(listOf("b", "a"), top.map { it.key })
    }

    @Test
    fun topSongsExcludesZeroPlays() {
        val stats = listOf(
            stat("a", plays = 0, lastPlayed = 5000L),
            stat("b", plays = 1),
        )
        val top = ListeningStats.topSongs(stats)
        assertEquals(listOf("b"), top.map { it.key })
    }

    @Test
    fun topSongsRespectsLimit() {
        val stats = (1..10).map { stat("k$it", plays = it) }
        assertEquals(3, ListeningStats.topSongs(stats, limit = 3).size)
    }

    @Test
    fun topArtistsAggregatesPlays() {
        val stats = listOf(
            stat("a", artist = "X", plays = 2),
            stat("b", artist = "X", plays = 3),
            stat("c", artist = "Y", plays = 4),
        )
        val top = ListeningStats.topArtists(stats)
        assertEquals(2, top.size)
        assertEquals("X", top[0].artist)
        assertEquals(5, top[0].playCount)
        assertEquals("Y", top[1].artist)
        assertEquals(4, top[1].playCount)
    }

    @Test
    fun topArtistsSkipsBlankArtist() {
        val stats = listOf(
            stat("a", artist = "", plays = 10),
            stat("b", artist = "Y", plays = 1),
        )
        val top = ListeningStats.topArtists(stats)
        assertEquals(listOf("Y"), top.map { it.artist })
    }

    @Test
    fun topArtistsBreaksTiesAlphabetically() {
        val stats = listOf(
            stat("a", artist = "Zebra", plays = 2),
            stat("b", artist = "Apple", plays = 2),
        )
        val top = ListeningStats.topArtists(stats)
        assertEquals(listOf("Apple", "Zebra"), top.map { it.artist })
    }

    @Test
    fun recentlyPlayedOrdersByRecency() {
        val stats = listOf(
            stat("a", lastPlayed = 1000L),
            stat("b", lastPlayed = 3000L),
            stat("c", lastPlayed = 2000L),
        )
        val recent = ListeningStats.recentlyPlayed(stats)
        assertEquals(listOf("b", "c", "a"), recent.map { it.key })
    }

    @Test
    fun recentlyPlayedExcludesNeverPlayed() {
        val stats = listOf(
            stat("a", lastPlayed = 0L),
            stat("b", lastPlayed = 1000L),
        )
        val recent = ListeningStats.recentlyPlayed(stats)
        assertEquals(listOf("b"), recent.map { it.key })
    }

    @Test
    fun emptyInputGivesEmptyOutput() {
        assertTrue(ListeningStats.topSongs(emptyList()).isEmpty())
        assertTrue(ListeningStats.topArtists(emptyList()).isEmpty())
        assertTrue(ListeningStats.recentlyPlayed(emptyList()).isEmpty())
    }
}
