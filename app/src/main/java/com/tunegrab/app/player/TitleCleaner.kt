package com.tunegrab.app.player

/**
 * Shared trailing-junk stripper for YouTube-style titles. Moved out of
 * [RadioEngine] so the metadata cleanup pass ([com.tunegrab.app.library.SongTagParser])
 * reuses the exact same rules. Pure — behavior covered by RadioEngineTest.
 */
object TitleCleaner {

    private val TITLE_SUFFIX_STRIPPER: java.util.regex.Pattern =
        java.util.regex.Pattern.compile(
            "(\\s*[\\(\\[][^\\(\\)\\[\\]]*[\\)\\]]\\s*$)" + // trailing ( ... ) or [ ... ]
                "|(\\s*-\\s*topic\\s*$)" +                 // " - Topic"
                "|(\\s*official\\s+(music\\s+)?video\\s*$)" +
                "|(\\s*official\\s+audio\\s*$)" +
                "|(\\s*lyric\\s*video\\s*$)" +
                "|(\\s*lyrics?\\s*$)" +
                "|(\\s*audio\\s*$)" +
                "|(\\s*m/?v\\s*$)" +
                "|(\\s*-?\\s*(symphonic|acoustic|live|unplugged|remaster(ed)?)\\s+(version|edit(ion)?)\\s*$)" +
                "|(\\s*-?\\s*(sped\\s*-?\\s*up|slowed(\\s*\\+?\\s*reverb(ed)?)?)\\s*$)" +
                "|(\\s*-?\\s*cover\\s*$)" +
                "|(\\s*-?\\s*\\d{1,4}\\s*[kp]\\s*(remaster(ed)?|version)?\\s*$)" + // trailing "4K Remaster", "1080p"
                "|(\\s*-?\\s*remaster(ed)?\\s*$)" +
                "|(\\s*-?\\s*instrumental\\s*$)",
            java.util.regex.Pattern.CASE_INSENSITIVE,
        )

    /**
     * Repeatedly strips trailing "(...)" / "[...]" / " - Topic" /
     * official-video-audio-lyric suffixes until nothing more matches.
     * Case is preserved; lowercase first if you need a normalized key.
     */
    fun strip(title: String): String {
        var t = title
        var prev: String
        do {
            prev = t
            t = TITLE_SUFFIX_STRIPPER.matcher(t).replaceAll("").trim()
        } while (t != prev)
        return t
    }
}
