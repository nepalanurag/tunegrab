package com.tunegrab.app.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One playlist entry: either a downloaded library song or a streaming
 * YouTube Music track. A remote entry flips to its downloaded file
 * ([Remote.localContentUri]) once saved — the playlist then plays offline.
 */
sealed interface PlaylistEntry {
    /** Stable identity for diffing and queue matching. */
    val key: String

    data class Local(val songId: Long) : PlaylistEntry {
        override val key: String get() = "local:$songId"
    }

    data class Remote(
        val videoId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String?,
        val durationSec: Long?,
        /** MediaStore content Uri string once downloaded; null while streaming-only. */
        val localContentUri: String? = null,
    ) : PlaylistEntry {
        override val key: String get() = "yt:$videoId"
        val isDownloaded: Boolean get() = localContentUri != null
    }
}

data class Playlist(
    val id: String,
    val name: String,
    val entries: List<PlaylistEntry>,
    val createdAtMs: Long,
) {
    /** MediaStore ids of the local entries (compat for older call sites). */
    val songIds: List<Long> get() = entries
        .filterIsInstance<PlaylistEntry.Local>()
        .map { it.songId }
}

class PlaylistStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "tunegrab_playlists", Context.MODE_PRIVATE
    )

    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    suspend fun createPlaylist(name: String): Playlist = withContext(Dispatchers.IO) {
        val trimmed = name.trim().ifEmpty { "Playlist" }
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            entries = emptyList(),
            createdAtMs = System.currentTimeMillis(),
        )
        update((_playlists.value + playlist))
        playlist
    }

    /** Adds a local song; returns false when it was already in the playlist. */
    suspend fun addToPlaylist(playlistId: String, songId: Long): Boolean =
        withContext(Dispatchers.IO) {
            addEntry(playlistId, PlaylistEntry.Local(songId))
        }

    /** Adds a streaming entry; returns false when that video is already in it. */
    suspend fun addRemoteToPlaylist(
        playlistId: String,
        videoId: String,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        durationSec: Long?,
    ): Boolean = withContext(Dispatchers.IO) {
        addEntry(
            playlistId,
            PlaylistEntry.Remote(videoId, title, artist, thumbnailUrl, durationSec)
        )
    }

    private fun addEntry(playlistId: String, entry: PlaylistEntry): Boolean {
        val (updated, added) = addEntryToPlaylist(_playlists.value, playlistId, entry)
        if (added) update(updated)
        return added
    }

    suspend fun removeFromPlaylist(playlistId: String, songId: Long) =
        withContext(Dispatchers.IO) {
            removeEntry(playlistId, PlaylistEntry.Local(songId).key)
        }

    suspend fun removeEntry(playlistId: String, entryKey: String) =
        withContext(Dispatchers.IO) {
            update(_playlists.value.map {
                if (it.id == playlistId) it.copy(entries = it.entries.filterNot { e -> e.key == entryKey })
                else it
            })
        }

    /**
     * Points a remote entry at its downloaded file so the playlist plays it
     * offline. Idempotent — repeated calls with the same Uri are no-ops.
     */
    suspend fun linkDownloaded(playlistId: String, videoId: String, contentUri: String) =
        withContext(Dispatchers.IO) {
            val updated = _playlists.value.map { p ->
                if (p.id != playlistId) return@map p
                p.copy(entries = linkEntryLocal(p.entries, videoId, contentUri))
            }
            // Only persist when something actually changed.
            if (updated != _playlists.value) update(updated)
        }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        update(_playlists.value.filterNot { it.id == playlistId })
    }

    suspend fun renamePlaylist(playlistId: String, name: String) =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@withContext
            update(_playlists.value.map {
                if (it.id == playlistId) it.copy(name = trimmed) else it
            })
        }

    private fun update(playlists: List<Playlist>) {
        _playlists.value = playlists.sortedBy { it.createdAtMs }
        prefs.edit().putString(KEY_JSON, serialize(_playlists.value)).apply()
    }

    private fun load(): List<Playlist> {
        val raw = prefs.getString(KEY_JSON, null) ?: return emptyList()
        return runCatching { deserialize(raw) }.getOrDefault(emptyList())
            .sortedBy { it.createdAtMs }
    }

    private fun serialize(playlists: List<Playlist>): String {
        val arr = JSONArray()
        for (p in playlists) {
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("createdAt", p.createdAtMs)
                put("entries", serializeEntries(p.entries))
            })
        }
        return arr.toString()
    }

    private fun deserialize(raw: String): List<Playlist> {
        val arr = JSONArray(raw)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            // Pre-unified format stored a bare "songs" id array — migrate to
            // Local entries so old playlists keep working.
            val entries = if (o.has("entries")) {
                deserializeEntries(o.getJSONArray("entries"))
            } else {
                val songs = o.optJSONArray("songs") ?: JSONArray()
                List(songs.length()) { j -> PlaylistEntry.Local(songs.getLong(j)) }
            }
            Playlist(
                id = o.getString("id"),
                name = o.getString("name"),
                entries = entries,
                createdAtMs = o.optLong("createdAt", 0L),
            )
        }
    }

    companion object {
        private const val KEY_JSON = "playlists_json"
    }
}

