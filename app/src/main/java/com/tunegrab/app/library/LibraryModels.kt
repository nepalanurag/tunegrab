package com.tunegrab.app.library

import android.net.Uri

/**
 * Data model layer for the local music library (Phase 1).
 *
 * Everything is read from MediaStore by [MediaStoreRepository]; everything
 * here is Android-dependent only in the sense that [Uri] is an Android
 * type. All sorting/diffing lives in [LibraryLogic], which works on the
 * pure [SongSortable] interface so it stays JVM-testable.
 */

/** A single audio track from the device's media library. */
data class Song(
    val id: Long,
    override val title: String,
    override val artist: String,
    override val album: String,
    val albumId: Long,
    val artistId: Long,
    /** Resolved via the MediaStore genre tables; null when unknown. */
    val genre: String?,
    override val durationMs: Long,
    /** Raw MediaStore TRACK value (may encode disc number as 1001). */
    val trackNumber: Int,
    override val year: Int,
    /** Epoch seconds, from MediaStore DATE_ADDED. */
    override val dateAddedSec: Long,
    /** Epoch seconds, from MediaStore DATE_MODIFIED. */
    val dateModifiedSec: Long,
    val sizeBytes: Long,
    val contentUri: Uri,
    /** Parent directory of the file, e.g. "/storage/emulated/0/Music". */
    val folderPath: String,
    /** Full filesystem path from the MediaStore DATA column (display only). */
    val filePath: String = "",
    val mimeType: String
) : SongSortable

/** An album entry from MediaStore.Audio.Albums. */
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songCount: Int
)

/** An artist entry from MediaStore.Audio.Artists. */
data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int
)

/** A genre entry from MediaStore.Audio.Genres, with its song count. */
data class Genre(
    val id: Long,
    val name: String,
    val songCount: Int
)

/**
 * A folder in the library, derived by grouping songs by the parent
 * directory of their file path. Not a MediaStore entity — computed by
 * [MediaStoreRepository.getFolders].
 */
data class FolderInfo(
    /** Full parent directory path, e.g. "/storage/emulated/0/Music". */
    val path: String,
    /** Last path segment, e.g. "Music". */
    val displayName: String,
    val songCount: Int
)

/**
 * Library sort orders. The repository applies the primary order in the
 * SQL query; [sortSongs] re-applies the same semantics in memory for
 * already-loaded lists.
 */
enum class SortBy {
    TITLE,
    ARTIST,
    ALBUM,
    DURATION,
    DATE_ADDED,
    YEAR
}
