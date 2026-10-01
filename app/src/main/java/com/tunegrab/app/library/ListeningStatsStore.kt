package com.tunegrab.app.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One track's listening stats. Key is the queueKey ("yt:<videoId>" or MediaStore id). */
data class PlayStat(
    val key: String,
    val title: String,
    val artist: String,
    val artworkKey: String?,
    val playCount: Int,
    val lastPlayedMs: Long,
)

/** Aggregated stats for one artist. */
data class ArtistStat(
    val artist: String,
    val playCount: Int,
    val artworkKey: String?,
)

/**
 * Listening statistics: play counts and recency per track, persisted as
 * JSON in SharedPreferences. A "play" is recorded after 30 seconds of
 * continuous playback (or when the track ends); every track change updates
 * the recency timestamp so Recently Played stays fresh.
 */
class ListeningStatsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "tunegrab_stats", Context.MODE_PRIVATE
    )

    private val _stats = MutableStateFlow(load())
    val stats: StateFlow<List<PlayStat>> = _stats.asStateFlow()

    /** Counts a full play: increments the counter and stamps recency. */
    suspend fun recordPlay(
        key: String,
        title: String,
        artist: String,
        artworkKey: String?,
    ) = withContext(Dispatchers.IO) {
        mutate(key) { existing ->
            val base = existing ?: PlayStat(key, title, artist, artworkKey, 0, 0L)
            base.copy(
                title = title.ifBlank { base.title },
                artist = artist.ifBlank { base.artist },
                artworkKey = artworkKey ?: base.artworkKey,
                playCount = base.playCount + 1,
                lastPlayedMs = System.currentTimeMillis(),
            )
        }
    }

    /** Stamps recency without counting a play (track skipped quickly). */
    suspend fun recordRecent(
        key: String,
        title: String,
        artist: String,
        artworkKey: String?,
    ) = withContext(Dispatchers.IO) {
        mutate(key) { existing ->
            val base = existing ?: PlayStat(key, title, artist, artworkKey, 0, 0L)
            base.copy(
                title = title.ifBlank { base.title },
                artist = artist.ifBlank { base.artist },
                artworkKey = artworkKey ?: base.artworkKey,
                lastPlayedMs = System.currentTimeMillis(),
            )
        }
    }

    fun topSongs(limit: Int = 20): List<PlayStat> =
        ListeningStats.topSongs(_stats.value, limit)

    fun topArtists(limit: Int = 10): List<ArtistStat> =
        ListeningStats.topArtists(_stats.value, limit)

    fun recentlyPlayed(limit: Int = 20): List<PlayStat> =
        ListeningStats.recentlyPlayed(_stats.value, limit)

    private fun mutate(key: String, transform: (PlayStat?) -> PlayStat) {
        val map = _stats.value.associateBy { it.key }.toMutableMap()
        map[key] = transform(map[key])
        val updated = map.values.toList()
        _stats.value = updated
        save(updated)
    }

    private fun load(): List<PlayStat> {
        val raw = prefs.getString(KEY_STATS, null) ?: return emptyList()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().mapNotNull { key ->
                val o = obj.optJSONObject(key) ?: return@mapNotNull null
                PlayStat(
                    key = key,
                    title = o.optString("t"),
                    artist = o.optString("a"),
                    artworkKey = o.optString("w").ifEmpty { null },
                    playCount = o.optInt("c"),
                    lastPlayedMs = o.optLong("l"),
                )
            }.toList()
        }.getOrElse { emptyList() }
    }

    private fun save(stats: List<PlayStat>) {
        val obj = JSONObject()
        // Cap stored history so the prefs blob stays small.
        stats.sortedByDescending { it.lastPlayedMs }.take(MAX_STORED).forEach { s ->
            obj.put(s.key, JSONObject()
                .put("t", s.title)
                .put("a", s.artist)
                .put("w", s.artworkKey.orEmpty())
                .put("c", s.playCount)
                .put("l", s.lastPlayedMs))
        }
        prefs.edit().putString(KEY_STATS, obj.toString()).apply()
    }

    companion object {
        private const val KEY_STATS = "play_stats"
        private const val MAX_STORED = 500
        /** Seconds of continuous playback before a play is counted. */
        const val PLAY_COUNT_THRESHOLD_SEC = 30L
    }
}
