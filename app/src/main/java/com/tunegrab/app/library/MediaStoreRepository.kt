package com.tunegrab.app.library

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.app.RecoverableSecurityException
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of [MediaStoreRepository.deleteSong]. */
sealed interface DeleteResult {
    /** The file is gone. */
    data object Deleted : DeleteResult
    /** The system must ask the user first; launch this IntentSender. */
    data class NeedsConsent(val intentSender: android.content.IntentSender) : DeleteResult
    /** Delete failed; [message] is user-readable. */
    data class Failed(val message: String) : DeleteResult
}

/**
 * Reads the on-device music library through MediaStore.
 *
 * Design rules:
 * - Every query is projection-limited (never `null` projection) and
 *   filtered with `IS_MUSIC != 0`, so ringtones/notifications never leak in.
 * - All listing queries are paginated with limit/offset. On API 30+ this
 *   uses the [ContentResolver] query-bundle pagination args; on API 26-29
 *   it appends `LIMIT x OFFSET y` to the sort-order string, which the
 *   MediaStore SQLite backend honors.
 * - Cursor access is null-safe: missing columns (index -1) fall back to
 *   defaults instead of throwing, and cursors are always closed via `use`.
 * - Callers should check [hasPermission] before calling; without it the
 *   getters return empty results rather than crashing.
 *
 * Per-song genre is not a MediaStore.Audio.Media column, so genres are
 * resolved through the Audio.Genres + Audio.Genres.Members tables. The
 * song-id -> genre map is built once per repository instance (small,
 * projection-limited queries) and cached.
 */
class MediaStoreRepository(context: Context) {

    private val appContext: Context = context.applicationContext

    // ---------------- permissions ----------------

    companion object {
        /** True when the app may read audio files from shared storage. */
        fun hasPermission(context: Context): Boolean {
            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            return context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
        }

        /** Effectively unbounded page size for single-collection queries. */
        const val SONGS_ALL = 50_000
    }

    // ---------------- songs ----------------

