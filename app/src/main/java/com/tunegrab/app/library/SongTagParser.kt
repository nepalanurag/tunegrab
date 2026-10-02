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
 * Result of the hard-rule tag fixer: the title/artist the file should
 * carry. See [SongTagParser.proposeFix].
 */
data class TagFix(
    val title: String,
    val artist: String,
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
     * Decides whether a file's tags should be touched, under hard rules
     * born from a real data-loss incident (cleanup once replaced a good
     * artist with the song title):
     *
     * - A non-empty, non-garbled artist is sacred: it is never overwritten.
     * - A non-empty title is never blanked and never replaced with a
     *   guess; it only changes via suffix de-junking or an "Artist - Title"
     *   split whose artist side agrees with the known artist.
     * - A missing or clearly garbled artist ("", "<unknown>", "XVEVO",
     *   "X - Topic", "X Official") may be filled/repaired, but only from
     *   solid material — never blank, never an echo of the title.
     * - Anything uncertain returns null: the original tag stays untouched.
     *
     * Pure — unit-tested.
     */
    fun proposeFix(curTitle: String, curArtist: String): TagFix? {
        if (curTitle.isBlank()) return null
        val cleanTitle = TitleCleaner.strip(curTitle).collapseWhitespace()
        // Never blank the title.
        if (cleanTitle.isEmpty()) return null

        val artistNow = curArtist.collapseWhitespace()
        val artistMissing = artistNow.isEmpty() || isUnknownArtist(artistNow)
        // Channel junk ("X - Topic", "XVEVO", "X Official") is "clearly
        // garbled": de-junking it is repair, not guessing.
        val deJunked = if (artistMissing) null else cleanUploader(artistNow)
        val artistGarbled = !artistMissing && deJunked != null && deJunked != artistNow
        val artistGood = !artistMissing && !artistGarbled

        val dash = cleanTitle.indexOf(" - ")
        val left = if (dash > 0) cleanTitle.substring(0, dash).collapseWhitespace() else ""
        val right = if (dash > 0) cleanTitle.substring(dash + 3).collapseWhitespace() else ""
        val hasSplit = dash > 0 && left.isNotEmpty() && right.isNotEmpty()
        // A split whose "artist" is just the title again carries no
        // information — it must never touch the artist.
        val splitEchoesTitle = hasSplit && norm(left) == norm(right)

        val newArtist: String
        val newTitle: String
        if (artistGood) {
            // The artist is sacred: it never changes here.
            newArtist = artistNow
            newTitle = when {
                // Trust "Artist - Title" only when the artist side agrees
                // with the known artist; otherwise the split is a guess.
                hasSplit && !splitEchoesTitle && norm(left) == norm(artistNow) -> right
                cleanTitle != curTitle.collapseWhitespace() -> cleanTitle
                else -> return null
            }
        } else {
            // Artist missing or garbled: fill/repair, but only from solid
            // material — a corroborated split or plain de-junking.
            val splitUsable = hasSplit && !splitEchoesTitle &&
                (deJunked == null || norm(left) == norm(deJunked))
            newArtist = when {
                splitUsable -> left
                deJunked != null && deJunked.isNotEmpty() -> deJunked
                else -> return null
            }
            newTitle = when {
                splitUsable -> right
                cleanTitle != curTitle.collapseWhitespace() -> cleanTitle
                else -> cleanTitle
            }
        }
        if (newArtist.isEmpty() || newTitle.isEmpty()) return null
        // Never set the artist to the title.
        if (norm(newArtist) == norm(newTitle)) return null
        if (newArtist == artistNow &&
            newTitle.equals(curTitle.collapseWhitespace(), ignoreCase = true)
        ) {
            return null
        }
        return TagFix(title = newTitle, artist = newArtist)
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

    /**
     * "<unknown>" / "unknown" / "unknown artist" — the tag equivalent of
     * nothing. "Various Artists" is a real value and is NOT unknown.
     */
    private fun isUnknownArtist(artist: String): Boolean {
        val n = norm(artist)
        return n == "unknown" || n == "unknownartist"
    }

    /** Case/punctuation-insensitive comparison key. */
    private fun norm(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    private fun String.collapseWhitespace(): String =
        trim().replace(Regex("\\s+"), " ")
}
