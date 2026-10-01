package com.tunegrab.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure-JVM tests for [FlacParser]: builds minimal FLAC headers in memory
 * (fLaC marker + STREAMINFO block) and checks the extracted stream info.
 */
class FlacParserTest {

    /** Builds a minimal FLAC file: marker + one STREAMINFO block. */
    private fun flacBytes(
        sampleRateHz: Int,
        channels: Int,
        bitsPerSample: Int,
        lastBlock: Boolean = true,
    ): ByteArray {
        val streamInfo = ByteArray(34)
        val buf = ByteBuffer.wrap(streamInfo).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0) // min block size
        buf.putShort(0) // max block size
        buf.put(byteArrayOf(0, 0, 0)) // min frame size
        buf.put(byteArrayOf(0, 0, 0)) // max frame size
        // Packed 64-bit field: 20 bits sample rate, 3 bits (channels-1),
        // 5 bits (bitsPerSample-1), 36 bits total samples.
        val packed = (sampleRateHz.toLong() shl 44) or
            ((channels - 1).toLong() shl 41) or
            ((bitsPerSample - 1).toLong() shl 36)
        buf.putLong(packed)
        buf.put(ByteArray(16)) // md5

        val header = byteArrayOf(
            'f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte(),
            ((if (lastBlock) 0x80 else 0x00) or 0x00).toByte(), // block type 0 = STREAMINFO
            0, 0, 34, // length
        )
        return header + streamInfo
    }

    @Test
    fun `parses 48kHz stereo 16-bit STREAMINFO`() {
        val info = FlacParser.parse(ByteArrayInputStream(flacBytes(48000, 2, 16)))
        assertEquals(48000, info?.sampleRateHz)
        assertEquals(2, info?.channels)
        assertEquals(16, info?.bitsPerSample)
    }

    @Test
    fun `parses 44_1kHz stereo 24-bit STREAMINFO`() {
        val info = FlacParser.parse(ByteArrayInputStream(flacBytes(44100, 2, 24)))
        assertEquals(44100, info?.sampleRateHz)
        assertEquals(2, info?.channels)
        assertEquals(24, info?.bitsPerSample)
    }

    @Test
    fun `parses mono 96kHz 24-bit STREAMINFO`() {
        val info = FlacParser.parse(ByteArrayInputStream(flacBytes(96000, 1, 24)))
        assertEquals(96000, info?.sampleRateHz)
        assertEquals(1, info?.channels)
        assertEquals(24, info?.bitsPerSample)
    }

    @Test
    fun `returns null for non-FLAC data`() {
        assertNull(FlacParser.parse(ByteArrayInputStream("ID3....".toByteArray())))
        assertNull(FlacParser.parse(ByteArrayInputStream(ByteArray(0))))
        assertNull(FlacParser.parse(ByteArrayInputStream("fLaX".toByteArray())))
    }

    @Test
    fun `returns null when STREAMINFO is truncated`() {
        val truncated = flacBytes(48000, 2, 16).copyOf(20)
        assertNull(FlacParser.parse(ByteArrayInputStream(truncated)))
    }
}
