package com.tunegrab.app.library

import android.content.Context
import android.net.Uri
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * Audio tag read/write for the "Clean up song info" and "Fix song genres"
 * passes. Title/artist/genre are the only fields ever written — artwork
 * and every other frame stay untouched. Call from Dispatchers.IO.
 *
 * MP3/FLAC/M4A go through jaudiotagger. Opus goes through [OpusTagger]:
 * jaudiotagger cannot read Opus at all, which used to make every Opus
 * tag write fail silently.
 *
 * Two flavors of every operation:
 * - `File` variants: for files in app-private storage (the download
 *   pipeline's tmp dir), where raw filesystem access works.
 * - `(Context, Uri, fileName)` variants: for songs already published to
 *   shared storage (Music/). Raw `File` writes there are denied by scoped
 *   storage on Android 11+, so these copy through the ContentResolver via
 *   a temp file instead. The app owns its downloads, so no extra
 *   permission or user consent is needed for the write-back.
 */
object SongTagger {

    private const val TAG = "SongTagger"

    private fun isOpus(file: File): Boolean = file.extension.equals("opus", ignoreCase = true)

    data class Tags(val title: String?, val artist: String?, val genre: String?)

    /** Current title/artist/genre tags, or null when unreadable. */
    fun readTags(file: File): Tags? =
        if (isOpus(file)) {
            OpusTagger.readTags(file)?.let { Tags(it.title, it.artist, it.genre) }
        } else runCatching {
            val tag = AudioFileIO.read(file).tag ?: return null
            Tags(
                title = tag.getFirst(FieldKey.TITLE).takeIf { it.isNotBlank() },
                artist = tag.getFirst(FieldKey.ARTIST).takeIf { it.isNotBlank() },
                genre = tag.getFirst(FieldKey.GENRE).takeIf { it.isNotBlank() },
            )
        }.getOrNull()

    /**
     * Writes [title]/[artist] into the file's tag (creating one if the
     * file has none). True on success.
     */
    fun writeTags(file: File, title: String, artist: String): Boolean =
        if (isOpus(file)) {
            OpusTagger.writeTags(file, title, artist, null)
        } else runCatching {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(FieldKey.TITLE, title)
            tag.setField(FieldKey.ARTIST, artist)
            audioFile.commit()
            true
        }.getOrElse { e ->
            Log.e(TAG, "writeTags failed for ${file.name}", e)
            false
        }

    // ---------------- ContentResolver variants (shared storage) ----------------

    /** Outcome of a tag write on a shared-storage song. */
    sealed interface TagWriteResult {
        data object Ok : TagWriteResult
        data object Failed : TagWriteResult
        /**
         * Android denied the write because the file belongs to another app
         * (sideloaded music). The user can grant access with a single
         * [android.provider.MediaStore.createWriteRequest] dialog; the
         * write can then be retried.
         */
        data object NeedsConsent : TagWriteResult
    }

    /**
     * Temp-file extension derived from the MediaStore mime type first,
     * filename second — so extension-less files still route to the right
     * tagger (jaudiotagger picks its reader by extension).
     */
    private fun tempExtension(fileName: String, mimeType: String): String =
        when (mimeType.lowercase()) {
            "audio/opus" -> "opus"
            "audio/ogg" -> "ogg"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/mpeg", "audio/mp3", "audio/x-mp3" -> "mp3"
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            else ->
                fileName.substringAfterLast('.', "")
                    .takeIf { it.isNotEmpty() } ?: "tmp"
        }

    /**
     * Copies the content at [uri] to a temp file, runs [edit] on it, and
     * writes the result back through the ContentResolver.
     */
    private fun editViaResolver(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String,
        edit: (File) -> Boolean,
    ): TagWriteResult {
        val ext = tempExtension(fileName, mimeType)
        val tmp = File.createTempFile("tagedit", ".$ext", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: run {
                Log.w(TAG, "editViaResolver: cannot open $uri for read")
                return TagWriteResult.Failed
            }
            if (!edit(tmp)) return TagWriteResult.Failed
            context.contentResolver.openOutputStream(uri, "w")?.use { out ->
                tmp.inputStream().use { it.copyTo(out) }
            } ?: run {
                Log.w(TAG, "editViaResolver: cannot open $uri for write")
                return TagWriteResult.Failed
            }
            return TagWriteResult.Ok
        } catch (e: SecurityException) {
            Log.w(TAG, "editViaResolver needs user consent for $uri", e)
            return TagWriteResult.NeedsConsent
        } catch (e: Exception) {
            Log.e(TAG, "editViaResolver failed for $uri", e)
            return TagWriteResult.Failed
        } finally {
            tmp.delete()
        }
    }

    /** Current title/artist/genre tags via ContentResolver, or null when unreadable. */
    fun readTags(context: Context, uri: Uri, fileName: String, mimeType: String): Tags? {
        val ext = tempExtension(fileName, mimeType)
        val tmp = File.createTempFile("tagread", ".$ext", context.cacheDir)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return null
            readTags(tmp)
        } catch (e: Exception) {
            Log.e(TAG, "readTags failed for $uri", e)
            null
        } finally {
            tmp.delete()
        }
    }

    /** Writes [title]/[artist] via ContentResolver. */
    fun writeTags(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String,
        title: String,
        artist: String,
    ): TagWriteResult = editViaResolver(context, uri, fileName, mimeType) { tmp ->
        writeTags(tmp, title, artist)
    }
}
