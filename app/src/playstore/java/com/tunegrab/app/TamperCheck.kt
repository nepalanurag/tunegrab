package com.tunegrab.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * Best-effort local tamper detection.
 *
 * No client-side check is unbreakable — a skilled reverser can patch
 * anything. These checks raise the bar so casual tools (Lucky Patcher
 * re-signs, hook frameworks) fail instead of silently working:
 *
 * 1. **Signing-cert pinning**: a re-signed APK has a different cert.
 *    The expected SHA-256 comes from BuildConfig.RELEASE_CERT_SHA256,
 *    which is empty in dev builds (check skipped) and filled in when the
 *    real release keystore exists (see PUBLISH_GUIDE.md).
 * 2. **Debuggable flag**: release builds must not be debuggable.
 * 3. **Hook frameworks**: Xposed / Substrate / Frida classes present.
 *
 * The definitive server-side verdict is the Play Integrity API
 * ([IntegrityManager]); these local checks are defense in depth.
 */
object TamperCheck {

    private const val TAG = "TamperCheck"

    /** Result of the local tamper sweep. */
    data class Report(
        val certOk: Boolean,
        val notDebuggable: Boolean,
        val noHooks: Boolean,
        val installedFromPlay: Boolean,
    ) {
        /** True when every *enforced* check passes. Installer source is informational only. */
        val clean: Boolean get() = certOk && notDebuggable && noHooks
    }

    fun run(context: Context): Report {
        val certOk = checkSigningCert(context)
        val notDebuggable = checkNotDebuggable(context)
        val noHooks = checkNoHookFrameworks()
        val fromPlay = checkInstalledFromPlay(context)
        if (!certOk) Log.w(TAG, "signing certificate mismatch — possible repack")
        if (!notDebuggable) Log.w(TAG, "debuggable flag set on release build")
        if (!noHooks) Log.w(TAG, "hooking framework detected")
        return Report(certOk, notDebuggable, noHooks, fromPlay)
    }

    private fun checkSigningCert(context: Context): Boolean {
        val expected = BuildConfig.RELEASE_CERT_SHA256
        if (expected.isBlank()) return true // dev build: nothing to pin against yet
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                val sc = info.signingInfo
                if (sc?.hasMultipleSigners() == true) sc.apkContentsSigners else sc?.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            val digest = MessageDigest.getInstance("SHA-256")
            sigs?.any { sig ->
                digest.digest(sig.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                    .equals(expected, ignoreCase = true)
            } == true
        } catch (t: Throwable) {
            Log.w(TAG, "cert check failed", t)
            false
        }
    }

    private fun checkNotDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0

    private fun checkNoHookFrameworks(): Boolean {
        val hookClasses = listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "com.saurik.substrate.MS\$MethodHook",
            "com.frida.server",
        )
        return hookClasses.none { name ->
            runCatching { Class.forName(name); true }.getOrDefault(false)
        }
    }

    /**
     * Informational only — direct APK installs (e.g. from the developer)
     * legitimately report null here, so this never blocks Pro.
     */
    private fun checkInstalledFromPlay(context: Context): Boolean {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                context.packageManager.getInstallSourceInfo(context.packageName)
                    .installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()
        return installer == "com.android.vending"
    }
}
