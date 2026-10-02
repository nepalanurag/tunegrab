package com.tunegrab.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.tunegrab.app.AppSettings
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.tunegrab.app.DirectBridge
import com.tunegrab.app.library.ListeningStatsStore
import com.tunegrab.app.widget.PlayerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pure track descriptor for the queue. Converted to a Media3 [MediaItem]
 * via [toMediaItem].
 *
 * Local tracks carry their MediaStore content Uri. Streaming tracks
 * ([isRemote]) start with an empty [contentUri] — the fresh audio-stream URL
 * is resolved when playback reaches them, because YouTube stream URLs
 * expire. A remote track that has since been downloaded carries the file in
 * [localFileUri] and plays offline; if that file is gone the player falls
 * back to resolving the stream again.
 */
data class PlayerTrack(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val contentUri: String,
    val artworkUri: String?,
    val isRemote: Boolean = false,
    val videoId: String? = null,
    val localFileUri: String? = null,
) {
    /** True when there is something playable right now (no resolve needed). */
    val hasPlayableUri: Boolean
        get() = !isRemote || !localFileUri.isNullOrBlank() || contentUri.isNotBlank()

    fun toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .apply { artworkUri?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        val builder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
        // Remote tracks may not have a stream URL yet; the item is then
        // created without a URI and never handed to the player unresolved.
        val playable = localFileUri?.takeIf { it.isNotBlank() }
            ?: contentUri.takeIf { it.isNotBlank() }
        if (playable != null) builder.setUri(Uri.parse(playable))
        return builder.build()
    }

    companion object {
        /**
         * Mapping from the library layer's Song primitives. [songId] becomes
         * the stable [mediaId] so queue entries survive re-queries.
         */
        fun fromSong(
            songId: Long,
            title: String,
            artist: String,
            album: String,
            contentUri: Uri,
        ): PlayerTrack = PlayerTrack(
            mediaId = songId.toString(),
            title = title,
            artist = artist,
            album = album,
            contentUri = contentUri.toString(),
            // See Wiring.Song.toUi: the song content Uri (not the legacy
            // albumart provider) is what yields embedded art on API 29+.
            artworkUri = contentUri.toString()
        )

        /** A YouTube Music stream; the audio URL is resolved at play time. */
        fun remote(
            videoId: String,
            title: String,
            artist: String,
            thumbnailUrl: String?,
            durationMs: Long,
        ): PlayerTrack = PlayerTrack(
            mediaId = "yt:$videoId",
            title = title,
            artist = artist,
            album = "",
            contentUri = "",
            artworkUri = thumbnailUrl,
            isRemote = true,
            videoId = videoId,
        )

        /** A remote entry whose file has been downloaded: plays the file. */
        fun remoteDownloaded(
            videoId: String,
            title: String,
            artist: String,
            localContentUri: String,
            thumbnailUrl: String?,
        ): PlayerTrack = PlayerTrack(
            mediaId = "yt:$videoId",
            title = title,
            artist = artist,
            album = "",
            contentUri = "",
            artworkUri = thumbnailUrl,
            isRemote = true,
            videoId = videoId,
            localFileUri = localContentUri,
        )
    }
}

/**
 * App-scoped playback facade. Owns the [MediaController] connection to
 * [PlaybackService] and exposes playback state as [StateFlow]s for the UI.
 *
 * Every controller call is guarded by [isConnected]: calls made before the
 * async connection completes are ignored rather than crashing. The 500 ms
 * position poller only runs while the player is actually playing, so an idle
 * app burns no wakeups.
 *
 * Queues mix local and streaming tracks ([queueTracks], same order as the
 * UI queue). Streaming URLs are resolved incrementally: the requested track
 * resolves first so playback starts fast, then [fillJob] resolves the rest
 * in playback order in the background. Queue edits map queue indices to
 * controller positions by media id, so they stay correct while the fill
 * is still running.
 */
object PlayerManager {

