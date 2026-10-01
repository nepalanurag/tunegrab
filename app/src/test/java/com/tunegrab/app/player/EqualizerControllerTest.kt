package com.tunegrab.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM tests for the pure helpers in [EqualizerController]. */
class EqualizerControllerTest {

    @Test fun `formats sub-kilohertz bands as Hz`() {
        assertEquals("60 Hz", EqualizerController.formatFreq(60_000))
        assertEquals("230 Hz", EqualizerController.formatFreq(230_000))
    }

    @Test fun `formats kilohertz bands as kHz`() {
        assertEquals("1 kHz", EqualizerController.formatFreq(1_000_000))
        assertEquals("14 kHz", EqualizerController.formatFreq(14_000_000))
    }
}
