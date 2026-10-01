package com.tunegrab.app.library

/**
 * Pure listening-stats queries, kept free of Android so they are
 * unit-testable on the JVM.
 */
object ListeningStats {

    /** Most-played tracks, most plays first (recency breaks ties). */
    fun topSongs(stats: List<PlayStat>, limit: Int = 20): List<PlayStat> =
        stats.filter { it.playCount > 0 }
            .sortedWith(
                compareByDescending<PlayStat> { it.playCount }
                    .thenByDescending { it.lastPlayedMs }
            )
            .take(limit)

    /** Artists ranked by total plays across their tracks. */
    fun topArtists(stats: List<PlayStat>, limit: Int = 10): List<ArtistStat> =
        stats.filter { it.playCount > 0 && it.artist.isNotBlank() }
            .groupBy { it.artist }
            .map { (artist, plays) ->
                ArtistStat(
                    artist = artist,
                    playCount = plays.sumOf { it.playCount },
                    artworkKey = plays.maxByOrNull { it.playCount }?.artworkKey,
                )
            }
            .sortedWith(
                compareByDescending<ArtistStat> { it.playCount }
                    .thenBy { it.artist }
            )
            .take(limit)

    /** Tracks ordered by most recent play. */
    fun recentlyPlayed(stats: List<PlayStat>, limit: Int = 20): List<PlayStat> =
        stats.filter { it.lastPlayedMs > 0 }
            .sortedByDescending { it.lastPlayedMs }
            .take(limit)
}
