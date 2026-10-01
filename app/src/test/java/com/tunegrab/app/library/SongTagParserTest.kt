package com.tunegrab.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for [SongTagParser] (pure). */
class SongTagParserTest {

    @Test fun `artist dash title with official video suffix splits cleanly`() {
        val p = SongTagParser.parse(
            rawTitle = "Olivia Rodrigo - deja vu (Official Video)",
            artistTag = "OliviaRodrigoVEVO",
            uploader = "OliviaRodrigoVEVO",
        )
        assertEquals("Olivia Rodrigo", p.artist)
        assertEquals("deja vu", p.title)
        assertTrue(p.confident)
    }

    @Test fun `suffixes strip in a loop`() {
        val p = SongTagParser.parse(
            rawTitle = "Some Artist - Some Song (Official Video) [4K]",
            artistTag = null,
            uploader = null,
        )
        assertEquals("Some Artist", p.artist)
        assertEquals("Some Song", p.title)
        assertTrue(p.confident)
    }

    @Test fun `topic uploader becomes the artist when title has no split`() {
        val p = SongTagParser.parse(
            rawTitle = "deja vu",
            artistTag = null,
            uploader = "Olivia Rodrigo - Topic",
        )
        assertEquals("Olivia Rodrigo", p.artist)
        assertEquals("deja vu", p.title)
        assertTrue(p.confident)
    }

    @Test fun `vevo uploader is cleaned`() {
        val p = SongTagParser.parse(
            rawTitle = "deja vu (Lyric Video)",
            artistTag = null,
            uploader = "OliviaRodrigoVEVO",
        )
        assertEquals("OliviaRodrigo", p.artist)
        assertEquals("deja vu", p.title)
        assertTrue(p.confident)
    }

    @Test fun `no split falls back to the artist tag`() {
        val p = SongTagParser.parse(
            rawTitle = "deja vu",
            artistTag = "Olivia Rodrigo",
            uploader = null,
        )
        assertEquals("Olivia Rodrigo", p.artist)
        assertEquals("deja vu", p.title)
        assertTrue(p.confident)
    }

    @Test fun `no split and no artist is not confident`() {
        val p = SongTagParser.parse(
            rawTitle = "deja vu",
            artistTag = null,
            uploader = null,
        )
        assertFalse(p.confident)
    }

    @Test fun `whitespace is collapsed`() {
        val p = SongTagParser.parse(
            rawTitle = "  Olivia   Rodrigo  -  deja   vu  ",
            artistTag = null,
            uploader = null,
        )
        assertEquals("Olivia Rodrigo", p.artist)
        assertEquals("deja vu", p.title)
        assertTrue(p.confident)
    }

    @Test fun `cleanUploader strips topic`() {
        assertEquals("Olivia Rodrigo", SongTagParser.cleanUploader("Olivia Rodrigo - Topic"))
    }

    @Test fun `cleanUploader strips vevo and official`() {
        assertEquals("OliviaRodrigo", SongTagParser.cleanUploader("OliviaRodrigoVEVO"))
        assertEquals("Taylor Swift", SongTagParser.cleanUploader("Taylor Swift Official"))
    }

    @Test fun `cleanUploader returns null for blank`() {
        assertNull(SongTagParser.cleanUploader(null))
        assertNull(SongTagParser.cleanUploader("   "))
    }
}
