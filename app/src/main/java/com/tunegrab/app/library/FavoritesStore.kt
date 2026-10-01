package com.tunegrab.app.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A hearted streaming track, with the metadata needed to list and play it. */
data class RemoteFavorite(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkKey: String?,
)

/**
 * Liked songs, backing the heart button in the player. Local songs are
 * stored as a SharedPreferences string set of MediaStore song ids;
 * streaming tracks are stored separately by YouTube video id so they
 * survive without a local file. Streamed hearts also persist their
 * title/artist/artwork so the Liked tab can list them.
 */
class FavoritesStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "tunegrab_favorites", Context.MODE_PRIVATE
    )

    private val _favorites = MutableStateFlow(load())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _remoteFavorites = MutableStateFlow(loadRemote())
    val remoteFavorites: StateFlow<Set<String>> = _remoteFavorites.asStateFlow()

    private val _remoteFavoriteTracks = MutableStateFlow(loadRemoteMeta())
    val remoteFavoriteTracks: StateFlow<List<RemoteFavorite>> =
        _remoteFavoriteTracks.asStateFlow()

    fun isFavorite(songId: Long): Boolean = songId in _favorites.value

    fun isRemoteFavorite(videoId: String): Boolean = videoId in _remoteFavorites.value

    /** Toggles; returns the new state (true = now a favorite). */
    suspend fun toggle(songId: Long): Boolean = withContext(Dispatchers.IO) {
        val current = _favorites.value
        val updated = if (songId in current) current - songId else current + songId
        _favorites.value = updated
        prefs.edit()
            .putStringSet(KEY_IDS, updated.map { it.toString() }.toSet())
            .apply()
        songId in updated
    }

    /**
     * Toggles a streaming track; returns the new state (true = now a
     * favorite). The track metadata is persisted so the Liked tab can
     * show the entry without a network lookup.
     */
    suspend fun toggleRemote(
        videoId: String,
        title: String = "",
        artist: String = "",
        artworkKey: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val current = _remoteFavorites.value
        val nowFavorite = videoId !in current
        val updated = if (nowFavorite) current + videoId else current - videoId
        _remoteFavorites.value = updated
        val meta = _remoteFavoriteTracks.value.toMutableList()
        if (nowFavorite) {
            meta.removeAll { it.videoId == videoId }
            meta.add(RemoteFavorite(videoId, title, artist, artworkKey))
        } else {
            meta.removeAll { it.videoId == videoId }
        }
        _remoteFavoriteTracks.value = meta.toList()
        prefs.edit()
            .putStringSet(KEY_REMOTE_IDS, updated)
            .putString(KEY_REMOTE_META, encodeRemoteMeta(meta))
            .apply()
        nowFavorite
    }

    private fun load(): Set<Long> =
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }.toSet()

    private fun loadRemote(): Set<String> =
        prefs.getStringSet(KEY_REMOTE_IDS, emptySet()).orEmpty().toSet()

    private fun loadRemoteMeta(): List<RemoteFavorite> =
        decodeRemoteMeta(prefs.getString(KEY_REMOTE_META, null))

    companion object {
        private const val KEY_IDS = "favorite_ids"
        private const val KEY_REMOTE_IDS = "favorite_remote_ids"
        private const val KEY_REMOTE_META = "favorite_remote_meta"

        /** Pure JSON codec for streamed-heart metadata; unit tested. */
        fun encodeRemoteMeta(favorites: List<RemoteFavorite>): String {
            val array = org.json.JSONArray()
            favorites.forEach {
                array.put(
                    JSONObject()
                        .put("id", it.videoId)
                        .put("title", it.title)
                        .put("artist", it.artist)
                        .put("artwork", it.artworkKey ?: JSONObject.NULL),
                )
            }
            return array.toString()
        }

        fun decodeRemoteMeta(json: String?): List<RemoteFavorite> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val trimmed = json.trim()
                if (trimmed.startsWith("[")) {
                    // Current format: ordered array of entries.
                    val array = org.json.JSONArray(trimmed)
                    (0 until array.length()).map { i ->
                        val o = array.getJSONObject(i)
                        RemoteFavorite(
                            videoId = o.optString("id", ""),
                            title = o.optString("title", ""),
                            artist = o.optString("artist", ""),
                            artworkKey = o.optString("artwork", null),
                        )
                    }.filter { it.videoId.isNotBlank() }
                } else {
                    // Legacy format: object keyed by video id (unordered).
                    val root = JSONObject(trimmed)
                    root.keys().asSequence().map { videoId ->
                        val o = root.getJSONObject(videoId)
                        RemoteFavorite(
                            videoId = videoId,
                            title = o.optString("title", ""),
                            artist = o.optString("artist", ""),
                            artworkKey = o.optString("artwork", null),
                        )
                    }.toList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
