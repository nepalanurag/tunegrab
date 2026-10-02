package com.tunegrab.app

import android.net.Uri
import com.tunegrab.app.library.Album
import com.tunegrab.app.library.Artist
import com.tunegrab.app.library.DeleteResult
import com.tunegrab.app.library.PlaylistStore
import com.tunegrab.app.library.FolderInfo
import com.tunegrab.app.library.Genre
import com.tunegrab.app.library.MediaStoreRepository
import com.tunegrab.app.library.Song
import com.tunegrab.app.library.SortBy
import com.tunegrab.app.library.folderDisplayName
import com.tunegrab.app.player.PlayerManager
import com.tunegrab.app.player.PlayerTrack
import com.tunegrab.app.player.RadioFetcher
import com.tunegrab.app.player.RADIO_REFILL_THRESHOLD
import com.tunegrab.app.player.SongKey
import com.tunegrab.app.ui.library.AlbumUi
import com.tunegrab.app.ui.library.ArtistUi
import com.tunegrab.app.ui.library.FolderUi
import com.tunegrab.app.ui.library.GenreUi
import com.tunegrab.app.ui.library.LibraryDataSource
import com.tunegrab.app.ui.library.LibraryTab
import com.tunegrab.app.ui.library.SongUi
import com.tunegrab.app.ui.library.SortOption
import com.tunegrab.app.ui.player.PlayerUiController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/**
 * Phase 1 wiring: connects the parallel-built library, player, and UI
 * layers through the contract seams defined in ui.library / ui.player.
 *
 * - [MediaStoreLibraryDataSource] adapts [MediaStoreRepository] to
 *   [LibraryDataSource], caching returned songs so the player can resolve
 *   content URIs later.
 * - [PlayerUiControllerAdapter] adapts [PlayerManager] to
 *   [PlayerUiController], tracking the queue as [SongUi] and converting to
 *   [PlayerTrack] on play.
 */

/** Small in-memory cache of songs the UI has seen (id -> full Song). */
class TrackCache {
    private val map = LinkedHashMap<Long, Song>()

    @Synchronized
    fun putAll(songs: List<Song>) {
        for (s in songs) {
            map[s.id] = s
            if (map.size > 4000) map.remove(map.keys.first())
        }
    }

    @Synchronized
    fun get(id: Long): Song? = map[id]
}

