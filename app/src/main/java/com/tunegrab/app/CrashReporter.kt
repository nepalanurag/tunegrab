package com.tunegrab.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic crash capture. Installed as the first step of
 * [YtFlacApp.onCreate]; any uncaught throwable (including [Error]s such as
 * [UnsatisfiedLinkError] or [NoClassDefFoundError] that a plain
 * `catch (e: Exception)` would miss) is written to a user-visible file in
 * Downloads before the process dies, so the exact stack trace can be
 * recovered from a release build without logcat access.
 *
 * Milestone logging ([milestone]) additionally records how far startup got,
 * which pinpoints the crash site even if the trace itself is unavailable.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val FILE_NAME = "tunegrab-crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeReport(appContext, throwable) }
            previous?.uncaughtException(thread, throwable)
                ?: throw throwable
        }
        milestone(appContext, "CrashReporter installed")
    }

    fun milestone(context: Context, name: String) {
        runCatching {
            val dir = File(context.getExternalFilesDir(null), "crashes")
            dir.mkdirs()
            val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            File(dir, "startup.log").appendText("$stamp $name\n")
        }
    }

    private fun writeReport(context: Context, throwable: Throwable) {
        val trace = Log.getStackTraceString(throwable)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val milestones = runCatching {
            File(context.getExternalFilesDir(null), "crashes/startup.log")
                .takeIf { it.exists() }?.readText()
        }.getOrNull().orEmpty()
        val report = buildString {
            appendLine("TuneGrab crash report — $stamp")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${Thread.currentThread().name}")
            appendLine()
            appendLine("--- startup milestones ---")
            appendLine(milestones.ifBlank { "(none)" })
            appendLine("--- stack trace ---")
            appendLine(trace)
        }

        // 1) App-private copy (always writable).
        runCatching {
            val dir = File(context.getExternalFilesDir(null), "crashes")
            dir.mkdirs()
            File(dir, FILE_NAME).writeText(report)
        }
        // 2) User-visible copy in Downloads so it can be shared without a PC.
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS
                    )
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return
            resolver.openOutputStream(uri)?.use { out ->
                out.write(report.toByteArray())
            }
        }
    }
}