    private const val POSITION_POLL_MS = 500L

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    /** True once [MediaController] is connected; all calls guard on this. */
    @Volatile
    var isConnected: Boolean = false
        private set

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _currentMediaId = MutableStateFlow<String?>(null)
    val currentMediaId: StateFlow<String?> = _currentMediaId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queueTitles = MutableStateFlow<List<String>>(emptyList())
    val queueTitles: StateFlow<List<String>> = _queueTitles.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    /** True while the now-starting stream's audio URL is being resolved. */
    private val _remoteLoading = MutableStateFlow(false)
    val remoteLoading: StateFlow<Boolean> = _remoteLoading.asStateFlow()

    /** True when playback reached the natural end of the queue. */
    private val _playbackEnded = MutableStateFlow(false)
    val playbackEnded: StateFlow<Boolean> = _playbackEnded.asStateFlow()

    /** One of [Player.REPEAT_MODE_OFF], [Player.REPEAT_MODE_ALL], [Player.REPEAT_MODE_ONE]. */
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    /**
     * Sleep timer deadline as wall-clock ms, or null when the timer is off.
     * This is only the UI mirror: the timer itself is owned and executed
     * by [PlaybackService] (see [setSleepTimer]), which survives the
     * activity going to the background. Kept here (not in a composable)
     * so the countdown survives navigation away from Now Playing.
     */
    private val _sleepUntilMs = MutableStateFlow<Long?>(null)
    val sleepUntilMs: StateFlow<Long?> = _sleepUntilMs.asStateFlow()

    /**
     * Deadline armed while no controller was connected (e.g. set during
     * the connect handshake). Flushed to the service on (re)connect; 0
     * means "cancel".
     */
    private var pendingSleepUntilMs: Long? = null

    /** Full queue in UI order (local + remote), with resolved URLs filled in. */
    private var queueTracks: List<PlayerTrack> = emptyList()
    private var fillJob: Job? = null
    private var insertJob: Job? = null
    /** Bumps on every explicit play so stale resolve jobs abort. */
    private var playGeneration = 0
    /** Media id already retried once after a playback error. */
    private var lastErrorRetryMediaId: String? = null

    /**
     * Set by Radio when the queue naturally ended: as soon as the
     * background fill appends the next track, playback continues into it
     * instead of sitting at the end. Cleared on any explicit play.
     */
    private var continueWhenItemsArrive = false

    /**
     * Radio calls this after appending fresh tracks: if playback had
     * reached the natural end of the queue, it resumes with the first
     * newly arrived track. No-op otherwise (a paused player stays
     * paused).
     */
    fun radioContinueIfEnded() {
        if (_playbackEnded.value) continueWhenItemsArrive = true
    }

    /** Snapshot of what's on the widget / notification. */
    data class NowPlayingInfo(
        val title: String,
        val artist: String,
        val artworkUri: Uri?,
    )

    /** Current track's display info, or null when the queue is empty. */
    fun nowPlayingInfo(): NowPlayingInfo? {
        val md = controller?.currentMediaItem?.mediaMetadata ?: return null
        val title = md.title?.toString().orEmpty()
        if (title.isEmpty()) return null
        return NowPlayingInfo(
            title = title,
            artist = md.artist?.toString().orEmpty(),
            artworkUri = md.artworkUri,
        )
    }

    private var appContext: Context? = null

    /** Listening stats; created once the app context is available. */
    private var statsStore: ListeningStatsStore? = null

    /** Media id of the track currently being timed for a play count. */
    private var statsTimingMediaId: String? = null
    private var statsTimingStartMs: Long = 0L
    private var statsCountedMediaId: String? = null

