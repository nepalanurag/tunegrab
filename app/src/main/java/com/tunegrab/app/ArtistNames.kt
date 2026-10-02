package com.tunegrab.app

/**
 * Splits a combined artist string ("A, B & C", "A feat. B") into the
 * individual artist names, so the user can pick one to search.
 *
 * Returns a single-element list when no separator is found. Blank parts
 * are dropped, so "A, " still yields just ["A"]. Known band names that
 * contain separators ("AC/DC", "Simon & Garfunkel") are never split.
 */
/**
 * Band names that contain separator-looking characters but must never be
 * split (AC/DC, Simon & Garfunkel, ...). Compared case-insensitively
 * against the whole tag before any splitting.
 */
private val UNSPLITTABLE_ARTISTS = setOf(
    "ac/dc",
    "simon & garfunkel",
    "earth, wind & fire",
    "hall & oates",
    "daryl hall & john oates",
    "brooks & dunn",
    "tegan and sara",
    "peter, paul and mary",
)

fun splitArtists(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    if (raw.trim().lowercase() in UNSPLITTABLE_ARTISTS) return listOf(raw.trim())
    // Ordered longest-first so " feat. " wins over a bare space split;
    // matching is case-insensitive ("Feat.", "FT.", ...).
    val separators = listOf(
        ",",
        ";",
        " / ", // spaced slash only: "AC/DC" is one band
        " feat. ",
        " ft. ",
        " featuring ",
        " with ",
        " x ",
        " \u00d7 ", // multiplication sign, used for collabs ("A \u00d7 B")
        " & ",
        " and ",
    )
    var parts = listOf(raw)
    for (sep in separators) {
        parts = parts.flatMap { part ->
            part.split(sep, ignoreCase = true)
        }
    }
    return parts.map { it.trim() }.filter { it.isNotEmpty() }
}

/** One row of the library's artist index, built from split credits. */
data class SplitArtistEntry(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

/**
 * True for blank artist tags and the placeholder values taggers write
 * when the artist is missing: "<unknown>", "unknown", "unknown artist"
 * (any case). All of these mean the same bucket: songs with no known
 * artist. Pure and unit-tested.
 */
fun isUnknownArtist(name: String): Boolean {
    val n = name.trim().lowercase()
    return n.isEmpty() || n == "<unknown>" || n == "unknown" || n == "unknown artist"
}

/**
 * Builds the library artist index from (artist tag, album title) pairs.
 * Each tag is split into individual artists; every artist credited on a
 * song gets that song (and its album) counted, so a "A, B, C" track
 * appears under A, B, and C separately. Pure and unit-tested.
 */
fun aggregateArtists(entries: List<Pair<String, String>>): List<SplitArtistEntry> {
    val display = LinkedHashMap<String, String>()
    val songCounts = HashMap<String, Int>()
    val albumSets = HashMap<String, MutableSet<String>>()
    for ((tag, album) in entries) {
        val names = splitArtists(tag.ifBlank { "Unknown artist" })
        for (name in names) {
            val key = name.lowercase()
            display.getOrPut(key) { name }
            songCounts[key] = (songCounts[key] ?: 0) + 1
            if (album.isNotBlank()) {
                albumSets.getOrPut(key) { HashSet() }.add(album.lowercase())
            }
        }
    }
    return display.map { (key, name) ->
        SplitArtistEntry(
            name = name,
            songCount = songCounts[key] ?: 0,
            albumCount = albumSets[key]?.size ?: 0,
        )
    }.sortedBy { it.name.lowercase() }
}
