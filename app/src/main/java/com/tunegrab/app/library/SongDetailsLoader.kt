package com.tunegrab.app.library

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the Oto-style Details view shows for one song.
 *
 * Sources: MediaStore (tags, dates, size, path), the FLAC STREAMINFO block
 * (sample rate / channels / bit depth — [FlacParser]), and
 * [MediaMetadataRetriever] (bitrate fallback, embedded artwork dimensions).
 * Anything unknown stays null and the UI renders an em dash.
 */
data class SongDetails(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String?,
    val year: Int,
    val trackNumber: Int,
    /** "FLAC", "MP3", ... derived from the mime type. */
    val format: String,
    val mimeType: String,
    /** e.g. "Hi-Res Lossless · 24-bit · 48 kHz"; null when unknown. */
    val qualityBadge: String?,
    val sampleRateHz: Int?,
    val bitDepth: Int?,
    val channels: Int?,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val bitrateKbps: Int?,
    val durationMs: Long,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val artWidth: Int?,
    val artHeight: Int?,
    val artFormat: String?,
    val artSizeBytes: Long?,
)

class SongDetailsLoader(
    private val context: Context,
    private val repo: MediaStoreRepository,
) {

    /** Full details for the Details sheet; null when the song is gone. */
    suspend fun load(songId: Long): SongDetails? = withContext(Dispatchers.IO) {
        val song = repo.getSongsByIds(listOf(songId)).firstOrNull()
            ?: return@withContext null
        val format = formatLabel(song.mimeType)
        val flac = if (format == "FLAC") {
            runCatching {
                context.contentResolver.openInputStream(song.contentUri)?.use {
                    FlacParser.parse(it)
                }
            }.getOrNull()
        } else null

        var bitrateKbps: Int? = null
        var art: EmbeddedArt? = null
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, song.contentUri)
                bitrateKbps = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()?.div(1000)
                art = retriever.embeddedPicture?.let { bytes ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    EmbeddedArt(
                        width = opts.outWidth.takeIf { it > 0 },
                        height = opts.outHeight.takeIf { it > 0 },
                        format = sniffImageFormat(bytes),
                        sizeBytes = bytes.size.toLong(),
                    )
                }
            } finally {
                retriever.release()
            }
        }
        if (bitrateKbps == null && song.durationMs > 0 && song.sizeBytes > 0) {
            // Lossless files often report no bitrate tag; estimate from size.
            bitrateKbps = ((song.sizeBytes * 8L) / (song.durationMs / 1000L)).toInt()
        }

        val sampleRate = flac?.sampleRateHz
        val badge = qualityBadge(format, flac?.bitsPerSample, sampleRate)
        SongDetails(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            genre = song.genre,
            year = song.year,
            trackNumber = song.trackNumber,
            format = format,
            mimeType = song.mimeType,
            qualityBadge = badge,
            sampleRateHz = sampleRate,
            bitDepth = flac?.bitsPerSample,
            channels = flac?.channels,
            fileName = song.filePath.substringAfterLast('/').ifEmpty { song.title },
            filePath = song.filePath,
            sizeBytes = song.sizeBytes,
            bitrateKbps = bitrateKbps,
            durationMs = song.durationMs,
            dateAddedSec = song.dateAddedSec,
            dateModifiedSec = song.dateModifiedSec,
            artWidth = art?.width,
            artHeight = art?.height,
            artFormat = art?.format,
            artSizeBytes = art?.sizeBytes,
        )
    }

    /**
     * Lightweight one-liner for under the player's seekbar, e.g.
     * "FLAC · 1565 kb/s · 48.0 kHz". Skips artwork decoding.
     */
    suspend fun qualityLine(songId: Long): String? = withContext(Dispatchers.IO) {
        val song = repo.getSongsByIds(listOf(songId)).firstOrNull()
            ?: return@withContext null
        val format = formatLabel(song.mimeType)
        val flac = if (format == "FLAC") {
            runCatching {
                context.contentResolver.openInputStream(song.contentUri)?.use {
                    FlacParser.parse(it)
                }
            }.getOrNull()
        } else null
        var bitrateKbps: Int? = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, song.contentUri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()?.div(1000)
            } finally {
                retriever.release()
            }
        }.getOrNull()
        if (bitrateKbps == null && song.durationMs > 0 && song.sizeBytes > 0) {
            bitrateKbps = ((song.sizeBytes * 8L) / (song.durationMs / 1000L)).toInt()
        }
        buildList {
            add(format)
            bitrateKbps?.let { add("$it kb/s") }
            flac?.sampleRateHz?.let { add(formatSampleRate(it)) }
        }.joinToString(" · ")
    }

    private data class EmbeddedArt(
        val width: Int?,
        val height: Int?,
        val format: String?,
        val sizeBytes: Long,
    )

    companion object {
        fun formatLabel(mimeType: String): String = when (mimeType.lowercase()) {
            "audio/flac", "audio/x-flac" -> "FLAC"
            "audio/mpeg", "audio/mp3" -> "MP3"
            "audio/mp4", "audio/x-m4a", "audio/aac" -> "M4A"
            "audio/ogg", "audio/opus" -> "OGG"
            "audio/wav", "audio/x-wav" -> "WAV"
            else -> mimeType.substringAfter('/').uppercase().ifEmpty { "AUDIO" }
        }

        fun formatSampleRate(hz: Int): String {
            val khz = hz / 1000.0
            val text = if (khz == khz.toInt().toDouble()) "${khz.toInt()}" else "%.1f".format(khz)
            return "$text kHz"
        }

        fun formatFileSize(bytes: Long): String = when {
            bytes < 0 -> "-"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }

        fun formatChannels(channels: Int?): String = when (channels) {
            1 -> "Mono"
            2 -> "Stereo"
            null -> "-"
            else -> "$channels ch"
        }

        fun formatDateTime(epochSec: Long): String {
            if (epochSec <= 0) return "-"
            return try {
                SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.US)
                    .format(Date(epochSec * 1000))
            } catch (_: Exception) {
                "-"
            }
        }

        private fun qualityBadge(
            format: String,
            bitDepth: Int?,
            sampleRateHz: Int?,
        ): String? {
            if (format != "FLAC") return null
            val label = if ((bitDepth ?: 16) > 16) "Hi-Res Lossless" else "Lossless"
            return buildList {
                add(label)
                bitDepth?.let { add("$it-bit") }
                sampleRateHz?.let { add(formatSampleRate(it)) }
            }.joinToString(" · ")
        }

        private fun sniffImageFormat(bytes: ByteArray): String? = when {
            bytes.size >= 4 &&
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "PNG"
            bytes.size >= 2 &&
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "JPEG"
            bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() -> "WEBP"
            bytes.size >= 3 &&
                bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() -> "GIF"
            else -> null
        }
    }
}
