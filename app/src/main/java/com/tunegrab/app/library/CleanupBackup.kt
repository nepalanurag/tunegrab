package com.tunegrab.app.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pre-cleanup tag backup for "Clean up song info". Before any tags are
 * rewritten, the current title/artist of every touched song is saved to
 * app-private storage; the dialog offers a one-tap restore of that exact
 * snapshot.
 *
 * Note: no backup existed before this was added, so songs cleaned by
 * older builds cannot be restored — this only protects cleanups run
 * from now on.
 */
object CleanupBackup {

    private const val FILE_NAME = "cleanup_tag_backup.json"

    data class Entry(
        val contentUri: String,
        val filePath: String,
        val mimeType: String,
        val title: String,
        val artist: String,
    )

    fun exists(context: Context): Boolean = backupFile(context).exists()

    fun save(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("uri", e.contentUri)
                    .put("path", e.filePath)
                    .put("mime", e.mimeType)
                    .put("title", e.title)
                    .put("artist", e.artist),
            )
        }
        backupFile(context).writeText(arr.toString())
    }

    fun load(context: Context): List<Entry>? {
        val f = backupFile(context)
        if (!f.exists()) return null
        return runCatching {
            val arr = JSONArray(f.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    contentUri = o.getString("uri"),
                    filePath = o.getString("path"),
                    mimeType = o.optString("mime", ""),
                    title = o.optString("title", ""),
                    artist = o.optString("artist", ""),
                )
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun clear(context: Context) {
        backupFile(context).delete()
    }

    private fun backupFile(context: Context): File = File(context.filesDir, FILE_NAME)
}
