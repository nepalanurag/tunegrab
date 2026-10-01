package com.tunegrab.app

import android.app.Activity
import android.util.Log

/**
 * The single authority for "is this user entitled to Pro?".
 *
 * Threat model: flipping a boolean in SharedPreferences (or patching a
 * smali `if`) must not grant Pro. So:
 *
 * 1. **Pro is re-derived from the flavor's license check on every app start
 *    / resume** (Play Billing in the direct build; the Play Store build
 *    has no Pro tier at all). A patched boolean is overwritten with
 *    the truth within one launch.
 * 2. **Offline grace**: a legit buyer keeps Pro for [OFFLINE_GRACE_MS]
 *    without network, based on the last time Play *confirmed* the
 *    purchase — not on a flag a patcher can set (the timestamp is only
 *    ever written right after a live Play confirmation).
 * 3. **Tamper sweep**: [TamperCheck] runs first; a re-signed APK or
 *    hooking framework revokes Pro immediately.
 * 4. **Integrity verdict**: when the server backend is configured,
 *    a dirty Play Integrity verdict also revokes Pro.
 *
 * Honest caveat: this stops casual patching (Lucky Patcher re-signs,
 * boolean flips, Xposed hooks). A determined reverser with time can
 * defeat any client-side scheme — the only complete answer is
 * server-side receipt verification, which the optional backend
 * ([IntegrityManager]) is designed to grow into.
 */
object LicenseGuard {

    private const val TAG = "LicenseGuard"

    /** Legit buyers keep Pro offline this long after Play last confirmed it. */
    private const val OFFLINE_GRACE_MS = 7L * 24 * 60 * 60 * 1000

    /** Minimum gap between live Play re-verifications. */
    private const val REFRESH_THROTTLE_MS = 6L * 60 * 60 * 1000

    private var lastRefreshAttempt = 0L

    /**
     * Re-evaluates entitlement. Safe to call from onCreate and onResume —
     * live Play queries are throttled, the tamper sweep is cheap.
     * Pass the Activity (not just a Context) so the Play Integrity check
     * can run.
     */
    fun refreshEntitlement(activity: Activity, force: Boolean = false) {
        val appCtx = activity.applicationContext

        // Personal Pro build: Pro is baked in — nothing to verify or revoke.
        if (BuildConfig.FORCE_PRO) {
            AppSettings.confirmProPurchase()
            return
        }

        // 1. Tamper sweep first — a repacked APK never gets Pro, period.
        val report = TamperCheck.run(appCtx)
        if (!report.clean) {
            Log.w(TAG, "tamper detected — revoking Pro")
            AppSettings.revokePro("tamper")
            return
        }

        // 2. Live verification is throttled; otherwise apply the offline grace.
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshAttempt < REFRESH_THROTTLE_MS) {
            applyOfflineGrace()
            return
        }
        lastRefreshAttempt = now

        // 3. Play Integrity verdict, when the backend is configured.
        IntegrityManager.check(activity) { verdict ->
            if (verdict != null && !verdict.clean) {
                Log.w(TAG, "integrity verdict dirty — revoking Pro")
                AppSettings.revokePro("integrity")
                return@check
            }
            // 4. Live Pro ownership verification via the flavor bridge
            // (Play Billing in the direct build; always false in the
            // Play Store build, which has no Pro tier).
            DirectBridge.verifyProOwnership { owned ->
                if (owned) {
                    AppSettings.confirmProPurchase()
                } else {
                    applyOfflineGrace()
                }
            }
        }
    }

    /** Called by [BillingManager] the moment a purchase is acknowledged. */
    fun onPurchaseAcknowledged() {
        AppSettings.confirmProPurchase()
    }

    /**
     * No live Play answer (throttled / offline): keep Pro only if Play
     * confirmed the purchase within the grace window.
     */
    private fun applyOfflineGrace() {
        val verifiedAt = AppSettings.lastProVerifiedAt()
        val withinGrace = verifiedAt > 0 &&
            System.currentTimeMillis() - verifiedAt < OFFLINE_GRACE_MS
        if (AppSettings.isProNow() && !withinGrace) {
            Log.i(TAG, "no recent Play confirmation — suspending Pro until online")
            AppSettings.revokePro("grace-expired")
        }
    }
}
