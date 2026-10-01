package com.tunegrab.app.library

import com.tunegrab.app.player.TitleCleaner

/**
 * Result of parsing messy download tags into a clean artist/title pair.
 * [confident] is false when there was nothing solid to split on — the
 * caller should skip the file rather than guess.
 */
data class ParsedMeta(
    val artist: String,
    val title: String,
    val confident: Boolean,
)

/**
 * Turns yt-dlp-style tags into clean artist/title pairs. Downloads land
 * with the video title as the title tag and the uploader as the artist
 * tag (e.g. "Olivia Rodrigo - deja vu (Official Video)" / "OliviaRodrigoVEVO"),
 * which looks inconsistent next to properly tagged music in the library.
 * Pure — unit-tested.
 */
object SongTagParser {

    /**
     * Parses [rawTitle] (the title tag) with [artistTag] and [uploader] as
     * artist fallbacks. Suffix junk is stripped first (via [TitleCleaner]),
     * then "Artist - Title" is split on " - ". Without a split, the artist
     * comes from the cleaned uploader, else the artist tag.
     */
    fun parse(rawTitle: String, artistTag: String?, uploader: String?): ParsedMeta {
        val cleaned = TitleCleaner.strip(rawTitle).collapseWhitespace()
        val dash = cleaned.indexOf(" - ")
        if (dash > 0) {
            val artist = cleaned.substring(0, dash).collapseWhitespace()
            val title = cleaned.substring(dash + 3).collapseWhitespace()
            return ParsedMeta(artist, title, artist.isNotEmpty() && title.isNotEmpty())
        }
        val artist = cleanUploader(uploader)
            ?: artistTag?.collapseWhitespace().orEmpty()
        return ParsedMeta(artist, cleaned, artist.isNotEmpty() && cleaned.isNotEmpty())
    }

    /**
     * "Olivia Rodrigo - Topic" / "OliviaRodrigoVEVO" / "X Official" → "X".
     * Null when there is nothing usable.
     */
    fun cleanUploader(uploader: String?): String? {
        if (uploader.isNullOrBlank()) return null
        var u = uploader.collapseWhitespace()
        u = u.removeSuffix(" - Topic").trim()
        u = Regex("(vevo|official)\\s*$", RegexOption.IGNORE_CASE).replace(u, "").trim()
        u = u.removeSuffix("-").trim()
        return u.takeIf { it.isNotEmpty() }
    }

    private fun String.collapseWhitespace(): String =
        trim().replace(Regex("\\s+"), " ")
}
