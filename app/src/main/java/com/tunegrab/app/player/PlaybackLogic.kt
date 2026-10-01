package com.tunegrab.app.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

/**
 * Pure playback math — zero Android imports, covered by JVM unit tests
 * ([PlaybackLogicTest]). The queue semantics here mirror what
 * [PlayerManager] delegates to ExoPlayer, so UI preview logic (e.g. "what
 * plays next") can be computed without touching the player.
 *
 * Index conventions:
 * - [current] is a queue index (0..size-1) in normal order. When [shuffle]
 *   is on, [shuffleOrder] gives the play order as a permutation of the
 *   queue indices, with the starting track first.
 * - [nextIndex] returns -1 when the queue ends with no repeat; callers treat
 *   -1 as "stop at the end".
 */
object PlaybackLogic {

    /** Maximum crossfade window in seconds (mirrors CrossfadeController). */
    const val MAX_CROSSFADE_SEC = 12

    /**
     * Index of the track that plays after [current], or -1 if the queue ends
     * with no repeat enabled.
     */
    fun nextIndex(
        current: Int,
        size: Int,
        shuffle: Boolean,
        repeatOne: Boolean,
        repeatAll: Boolean,
        shuffleOrder: List<Int>
    ): Int {
        if (size <= 0) return -1
        if (repeatOne) return current.coerceIn(0, size - 1)
        val cur = current.coerceIn(0, size - 1)
        if (shuffle) {
            val pos = shuffleOrder.indexOf(cur)
            // Defensive: current not in order (stale state) → stay put.
            if (pos < 0) return cur
            val nextPos = pos + 1
            return when {
                nextPos < shuffleOrder.size -> shuffleOrder[nextPos]
                repeatAll -> shuffleOrder.first()
                else -> -1
            }
        }
        return when {
            cur + 1 < size -> cur + 1
            repeatAll -> 0
            else -> -1
        }
    }

    /**
     * Index of the track that plays when the user presses "previous".
     * [restarted] encodes the 3-second restart rule: when the user was far
     * enough into the track that the UI restarted it instead of skipping
     * back, the previous track is simply [current].
     * Returns -1 when before the queue start with no repeat.
     */
    fun prevIndex(
        current: Int,
        size: Int,
        shuffle: Boolean,
        repeatOne: Boolean,
        repeatAll: Boolean,
        shuffleOrder: List<Int>,
        restarted: Boolean
    ): Int {
        if (size <= 0) return -1
        val cur = current.coerceIn(0, size - 1)
        if (restarted) return cur
        if (repeatOne) return cur
        if (shuffle) {
            val pos = shuffleOrder.indexOf(cur)
            if (pos < 0) return cur
            val prevPos = pos - 1
            return when {
                prevPos >= 0 -> shuffleOrder[prevPos]
                repeatAll -> shuffleOrder.last()
                else -> -1
            }
        }
        return when {
            cur - 1 >= 0 -> cur - 1
            repeatAll -> size - 1
            else -> -1
        }
    }

    /**
     * Builds a shuffle play order: [startIndex] first, the remaining indices
     * shuffled with [random] (seed it for deterministic tests).
     */
    fun buildShuffleOrder(size: Int, startIndex: Int, random: Random): List<Int> {
        if (size <= 0) return emptyList()
        val start = startIndex.coerceIn(0, size - 1)
        val rest = (0 until size).filter { it != start }.shuffled(random)
        return listOf(start) + rest
    }

    /** Clamps a user-supplied crossfade value to the valid 0..12 s range. */
    fun clampCrossfade(sec: Int): Int = sec.coerceIn(0, MAX_CROSSFADE_SEC)

    /**
     * Cosine fade-out curve: [progress] 0 → 1 maps to volume 1 → 0.
     * Cosine gives a smooth, equal-power-ish ramp that sounds natural
     * compared to a linear fade. Progress outside 0..1 is clamped.
     */
    fun fadeVolume(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return (0.5f * (1f + cos(PI * p))).toFloat()
    }
}
