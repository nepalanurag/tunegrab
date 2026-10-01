package com.tunegrab.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the pure lyric helpers in [LyricsRepository]:
 * LRC parsing, title cleanup, and LRCLIB response picking.
 */
class LyricsRepositoryTest {

    // ---------------- parseLrc ----------------

    @Test fun `parses basic centisecond timestamps`() {
        val lines = LyricsRepository.parseLrc("[00:12.34] hello\n[01:02.50] world")
        assertEquals(2, lines.size)
        assertEquals(12_340L, lines[0].timeMs)
        assertEquals("hello", lines[0].text)
        assertEquals(62_500L, lines[1].timeMs)
    }

    @Test fun `parses timestamps without fraction`() {
        val lines = LyricsRepository.parseLrc("[00:05] intro")
        assertEquals(listOf(LyricLine(5_000L, "intro")), lines)
    }

    @Test fun `multiple timestamps on one line fan out`() {
        val lines = LyricsRepository.parseLrc("[00:01.00][00:02.00] echo")
        assertEquals(2, lines.size)
        assertEquals(1_000L, lines[0].timeMs)
        assertEquals(2_000L, lines[1].timeMs)
        assertTrue(lines.all { it.text == "echo" })
    }

    @Test fun `millisecond fractions parsed as ms`() {
        val lines = LyricsRepository.parseLrc("[00:01.500] half")
        assertEquals(1_500L, lines[0].timeMs)
    }

    @Test fun `unsorted input comes out sorted`() {
        val lines = LyricsRepository.parseLrc("[00:10.00] b\n[00:02.00] a")
        assertEquals("a", lines[0].text)
        assertEquals("b", lines[1].text)
    }

    @Test fun `lines without timestamps or text are skipped`() {
        val lines = LyricsRepository.parseLrc("[ar:Artist]\n[00:01.00]   \nplain text\n[00:02.00] ok")
        assertEquals(1, lines.size)
        assertEquals("ok", lines[0].text)
    }

    // ---------------- cleanTitle ----------------

    @Test fun `strips official video suffix`() {
        assertEquals("Blinding Lights", LyricsRepository.cleanTitle("Blinding Lights (Official Video)"))
    }

    @Test fun `strips topic suffix`() {
        assertEquals("Starboy", LyricsRepository.cleanTitle("Starboy - Topic"))
    }

    @Test fun `strips lyrics bracket`() {
        assertEquals("Song", LyricsRepository.cleanTitle("Song [Lyrics]"))
    }

    @Test fun `keeps musical qualifiers`() {
        assertEquals(
            "Song (feat. Someone)",
            LyricsRepository.cleanTitle("Song (feat. Someone)"),
        )
        assertEquals(
            "Song (Remastered)",
            LyricsRepository.cleanTitle("Song (Remastered)"),
        )
    }

    @Test fun `plain title unchanged`() {
        assertEquals("Blinding Lights", LyricsRepository.cleanTitle("Blinding Lights"))
    }

    // ---------------- parseSearchResults ----------------

    @Test fun `prefers synced lyrics over plain`() {
        val body = """[
          {"trackName":"A","artistName":"B","syncedLyrics":null,"plainLyrics":"la la"},
          {"trackName":"A","artistName":"B","syncedLyrics":"[00:01.00] hey","plainLyrics":"la la"}
        ]"""
        val lyrics = LyricsRepository.parseSearchResults(body)
        assertEquals(true, lyrics?.synced)
        assertEquals("hey", lyrics?.lines?.firstOrNull()?.text)
    }

    @Test fun `falls back to plain lyrics`() {
        val body = """[{"trackName":"A","artistName":"B","syncedLyrics":null,"plainLyrics":"line1\nline2"}]"""
        val lyrics = LyricsRepository.parseSearchResults(body)
        assertEquals(false, lyrics?.synced)
        assertEquals("line1\nline2", lyrics?.plainText)
    }

    @Test fun `empty results return null`() {
        assertNull(LyricsRepository.parseSearchResults("[]"))
        assertNull(LyricsRepository.parseSearchResults("garbage"))
    }
}
