package com.tunegrab.app.player

/**
 * Normalized song identity for duplicate detection: the same song saved
 * from two different uploads (or typed slightly differently) still
 * collides. Pure — unit-tested.
 */
object SongKey {

    /**
     * Normalized artist: lowercase, YouTube channel suffixes (" - Topic",
     * trailing "VEVO" / "Official") stripped, only letters/digits/spaces
     * kept, whitespace collapsed.
     */
    fun artistKey(artist: String): String {
        var a = artist.lowercase().trim()
        a = a.removeSuffix(" - topic").trim()
        a = Regex("(vevo|official)\\s*$").replace(a, "").trim()
        a = a.removeSuffix("-").trim()
        return a.filter { it.isLetterOrDigit() || it == ' ' }
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Normalized title: lowercase, version words ("lyrics", "official
     * video", ...) stripped via [TitleCleaner], only letters/digits kept.
     * Shared with Radio's exclude-title matching.
     */
    fun titleKey(title: String): String {
        val stripped = TitleCleaner.strip(title.lowercase())
        return stripped.filter { it.isLetterOrDigit() }
    }

    /**
     * Combined title+artist key, or null when either side is blank —
     * title-only matching false-positives too easily ("Hello" is not one
     * song), so the pair is required.
     */
    fun of(title: String, artist: String): String? {
        val a = artistKey(artist)
        val t = titleKey(title)
        if (a.isEmpty() || t.isEmpty()) return null
        return "$a|$t"
    }
}
