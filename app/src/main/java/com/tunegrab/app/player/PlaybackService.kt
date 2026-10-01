package com.tunegrab.app.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.tunegrab.app.AppSettings
import com.tunegrab.app.MainActivity
import com.tunegrab.app.library.Album
import com.tunegrab.app.library.Artist
import com.tunegrab.app.library.MediaStoreRepository
import com.tunegrab.app.library.Song
import com.tunegrab.app.library.SortBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground playback service hosting the [ExoPlayer] instance and the
 * [MediaLibrarySession]. [PlayerManager] connects to it via
 * [androidx.media3.session.MediaController].
 *
 * A [MediaLibraryService] (not just [androidx.media3.session.MediaSessionService])
 * so Android Auto can browse the on-device library: root → Songs / Artists /
 * Albums, with playable song items resolved to their MediaStore content URIs.
 * The manifest already advertises `android.media.browse.MediaBrowserService`;
 * `res/xml/automotive_app_desc.xml` declares the Auto media template.
 *
 * Audio focus: requesting focus with [AudioAttributes] (`handleAudioFocus = true`)
 * ducks/pauses on transient loss (e.g. navigation prompts) and resumes per
 * ExoPlayer's defaults; permanent loss stops playback. Headset/Bluetooth
 * disconnect pauses via [setHandleAudioBecomingNoisyEnabled].
 *
 * Gapless playback: [PlayerManager.playQueue] enqueues all items with
 * `setMediaItems`, which builds a `ConcatenatingMediaSource` internally.
 * ExoPlayer pre-buffers the upcoming item and trims encoder delay/padding
 * on adjacent same-format items, so album tracks join with no audible gap
 * — no extra configuration is required or applied here. The fade-out-only
 * crossfade in [CrossfadeController] is layered on top and never disables
 * this gapless join.
 *
 * Manifest: this service must be declared (exported=false) — done by the
 * app coordinator in AndroidManifest.xml.
 */
