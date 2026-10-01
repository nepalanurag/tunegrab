package com.tunegrab.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * JVM unit tests for the pure playback math in [PlaybackLogic].
 * No device or Media3 needed — these run on the plain JVM.
 */
class PlaybackLogicTest {

    // ---------------- nextIndex ----------------

    @Test fun `next advances normally`() {
        assertEquals(1, PlaybackLogic.nextIndex(0, 5, false, false, false, emptyList()))
        assertEquals(4, PlaybackLogic.nextIndex(3, 5, false, false, false, emptyList()))
    }

    @Test fun `next at queue end with no repeat returns -1`() {
        assertEquals(-1, PlaybackLogic.nextIndex(4, 5, false, false, false, emptyList()))
    }

    @Test fun `next at queue end with repeatAll wraps to 0`() {
        assertEquals(0, PlaybackLogic.nextIndex(4, 5, false, false, true, emptyList()))
    }

    @Test fun `next with repeatOne stays on current`() {
        assertEquals(2, PlaybackLogic.nextIndex(2, 5, false, true, false, emptyList()))
        assertEquals(2, PlaybackLogic.nextIndex(2, 5, false, true, true, emptyList()))
    }

    @Test fun `next follows shuffle order`() {
        val order = listOf(2, 0, 4, 1, 3)
        assertEquals(0, PlaybackLogic.nextIndex(2, 5, true, false, false, order))
        assertEquals(4, PlaybackLogic.nextIndex(0, 5, true, false, false, order))
    }

    @Test fun `next at shuffle end with no repeat returns -1`() {
        val order = listOf(2, 0, 4, 1, 3)
        assertEquals(-1, PlaybackLogic.nextIndex(3, 5, true, false, false, order))
    }

    @Test fun `next at shuffle end with repeatAll wraps to first in order`() {
        val order = listOf(2, 0, 4, 1, 3)
        assertEquals(2, PlaybackLogic.nextIndex(3, 5, true, false, true, order))
    }

    @Test fun `next on empty queue returns -1`() {
        assertEquals(-1, PlaybackLogic.nextIndex(0, 0, false, false, false, emptyList()))
    }

    // ---------------- prevIndex ----------------

    @Test fun `prev goes back normally`() {
        assertEquals(2, PlaybackLogic.prevIndex(3, 5, false, false, false, emptyList(), false))
    }

    @Test fun `prev at queue start with no repeat returns -1`() {
        assertEquals(-1, PlaybackLogic.prevIndex(0, 5, false, false, false, emptyList(), false))
    }

    @Test fun `prev at queue start with repeatAll wraps to last`() {
        assertEquals(4, PlaybackLogic.prevIndex(0, 5, false, false, true, emptyList(), false))
    }

    @Test fun `prev with repeatOne stays on current`() {
        assertEquals(2, PlaybackLogic.prevIndex(2, 5, false, true, false, emptyList(), false))
    }

    @Test fun `prev with restarted true stays on current (3-second rule)`() {
        assertEquals(3, PlaybackLogic.prevIndex(3, 5, false, false, false, emptyList(), true))
        assertEquals(0, PlaybackLogic.prevIndex(0, 5, false, false, false, emptyList(), true))
    }

    @Test fun `prev follows shuffle order`() {
        val order = listOf(2, 0, 4, 1, 3)
        assertEquals(2, PlaybackLogic.prevIndex(0, 5, true, false, false, order, false))
        assertEquals(0, PlaybackLogic.prevIndex(4, 5, true, false, false, order, false))
    }

    @Test fun `prev at shuffle start with repeatAll wraps to last in order`() {
        val order = listOf(2, 0, 4, 1, 3)
        assertEquals(3, PlaybackLogic.prevIndex(2, 5, true, false, true, order, false))
    }

    // ---------------- buildShuffleOrder ----------------

    @Test fun `shuffle order contains every index exactly once`() {
        val order = PlaybackLogic.buildShuffleOrder(10, 4, Random(1234))
        assertEquals(10, order.size)
        assertEquals((0 until 10).toList(), order.sorted())
    }

    @Test fun `shuffle order starts with startIndex`() {
        val order = PlaybackLogic.buildShuffleOrder(10, 7, Random(42))
        assertEquals(7, order.first())
    }

    @Test fun `shuffle order of size 1 is just the start index`() {
        assertEquals(listOf(0), PlaybackLogic.buildShuffleOrder(1, 0, Random(1)))
    }

    @Test fun `shuffle order of size 0 is empty`() {
        assertTrue(PlaybackLogic.buildShuffleOrder(0, 0, Random(1)).isEmpty())
    }

    // ---------------- clampCrossfade ----------------

    @Test fun `negative crossfade clamps to 0`() {
        assertEquals(0, PlaybackLogic.clampCrossfade(-5))
    }

    @Test fun `crossfade above 12 clamps to 12`() {
        assertEquals(12, PlaybackLogic.clampCrossfade(30))
        assertEquals(12, PlaybackLogic.clampCrossfade(13))
    }

    @Test fun `crossfade within range passes through`() {
        assertEquals(0, PlaybackLogic.clampCrossfade(0))
        assertEquals(6, PlaybackLogic.clampCrossfade(6))
        assertEquals(12, PlaybackLogic.clampCrossfade(12))
    }

    // ---------------- fadeVolume ----------------

    @Test fun `fade starts at full volume`() {
        assertEquals(1f, PlaybackLogic.fadeVolume(0f), 1e-6f)
    }

    @Test fun `fade ends at silence`() {
        assertEquals(0f, PlaybackLogic.fadeVolume(1f), 1e-6f)
    }

    @Test fun `fade midpoint is half volume`() {
        assertEquals(0.5f, PlaybackLogic.fadeVolume(0.5f), 1e-6f)
    }

    @Test fun `fade is monotonically decreasing`() {
        var prev = Float.MAX_VALUE
        var p = 0f
        while (p <= 1f) {
            val v = PlaybackLogic.fadeVolume(p)
            assertTrue("not decreasing at p=$p: $v > $prev", v <= prev + 1e-6f)
            prev = v
            p += 0.05f
        }
    }

    @Test fun `fade clamps out-of-range progress`() {
        assertEquals(1f, PlaybackLogic.fadeVolume(-0.5f), 1e-6f)
        assertEquals(0f, PlaybackLogic.fadeVolume(1.5f), 1e-6f)
    }
}
