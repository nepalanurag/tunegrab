package com.tunegrab.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Play Store build: a free local music player. No downloader, no YouTube
 * search, no sleep timer, no ads, no Pro tier. The downloader code and
 * its native libraries are not in this build's source set, so they
 * cannot end up in the binary. Radio and the equalizer are visible as
 * Pro-badged locked options.
 */
class MainActivity : BaseMainActivity() {

    override val defaultTab: AppTab = AppTab.HOME

    /** Play build tabs: Home, Library, plus a Settings nav item. */
    override val navTabs: List<AppTab>
        get() = listOf(AppTab.HOME, AppTab.LIBRARY)

    override val showSettingsNavItem: Boolean = true

    override fun openAlbum(album: String) {
        // Local album detail lives in the Library tab's back stack.
        appTab = AppTab.LIBRARY
        libraryAlbumTarget = album
    }

    /** Set by openAlbum; LibraryTabContent pushes the detail screen. */
    var libraryAlbumTarget by mutableStateOf<String?>(null)

    override val albumTarget: String? get() = libraryAlbumTarget
    override fun onAlbumTargetConsumed() { libraryAlbumTarget = null }

    /**
     * The Play build has no Pro tier or purchase flow, so locked Pro
     * teasers (Radio, equalizer) show this dialog instead of doing
     * nothing when tapped.
     */
    @Composable
    override fun ProUpsellSlot() {
        if (!showProUpsell) return
        AlertDialog(
            onDismissRequest = { showProUpsell = false },
            title = { Text("Pro feature") },
            text = {
                Text(
                    proUpsellReason +
                        "\n\nTuneGrab Pro adds downloads, Radio, the sleep " +
                        "timer, and the equalizer. It is sold separately on Ko-fi."
                )
            },
            confirmButton = {
                TextButton(onClick = { showProUpsell = false }) { Text("Got it") }
            },
        )
    }

    // All other flavor hooks keep their base no-op defaults:
    // - FlavorTabContent renders nothing (Library is handled by the base).
    // - ExtraNavItems adds nothing.
    // - FlavorOverlays adds nothing.
    // - ProUpsellSlot shows nothing.
    // - onDownloadSongRequested does nothing.
    // - onTabBackPressed returns false, so back exits the app.

    /**
     * The Play build has no online artist screen: "Go to artist" rows and
     * the details sheet's YouTube Music button are locked Pro teasers, so
     * tapping them shows the upsell instead of doing nothing.
     */
    override fun openArtist(name: String, videoId: String?) {
        proUpsellReason = "The online artist screen is a Pro feature."
        showProUpsell = true
    }
}
