package com.tunegrab.app

/** How downloaded files are named. */
enum class FilenameStyle(val title: String, val example: String) {
    TITLE_ONLY("Song title", "Somewhere Only We Know"),
    NUMBERED("Number + title", "01 Somewhere Only We Know"),
    ARTIST_TITLE("Artist – title", "Keane – Somewhere Only We Know"),
    DETAILED(
        "Artist – title – album (year)",
        "Keane – Somewhere Only We Know – Hopes and Fears (2004)"
    );
}

/** Container/codec yt-dlp converts the audio to. */
enum class AudioFormat(
    val title: String,
    val summary: String,
    val ytdlpName: String,
    val mimeType: String
) {
    FLAC("FLAC", "Lossless · biggest files", "flac", "audio/flac"),
    MP3("MP3", "Plays everywhere", "mp3", "audio/mpeg"),
    OPUS("Opus", "Best quality per megabyte", "opus", "audio/opus"),
    M4A("M4A", "Apple-friendly AAC", "m4a", "audio/mp4");
}

/** Target quality for lossy formats. FLAC is lossless, so this is ignored for it. */
enum class AudioQuality(val title: String, val summary: String, val ytdlpValue: String) {
    BEST("Best", "Highest quality available", "0"),
    HIGH("High", "≈ 320 kbps", "320K"),
    MEDIUM("Medium", "≈ 192 kbps · smaller files", "192K"),
    LOW("Compact", "≈ 128 kbps · smallest files", "128K");
}

/**
 * Pure freemium business rules — no Android dependencies, so this file is
 * covered by JVM unit tests ([FreemiumRulesTest]). [AppSettings] is the
 * Android-bound facade that persists choices and exposes them as flows;
 * every gating decision delegates here.
 *
 * Store model (no ads): the free tier is a music player for the on-device
 * library. Downloads, Radio, and the sleep timer are Pro-only.
 */
object FreemiumRules {

    /** Downloads are a Pro feature; there is no free/ad-supported tier. */
    fun canDownload(isPro: Boolean): Boolean = isPro

    /** Radio (endless similar-track queue) is a Pro feature. */
    fun canUseRadio(isPro: Boolean): Boolean = isPro

    /** The sleep timer is a Pro feature. */
    fun canUseSleepTimer(isPro: Boolean): Boolean = isPro

    /** Free users are clamped to MP3 no matter what they picked. */
    fun effectiveFormat(isPro: Boolean, chosen: AudioFormat): AudioFormat =
        if (isPro || chosen == AudioFormat.MP3) chosen else AudioFormat.MP3

    /** Free users always get 192 kbps for lossy formats. */
    fun effectiveQualityArgs(
        isPro: Boolean,
        format: AudioFormat,
        quality: AudioQuality
    ): List<Pair<String, String>> =
        if (isPro) {
            if (format == AudioFormat.FLAC) emptyList()
            else listOf("--audio-quality" to quality.ytdlpValue)
        } else {
            listOf("--audio-quality" to AudioQuality.MEDIUM.ytdlpValue)
        }

    /** Free users always get title-only filenames. */
    fun effectiveTemplate(
        isPro: Boolean,
        style: FilenameStyle,
        isCollection: Boolean
    ): String =
        if (isPro) outputTemplate(style, isCollection) else "%(title)s.%(ext)s"

    fun isFormatAllowed(isPro: Boolean, f: AudioFormat) =
        isPro || f == AudioFormat.MP3

    fun isQualityAllowed(isPro: Boolean, q: AudioQuality) =
        isPro || q == AudioQuality.MEDIUM

    fun isFilenameAllowed(isPro: Boolean, s: FilenameStyle) =
        isPro || s == FilenameStyle.TITLE_ONLY

    /**
     * Output template honoring the filename style. Numbered style only
     * prefixes the track number for real collections. Detailed style falls
     * back to the uploader for a missing artist and to "Single" for a
     * missing album; a missing year leaves " ()", which [DownloadService]
     * strips before publishing.
     */
    fun outputTemplate(style: FilenameStyle, isCollection: Boolean): String =
        when (style) {
            FilenameStyle.TITLE_ONLY -> "%(title)s.%(ext)s"
            FilenameStyle.NUMBERED ->
                if (isCollection) "%(playlist_index)02d %(title)s.%(ext)s"
                else "%(title)s.%(ext)s"
            FilenameStyle.ARTIST_TITLE -> "%(artist,uploader)s - %(title)s.%(ext)s"
            FilenameStyle.DETAILED ->
                "%(artist,uploader)s - %(title)s - %(album|Single)s (%(release_year|)s).%(ext)s"
        }
}
