package com.tunegrab.app

import android.app.Activity

/**
 * F-Droid stub: the Play Integrity API is proprietary and unavailable in
 * the F-Droid build. Callers treat a null verdict as "unknown", never
 * "failed", so nothing is gated on integrity here.
 */
object IntegrityManager {

    data class Verdict(
        val appRecognized: Boolean,
        val deviceOk: Boolean,
    ) {
        val clean: Boolean get() = appRecognized && deviceOk
    }

    fun check(activity: Activity, onResult: (Verdict?) -> Unit) {
        onResult(null)
    }
}