/** Adapts [MediaStoreRepository] to the UI's [LibraryDataSource]. */
class MediaStoreLibraryDataSource(
    private val repo: MediaStoreRepository,
    private val cache: TrackCache,
) : LibraryDataSource {

    private fun Song.toUi(): SongUi = SongUi(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        // loadThumbnail() on the song's own content Uri returns the embedded
        // cover art on API 29+. (The legacy
        // content://media/external/audio/albumart provider no longer serves
        // artwork on modern Android, so the albumArtUri always missed.)
        artworkKey = contentUri.toString(),
    )

    // Artwork maps: built once per data-source lifetime (one indexed
    // MediaStore query each). Album/artist art changes only when the
    // library itself changes.
    private var albumArtwork: Map<Long, String>? = null
    private var artistArtwork: Map<String, List<String>>? = null

    private suspend fun albumArtwork(): Map<Long, String> =
        albumArtwork ?: repo.getAlbumArtworkMap().also { albumArtwork = it }

    private suspend fun artistArtwork(): Map<String, List<String>> =
        artistArtwork ?: repo.getArtistArtworkMap().also { artistArtwork = it }

    private fun Album.toUi(artworkKey: String?): AlbumUi = AlbumUi(
        id = id,
        title = title,
        artist = artist,
        songCount = songCount,
        year = year,
        artworkKey = artworkKey,
    )

    private fun Artist.toUi(artworkKeys: List<String>): ArtistUi = ArtistUi(
        id = id,
        name = name,
        songCount = songCount,
        albumCount = albumCount,
        artworkKeys = artworkKeys,
    )

    private fun Genre.toUi(): GenreUi = GenreUi(
        id = id,
        name = name,
        songCount = songCount,
    )

    private fun FolderInfo.toUi(): FolderUi = FolderUi(
        path = path,
        name = folderDisplayName(path).ifBlank { displayName },
        songCount = songCount,
    )

    private fun SortOption.toSortBy(): SortBy = when (this) {
        SortOption.TITLE_ASC -> SortBy.TITLE
        SortOption.ARTIST_ASC -> SortBy.ARTIST
        SortOption.ALBUM_ASC -> SortBy.ALBUM
        SortOption.DURATION_DESC -> SortBy.DURATION
        SortOption.DATE_ADDED_DESC -> SortBy.DATE_ADDED
    }

    override suspend fun songs(
        page: Int,
        pageSize: Int,
        sort: SortOption,
        query: String,
    ): List<SongUi> {
        val list = repo.getSongs(
            limit = pageSize,
            offset = page * pageSize,
            sortBy = sort.toSortBy(),
            query = query.ifBlank { null },
        )
        cache.putAll(list)
        return list.map { it.toUi() }
    }

    override suspend fun albums(
        page: Int,
        pageSize: Int,
        sort: SortOption,
        query: String,
    ): List<AlbumUi> {
        // Album/artist counts are small; fetch whole and sort/filter in memory.
        var list = repo.getAlbums(limit = 10_000, offset = 0)
        if (query.isNotBlank()) {
            list = list.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true)
            }
        }
        list = when (sort) {
            SortOption.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
            SortOption.ARTIST_ASC -> list.sortedBy { it.artist.lowercase() }
            SortOption.ALBUM_ASC -> list.sortedBy { it.title.lowercase() }
            SortOption.DURATION_DESC -> list.sortedByDescending { it.songCount }
            SortOption.DATE_ADDED_DESC -> list.sortedByDescending { it.year }
        }
        val art = albumArtwork()
        return list.drop(page * pageSize).take(pageSize).map { it.toUi(art[it.id]) }
    }

    override suspend fun artists(
        page: Int,
        pageSize: Int,
        sort: SortOption,
        query: String,
    ): List<ArtistUi> {
        var list = repo.getArtists(limit = 10_000, offset = 0)
        if (query.isNotBlank()) {
            list = list.filter { it.name.contains(query, ignoreCase = true) }
        }
        list = when (sort) {
            SortOption.TITLE_ASC, SortOption.ALBUM_ASC ->
                list.sortedBy { it.name.lowercase() }
            SortOption.ARTIST_ASC -> list.sortedBy { it.name.lowercase() }
            SortOption.DURATION_DESC -> list.sortedByDescending { it.songCount }
            SortOption.DATE_ADDED_DESC -> list.sortedByDescending { it.albumCount }
        }
        val art = artistArtwork()
        return list.drop(page * pageSize).take(pageSize)
            .map { it.toUi(art[it.name.lowercase()].orEmpty()) }
    }

    override suspend fun genres(): List<GenreUi> =
        repo.getGenres().map { it.toUi() }

    override suspend fun songsForGenre(genreId: Long): List<SongUi> =
        repo.getGenreSongs(genreId).map { it.toUi() }

    override suspend fun folders(): List<FolderUi> =
        repo.getFolders().map { it.toUi() }

    override suspend fun songsForAlbum(albumId: Long): List<SongUi> {
        val list = repo.getAlbumSongs(albumId)
        cache.putAll(list)
        return list.map { it.toUi() }
    }

    override suspend fun songsForArtist(artistName: String): List<SongUi> {
        val list = repo.getArtistSongs(artistName)
        cache.putAll(list)
        return list.map { it.toUi() }
    }

    override suspend fun songsForFolder(path: String): List<SongUi> {
        val list = repo.getFolderSongs(path)
        cache.putAll(list)
        return list.map { it.toUi() }
    }

    override suspend fun songCount(): Long = repo.getSongCount().toLong()

    override suspend fun deleteSong(songId: Long): DeleteResult =
        repo.deleteSong(songId)

    override suspend fun songsByIds(ids: List<Long>): List<SongUi> =
        repo.getSongsByIds(ids).map { it.toUi() }
}

