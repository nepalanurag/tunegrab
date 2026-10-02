package com.tunegrab.app.ui.library

/**
 * UI-layer seam between the Library screen and the real MediaStore layer.
 *
 * The coordinator wires this up: `com.tunegrab.app.library` (the parallel
 * MediaStore agent) provides an implementation of [LibraryDataSource] that
 * maps its Song/Album/Artist/Genre models onto these UI models, and the
 * player agent's queue starter converts [SongUi] into its PlayerTrack.
 *
 * Field conventions (must be honored by the implementation):
 * - `id`: MediaStore audio/albums/artists row id.
 * - `artworkKey`: string form of a content Uri that [com.tunegrab.app.ui.player.ArtworkImage]
 *   can load (e.g. "content://media/external/audio/albumart/<albumId>"), or null
 *   when the item has no art (UI shows a fallback icon).
 * - `durationMs`: track length in milliseconds.
 *
 * Streaming tracks (YouTube Music, not downloaded): `id` is -1,
 * [remoteVideoId] carries the YouTube video id, `artworkKey` is the https
 * thumbnail URL, and [localContentUri] is set once the track has been
 * downloaded — the player then prefers the local file and the entry works
 * offline. [queueKey] is the stable identity used to match the player
 * queue (MediaStore id for local tracks, "yt:&lt;videoId&gt;" for streams).
 */
data class SongUi(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkKey: String?,
    val remoteVideoId: String? = null,
    val localContentUri: String? = null,
) {
    val isRemote: Boolean get() = remoteVideoId != null
    val queueKey: String get() = remoteVideoId?.let { "yt:$it" } ?: id.toString()

    companion object {
        /**
         * Placeholder artist for streams whose uploader is unknown (flat
         * YouTube Music rows carry none). The real name is resolved in the
         * background after playback starts, and "Go to artist" resolves it
         * from the video when this placeholder is all we have.
         */
        const val UNKNOWN_ARTIST = "YouTube Music"

        /** Builds a streaming [SongUi] from a YouTube Music search result. */
        fun remote(
            videoId: String,
            title: String,
            artist: String,
            thumbnailUrl: String?,
            durationSec: Long?,
        ): SongUi = SongUi(
            id = -1L,
            title = title,
            artist = artist,
            album = "",
            durationMs = (durationSec ?: 0L) * 1000L,
            artworkKey = thumbnailUrl,
            remoteVideoId = videoId,
        )
    }
}

data class AlbumUi(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val year: Int,
    val artworkKey: String?,
)

data class ArtistUi(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    /** Up to 4 song content-Uris (distinct albums) for the mosaic tile. */
    val artworkKeys: List<String> = emptyList(),
)

data class GenreUi(
    val id: Long,
    val name: String,
    val songCount: Int,
)

data class FolderUi(
    val path: String,
    val name: String,
    val songCount: Int,
)

/** The library filters, in display order. */
enum class LibraryTab {
    SONGS,
    LIKED,
    ALBUMS,
    ARTISTS,
    GENRES,
    FOLDERS,
    PLAYLISTS,
}

/** Sort orders offered by the Library screen's sort dropdown. */
enum class SortOption(val displayName: String) {
    TITLE_ASC("Title A-Z"),
    ARTIST_ASC("Artist A-Z"),
    ALBUM_ASC("Album A-Z"),
    DURATION_DESC("Longest first"),
    DATE_ADDED_DESC("Recently added"),
}

/**
 * Read seam for the library. Implementations query MediaStore off the main
 * thread (all functions are suspend) and return plain lists; the UI layer
 * ([LibraryScreen]) handles pagination by requesting pages.
 *
 * - `page` is 0-based; `pageSize` is a hint the UI sets (currently 200).
 * - `query` is a case-insensitive substring filter on title/artist/album;
 *   blank means no filtering.
 * - `sort` applies to the full result set, not just the page.
 * - `songCount()` returns the total number of audio tracks (for headers).
 */
interface LibraryDataSource {
    suspend fun songs(
        page: Int,
        pageSize: Int,
        sort: SortOption,
        query: String,
    ): List<SongUi>

    suspend fun albums(
        page: Int,
        pageSize: Int,
        sort: SortOption,
        query: String,
    ): List<AlbumUi>

    suspend fun artists(
        page: Int,
        pageSize: Int,
        sort: SortOption,
        query: String,
    ): List<ArtistUi>

    suspend fun genres(): List<GenreUi>

    suspend fun songsForGenre(genreId: Long): List<SongUi>

    suspend fun folders(): List<FolderUi>

    suspend fun songsForAlbum(albumId: Long): List<SongUi>

    suspend fun songsForArtist(artistName: String): List<SongUi>

    suspend fun songsForFolder(path: String): List<SongUi>

    suspend fun songCount(): Long

    /** Deletes a song file; see [com.tunegrab.app.library.DeleteResult]. */
    suspend fun deleteSong(songId: Long): com.tunegrab.app.library.DeleteResult

    /** Songs for explicit MediaStore ids, in the caller's order. */
    suspend fun songsByIds(ids: List<Long>): List<SongUi>
}
