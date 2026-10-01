package com.tunegrab.app.ui.player

import com.tunegrab.app.ui.library.SongUi
import kotlinx.coroutines.flow.StateFlow

/**
 * UI-layer seam between the Now Playing UI and the real Media3 player.
 *
 * The coordinator wires this up: `com.tunegrab.app.player` (the parallel
 * playback agent) adapts its PlayerManager singleton / PlayerTrack model to
 * this interface. The player UI below only ever talks to
 * [PlayerUiController]; it never touches Media3 directly.
 *
 * Threading: all StateFlows may be updated from any thread; methods are
 * safe to call from the main thread and must return immediately (the real
 * player applies them asynchronously).
 */
interface PlayerUiController {
    /** Currently playing track, or null when nothing has been queued. */
    val current: StateFlow<SongUi?>

    /** True while audio is actually playing (not just prepared). */
    val isPlaying: StateFlow<Boolean>

    /** Current playback position in milliseconds. */
    val positionMs: StateFlow<Long>

    /** Duration of [current] in milliseconds; 0 when unknown. */
    val durationMs: StateFlow<Long>

    /** Upcoming + current queue; index of [current] is tracked by the player. */
    val queue: StateFlow<List<SongUi>>

    /** Shuffle mode on/off. */
    val shuffle: StateFlow<Boolean>

    /**
     * Repeat mode: 0 = off, 1 = repeat all, 2 = repeat one.
     * (Int, not an enum, so the Media3 RepeatMode constants map 1:1.)
     */
    val repeatMode: StateFlow<Int>

    /** True while the now-starting stream's audio URL is being resolved. */
    val remoteLoading: StateFlow<Boolean>

    /**
     * True while Radio is on: similar tracks keep being appended after the
     * queue's end so playback never stops on its own.
     */
    val isRadio: StateFlow<Boolean>

    /**
     * Sleep timer deadline as wall-clock ms, or null when the timer is off.
     * Lives in the player, so it survives leaving the Now Playing screen.
     */
    val sleepUntilMs: StateFlow<Long?>

    /**
     * Sets the sleep timer: pauses playback [minutes] from now.
     * A [minutes] value of 0 turns the timer off.
     */
    fun setSleepTimer(minutes: Int)

    /**
     * Starts Radio from [song]: the song plays now and an endless queue of
     * similar YouTube Music tracks follows it. Replaces the current queue;
     * this is the explicit tap, so it never happens on its own.
     */
    fun startRadio(song: SongUi)

    /** Turns Radio off. The queue keeps playing as it stands. */
    fun stopRadio()

    fun togglePlayPause()
    fun seekTo(ms: Long)
    fun next()
    fun previous()
    fun toggleShuffle()
    fun cycleRepeat()
    fun playQueue(songs: List<SongUi>, index: Int)
    /** Jumps to the queue entry at [index] (UI queue order). */
    fun playQueueIndex(index: Int)
    /** Appends songs to the end of the queue (starts playback if empty). */
    fun enqueue(songs: List<SongUi>)
    /** Inserts songs right after the playing entry (starts playback if empty). */
    fun playNext(songs: List<SongUi>)
    fun removeFromQueue(index: Int)
    fun moveInQueue(from: Int, to: Int)
    /**
     * Patches the artist of the queued entry with [queueKey] (used when a
     * stream's real channel is resolved in the background after playback
     * starts). Updates UI state only; playback is unaffected.
     */
    fun updateTrackArtist(queueKey: String, artist: String)
}

/** Now Playing visual themes. BLUR_ARTWORK and MINIMAL are Pro-only. */
enum class NpTheme {
    CLEAN,
    BLUR_ARTWORK,
    MINIMAL,
}

/**
 * Pro gate for Now Playing themes: free users always get [NpTheme.CLEAN],
 * Pro users get their preferred theme. Call sites should pass
 * `AppSettings.isPro` (collected as state) as [isPro].
 */
fun themeFor(isPro: Boolean, preferred: NpTheme): NpTheme =
    if (isPro) preferred else NpTheme.CLEAN

/** Pure helper: 187_000 -> "3:07", 0 -> "0:00", negatives clamp to "0:00". */
fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
