package com.tunegrab.app

import com.tunegrab.app.player.RadioFetcher

/**
 * Flavor seam for everything download/online related. Main code talks only
 * to this interface; each flavor provides the `DirectBridge` object.
 *
 * - Play Store build: a plain local music player. Every answer is "no":
 *   no downloader, no stream resolution, no Radio, no Pro tier. The Play
 *   binary contains no yt-dlp code, no download service, no share target.
 * - Direct (website/Ko-fi) build: full feature set — downloads, YouTube
 *   search, Radio, sleep timer, share-sheet intake.
 */
interface DirectBridgeApi {
    /** True in the direct build; always false in the Play build. */
    val hasDownloader: Boolean

    /**
     * Start the download engines (yt-dlp, FFmpeg) and report readiness
     * through [YtFlacApp.setEngineState]. No-op in the Play build.
     */
    fun onAppCreate(app: YtFlacApp)

    /**
     * Resolve a YouTube video id to a playable audio stream URL.
     * Returns null in the Play build (remote tracks never exist there).
     */
    suspend fun resolveStreamUrl(videoId: String): String?

    /**
     * Resolve a YouTube video's channel name (without the " - Topic"
     * suffix). Returns null in the Play build.
     */
    suspend fun fetchUploader(videoId: String): String?

    /** Radio similarity fetcher, or null when Radio is unavailable. */
    fun radioFetcher(): RadioFetcher?

    /**
     * Re-verify Pro ownership. The Play build has no Pro tier and always
     * answers false; the direct build delegates to its license check.
     */
    fun verifyProOwnership(callback: (Boolean) -> Unit)
}
