package com.tunegrab.app

import android.content.Context

/**
 * F-Droid stub: F-Droid signs with its own key, so pinning the signing
 * certificate against our release cert would always report tampering.
 * Tamper checks are a Play-build concern; the F-Droid build is
 * built-from-source by F-Droid itself and reports clean.
 */
object TamperCheck {

    data class Report(
        val certOk: Boolean,
        val notDebuggable: Boolean,
        val noHooks: Boolean,
        val installedFromPlay: Boolean,
    ) {
        /** True when every *enforced* check passes. */
        val clean: Boolean get() = certOk && notDebuggable && noHooks
    }

    fun run(context: Context): Report = Report(
        certOk = true,
        notDebuggable = true,
        noHooks = true,
        installedFromPlay = false,
    )
}