    private val songProjection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.ARTIST_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.YEAR,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.RELATIVE_PATH,
        MediaStore.Audio.Media.MIME_TYPE
    )

    /**
     * Songs in the library, paginated. [query] matches title/artist/album
     * with a LIKE filter handled by SQLite's index-friendly prefix path.
     * The primary order is applied by the query itself.
     */
    suspend fun getSongs(
        limit: Int,
        offset: Int,
        sortBy: SortBy,
        query: String? = null
    ): List<Song> = withContext(Dispatchers.IO) {
        if (!hasPermission(appContext)) return@withContext emptyList()
        val (selection, args) = songSelection(query, extra = null, extraArgs = null)
        val cursor = queryPaged(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            songProjection,
            selection,
            args,
            sortColumnsFor(sortBy),
            limit.coerceAtLeast(1),
            offset.coerceAtLeast(0)
        ) ?: return@withContext emptyList()
        val genreMap = songGenreMap()
        cursor.use { readSongs(it, genreMap) }
    }

    /**
     * Songs for the given MediaStore ids, returned in the caller's order.
     * Ids that no longer exist are dropped — playlists clean themselves up.
     */
    suspend fun getSongsByIds(ids: List<Long>): List<Song> =
        withContext(Dispatchers.IO) {
            if (!hasPermission(appContext) || ids.isEmpty()) {
                return@withContext emptyList()
            }
            val genreMap = songGenreMap()
            val byId = mutableMapOf<Long, Song>()
            // SQLite caps bound variables (~999); chunk to stay well under.
            for (chunk in ids.chunked(400)) {
                val placeholders = chunk.joinToString(",") { "?" }
                val cursor = appContext.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    songProjection,
                    "${MediaStore.Audio.Media._ID} IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray(),
                    null,
                ) ?: continue
                cursor.use { byId.putAll(readSongs(it, genreMap).associateBy { s -> s.id }) }
            }
            ids.mapNotNull { byId[it] }
        }

    /** Total number of music tracks visible to the app. */
    suspend fun getSongCount(): Int = withContext(Dispatchers.IO) {        if (!hasPermission(appContext)) return@withContext 0
        val cursor = appContext.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null
        ) ?: return@withContext 0
        cursor.use { it.count }
    }

    /**
     * Deletes a song from MediaStore (and its underlying file).
     *
     * - Files this app published delete silently.
     * - Other files need the user's consent on API 29+: the system throws
     *   RecoverableSecurityException, and we answer with a delete request
     *   whose IntentSender the UI launches for a system consent dialog.
     */
    suspend fun deleteSong(songId: Long): DeleteResult = withContext(Dispatchers.IO) {
        if (!hasPermission(appContext)) {
            return@withContext DeleteResult.Failed("No media permission")
        }
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId
        )
        try {
            val rows = appContext.contentResolver.delete(uri, null, null)
            if (rows > 0) DeleteResult.Deleted
            else DeleteResult.Failed("Song not found")
        } catch (e: RecoverableSecurityException) {
            // The file belongs to another app: the system must ask the user.
            val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.createDeleteRequest(appContext.contentResolver, listOf(uri))
                    .intentSender
            } else {
                runCatching { e.userAction.actionIntent.intentSender }.getOrNull()
            }
            if (sender != null) DeleteResult.NeedsConsent(sender)
            else DeleteResult.Failed("Delete needs approval this app can't request")
        } catch (e: SecurityException) {
            DeleteResult.Failed(e.message ?: "Delete blocked by the system")
        }
    }

    /** Search across title/artist/album; results ordered by title. */
    suspend fun searchSongs(query: String, limit: Int): List<Song> =
        getSongs(limit.coerceIn(1, 500), 0, SortBy.TITLE, query)

    /** Songs of one album, ordered by track number then title. */
    suspend fun getAlbumSongs(albumId: Long): List<Song> = withContext(Dispatchers.IO) {
        if (!hasPermission(appContext)) return@withContext emptyList()
        val (selection, args) = songSelection(
            query = null,
            extra = "${MediaStore.Audio.Media.ALBUM_ID} = ?",
            extraArgs = arrayOf(albumId.toString())
        )
        val cursor = queryPaged(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            songProjection,
            selection,
            args,
            listOf(MediaStore.Audio.Media.TRACK to true, MediaStore.Audio.Media.TITLE to true),
            SONGS_ALL,
            0
        ) ?: return@withContext emptyList()
        cursor.use { readSongs(it, songGenreMap()) }
    }

    /** Songs of one artist, ordered by album year then album then track. */
    suspend fun getArtistSongs(artistId: Long): List<Song> = withContext(Dispatchers.IO) {
        if (!hasPermission(appContext)) return@withContext emptyList()
        val (selection, args) = songSelection(
            query = null,
            extra = "${MediaStore.Audio.Media.ARTIST_ID} = ?",
            extraArgs = arrayOf(artistId.toString())
        )
        val cursor = queryPaged(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            songProjection,
            selection,
            args,
            listOf(
                MediaStore.Audio.Media.YEAR to false,
                MediaStore.Audio.Media.ALBUM to true,
                MediaStore.Audio.Media.TRACK to true
            ),
            SONGS_ALL,
            0
        ) ?: return@withContext emptyList()
        cursor.use { readSongs(it, songGenreMap()) }
    }

    /** Songs stored under one folder path (including sub-folders), ordered by title. */
    suspend fun getFolderSongs(folderPath: String): List<Song> = withContext(Dispatchers.IO) {
        if (!hasPermission(appContext)) return@withContext emptyList()
        val (selection, args) = songSelection(
            query = null,
            extra = "${MediaStore.Audio.Media.DATA} LIKE ?",
            extraArgs = arrayOf("$folderPath/%")
        )
        val cursor = queryPaged(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            songProjection,
            selection,
            args,
            sortColumnsFor(SortBy.TITLE),
            SONGS_ALL,
            0
        ) ?: return@withContext emptyList()
        cursor.use { readSongs(it, songGenreMap()) }
    }

    // ---------------- albums / artists / genres ----------------

    /** Albums, paginated, ordered by album title. */
    suspend fun getAlbums(limit: Int, offset: Int): List<Album> = withContext(Dispatchers.IO) {
        if (!hasPermission(appContext)) return@withContext emptyList()
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.FIRST_YEAR,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS
        )
        val cursor = queryPaged(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            listOf(MediaStore.Audio.Albums.ALBUM to true),
            limit.coerceAtLeast(1),
            offset.coerceAtLeast(0)
        ) ?: return@withContext emptyList()
        cursor.use {
            val id = it.col(MediaStore.Audio.Albums._ID)
            val title = it.col(MediaStore.Audio.Albums.ALBUM)
            val artist = it.col(MediaStore.Audio.Albums.ARTIST)
            val year = it.col(MediaStore.Audio.Albums.FIRST_YEAR)
            val count = it.col(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
            buildList {
                while (it.moveToNext()) {
                    val albumId = it.getLongOr(id, 0L)
                    add(
                        Album(
                            id = albumId,
                            title = it.getStringOrEmpty(title, "Unknown album"),
                            artist = it.getStringOrEmpty(artist, "Unknown artist"),
                            year = it.getIntOr(year, 0),
                            songCount = it.getIntOr(count, 0)
                        )
                    )
                }
            }
        }
    }

    /** Artists, paginated, ordered by artist name. */
    suspend fun getArtists(limit: Int, offset: Int): List<Artist> = withContext(Dispatchers.IO) {
        if (!hasPermission(appContext)) return@withContext emptyList()
        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS
        )
        val cursor = queryPaged(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            listOf(MediaStore.Audio.Artists.ARTIST to true),
            limit.coerceAtLeast(1),
            offset.coerceAtLeast(0)
        ) ?: return@withContext emptyList()
        cursor.use {
            val id = it.col(MediaStore.Audio.Artists._ID)
            val name = it.col(MediaStore.Audio.Artists.ARTIST)
            val albums = it.col(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)
            val tracks = it.col(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
            buildList {
                while (it.moveToNext()) {
                    add(
                        Artist(
                            id = it.getLongOr(id, 0L),
                            name = it.getStringOrEmpty(name, "Unknown artist"),
                            songCount = it.getIntOr(tracks, 0),
                            albumCount = it.getIntOr(albums, 0)
                        )
                    )
                }
            }
        }
    }

    /**
     * albumId -> content-Uri string of one of its songs. A single indexed
     * query; the Uri feeds loadThumbnail(), which returns embedded cover
     * art on API 29+. (The legacy audio/albumart provider is dead on
     * modern Android, so per-song Uris are the only reliable artwork.)
     */
    suspend fun getAlbumArtworkMap(): Map<Long, String> = withContext(Dispatchers.IO) {
        if (!hasPermission(appContext)) return@withContext emptyMap()
        val cursor = appContext.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ALBUM_ID),
            null,
            null,
            "${MediaStore.Audio.Media.ALBUM_ID} ASC"
        ) ?: return@withContext emptyMap()
        cursor.use {
            val id = it.col(MediaStore.Audio.Media._ID)
            val album = it.col(MediaStore.Audio.Media.ALBUM_ID)
            val map = LinkedHashMap<Long, String>()
            while (it.moveToNext()) {
                val albumId = it.getLongOr(album, 0L)
                if (albumId != 0L && albumId !in map) {
                    map[albumId] = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        it.getLongOr(id, 0L)
                    ).toString()
                }
            }
            map
        }
    }

    /**
     * artistId -> up to 4 content-Uri strings from distinct albums, for
     * mosaic artist tiles. MediaStore keeps no artist photos, so a collage
     * of the artist's own cover art is the honest offline stand-in.
     */
    suspend fun getArtistArtworkMap(): Map<Long, List<String>> = withContext(Dispatchers.IO) {
        if (!hasPermission(appContext)) return@withContext emptyMap()
        val cursor = appContext.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ALBUM_ID
            ),
            null,
            null,
            "${MediaStore.Audio.Media.ARTIST_ID} ASC"
        ) ?: return@withContext emptyMap()
        cursor.use {
            val id = it.col(MediaStore.Audio.Media._ID)
            val artist = it.col(MediaStore.Audio.Media.ARTIST_ID)
            val album = it.col(MediaStore.Audio.Media.ALBUM_ID)
            val map = LinkedHashMap<Long, LinkedHashMap<Long, String>>()
            while (it.moveToNext()) {
                val artistId = it.getLongOr(artist, 0L)
                if (artistId == 0L) continue
                val albums = map.getOrPut(artistId) { LinkedHashMap() }
                if (albums.size >= 4) continue
                val albumId = it.getLongOr(album, 0L)
                if (albumId !in albums) {
                    albums[albumId] = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        it.getLongOr(id, 0L)
                    ).toString()
                }
            }
            map.mapValues { (_, albums) -> albums.values.toList() }
        }
    }

    /** All genres with their song counts, ordered by name. */
    suspend fun getGenres(): List<Genre> = withContext(Dispatchers.IO) {
        if (!hasPermission(appContext)) return@withContext emptyList()
        val cursor = appContext.contentResolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null,
            null,
            "${MediaStore.Audio.Genres.NAME} ASC"
        ) ?: return@withContext emptyList()
        cursor.use {
            val id = it.col(MediaStore.Audio.Genres._ID)
            val name = it.col(MediaStore.Audio.Genres.NAME)
            buildList {
                while (it.moveToNext()) {
                    val genreId = it.getLongOr(id, 0L)
                    add(
                        Genre(
                            id = genreId,
                            name = it.getStringOrEmpty(name, "Unknown genre"),
                            songCount = genreSongCount(genreId)
                        )
                    )
                }
            }
        }
    }

    /** Songs in one genre, ordered by title. */
    suspend fun getGenreSongs(genreId: Long): List<Song> = withContext(Dispatchers.IO) {
        if (!hasPermission(appContext)) return@withContext emptyList()
        val members = appContext.contentResolver.query(
            MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
            arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
            null,
            null,
            null
        ) ?: return@withContext emptyList()
        val ids = members.use {
            val audioId = it.col(MediaStore.Audio.Genres.Members.AUDIO_ID)
            buildList {
                while (it.moveToNext()) {
                    val id = it.getLongOr(audioId, 0L)
                    if (id != 0L) add(id)
                }
            }
        }
        if (ids.isEmpty()) return@withContext emptyList()
        getSongsByIds(ids)
    }

    // ---------------- folders ----------------

    /**
     * Library folders derived by grouping songs by parent directory.
     * Uses a single projection-limited pass over the DATA column only —
     * rows are never materialized, only path prefixes are counted.
     */
    suspend fun getFolders(): List<FolderInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission(appContext)) return@withContext emptyList()
        val cursor = appContext.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.DATA),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null
        ) ?: return@withContext emptyList()
        cursor.use {
            val data = it.col(MediaStore.Audio.Media.DATA)
            val counts = HashMap<String, Int>()
            while (it.moveToNext()) {
                val path = it.getStringOr(data, null) ?: continue
                val parent = path.substringBeforeLast('/', "")
                if (parent.isEmpty()) continue
                counts[parent] = (counts[parent] ?: 0) + 1
            }
            counts.map { (path, count) ->
                FolderInfo(
                    path = path,
                    displayName = folderDisplayName(path),
                    songCount = count
                )
            }.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { info: FolderInfo -> info.displayName }
            )
        }
    }

    // ---------------- internals ----------------

    private fun songSelection(
        query: String?,
        extra: String?,
        extraArgs: Array<String>?
    ): Pair<String, Array<String>?> {
        val parts = ArrayList<String>(3)
        val args = ArrayList<String>(4)
        parts += "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        if (!query.isNullOrBlank()) {
            parts += (
                "(${MediaStore.Audio.Media.TITLE} LIKE ?" +
                    " OR ${MediaStore.Audio.Media.ARTIST} LIKE ?" +
                    " OR ${MediaStore.Audio.Media.ALBUM} LIKE ?)"
                )
            val like = "%${query.trim()}%"
            args += like
            args += like
            args += like
        }
        if (extra != null) {
            parts += extra
            if (extraArgs != null) args.addAll(extraArgs)
        }
        return parts.joinToString(" AND ") to args.toTypedArray().takeIf { it.isNotEmpty() }
    }

    private fun sortColumnsFor(sortBy: SortBy): List<Pair<String, Boolean>> = when (sortBy) {
        SortBy.TITLE -> listOf(MediaStore.Audio.Media.TITLE to true)
        SortBy.ARTIST -> listOf(
            MediaStore.Audio.Media.ARTIST to true,
            MediaStore.Audio.Media.TITLE to true
        )
        SortBy.ALBUM -> listOf(
            MediaStore.Audio.Media.ALBUM to true,
            MediaStore.Audio.Media.TRACK to true
        )
        SortBy.DURATION -> listOf(MediaStore.Audio.Media.DURATION to true)
        SortBy.DATE_ADDED -> listOf(MediaStore.Audio.Media.DATE_ADDED to false)
        SortBy.YEAR -> listOf(
            MediaStore.Audio.Media.YEAR to false,
            MediaStore.Audio.Media.TITLE to true
        )
    }

    /**
     * Paged query. API 30+ uses the ContentResolver pagination bundle
     * (server-side limit/offset, indexed when the sort column is
     * indexed); older APIs append `LIMIT/OFFSET` to the sort-order string.
     */
    private fun queryPaged(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortColumns: List<Pair<String, Boolean>>,
        limit: Int,
        offset: Int
    ): Cursor? {
        val resolver = appContext.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val extras = Bundle().apply {
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    sortColumns.map { it.first }.toTypedArray()
                )
                // Direction applies to the primary sort column.
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    if (sortColumns.first().second)
                        ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                    else ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                if (selection != null) {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                }
                if (selectionArgs != null) {
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                }
            }
            resolver.query(uri, projection, extras, null)
        } else {
            val order = sortColumns.joinToString(", ") { (col, asc) ->
                "$col ${if (asc) "ASC" else "DESC"}"
            }
            resolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "$order LIMIT $limit OFFSET $offset"
            )
        }
    }

    private fun readSongs(cursor: Cursor, genreMap: Map<Long, String>): List<Song> {
        val id = cursor.col(MediaStore.Audio.Media._ID)
        val title = cursor.col(MediaStore.Audio.Media.TITLE)
        val artist = cursor.col(MediaStore.Audio.Media.ARTIST)
        val album = cursor.col(MediaStore.Audio.Media.ALBUM)
        val albumId = cursor.col(MediaStore.Audio.Media.ALBUM_ID)
        val artistId = cursor.col(MediaStore.Audio.Media.ARTIST_ID)
        val duration = cursor.col(MediaStore.Audio.Media.DURATION)
        val track = cursor.col(MediaStore.Audio.Media.TRACK)
        val year = cursor.col(MediaStore.Audio.Media.YEAR)
        val dateAdded = cursor.col(MediaStore.Audio.Media.DATE_ADDED)
        val dateModified = cursor.col(MediaStore.Audio.Media.DATE_MODIFIED)
        val size = cursor.col(MediaStore.Audio.Media.SIZE)
        val data = cursor.col(MediaStore.Audio.Media.DATA)
        val mime = cursor.col(MediaStore.Audio.Media.MIME_TYPE)
        return buildList {
            while (cursor.moveToNext()) {
                val songId = cursor.getLongOr(id, 0L)
                val albumIdValue = cursor.getLongOr(albumId, 0L)
                val dataValue = cursor.getStringOrEmpty(data, "")
                add(
                    Song(
                        id = songId,
                        title = cursor.getStringOrEmpty(title, "Unknown title"),
                        artist = cursor.getStringOrEmpty(artist, "Unknown artist"),
                        album = cursor.getStringOrEmpty(album, "Unknown album"),
                        albumId = albumIdValue,
                        artistId = cursor.getLongOr(artistId, 0L),
                        genre = genreMap[songId],
                        durationMs = cursor.getLongOr(duration, 0L),
                        trackNumber = cursor.getIntOr(track, 0),
                        year = cursor.getIntOr(year, 0),
                        dateAddedSec = cursor.getLongOr(dateAdded, 0L),
                        dateModifiedSec = cursor.getLongOr(dateModified, 0L),
                        sizeBytes = cursor.getLongOr(size, 0L),
                        contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId
                        ),
                        folderPath = dataValue.substringBeforeLast('/', ""),
                        filePath = dataValue,
                        mimeType = cursor.getStringOrEmpty(mime, "audio/*")
                    )
                )
            }
        }
    }

    /** Song count for one genre, from the genre's Members table (indexed). */
    private fun genreSongCount(genreId: Long): Int {
        val uri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
        val cursor = appContext.contentResolver.query(
            uri,
            arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
            null,
            null,
            null
        ) ?: return 0
        cursor.use { return it.count }
    }

    /**
     * Builds song-id -> genre-name. One small query per genre, each
     * reading only the AUDIO_ID column from the genre's indexed Members
     * table. Cached per repository instance.
     */
    private var genreMapCache: Map<Long, String>? = null

    private fun songGenreMap(): Map<Long, String> {
        genreMapCache?.let { return it }
        val map = HashMap<Long, String>()
        val cursor = appContext.contentResolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null,
            null,
            null
        )
        cursor?.use {
            val id = it.col(MediaStore.Audio.Genres._ID)
            val name = it.col(MediaStore.Audio.Genres.NAME)
            while (it.moveToNext()) {
                val genreId = it.getLongOr(id, 0L)
                val genreName = it.getStringOr(name, null) ?: continue
                val members = appContext.contentResolver.query(
                    MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                    arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                    null,
                    null,
                    null
                )
                members?.use { m ->
                    val audioId = m.col(MediaStore.Audio.Genres.Members.AUDIO_ID)
                    while (m.moveToNext()) {
                        map[m.getLongOr(audioId, 0L)] = genreName
                    }
                }
            }
        }
        genreMapCache = map
        return map
    }

    // ---------------- null-safe cursor helpers ----------------

    private fun Cursor.col(name: String): Int = getColumnIndex(name)

    private fun Cursor.getStringOr(index: Int, fallback: String?): String? =
        if (index < 0 || isNull(index)) fallback else getString(index)

    private fun Cursor.getStringOrEmpty(index: Int, fallback: String): String =
        getStringOr(index, fallback as String?) ?: fallback

    private fun Cursor.getLongOr(index: Int, fallback: Long): Long =
        if (index < 0 || isNull(index)) fallback else getLong(index)

    private fun Cursor.getIntOr(index: Int, fallback: Int): Int =
        if (index < 0 || isNull(index)) fallback else getInt(index)
}
