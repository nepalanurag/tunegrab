package com.tunegrab.app.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Verifies the real-time silence skipper's behavior on synthetic PCM:
 * long digital silences are tightened to ~125 ms + 5%, very short pauses
 * pass through untouched, and the disabled processor is a no-op.
 *
 * Mirrors the production configuration in [SilenceSkippingRenderersFactory].
 * Note: in Media3 1.7.1 the minimum-silence parameter sizes an internal
 * keep window (~minimum/4) rather than acting as a hard gate — silences
 * longer than the window are compressed, shorter ones are fully kept.
 */
class SilenceSkippingTest {

    private val sampleRate = 44100
    private val bytesPerSecond = sampleRate * 2 /* channels */ * 2 /* PCM16 bytes */

    /** One PCM chunk: [seconds] of a constant 16-bit stereo sample. */
    private fun chunk(sample: Short, seconds: Double): ByteBuffer {
        val frames = (sampleRate * seconds).toInt()
        val buf = ByteBuffer.allocate(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames * 2) { buf.putShort(sample) }
        buf.flip()
        return buf
    }

    private fun processor(enabled: Boolean): SilenceSkippingAudioProcessor {
        val p = SilenceSkippingAudioProcessor(
            /* minimumSilenceDurationUs= */ 500_000L,
            /* silenceRetentionRatio= */ 0.05f,
            /* maxSilenceToKeepDurationUs= */ 2_000_000L,
            /* minVolumeToKeepPercentage= */ 10,
            /* silenceThresholdLevel= */ 1024,
        )
        p.setEnabled(enabled)
        p.configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        // ExoPlayer always flushes after configure; this allocates the
        // processor's internal silence buffer.
        p.flush()
        return p
    }

    /**
     * Feeds a chunk fully and returns the bytes produced so far. Per the
     * [AudioProcessor] contract, queueInput only consumes input until output
     * is pending, so the same buffer must be re-queued until exhausted —
     * this mirrors what ExoPlayer's AudioProcessingPipeline does.
     */
    private fun feed(p: SilenceSkippingAudioProcessor, input: ByteBuffer): Int {
        var out = 0
        while (input.hasRemaining()) {
            p.queueInput(input)
            val o = p.output
            out += o.remaining()
            o.position(o.limit())
        }
        return out
    }

    private fun drain(p: SilenceSkippingAudioProcessor): Int {
        var total = 0
        while (!p.isEnded) {
            val out = p.output
            total += out.remaining()
            out.position(out.limit())
        }
        return total
    }

    @Test
    fun `long silence in the middle is mostly skipped`() {
        val p = processor(enabled = true)
        assertTrue(p.isActive)
        var outBytes = 0
        outBytes += feed(p, chunk(10_000, 1.0))
        outBytes += feed(p, chunk(0, 2.0))
        outBytes += feed(p, chunk(10_000, 1.0))
        p.queueEndOfStream()
        outBytes += drain(p)
        val outSeconds = outBytes / bytesPerSecond.toDouble()
        // 2 s of silence -> ~100 ms retained (5%); expect ~2.1 s total.
        assertTrue("expected ~2.1s of audio, got ${outSeconds}s", outSeconds in 1.8..2.5)
    }

    @Test
    fun `disabled processor reports inactive so the pipeline bypasses it`() {
        // ExoPlayer's AudioProcessingPipeline only feeds processors where
        // isActive() is true; a disabled skipper never sees audio at all.
        val p = processor(enabled = false)
        assertFalse(p.isActive)
        assertTrue(processor(enabled = true).isActive)
    }

    @Test
    fun `very short pause below the keep window passes through untouched`() {
        val p = processor(enabled = true)
        var outBytes = 0
        outBytes += feed(p, chunk(10_000, 0.5))
        outBytes += feed(p, chunk(0, 0.05))
        outBytes += feed(p, chunk(10_000, 0.5))
        p.queueEndOfStream()
        outBytes += drain(p)
        assertEquals((1.05 * bytesPerSecond).toInt(), outBytes)
    }

    @Test
    fun `moderate silence is tightened to the keep window plus retention`() {
        val p = processor(enabled = true)
        var outBytes = 0
        outBytes += feed(p, chunk(10_000, 0.5))
        outBytes += feed(p, chunk(0, 0.3))
        outBytes += feed(p, chunk(10_000, 0.5))
        p.queueEndOfStream()
        outBytes += drain(p)
        val outSeconds = outBytes / bytesPerSecond.toDouble()
        // 0.3 s silence -> ~125 ms keep window + 5% of the rest ~= 1.13 s total.
        assertTrue("expected ~1.13s of audio, got ${outSeconds}s", outSeconds in 1.05..1.25)
    }
}
