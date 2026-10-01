package com.tunegrab.app

import android.app.Activity
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * Play Integrity API wiring.
 *
 * The integrity token is opaque on-device — the verdict (is the APK
 * genuine? is the device trustworthy?) can only be read by decrypting it
 * server-side. Full enforcement therefore needs the tiny verification
 * endpoint described in PUBLISH_GUIDE.md ("Integrity verification
 * backend", ~20 lines of Python/Node).
 *
 * Until [BuildConfig.INTEGRITY_VERIFY_URL] is set, this class requests the
 * token (proving the API is wired and functional) but does not gate
 * anything on it. The enforced local lines of defense are [TamperCheck]
 * and [LicenseGuard]'s live Play Billing verification.
 */
object IntegrityManager {

    private const val TAG = "IntegrityManager"

    /** Verdict returned by the server endpoint, or null when unverifiable. */
    data class Verdict(
        val appRecognized: Boolean,
        val deviceOk: Boolean,
    ) {
        val clean: Boolean get() = appRecognized && deviceOk
    }

    /**
     * Requests an integrity token and, when a verify URL is configured,
     * asks the backend for the verdict. Always invokes [onResult] — with
     * null when integrity can't be evaluated (API unavailable, no backend
     * configured, network error). Callers must treat null as
     * "unknown", not "failed".
     */
    fun check(activity: Activity, onResult: (Verdict?) -> Unit) {
        val verifyUrl = BuildConfig.INTEGRITY_VERIFY_URL
        val manager = runCatching { IntegrityManagerFactory.create(activity) }.getOrNull()
        if (manager == null) {
            Log.w(TAG, "integrity API unavailable on this device")
            onResult(null)
            return
        }
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val request = IntegrityTokenRequest.builder()
            .setNonce(nonce)
            .build()
        manager.requestIntegrityToken(request)
            .addOnSuccessListener { response ->
                if (verifyUrl.isBlank()) {
                    Log.i(TAG, "token acquired; no verify URL configured — skipping verdict")
                    onResult(null)
                } else {
                    verifyWithBackend(verifyUrl, response.token(), onResult)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "integrity token request failed", e)
                onResult(null)
            }
    }

    /**
     * POSTs the token to the user's backend. Expected response: a JSON
     * object like `{"appRecognized":true,"deviceOk":true}`. Runs on a
     * background thread; [onResult] is invoked on that same thread.
     */
    private fun verifyWithBackend(
        verifyUrl: String,
        token: String,
        onResult: (Verdict?) -> Unit
    ) {
        Thread {
            val verdict = runCatching {
                val conn = (URL(verifyUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val body = """{"token":"$token"}"""
                conn.outputStream.use { it.write(body.toByteArray()) }
                if (conn.responseCode != 200) return@runCatching null
                val json = conn.inputStream.bufferedReader().readText()
                Verdict(
                    appRecognized = """"appRecognized"\s*:\s*true""".toRegex().containsMatchIn(json),
                    deviceOk = """"deviceOk"\s*:\s*true""".toRegex().containsMatchIn(json),
                )
            }.getOrNull()
            onResult(verdict)
        }.start()
    }
}
