package com.tunegrab.app.library

import java.io.EOFException
import java.io.InputStream

/**
 * Minimal FLAC header parser — pure JVM, no Android dependencies.
 *
 * Reads the "fLaC" marker and the metadata blocks up to and including the
 * mandatory STREAMINFO block, extracting the sample rate, channel count and
 * bits per sample. Used by the song Details view; anything it can't parse
 * yields null and the UI shows an em dash.
 */
data class FlacStreamInfo(
    val sampleRateHz: Int,
    val channels: Int,
    val bitsPerSample: Int,
)

object FlacParser {

    fun parse(input: InputStream): FlacStreamInfo? {
        return try {
            parseOrThrow(input)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseOrThrow(input: InputStream): FlacStreamInfo? {
        val marker = ByteArray(4)
        readFully(input, marker)
        if (!(marker[0] == 'f'.code.toByte() &&
                marker[1] == 'L'.code.toByte() &&
                marker[2] == 'a'.code.toByte() &&
                marker[3] == 'C'.code.toByte())
        ) {
            return null
        }
        while (true) {
            val header = ByteArray(4)
            readFully(input, header)
            val lastBlock = (header[0].toInt() and 0x80) != 0
            val blockType = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            if (blockType == 0) {
                // STREAMINFO: 34 bytes; the audio parameters live in bytes 10-13.
                if (length < 18) return null
                val info = ByteArray(18)
                readFully(input, info)
                skipFully(input, (length - 18).toLong())
                val b10 = info[10].toInt() and 0xFF
                val b11 = info[11].toInt() and 0xFF
                val b12 = info[12].toInt() and 0xFF
                val b13 = info[13].toInt() and 0xFF
                val sampleRate = (b10 shl 12) or (b11 shl 4) or (b12 ushr 4)
                val channels = ((b12 and 0x0E) ushr 1) + 1
                val bitsPerSample = (((b12 and 0x01) shl 4) or (b13 ushr 4)) + 1
                if (sampleRate <= 0 || channels <= 0 || bitsPerSample <= 0) {
                    return null
                }
                return FlacStreamInfo(sampleRate, channels, bitsPerSample)
            }
            skipFully(input, length.toLong())
            if (lastBlock) return null
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val n = input.read(buf, offset, buf.size - offset)
            if (n < 0) throw EOFException("Unexpected end of FLAC stream")
            offset += n
        }
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                // skip() may return 0; fall back to a single-byte read.
                if (input.read() < 0) throw EOFException("Unexpected end of FLAC stream")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }
}