/** Adapts [PlayerManager] to the UI's [PlayerUiController]. */
class PlayerUiControllerAdapter(
    private val cache: TrackCache,
    private val onProFeatureLocked: (reason: String) -> Unit = {},
    /**
     * Radio similarity lookups. Null in the Play Store build, where Radio
     * does not exist — [startRadio] is then a no-op.
     */
    private val radioFetcher: RadioFetcher? = null,
) : PlayerUiController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _queue = MutableStateFlow<List<SongUi>>(emptyList())
    override val queue: StateFlow<List<SongUi>> = _queue.asStateFlow()

    override val current: StateFlow<SongUi?> =
        combine(PlayerManager.currentMediaId, _queue) { mediaId, q ->
            mediaId?.let { id -> q.find { it.queueKey == id } }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    override val isPlaying: StateFlow<Boolean> = PlayerManager.isPlaying
    override val positionMs: StateFlow<Long> = PlayerManager.positionMs
    override val durationMs: StateFlow<Long> = PlayerManager.durationMs
    override val shuffle: StateFlow<Boolean> = PlayerManager.shuffleEnabled
    override val repeatMode: StateFlow<Int> = PlayerManager.repeatMode
    override val remoteLoading: StateFlow<Boolean> = PlayerManager.remoteLoading
    override val sleepUntilMs: StateFlow<Long?> = PlayerManager.sleepUntilMs

    init {
        // Reconnect case: the app was killed and restarted while the
        // service kept playing. The fresh process has no queue, so the
        // mini player stays hidden even though currentMediaId is set.
        // Rebuild the UI queue from the controller's timeline.
        scope.launch {
            combine(PlayerManager.currentMediaId, _queue) { mediaId, q ->
                mediaId to q
            }.collect { (mediaId, q) ->
                if (mediaId != null && q.none { it.queueKey == mediaId }) {
                    val items = PlayerManager.controllerMediaItems()
                    if (items.isNotEmpty()) {
                        _queue.value = items.mapNotNull { it.toSongUi() }
                    }
                }
            }
        }
    }

    /** Rebuilds a [SongUi] from a controller [MediaItem] (reconnect only). */
    private fun androidx.media3.common.MediaItem.toSongUi(): SongUi? {
        val metadata = mediaMetadata
        // Be lenient: even a bare mediaId is enough to show the mini
        // player; the full metadata should be there, but don't hide the
        // player just because a field is missing.
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: "Unknown title"
        val artist = metadata.artist?.toString().orEmpty()
        val album = metadata.albumTitle?.toString().orEmpty()
        val durationMs = metadata.durationMs ?: 0L
        val artworkKey = metadata.artworkUri?.toString()
        return if (mediaId.startsWith("yt:")) {
            SongUi.remote(
                videoId = mediaId.removePrefix("yt:"),
                title = title,
                artist = artist,
                thumbnailUrl = artworkKey,
                durationSec = durationMs / 1000L,
            )
        } else {
            val id = mediaId.toLongOrNull() ?: return null
            SongUi(
                id = id,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                artworkKey = artworkKey,
            )
        }
    }

    private fun SongUi.toTrack(): PlayerTrack? {
        if (isRemote) {
            val videoId = remoteVideoId ?: return null
            val local = localContentUri
            return if (!local.isNullOrBlank()) {
                PlayerTrack.remoteDownloaded(videoId, title, artist, local, artworkKey)
            } else {
                PlayerTrack.remote(videoId, title, artist, artworkKey, durationMs)
            }
        }
        val song = cache.get(id) ?: return null
        return PlayerTrack.fromSong(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            contentUri = song.contentUri,
        )
    }

    override fun togglePlayPause() = PlayerManager.togglePlayPause()
    override fun seekTo(ms: Long) = PlayerManager.seekTo(ms)
    override fun previous() = PlayerManager.previous()
    override fun toggleShuffle() = PlayerManager.toggleShuffle()
    override fun cycleRepeat() = PlayerManager.cycleRepeat()
    override fun setSleepTimer(minutes: Int) {
        // Turning the timer off is always allowed; setting it needs Pro.
        if (minutes != 0 && !AppSettings.isProNow()) {
            onProFeatureLocked("The sleep timer is a Pro feature.")
            return
        }
        PlayerManager.setSleepTimer(minutes)
    }

    override fun playQueue(songs: List<SongUi>, index: Int) {
        // An explicit new queue ends Radio; only a Radio tap restarts it.
        if (_isRadio.value) stopRadio()
        // Local songs missing from the cache are dropped; remote tracks
        // always convert. Indices stay aligned with the kept list.
        val kept = songs.filter { it.isRemote || cache.get(it.id) != null }
        if (kept.isEmpty()) return
        val tracks = kept.mapNotNull { it.toTrack() }
        if (tracks.isEmpty()) return
        _queue.value = kept
        // Remap the requested index: dropped songs shift positions.
        val target = songs.getOrNull(index)
        val keptIndex = kept.indexOf(target).coerceAtLeast(0)
        PlayerManager.playQueue(tracks, keptIndex)
    }

    /** Jumps to the queue entry at [index] (UI queue order). */
    override fun playQueueIndex(index: Int) {
        if (index in _queue.value.indices) PlayerManager.playQueueIndex(index)
    }

    override fun enqueue(songs: List<SongUi>) {
        val kept = songs.filter { it.isRemote || cache.get(it.id) != null }
        val tracks = kept.mapNotNull { it.toTrack() }
        if (tracks.isEmpty()) return
        _queue.value = _queue.value + kept
        PlayerManager.enqueue(tracks)
    }

    override fun playNext(songs: List<SongUi>) {
        val kept = songs.filter { it.isRemote || cache.get(it.id) != null }
        val tracks = kept.mapNotNull { it.toTrack() }
        if (tracks.isEmpty()) return
        val q = _queue.value
        val cur = q.indexOfFirst { it.queueKey == PlayerManager.currentMediaId.value }
            .coerceAtLeast(0)
        val insertAt = if (q.isEmpty()) 0 else cur + 1
        _queue.value = q.toMutableList().also { it.addAll(insertAt, kept) }
        PlayerManager.playNext(tracks)
    }

    override fun removeFromQueue(index: Int) {
        _queue.value = _queue.value.toMutableList().also {
            if (index in it.indices) it.removeAt(index)
        }
        PlayerManager.removeQueueIndex(index)
    }

    override fun moveInQueue(from: Int, to: Int) {
        _queue.value = _queue.value.toMutableList().also {
            if (from in it.indices && to in it.indices) {
                it.add(to, it.removeAt(from))
            }
        }
        PlayerManager.moveQueueItem(from, to)
    }

    override fun updateTrackArtist(queueKey: String, artist: String) {
        _queue.value = _queue.value.map {
            if (it.queueKey == queueKey) it.copy(artist = artist) else it
        }
    }

    override fun updateTrackTitle(queueKey: String, title: String) {
        _queue.value = _queue.value.map {
            if (it.queueKey == queueKey) it.copy(title = title) else it
        }
    }

    // ---------------- Radio ----------------

    private val _isRadio = MutableStateFlow(false)
    override val isRadio: StateFlow<Boolean> = _isRadio.asStateFlow()

    /** Video ids already queued or played while Radio is on. */
    private val radioExcludeIds = mutableSetOf<String>()

    /** Normalized titles already queued or played while Radio is on. */
    private val radioExcludeTitles = mutableSetOf<String>()

    private var radioRefillJob: Job? = null

    init {
        // While Radio is on, top up the queue in the background whenever
        // few tracks remain after the current one, so playback never
        // stops on its own. Appends only; the user's queue is never
        // restarted or reordered here.
        scope.launch {
            combine(isRadio, current, queue) { radio, cur, q ->
                Triple(radio, cur, q)
            }.collect { (radio, cur, q) ->
                if (!radio || cur == null) return@collect
                val remaining = q.size - q.indexOf(cur) - 1
                if (remaining <= RADIO_REFILL_THRESHOLD) refillRadio()
            }
        }
    }

    override fun startRadio(song: SongUi) {
        if (!AppSettings.isProNow()) {
            onProFeatureLocked("Radio is a Pro feature.")
            return
        }
        // No fetcher in the Play Store build: Radio does not exist there.
        val fetcher = radioFetcher ?: return
        radioRefillJob?.cancel()
        radioExcludeIds.clear()
        radioExcludeTitles.clear()
        // Store raw video ids here: selectTracks compares them against
        // raw search-result ids, not queue keys.
        song.remoteVideoId?.let { radioExcludeIds.add(it) }
        radioExcludeTitles.add(fetcher.titleKey(song.title))
        _isRadio.value = true
        // When the seed is already the playing song, rebuild the queue
        // around it from its current position instead of restarting it.
        val keepPosition = PlayerManager.currentMediaId.value == song.queueKey
        val positionMs = PlayerManager.positionMs.value
        _queue.value = listOf(song)
        song.toTrack()?.let {
            // The seed plays right away; similar tracks stream in behind it.
            if (keepPosition) PlayerManager.playQueueAt(listOf(it), 0, positionMs)
            else PlayerManager.playQueue(listOf(it), 0)
        }
        refillRadio()
    }

    override fun stopRadio() {
        _isRadio.value = false
        radioRefillJob?.cancel()
    }

    /**
     * Fetches similar tracks for the most recent seeds and appends them.
     * The result is dropped when Radio was turned off mid-fetch or the
     * queue no longer needs topping up.
     */
    private fun refillRadio() {
        if (radioRefillJob?.isActive == true) return
        val fetcher = radioFetcher ?: return
        radioRefillJob = scope.launch {
            val q = _queue.value
            val cur = current.value
            val seeds = buildList {
                if (cur != null) add(fetcher.seedOf(cur))
                val idx = q.indexOf(cur)
                if (idx > 0) {
                    q.subList(maxOf(0, idx - 2), idx)
                        .mapTo(this) { fetcher.seedOf(it) }
                }
            }
            if (seeds.isEmpty()) return@launch
            val results = fetcher.fetchSimilar(
                seeds = seeds,
                excludeIds = radioExcludeIds.toSet(),
                excludeTitles = radioExcludeTitles.toSet(),
            )
            if (!_isRadio.value) return@launch
            val queuedKeys = _queue.value.map { it.queueKey }.toSet()
            val fresh = results
                .filter { "yt:${it.id}" !in queuedKeys }
                .map {
                    SongUi.remote(
                        videoId = it.id,
                        title = it.title,
                        artist = it.uploader?.takeIf { u -> u.isNotBlank() }
                            ?: SongUi.UNKNOWN_ARTIST,
                        thumbnailUrl = "https://i.ytimg.com/vi/${it.id}/mqdefault.jpg",
                        durationSec = it.durationSec,
                    )
                }
            if (fresh.isEmpty()) return@launch
            fresh.forEach {
                it.remoteVideoId?.let { id -> radioExcludeIds.add(id) }
                radioExcludeTitles.add(SongKey.titleKey(it.title))
            }
            // Re-check on the main thread before touching the queue.
            val qNow = _queue.value
            val curNow = current.value
            if (!_isRadio.value || curNow == null) return@launch
            val remaining = qNow.size - qNow.indexOf(curNow) - 1
            if (remaining > RADIO_REFILL_THRESHOLD) return@launch
            enqueue(fresh)
            // If the queue had naturally ended, step into the new tracks.
            PlayerManager.radioContinueIfEnded()
        }
    }

    override fun next() {
        // Skipping near the tail while Radio is on also asks for more
        // tracks, so a manual skip can't strand playback at the end.
        if (_isRadio.value) {
            val q = _queue.value
            val cur = current.value
            if (cur != null &&
                q.size - q.indexOf(cur) - 1 <= RADIO_REFILL_THRESHOLD
            ) {
                refillRadio()
            }
        }
        PlayerManager.next()
    }
}

/** Which top-level tab the app shows. */
enum class AppTab {
    HOME,
    DOWNLOAD,
    LIBRARY,
    ACTIVITY,
    /** Personal Pro build only: YouTube search. Never shown in store builds. */
    SEARCH,
}

/** Suppress unused warnings for tabs wired later. */
@Suppress("unused")
fun appTabFor(tab: LibraryTab): AppTab = AppTab.LIBRARY

/** Converts a content-Uri string back to a Uri, tolerating garbage. */
fun parseArtworkKey(key: String?): Uri? =
    runCatching { key?.let { Uri.parse(it) } }.getOrNull()
