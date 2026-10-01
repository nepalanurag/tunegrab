package com.tunegrab.app.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections

/** One synced lyric line. */
data class LyricLine(val timeMs: Long, val text: String)

/** Lyrics for a track: either synced (LRC) lines or plain text. */
data class SongLyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val plainText: String? = null,
)

/**
 * Fetches lyrics from the LRCLIB public API (no key needed).
 * Results are cached in memory per artist+title. Call off the main thread.
 */
object LyricsRepository {

    /**
     * Small in-memory cache keyed by "artist|title". Pure-JVM LinkedHashMap
     * LRU (instead of android.util.LruCache) so the parsing helpers stay
     * unit-testable off-device. Only non-null results are cached — a miss
     * is simply re-fetched next time.
     */
    private val cache: MutableMap<String, SongLyrics> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, SongLyrics>(64, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, SongLyrics>?,
                ): Boolean = size > 64
            },
        )

    suspend fun get(artist: String, title: String): SongLyrics? {
        val key = "${artist.lowercase()}|${title.lowercase()}"
        cache[key]?.let { return it }
        val found = withContext(Dispatchers.IO) { fetch(artist, title) }
        if (found != null) cache[key] = found
        return found
    }

    private fun fetch(artist: String, title: String): SongLyrics? {
        val q = buildString {
            if (artist.isNotBlank() && !artist.equals("<unknown>", ignoreCase = true)) {
                append(artist).append(' ')
            }
            append(cleanTitle(title))
        }.trim()
        if (q.isBlank()) return null
        val url = "https://lrclib.net/api/search?q=" + URLEncoder.encode(q, Charsets.UTF_8.name())
        val body = runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "TuneGrab/1.0")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return parseSearchResults(body)
    }

    /**
     * Picks the best entry from an LRCLIB `/api/search` response:
     * prefers synced lyrics, falls back to plain lyrics.
     */
    fun parseSearchResults(body: String): SongLyrics? {
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return null
        var plainFallback: SongLyrics? = null
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() }
            if (synced != null) {
                val lines = parseLrc(synced)
                if (lines.isNotEmpty()) return SongLyrics(lines, synced = true)
            }
            if (plainFallback == null) {
                val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() }
                if (plain != null) plainFallback = SongLyrics(emptyList(), synced = false, plainText = plain)
            }
        }
        return plainFallback
    }

    private val lrcTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")

    /**
     * Parses LRC text into timestamped lines. Supports multiple timestamps
     * on one line and centisecond/millisecond fractions.
     */
    fun parseLrc(lrc: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val matches = lrcTag.findAll(line).toList()
            if (matches.isEmpty()) continue
            val text = line.substringAfterLast("]").trim()
            if (text.isEmpty()) continue
            for (m in matches) {
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val fracMs = when (frac.length) {
                    1 -> frac.toLong() * 100 // tenths
                    2 -> frac.toLong() * 10 // centiseconds
                    3 -> frac.toLong() // milliseconds
                    else -> 0L
                }
                out += LyricLine(minutes * 60_000 + seconds * 1_000 + fracMs, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /**
     * Strips YouTube-style suffixes ("(Official Video)", "[Lyrics]", " - Topic"…)
     * so lyric search matches the actual song. Pure and unit-testable.
     */
    fun cleanTitle(title: String): String {
        var t = title
        t = t.replace(Regex("""\s*-\s*Topic\s*$""", RegexOption.IGNORE_CASE), "")
        // Remove trailing bracketed/parenthesized qualifiers that describe the
        // upload rather than the song (official video, lyric video, audio…).
        // Keep musical qualifiers like "(feat. X)" or "(Remastered)".
        val uploadWords = listOf(
            "official video", "official music video", "official audio",
            "lyric video", "lyrics", "audio", "m/v", "mv", "topic",
        )
        while (true) {
            val m = Regex("""\s*[\[(]([^)\]]+)[)\]]\s*$""").find(t) ?: break
            val inner = m.groupValues[1].lowercase()
            if (uploadWords.any { inner.contains(it) }) t = t.removeSuffix(m.value)
            else break
        }
        return t.trim().ifEmpty { title.trim() }
    }
}
