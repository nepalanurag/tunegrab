package com.tunegrab.app

import com.tunegrab.app.player.RadioFetcher

/**
 * Play Store build: a plain local music player. No downloader, no YouTube
 * search, no Radio, no sleep timer, no Pro tier, no ads. The Play binary
 * contains no yt-dlp code, no download service, and no share-sheet target —
 * this bridge is the compile-time proof: every download-related answer is
 * "no" and every download-related action is a no-op.
 */
object DirectBridge : DirectBridgeApi {

    override val hasDownloader: Boolean = false

    override fun onAppCreate(app: YtFlacApp) {
        // Nothing to start: no download engines in the Play build.
        app.setEngineState(EngineState.Ready)
    }

    override suspend fun resolveStreamUrl(videoId: String): String? = null

    override suspend fun fetchUploader(videoId: String): String? = null

    override fun radioFetcher(): RadioFetcher? = null

    override fun verifyProOwnership(callback: (Boolean) -> Unit) {
        // No Pro tier in the free Play player.
        callback(false)
    }
}
