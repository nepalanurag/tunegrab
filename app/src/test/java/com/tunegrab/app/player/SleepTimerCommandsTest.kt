package com.tunegrab.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM tests for [SleepTimerCommands.computeSleepUntilMs] (pure). */
class SleepTimerCommandsTest {

    @Test fun `positive minutes produce a deadline that far in the future`() {
        assertEquals(1_000L + 5 * 60_000L, SleepTimerCommands.computeSleepUntilMs(5, 1_000L))
    }

    @Test fun `one minute is 60_000 ms`() {
        assertEquals(60_000L, SleepTimerCommands.computeSleepUntilMs(1, 0L))
    }

    @Test fun `zero minutes means cancel`() {
        assertEquals(0L, SleepTimerCommands.computeSleepUntilMs(0, 999_000L))
    }

    @Test fun `negative minutes mean cancel`() {
        assertEquals(0L, SleepTimerCommands.computeSleepUntilMs(-3, 999_000L))
    }
}
