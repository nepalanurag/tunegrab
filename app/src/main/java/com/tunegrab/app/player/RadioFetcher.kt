package com.tunegrab.app.player

import com.tunegrab.app.ui.library.SongUi

/**
 * Queue length (after the current track) at or below which Radio tops up
 * the queue with more similar tracks.
 */
const val RADIO_REFILL_THRESHOLD = 4

/**
 * Flavor seam for Radio similarity lookups. The direct (website/Ko-fi)
 * build implements this with yt-dlp YouTube Music searches; the Play Store
 * build has no implementation — Radio is a direct-build feature and the
 * Play binary contains no online music code.
 */
data class RadioSeedQuery(
    val title: String,
    val artist: String,
    val videoId: String?,
)

data class SimilarTrack(
    val id: String,
    val title: String,
    val uploader: String?,
    val durationSec: Long?,
)

interface RadioFetcher {
    fun seedOf(song: SongUi): RadioSeedQuery
    fun titleKey(title: String): String
    suspend fun fetchSimilar(
        seeds: List<RadioSeedQuery>,
        excludeIds: Set<String>,
        excludeTitles: Set<String>,
    ): List<SimilarTrack>
}