class PlaybackService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null
    private var eqController: EqualizerController? = null
    private lateinit var repository: MediaStoreRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---- Sleep timer ----
    // Owned and executed here, not in the UI layer: the activity
    // disconnects its MediaController in onStop, but this service keeps
    // running, so the deadline survives the app going to the background
    // or the screen turning off. At the deadline the service's own
    // player is paused directly — no controller round-trip needed.
    private var sleepUntilMs: Long? = null
    private var sleepJob: Job? = null

    /**
     * Arms the sleep timer for the wall-clock deadline [untilMs], or
     * cancels it when [untilMs] <= 0. Replaces any previous timer.
     */
    fun setSleepTimer(untilMs: Long) {
        sleepJob?.cancel()
        sleepJob = null
        if (untilMs <= 0L) {
            sleepUntilMs = null
            return
        }
        sleepUntilMs = untilMs
        sleepJob = serviceScope.launch {
            val remaining = untilMs - System.currentTimeMillis()
            if (remaining > 0) delay(remaining)
            // Unconditional: pausing an already-paused player is a no-op,
            // and there is no controller whose state could lie here.
            player?.pause()
            sleepUntilMs = null
        }
    }

    // ---- Android Auto browse tree ----
    // Media IDs: "root" -> "songs" | "artists" | "albums";
    // "artist:<id>" / "album:<id>" -> songs; playable songs are "song:<id>".
    private val libraryCallback = object : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            // Advertise the sleep-timer custom commands so controllers
            // are allowed to send them.
            val commands = base.availableSessionCommands.buildUpon()
                .add(SessionCommand(SleepTimerCommands.SET_ACTION, Bundle.EMPTY))
                .add(SessionCommand(SleepTimerCommands.GET_ACTION, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                commands,
                base.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                SleepTimerCommands.SET_ACTION -> {
                    setSleepTimer(args.getLong(SleepTimerCommands.EXTRA_UNTIL_MS, 0L))
                    Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS, Bundle.EMPTY)
                    )
                }
                SleepTimerCommands.GET_ACTION -> {
                    val data = Bundle().apply {
                        putLong(
                            SleepTimerCommands.EXTRA_UNTIL_MS,
                            sleepUntilMs ?: SleepTimerCommands.NO_TIMER
                        )
                    }
                    Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS, data)
                    )
                }
                else -> Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_UNKNOWN)
                )
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = browseItem(
                "root", "TuneGrab",
                MediaMetadata.FOLDER_TYPE_MIXED,
            )
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val items = buildChildren(parentId, page, pageSize)
                    future.set(LibraryResult.ofItemList(items, params))
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val item = resolvePlayable(mediaId)
                    future.set(
                        if (item != null) LibraryResult.ofItem(item, null)
                        else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    )
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            // Android Auto sends items that carry only a mediaId; resolve
            // each to a playable item with its content URI.
            if (mediaItems.all { it.localConfiguration?.uri != null }) {
                return Futures.immediateFuture(mediaItems)
            }
            val future = SettableFuture.create<List<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    future.set(mediaItems.map { resolvePlayable(it.mediaId) ?: it })
                } catch (e: Exception) {
                    future.set(mediaItems)
                }
            }
            return future
        }
    }

    private suspend fun buildChildren(
        parentId: String,
        page: Int,
        pageSize: Int,
    ): List<MediaItem> {
        val size = pageSize.coerceIn(1, 200)
        val offset = (page.coerceAtLeast(0)) * size
        return when {
            parentId == "root" -> listOf(
                browseItem("songs", "Songs", MediaMetadata.FOLDER_TYPE_PLAYLISTS),
                browseItem("artists", "Artists", MediaMetadata.FOLDER_TYPE_ARTISTS),
                browseItem("albums", "Albums", MediaMetadata.FOLDER_TYPE_ALBUMS),
            )
            parentId == "songs" ->
                repository.getSongs(limit = size, offset = offset, sortBy = SortBy.TITLE)
                    .map { it.toPlayableItem() }
            parentId == "artists" ->
                repository.getArtists(limit = size, offset = offset)
                    .map { it.toBrowseItem() }
            parentId == "albums" ->
                repository.getAlbums(limit = size, offset = offset)
                    .map { it.toBrowseItem() }
            parentId.startsWith("artist:") -> {
                val id = parentId.removePrefix("artist:").toLongOrNull()
                    ?: return emptyList()
                repository.getArtistSongs(id).map { it.toPlayableItem() }
            }
            parentId.startsWith("album:") -> {
                val id = parentId.removePrefix("album:").toLongOrNull()
                    ?: return emptyList()
                repository.getAlbumSongs(id).map { it.toPlayableItem() }
            }
            else -> emptyList()
        }
    }

    private suspend fun resolvePlayable(mediaId: String): MediaItem? {
        if (!mediaId.startsWith("song:")) return null
        val id = mediaId.removePrefix("song:").toLongOrNull() ?: return null
        return repository.getSongsByIds(listOf(id)).firstOrNull()?.toPlayableItem()
    }

    private fun browseItem(mediaId: String, title: String, folderType: Int): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setFolderType(folderType)
                    .setExtras(Bundle())
                    .build()
            )
            .build()

    private fun Song.toPlayableItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId("song:$id")
            .setUri(contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setExtras(Bundle())
                    .build()
            )
            .build()

    private fun Artist.toBrowseItem(): MediaItem =
        browseItem("artist:$id", name, MediaMetadata.FOLDER_TYPE_ARTISTS)

    private fun Album.toBrowseItem(): MediaItem =
        browseItem("album:$id", title, MediaMetadata.FOLDER_TYPE_ALBUMS)

    override fun onCreate() {
        super.onCreate()
        repository = MediaStoreRepository(this)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setRenderersFactory(SilenceSkippingRenderersFactory(this))
            .build()
        player.setHandleAudioBecomingNoisy(true)
        this.player = player

        // Tapping the notification / system media controls opens the app.
        // FLAG_IMMUTABLE is required on API 31+; minSdk 26 never needs the mutable flag here.
        val sessionActivity = PendingIntent.getActivity(
            this,
            /* requestCode= */ 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        session = MediaLibrarySession.Builder(this, player, libraryCallback)
            .setSessionActivity(sessionActivity)
            .build()

        // Equalizer: platform audio effect on this player's audio session.
        // Settings changes apply live via the flows below.
        eqController = EqualizerController().also { it.attachTo(player) }
        serviceScope.launch {
            combine(
                AppSettings.eqEnabled,
                AppSettings.eqPreset,
                AppSettings.eqBands,
            ) { enabled, preset, bands -> Triple(enabled, preset, bands) }
                .collect { (enabled, preset, bands) ->
                    eqController?.applySettings(enabled, preset, bands)
                }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session

    override fun onDestroy() {
        // Release session first so no new controllers attach while tearing down.
        serviceScope.cancel()
        eqController?.detach()
        eqController = null
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }
}
