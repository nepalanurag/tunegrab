package com.tunegrab.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages AdMob rewarded ads for the free Play Store build.
 *
 * Free users watch a rewarded ad to unlock pro features (equalizer,
 * song info cleaner) for a session. The unlock is not persisted —
 * each app launch requires a fresh ad view. This keeps the
 * implementation simple and avoids any server-side entitlement
 * bookkeeping.
 */
object AdsManager {

    // Rewarded ad unit for "Pro Feature Unlock" (created 2026-10-02).
    private const val REWARDED_AD_UNIT_ID = "ca-app-pub-2603385080122452/2188898189"

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isAdReady = MutableStateFlow(false)
    val isAdReady: StateFlow<Boolean> = _isAdReady.asStateFlow()

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    /**
     * Initializes the Mobile Ads SDK. Call once from Application.onCreate().
     * Safe to call multiple times.
     */
    fun init(context: Context) {
        if (_isInitialized.value) return
        MobileAds.initialize(context) {
            _isInitialized.value = true
            loadRewardedAd(context)
        }
    }

    /**
     * Preloads a rewarded ad. Called after init and after each ad is shown.
     */
    fun loadRewardedAd(context: Context) {
        if (isLoading || rewardedAd != null) return
        if (!_isInitialized.value) return

        isLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    rewardedAd = ad
                    _isAdReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    rewardedAd = null
                    _isAdReady.value = false
                }
            }
        )
    }

    /**
     * Shows the rewarded ad. Calls [onRewarded] when the user earns the
     * reward (watches the ad to completion), [onDismissed] when the ad
     * is closed without earning (or if no ad was ready).
     *
     * Must be called from an Activity context.
     */
    fun showRewardedAd(
        activity: Activity,
        onRewarded: () -> Unit,
        onDismissed: () -> Unit = {},
    ) {
        val ad = rewardedAd
        if (ad == null) {
            // No ad ready — reload and report dismissal.
            loadRewardedAd(activity)
            onDismissed()
            return
        }

        // Clear the reference; preload the next one.
        rewardedAd = null
        _isAdReady.value = false

        ad.show(activity) { rewardItem ->
            // User watched enough to earn the reward.
            onRewarded()
        }

        // Preload the next ad regardless of outcome.
        loadRewardedAd(activity)
    }

    /**
     * Returns true if a rewarded ad is ready to show.
     */
    fun isReady(): Boolean = rewardedAd != null
}
