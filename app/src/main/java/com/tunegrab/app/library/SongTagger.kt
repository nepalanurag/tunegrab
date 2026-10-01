package com.tunegrab.app.library

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * Audio tag read/write for the "Clean up song info" pass, via jaudiotagger.
 * Only the title/artist fields are ever written — artwork and every other
 * frame stay untouched. Call from Dispatchers.IO; every failure is
 * swallowed into a null/false so one bad file can't abort the scan.
 */
object SongTagger {

    data class Tags(val title: String?, val artist: String?)

    /** Current title/artist tags, or null when unreadable. */
    fun readTags(file: File): Tags? = runCatching {
        val tag = AudioFileIO.read(file).tag ?: return null
        Tags(
            title = tag.getFirst(FieldKey.TITLE).takeIf { it.isNotBlank() },
            artist = tag.getFirst(FieldKey.ARTIST).takeIf { it.isNotBlank() },
        )
    }.getOrNull()

    /**
     * Writes [title]/[artist] into the file's tag (creating one if the
     * file has none). True on success.
     */
    fun writeTags(file: File, title: String, artist: String): Boolean = runCatching {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        tag.setField(FieldKey.TITLE, title)
        tag.setField(FieldKey.ARTIST, artist)
        audioFile.commit()
        true
    }.getOrDefault(false)
}