/** Serializes entries; pure so the format is unit-testable. */
internal fun serializeEntries(entries: List<PlaylistEntry>): JSONArray {
    val arr = JSONArray()
    for (e in entries) {
        when (e) {
            is PlaylistEntry.Local -> arr.put(JSONObject().apply {
                put("type", "local")
                put("songId", e.songId)
            })
            is PlaylistEntry.Remote -> arr.put(JSONObject().apply {
                put("type", "remote")
                put("videoId", e.videoId)
                put("title", e.title)
                put("artist", e.artist)
                put("thumbnailUrl", e.thumbnailUrl)
                e.durationSec?.let { put("durationSec", it) }
                e.localContentUri?.let { put("localContentUri", it) }
            })
        }
    }
    return arr
}

/** Deserializes entries; unknown shapes are skipped, never crash. */
internal fun deserializeEntries(arr: JSONArray): List<PlaylistEntry> {
    val out = mutableListOf<PlaylistEntry>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        when (o.optString("type")) {
            "local" -> {
                val songId = o.optLong("songId", -1L)
                if (songId < 0) continue
                out.add(PlaylistEntry.Local(songId))
            }
            "remote" -> {
                val videoId = o.optString("videoId")
                if (videoId.isBlank()) continue
                out.add(
                    PlaylistEntry.Remote(
                        videoId = videoId,
                        title = o.optString("title", "Unknown title"),
                        artist = o.optString("artist", ""),
                        thumbnailUrl = o.optString("thumbnailUrl").ifBlank { null },
                        durationSec = o.takeIf { it.has("durationSec") }
                            ?.optLong("durationSec")?.takeIf { it > 0 },
                        localContentUri = o.optString("localContentUri").ifBlank { null },
                    )
                )
            }
            // Unknown type: skip.
        }
    }
    return out
}

/**
 * Pure playlist-list manipulation (no Android dependencies, JVM-testable).
 *
 * Returns the updated list plus whether the entry was actually added —
 * false when the playlist doesn't exist or the entry is already in it.
 * Appends at the end so insertion order is preserved.
 */
internal fun addEntryToPlaylist(
    playlists: List<Playlist>,
    playlistId: String,
    entry: PlaylistEntry,
): Pair<List<Playlist>, Boolean> {
    val target = playlists.firstOrNull { it.id == playlistId }
        ?: return playlists to false
    if (target.entries.any { it.key == entry.key }) return playlists to false
    return playlists.map {
        if (it.id == playlistId) it.copy(entries = it.entries + entry) else it
    } to true
}

/** Pure: points the matching remote entry at its downloaded file. */
internal fun linkEntryLocal(
    entries: List<PlaylistEntry>,
    videoId: String,
    contentUri: String,
): List<PlaylistEntry> = entries.map { e ->
    if (e is PlaylistEntry.Remote && e.videoId == videoId && e.localContentUri != contentUri) {
        e.copy(localContentUri = contentUri)
    } else e
}

/**
 * Legacy pure helper kept for the older call sites/tests: adds a local
 * song id to a playlist. Delegates to [addEntryToPlaylist].
 */
internal fun addSongToPlaylist(
    playlists: List<Playlist>,
    playlistId: String,
    songId: Long,
): Pair<List<Playlist>, Boolean> =
    addEntryToPlaylist(playlists, playlistId, PlaylistEntry.Local(songId))
