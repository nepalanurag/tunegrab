package com.tunegrab.app.library

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * What changed in the library since the last scan.
 * [Unknown] means "something may have changed — reload"; [ContentChanged]
 * carries the exact added/removed song ids when they were computed.
 */
sealed interface LibraryChange {
    /** Nothing (re)loaded yet, or change details unavailable. */
    data object None : LibraryChange

    /** A rescan was requested (e.g. a download finished); reload the library. */
    data object Unknown : LibraryChange

    /** A rescan ran and produced an exact diff of song ids. */
    data class ContentChanged(
        val added: List<Long>,
        val removed: List<Long>
    ) : LibraryChange
}

/**
 * Incremental library-scan coordinator.
 *
 * Persists `lastScanTimeMs` and the MediaStore version in its own
 * SharedPreferences (`tunegrab_library`), so scans survive process death.
 * The coordinator (or DownloadService after a download finishes) calls
 * [triggerRescan] to flag that a refresh is needed; the UI collects
 * [changeFlow] to know when to reload. The actual scan runs in
 * [performRescan], which diffs song ids against the previous scan and
 * publishes the result.
 *
 * A rescan is needed when:
 * - [triggerRescan] was called since the last recorded scan, or
 * - the MediaStore volume version changed (media DB rebuilt), or
 * - the newest DATE_MODIFIED in the audio table is newer than the last
 *   recorded scan time.
 */
object LibraryScanner {

    private const val PREFS = "tunegrab_library"
    private const val KEY_LAST_SCAN_MS = "last_scan_ms"
    private const val KEY_MEDIASTORE_VERSION = "mediastore_version"

    private val _changeFlow = MutableStateFlow<LibraryChange>(LibraryChange.None)
    val changeFlow: StateFlow<LibraryChange> = _changeFlow.asStateFlow()

    /** Set by [triggerRescan]; cleared by [performRescan]. */
    @Volatile
    private var rescanRequested = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Cheap check for whether the library view is stale. Never touches the
     * network and makes a single projection-limited query for the newest
     * DATE_MODIFIED. Returns false when the audio permission is missing
     * (there is nothing to check against).
     */
    fun needsRescan(context: Context): Boolean {
        if (rescanRequested) return true
        val p = prefs(context)
        val lastScan = p.getLong(KEY_LAST_SCAN_MS, 0L)
        if (lastScan == 0L) return true
        if (p.getString(KEY_MEDIASTORE_VERSION, "") != mediaStoreVersion(context)) {
            return true
        }
        val newest = newestDateModifiedMs(context)
        return newest > lastScan
    }

    /**
     * Runs a scan: fetches current song ids (single-column, paged query),
     * diffs against the previous scan's ids, records the scan, and emits
     * the result on [changeFlow]. Heavy queries run on Dispatchers.IO.
     */
    suspend fun performRescan(context: Context): LibraryChange =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            if (!MediaStoreRepository.hasPermission(appContext)) {
                // Never diff against an empty set when we can't see the
                // library — that would look like "everything deleted".
                _changeFlow.value = LibraryChange.Unknown
                return@withContext LibraryChange.Unknown
            }
            val oldIds = lastKnownIds(appContext)
            val newIds = currentSongIds(appContext)
            val diff = diffSongs(oldIds, newIds)
            saveKnownIds(appContext, newIds)
            recordScan(appContext)
            rescanRequested = false
            val change = LibraryChange.ContentChanged(added = diff.added, removed = diff.removed)
            _changeFlow.value = change
            change
        }

    /** Marks the last scan as done *now* without computing a diff. */
    fun recordScan(context: Context) {
        val appContext = context.applicationContext
        prefs(appContext).edit()
            .putLong(KEY_LAST_SCAN_MS, System.currentTimeMillis())
            .putString(KEY_MEDIASTORE_VERSION, mediaStoreVersion(appContext))
            .apply()
        rescanRequested = false
    }

    /**
     * Hook for DownloadService (wired by the coordinator): call after a
     * download finishes so the library picks up the new track. Emits
     * [LibraryChange.Unknown] immediately; the UI decides when to call
     * [performRescan].
     */
    fun triggerRescan() {
        rescanRequested = true
        _changeFlow.value = LibraryChange.Unknown
    }

    /**
     * MediaStore volume version; changes when the media database is
     * rebuilt (e.g. after an SD-card remount). API 29+ only.
     */
    private fun mediaStoreVersion(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getVersion(context)
        } else {
            "legacy"
        }

    /** Newest DATE_MODIFIED in the audio table, in millis; 0 when unknown. */
    private fun newestDateModifiedMs(context: Context): Long {
        if (!MediaStoreRepository.hasPermission(context)) return 0L
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.DATE_MODIFIED),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.DATE_MODIFIED} DESC LIMIT 1"
        ) ?: return 0L
        cursor.use {
            if (!it.moveToFirst()) return 0L
            val idx = it.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            if (idx < 0 || it.isNull(idx)) return 0L
            return it.getLong(idx) * 1000L
        }
    }

    // ---------------- persisted id snapshots ----------------

    private const val KEY_KNOWN_IDS = "known_song_ids"

    private fun lastKnownIds(context: Context): List<Long> {
        val raw = prefs(context).getString(KEY_KNOWN_IDS, null) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split(',').mapNotNull { it.toLongOrNull() }
    }

    private fun saveKnownIds(context: Context, ids: List<Long>) {
        prefs(context).edit()
            .putString(KEY_KNOWN_IDS, ids.joinToString(","))
            .apply()
    }

    /**
     * All music-track ids, one column per row, fetched in pages so even
     * very large libraries never materialize full rows.
     */
    private fun currentSongIds(context: Context): List<Long> {
        if (!MediaStoreRepository.hasPermission(context)) return emptyList()
        val ids = ArrayList<Long>()
        var offset = 0
        while (true) {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media._ID} ASC LIMIT $PAGE OFFSET $offset"
            ) ?: break
            var got = 0
            cursor.use {
                val idx = it.getColumnIndex(MediaStore.Audio.Media._ID)
                while (it.moveToNext()) {
                    got++
                    if (idx >= 0 && !it.isNull(idx)) ids.add(it.getLong(idx))
                }
            }
            if (got < PAGE) break
            offset += PAGE
        }
        return ids
    }

    private const val PAGE = 5000
}