    /**
     * Starts (or restarts) play-count timing for [mediaId]: after
     * [ListeningStatsStore.PLAY_COUNT_THRESHOLD_SEC] of continuous
     * playback the play is recorded. Also stamps recency right away.
     */
    private fun noteTrackStarted(track: PlayerTrack?) {
        val ctx = appContext ?: return
        val store = statsStore ?: ListeningStatsStore(ctx).also { statsStore = it }
        statsTimingMediaId = track?.mediaId
        statsTimingStartMs = SystemClock.elapsedRealtime()
        if (track != null) {
            scope.launch {
                store.recordRecent(
                    track.mediaId, track.title, track.artist, track.artworkUri
                )
            }
        }
    }

    /** Counts the play for [mediaId] once per track start. */
    private fun countPlayOnce(track: PlayerTrack?) {
        val mediaId = track?.mediaId ?: return
        if (statsCountedMediaId == mediaId) return
        statsCountedMediaId = mediaId
        val store = statsStore ?: return
        scope.launch {
            store.recordPlay(mediaId, track.title, track.artist, track.artworkUri)
        }
    }

    private fun currentPlayerTrack(): PlayerTrack? {
        val id = _currentMediaId.value ?: return null
        return queueTracks.firstOrNull { it.mediaId == id }
    }

