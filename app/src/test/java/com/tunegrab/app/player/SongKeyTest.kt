package com.tunegrab.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for [SongKey] normalization (pure). */
class SongKeyTest {

    // ------------------------------------------------------------ artistKey ---

    @Test fun `artist key lowercases and collapses whitespace`() {
        assertEquals("olivia rodrigo", SongKey.artistKey("  Olivia   Rodrigo "))
    }

    @Test fun `artist key strips topic suffix`() {
        assertEquals("olivia rodrigo", SongKey.artistKey("Olivia Rodrigo - Topic"))
    }

    @Test fun `artist key strips vevo suffix`() {
        assertEquals("oliviarodrigo", SongKey.artistKey("OliviaRodrigoVEVO"))
    }

    @Test fun `artist key strips official suffix`() {
        assertEquals("taylor swift", SongKey.artistKey("Taylor Swift Official"))
        assertEquals("taylor swift", SongKey.artistKey("Taylor Swift - Official"))
    }

    @Test fun `artist key keeps letters digits and spaces only`() {
        assertEquals("acdc", SongKey.artistKey("AC/DC!"))
    }

    // ------------------------------------------------------------------ of ---

    @Test fun `same song from different uploads collides`() {
        val a = SongKey.of("Deja Vu (Official Video)", "Olivia Rodrigo")
        val b = SongKey.of("deja vu - lyric video", "olivia rodrigo - topic")
        assertEquals(a, b)
    }

    @Test fun `different songs do not collide`() {
        val a = SongKey.of("Deja Vu", "Olivia Rodrigo")
        val b = SongKey.of("Good 4 U", "Olivia Rodrigo")
        assertTrue(a != b)
    }

    @Test fun `same title different artist does not collide`() {
        val a = SongKey.of("Hello", "Adele")
        val b = SongKey.of("Hello", "Lionel Richie")
        assertTrue(a != b)
    }

    @Test fun `blank artist means no key`() {
        assertNull(SongKey.of("Deja Vu", ""))
    }

    @Test fun `blank title means no key`() {
        assertNull(SongKey.of("", "Olivia Rodrigo"))
    }
}
