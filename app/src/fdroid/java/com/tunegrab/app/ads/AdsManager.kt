package com.tunegrab.app.ads

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * F-Droid stub: no proprietary ad SDK is allowed in the F-Droid build,
 * so rewarded ads do not exist here. The features ads would gate
 * (equalizer, song-info cleanup) are unlocked outright at startup —
 * see [com.tunegrab.app.YtFlacApp].
 *
 * The public API mirrors the Play Store implementation so the shared
 * UI code in `main` compiles unchanged.
 */
object AdsManager {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isAdReady = MutableStateFlow(false)
    val isAdReady: StateFlow<Boolean> = _isAdReady.asStateFlow()

    fun init(context: Context) = Unit

    fun loadRewardedAd(context: Context) = Unit

    fun showRewardedAd(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit = {},
    ) {
        // No ads in the F-Droid build — the ad gate is never shown
        // (features are pre-unlocked), so report dismissal.
        onDismissed()
    }

    fun isReady(): Boolean = false
}