    private val internalListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) startPositionPolling() else handler.removeCallbacks(positionPoller)
            controller?.let(::syncNowPlaying)
            appContext?.let { PlayerWidget.refresh(it) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lastErrorRetryMediaId = null
            statsCountedMediaId = null
            controller?.let(::syncNowPlaying)
            noteTrackStarted(currentPlayerTrack())
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            controller?.let(::syncNowPlaying)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleEnabled.value = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _playbackEnded.value = playbackState == Player.STATE_ENDED
            if (playbackState == Player.STATE_ENDED) {
                countPlayOnce(currentPlayerTrack())
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            retryRemoteWithFreshUrl()
        }
    }

    private val positionPoller = object : Runnable {
        override fun run() {
            val c = ctl() ?: return
            _positionMs.value = c.currentPosition.coerceAtLeast(0L)
            // Count a play after 30s of continuous playback on one track.
            val timingId = statsTimingMediaId
            if (timingId != null && timingId == _currentMediaId.value &&
                SystemClock.elapsedRealtime() - statsTimingStartMs >=
                ListeningStatsStore.PLAY_COUNT_THRESHOLD_SEC * 1000L
            ) {
                statsTimingMediaId = null
                countPlayOnce(currentPlayerTrack())
            }
            if (c.isPlaying) handler.postDelayed(this, POSITION_POLL_MS)
        }
    }

    // ---------------- connection ----------------

    private data class PendingAction(val atMs: Long, val action: () -> Unit)
    private val pendingActions = mutableListOf<PendingAction>()
    /** Stale taps (older than this) are dropped instead of firing late. */
    private const val PENDING_ACTION_TTL_MS = 10_000L

    /**
     * Runs [action] as soon as the media controller is connected. Widget taps
     * often arrive before the async connect finishes; without this the first
     * tap was silently dropped and it looked like a double-tap was needed.
     */
    fun runWhenConnected(action: () -> Unit) {
        if (isConnected && controller != null) {
            runCatching { action() }
        } else {
            synchronized(pendingActions) {
                pendingActions.add(PendingAction(SystemClock.uptimeMillis(), action))
            }
        }
    }

    private fun drainPendingActions() {
        val now = SystemClock.uptimeMillis()
        val actions = synchronized(pendingActions) {
            pendingActions.toList().also { pendingActions.clear() }
        }.filter { now - it.atMs <= PENDING_ACTION_TTL_MS }
        // Already on the main executor here; run directly.
        actions.forEach { runCatching { it.action() } }
    }

    /**
     * Connects to [PlaybackService]. Safe to call repeatedly; extra calls
     * are no-ops once connected or while a connection is in flight.
     */
    @Synchronized
    fun connect(context: Context) {
        if (isConnected || controllerFuture != null) return
        appContext = context.applicationContext
        val token = SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val c = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = c
                isConnected = true
                c.addListener(internalListener)
                syncNowPlaying(c)
                _shuffleEnabled.value = c.shuffleModeEnabled
                _repeatMode.value = c.repeatMode
                if (c.isPlaying) startPositionPolling()
                // Fire any widget taps that arrived while connecting.
                drainPendingActions()
                // The service owns the sleep-timer deadline and outlives
                // activity recreation: flush anything armed while we were
                // disconnected, otherwise re-sync the UI mirror.
                val pending = pendingSleepUntilMs
                pendingSleepUntilMs = null
                if (pending != null) {
                    c.sendCustomCommand(
                        SessionCommand(SleepTimerCommands.SET_ACTION, Bundle.EMPTY),
                        sleepTimerArgs(pending)
                    )
                } else {
                    val timerFuture = c.sendCustomCommand(
                        SessionCommand(SleepTimerCommands.GET_ACTION, Bundle.EMPTY),
                        Bundle.EMPTY
                    )
                    timerFuture.addListener(
                        {
                            val until = runCatching { timerFuture.get() }
                                .getOrNull()
                                ?.extras
                                ?.getLong(
                                    SleepTimerCommands.EXTRA_UNTIL_MS,
                                    SleepTimerCommands.NO_TIMER
                                )
                                ?: SleepTimerCommands.NO_TIMER
                            _sleepUntilMs.value =
                                if (until == SleepTimerCommands.NO_TIMER) null else until
                        },
                        ContextCompat.getMainExecutor(context.applicationContext)
                    )
                }
            },
            ContextCompat.getMainExecutor(context.applicationContext)
        )
    }

    /** Releases the controller and stops all polling. */
    @Synchronized
    fun disconnect() {
        handler.removeCallbacks(positionPoller)
        fillJob?.cancel()
        fillJob = null
        insertJob?.cancel()
        insertJob = null
        controller?.removeListener(internalListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        isConnected = false
        synchronized(pendingActions) { pendingActions.clear() }
        _isPlaying.value = false
        _currentMediaId.value = null
        _positionMs.value = 0L
        _remoteLoading.value = false
    }

    // ---------------- transport ----------------

    /**
     * Replaces the queue with [items] and starts at [startIndex].
     *
     * Streaming tracks resolve their audio URL first: the requested track
     * resolves immediately so playback starts fast, the rest fill in
     * behind it in playback order.
     */
    fun playQueue(items: List<PlayerTrack>, startIndex: Int) {
        if (ctl() == null || items.isEmpty()) return
        startAt(items, startIndex.coerceIn(0, items.lastIndex))
    }

    /**
     * Like [playQueue], but begins the starting item at [startPositionMs]
     * instead of the beginning. Used when rebuilding the queue around the
     * track that is already playing (e.g. starting Radio on the current
     * song) so it keeps playing instead of restarting.
     */
    fun playQueueAt(items: List<PlayerTrack>, startIndex: Int, startPositionMs: Long) {
        if (ctl() == null || items.isEmpty()) return
        startAt(items, startIndex.coerceIn(0, items.lastIndex), startPositionMs.coerceAtLeast(0L))
    }

    /**
     * Jumps to the queue entry at [queueIndex] (UI queue order). Entries
     * whose stream URL isn't resolved yet resolve on demand.
     */
    fun playQueueIndex(queueIndex: Int) {
        if (ctl() == null) return
        val tracks = queueTracks
        if (queueIndex !in tracks.indices) return
        startAt(tracks, queueIndex)
    }

    /**
     * Appends [tracks] to the end of the queue. When nothing is queued yet,
     * playback starts with them instead.
     */
    fun enqueue(tracks: List<PlayerTrack>) {
        if (tracks.isEmpty()) return
        val existing = queueTracks
        if (existing.isEmpty() || ctl()?.mediaItemCount == 0) {
            playQueue(tracks, 0)
            return
        }
        queueTracks = existing + tracks
        // The background fill resolves and appends the new entries in order.
        insertJob?.cancel()
        val cur = queueTracks.indexOfFirst { it.mediaId == _currentMediaId.value }
        restartFill(cur.coerceAtLeast(0))
    }

    /**
     * Inserts [tracks] right after the currently playing entry. When nothing
     * is queued yet, playback starts with them instead.
     */
    fun playNext(tracks: List<PlayerTrack>) {
        if (tracks.isEmpty()) return
        val existing = queueTracks
        if (existing.isEmpty() || ctl()?.mediaItemCount == 0) {
            playQueue(tracks, 0)
            return
        }
        val cur = existing.indexOfFirst { it.mediaId == _currentMediaId.value }
            .coerceAtLeast(0)
        queueTracks = existing.toMutableList().also { it.addAll(cur + 1, tracks) }
        val gen = playGeneration
        insertJob?.cancel()
        fillJob?.cancel()
        insertJob = scope.launch {
            // Resolve in order, inserting each right after the previous one
            // so "play next" order holds on the controller too.
            val basePos = controllerPositionFor(cur)
            if (basePos == null) {
                // Current entry isn't on the controller yet; the normal
                // fill covers the inserted entries in order.
                restartFill(cur)
                return@launch
            }
            var insertPos: Int = basePos
            val ids = tracks.map { it.mediaId }
            for ((offset, mediaId) in ids.withIndex()) {
                val q = cur + 1 + offset
                ensureActive()
                if (gen != playGeneration) return@launch
                val track = queueTracks.getOrNull(q)
                if (track == null || track.mediaId != mediaId) continue
                val existingPos = controllerPositionFor(q)
                if (existingPos != null) {
                    insertPos = existingPos
                    continue
                }
                val resolved = withContext(Dispatchers.IO) { resolveTrack(track) }
                    ?: continue
                ensureActive()
                if (gen != playGeneration) return@launch
                val still = queueTracks.getOrNull(q)
                if (still == null || still.mediaId != mediaId) continue
                queueTracks = queueTracks.toMutableList().also { it[q] = resolved }
                ctl()?.addMediaItem(insertPos + 1, resolved.toMediaItem())
                insertPos += 1
            }
            // Fill the tail behind the inserted block.
            restartFill(cur + tracks.size)
        }
    }

    private fun startAt(items: List<PlayerTrack>, index: Int, startPositionMs: Long = 0L) {
        val gen = ++playGeneration
        fillJob?.cancel()
        insertJob?.cancel()
        continueWhenItemsArrive = false
        queueTracks = items
        // Optimistic current-track identity: the UI (queue highlight,
        // mini player, Now Playing) resolves `current` from this id, and
        // the stream URL can take seconds to resolve — without this the
        // UI shows a stale (or null) current track until the first
        // media-item event arrives. The listener's syncNowPlaying
        // confirms it once playback actually starts.
        _currentMediaId.value = items[index].mediaId
        _remoteLoading.value = items[index].isRemote && !items[index].hasPlayableUri
        scope.launch {
            val resolved = withContext(Dispatchers.IO) { resolveTrack(items[index]) }
            if (gen != playGeneration) return@launch
            val c = ctl()
            if (resolved == null || c == null) {
                _remoteLoading.value = false
                // Try the following entries in order before giving up.
                val next = items.indices
                    .filter { it != index }
                    .sortedBy { if (it > index) it else it + items.size }
                    .firstOrNull()
                if (next != null) startAt(items, next)
                return@launch
            }
            queueTracks = queueTracks.toMutableList().also { it[index] = resolved }
            c.setMediaItems(listOf(resolved.toMediaItem()), /* startIndex= */ 0, startPositionMs)
            c.prepare()
            c.play()
            _remoteLoading.value = false
            restartFill(index)
        }
    }

    /** Resolves the rest of the queue in playback order, in the background. */
    private fun restartFill(fromQueueIndex: Int) {
        fillJob?.cancel()
        val gen = playGeneration
        fillJob = scope.launch {
            val tracks = queueTracks
            val order = ((fromQueueIndex + 1)..tracks.lastIndex) +
                (0 until fromQueueIndex)
            // Snapshot (index, mediaId) so queue edits mid-fill can't
            // mis-resolve: a shifted entry is skipped, not mislabeled.
            val snapshot = order.map { it to tracks[it].mediaId }
            for ((q, mediaId) in snapshot) {
                ensureActive()
                if (gen != playGeneration) return@launch
                val current = queueTracks.getOrNull(q)
                if (current == null || current.mediaId != mediaId) continue
                if (controllerPositionFor(q) != null) continue
                val resolved = withContext(Dispatchers.IO) { resolveTrack(current) }
                    ?: continue
                ensureActive()
                if (gen != playGeneration) return@launch
                val still = queueTracks.getOrNull(q)
                if (still == null || still.mediaId != mediaId) continue
                queueTracks = queueTracks.toMutableList().also { it[q] = resolved }
                ctl()?.addMediaItem(resolved.toMediaItem())
                // Radio asked to continue past the natural end of the
                // queue: step into the first newly arrived track.
                if (continueWhenItemsArrive) {
                    continueWhenItemsArrive = false
                    val c = ctl()
                    if (c != null &&
                        c.playbackState == Player.STATE_ENDED &&
                        c.hasNextMediaItem()
                    ) {
                        c.seekToNextMediaItem()
                        c.play()
                    }
                }
            }
        }
    }

    /** Controller position of a queue entry by media id, or null when not added yet. */
    private fun controllerPositionForMediaId(mediaId: String): Int? {
        val c = ctl() ?: return null
        for (i in 0 until c.mediaItemCount) {
            if (c.getMediaItemAt(i).mediaId == mediaId) return i
        }
        return null
    }

    /** Controller position of the queue entry, or null when not added yet. */
    private fun controllerPositionFor(queueIndex: Int): Int? {
        val mediaId = queueTracks.getOrNull(queueIndex)?.mediaId ?: return null
        return controllerPositionForMediaId(mediaId)
    }

    /**
     * Resolves a streaming track's audio URL. Local tracks and already
     * resolved tracks pass through untouched. Never throws.
     */
    private suspend fun resolveTrack(track: PlayerTrack): PlayerTrack? {
        if (!track.isRemote) return track
        if (!track.localFileUri.isNullOrBlank()) return track
        if (track.contentUri.isNotBlank()) return track
        val videoId = track.videoId ?: return null
        return try {
            // Remote stream resolution lives behind the flavor bridge:
            // the Play build has no downloader and always gets null here.
            val url = DirectBridge.resolveStreamUrl(videoId)
            if (url.isNullOrBlank()) null else track.copy(contentUri = url)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * One automatic retry per track on playback failure: re-resolves the
     * stream URL (they expire) and swaps it under the playing item. A remote
     * entry whose downloaded file vanished falls back to streaming.
     */
    private fun retryRemoteWithFreshUrl() {
        val c = ctl() ?: return
        val mediaId = c.currentMediaItem?.mediaId ?: return
        if (mediaId == lastErrorRetryMediaId) return
        val q = queueTracks.indexOfFirst { it.mediaId == mediaId }
        if (q == -1) return
        val track = queueTracks[q]
        if (!track.isRemote) return
        lastErrorRetryMediaId = mediaId
        val gen = playGeneration
        scope.launch {
            val retryBase = if (!track.localFileUri.isNullOrBlank()) {
                track.copy(localFileUri = null, contentUri = "")
            } else {
                track.copy(contentUri = "")
            }
            val resolved = withContext(Dispatchers.IO) { resolveTrack(retryBase) }
                ?: return@launch
            if (gen != playGeneration) return@launch
            val pos = controllerPositionFor(q) ?: return@launch
            queueTracks = queueTracks.toMutableList().also { it[q] = resolved }
            ctl()?.let {
                it.replaceMediaItem(pos, resolved.toMediaItem())
                it.seekTo(pos, 0L)
                it.play()
            }
        }
    }

    fun togglePlayPause() {
        val c = ctl() ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    /** Pauses playback; no-op when already paused or not connected. */
    fun pause() {
        val c = ctl() ?: return
        if (c.isPlaying) c.pause()
    }

    /**
     * Sleep timer: arms the deadline on [PlaybackService], which pauses
     * its own player when it passes. Any previous timer is replaced; a
     * [minutes] value of 0 (or less) turns the timer off.
     *
     * The service owns execution because this controller is disconnected
     * in MainActivity.onStop — a UI-side timer job could never pause
     * anything once the app leaves the foreground. [_sleepUntilMs] is
     * only the countdown mirror for the UI.
     */
    fun setSleepTimer(minutes: Int) {
        val until = SleepTimerCommands.computeSleepUntilMs(
            minutes, System.currentTimeMillis()
        )
        _sleepUntilMs.value = if (until <= 0L) null else until
        val c = ctl()
        if (c != null) {
            pendingSleepUntilMs = null
            c.sendCustomCommand(
                SessionCommand(SleepTimerCommands.SET_ACTION, Bundle.EMPTY),
                sleepTimerArgs(until)
            )
        } else {
            // No controller yet (connect handshake in flight): remember
            // and arm/cancel on (re)connect instead of dropping it.
            pendingSleepUntilMs = until
        }
    }

    private fun sleepTimerArgs(untilMs: Long): Bundle =
        Bundle().apply {
            putLong(SleepTimerCommands.EXTRA_UNTIL_MS, untilMs)
        }

    fun seekTo(ms: Long) {
        ctl()?.seekTo(ms.coerceAtLeast(0L))
    }

    fun next() {
        val c = ctl() ?: return
        if (c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
            // A track change starts playback: staying paused after an
            // explicit next is the weird quirk, not a feature.
            c.play()
            return
        }
        // The background fill may not have added the next entry yet: jump
        // to it on demand instead of restarting the last track.
        val tracks = queueTracks
        val cur = tracks.indexOfFirst { it.mediaId == _currentMediaId.value }
        val nextIndex = when {
            cur != -1 && cur + 1 <= tracks.lastIndex -> cur + 1
            cur == -1 && tracks.isNotEmpty() -> 0
            else -> -1
        }
        if (nextIndex != -1) {
            startAt(tracks, nextIndex)
        } else if (c.mediaItemCount > 0) {
            c.seekTo(c.mediaItemCount - 1, 0L)
        }
    }

    fun previous() {
        val c = ctl() ?: return
        // 3-second restart rule: late into a track, restart it instead of going back.
        if (c.currentPosition > 3000L) {
            c.seekTo(0L)
            // Same quirk as next(): an explicit transport press starts
            // playback rather than leaving the new position paused.
            c.play()
            return
        }
        if (c.hasPreviousMediaItem()) {
            c.seekToPreviousMediaItem()
            c.play()
            return
        }
        // Jumped mid-queue (playQueueIndex): earlier entries may not be on
        // the controller yet — go back on demand.
        val tracks = queueTracks
        val cur = tracks.indexOfFirst { it.mediaId == _currentMediaId.value }
        if (cur > 0) startAt(tracks, cur - 1) else {
            c.seekTo(0L)
            c.play()
        }
    }

    fun toggleShuffle() {
        val c = ctl() ?: return
        val v = !c.shuffleModeEnabled
        c.shuffleModeEnabled = v
        // Remember across restarts.
        AppSettings.setShuffleEnabled(v)
    }

    /** Cycles OFF → ALL → ONE → OFF. */
    fun cycleRepeat() {
        val c = ctl() ?: return
        val v = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = v
        // Remember across restarts.
        AppSettings.setRepeatMode(v)
    }

    // ---------------- queue editing ----------------

    /** Inserts [track] directly after the currently playing item. */
    fun addNext(track: PlayerTrack) {
        val c = ctl() ?: return
        scope.launch {
            val resolved = withContext(Dispatchers.IO) { resolveTrack(track) }
                ?: return@launch
            val currentId = _currentMediaId.value
            val queueAt = (queueTracks.indexOfFirst { it.mediaId == currentId } + 1)
                .coerceIn(0, queueTracks.size)
            queueTracks = queueTracks.toMutableList().also { it.add(queueAt, resolved) }
            val c2 = ctl() ?: return@launch
            val at = (c2.currentMediaItemIndex + 1).coerceIn(0, c2.mediaItemCount)
            c2.addMediaItem(at, resolved.toMediaItem())
        }
    }

    /**
     * Removes the queue entry at [queueIndex] (UI queue order). Removing the
     * playing entry starts the next one.
     */
    fun removeQueueIndex(queueIndex: Int) {
        val tracks = queueTracks
        if (queueIndex !in tracks.indices) return
        insertJob?.cancel()
        val removedMediaId = tracks[queueIndex].mediaId
        val wasCurrent = removedMediaId == _currentMediaId.value
        queueTracks = tracks.toMutableList().also { it.removeAt(queueIndex) }
        // Locate by media id: the queue list is already mutated above, so
        // index-based lookup would remove the wrong controller item.
        controllerPositionForMediaId(removedMediaId)
            ?.let { ctl()?.removeMediaItem(it) }
        if (wasCurrent) {
            if (queueTracks.isEmpty()) {
                ctl()?.stop()
                ctl()?.clearMediaItems()
            } else {
                startAt(queueTracks, queueIndex.coerceIn(0, queueTracks.lastIndex))
            }
        } else {
            val c = ctl()
            if (c != null) {
                val cur = queueTracks.indexOfFirst { it.mediaId == _currentMediaId.value }
                restartFill(cur.coerceAtLeast(0))
            }
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        val tracks = queueTracks
        if (from !in tracks.indices || to !in tracks.indices || from == to) return
        insertJob?.cancel()
        val movedId = tracks[from].mediaId
        queueTracks = tracks.toMutableList().also { it.add(to, it.removeAt(from)) }
        val c = ctl() ?: return
        val fromPos = (0 until c.mediaItemCount)
            .firstOrNull { c.getMediaItemAt(it).mediaId == movedId }
        if (fromPos != null) {
            val toPos = to.coerceIn(0, c.mediaItemCount - 1)
            if (fromPos != toPos) c.moveMediaItem(fromPos, toPos)
        }
        val cur = queueTracks.indexOfFirst { it.mediaId == _currentMediaId.value }
            .coerceAtLeast(0)
        restartFill(cur)
    }

    // ---------------- internals ----------------

    private fun ctl(): MediaController? = if (isConnected) controller else null

    /**
     * The controller's full timeline, or empty when not connected. Used
     * by the UI adapter to rebuild its queue after an app restart: the
     * service keeps playing, but the fresh app process has no queue
     * state, so the mini player would otherwise stay hidden.
     */
    fun controllerMediaItems(): List<MediaItem> {
        val c = ctl() ?: return emptyList()
        return (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
    }

    private fun startPositionPolling() {
        handler.removeCallbacks(positionPoller)
        handler.post(positionPoller)
    }

    private fun syncNowPlaying(c: Player) {
        _currentMediaId.value = c.currentMediaItem?.mediaId
        _isPlaying.value = c.isPlaying
        _positionMs.value = c.currentPosition.coerceAtLeast(0L)
        _durationMs.value = c.duration.coerceAtLeast(0L)
        _queueTitles.value = List(c.mediaItemCount) { i ->
            c.getMediaItemAt(i).mediaMetadata.title?.toString().orEmpty()
        }
        appContext?.let { PlayerWidget.refresh(it) }
    }
}
