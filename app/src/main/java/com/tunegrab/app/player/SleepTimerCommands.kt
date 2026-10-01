package com.tunegrab.app.player

/**
 * Shared contract for the service-side sleep timer, used by both
 * [PlaybackService] (which owns and executes the timer) and
 * [PlayerManager] (which arms it and mirrors the deadline for the UI).
 * Keeping the action strings and extras in one place prevents drift
 * between the two sides of the custom command.
 *
 * Why the service owns it: the activity disconnects its MediaController
 * in onStop, so a UI-side timer job can no longer pause anything once
 * the app leaves the foreground (or the screen turns off). The service
 * pauses its own ExoPlayer directly, so the deadline fires regardless.
 */
object SleepTimerCommands {
    /** Arms the timer; [EXTRA_UNTIL_MS] <= 0 cancels it. */
    const val SET_ACTION = "com.tunegrab.app.SET_SLEEP_TIMER"

    /** Returns [EXTRA_UNTIL_MS] ([NO_TIMER] when no timer is armed). */
    const val GET_ACTION = "com.tunegrab.app.GET_SLEEP_TIMER"

    /** Long extra: wall-clock deadline in ms. */
    const val EXTRA_UNTIL_MS = "untilMs"

    /** GET result value meaning "no timer is armed". */
    const val NO_TIMER = -1L

    /**
     * Wall-clock deadline for [minutes] from [nowMs]. Non-positive
     * [minutes] yields 0, which the service treats as "cancel". Pure.
     */
    fun computeSleepUntilMs(minutes: Int, nowMs: Long): Long =
        if (minutes <= 0) 0L else nowMs + minutes * 60_000L
}
