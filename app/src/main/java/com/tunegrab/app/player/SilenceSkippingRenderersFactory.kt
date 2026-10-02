package com.tunegrab.app.player

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import com.tunegrab.app.AppSettings

/**
 * [DefaultRenderersFactory] that inserts ExoPlayer's
 * [SilenceSkippingAudioProcessor] into the audio pipeline, so dead air at
 * the start/end of tracks (common in YouTube rips) is skipped in real time.
 *
 * Only near-silence is skipped: any stretch quieter than about -42 dB
 * and longer than ~2 s is tightened down to ~0.5 s plus 5% of the
 * excess, so a 5-second dead-air outro becomes ~0.7 s while musical
 * pauses, breaths, and quiet passages pass through untouched. A small
 * stub of each skipped silence is retained so the cut doesn't sound
 * chopped.
 *
 * Controlled by the "Skip silence" toggle in Settings
 * ([AppSettings.skipSilence], off by default). The enabled flag is pushed
 * live via [setSkipSilenceEnabled] (the PlaybackService forwards the
 * setting), taking effect on the next track; the processor is inert while
 * disabled.
 */
class SilenceSkippingRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    private var processor: SilenceSkippingAudioProcessor? = null

    /** Updates the skipper live; takes effect on the next track. */
    fun setSkipSilenceEnabled(enabled: Boolean) {
        processor?.setEnabled(enabled)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        val processor = SilenceSkippingAudioProcessor(
            /* minimumSilenceDurationUs= */ MINIMUM_SILENCE_DURATION_US,
            /* silenceRetentionRatio= */ SILENCE_RETENTION_RATIO,
            /* maxSilenceToKeepDurationUs= */ MAX_SILENCE_TO_KEEP_US,
            /* minVolumeToKeepPercentage= */ MIN_VOLUME_TO_KEEP_PERCENTAGE,
            /* silenceThresholdLevel= */ SILENCE_THRESHOLD_LEVEL,
        )
        processor.setEnabled(AppSettings.skipSilenceNow())
        this.processor = processor
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf<AudioProcessor>(processor))
            .build()
    }

    companion object {
        /**
         * Only silences of at least this long are skipped. In Media3 this
         * sizes an internal keep window (~minimum/4): anything shorter
         * than ~0.5 s passes through fully, so musical pauses and breaths
         * are never touched — only true dead-air gaps get tightened.
         */
        private const val MINIMUM_SILENCE_DURATION_US = 2_000_000L

        /** Fraction of each skipped silence kept, so transitions sound natural. */
        private const val SILENCE_RETENTION_RATIO = 0.05f

        private const val MAX_SILENCE_TO_KEEP_US = 2_000_000L
        private const val MIN_VOLUME_TO_KEEP_PERCENTAGE = 10
        /**
         * Peak amplitude below which audio counts as silence, in 16-bit
         * units. 256 (~ -42 dB) means only near-silence is cut: the old
         * 1024 (~ -30 dB) also ate quiet musical passages, which sounded
         * like jarring skips on songs with soft sections.
         */
        private const val SILENCE_THRESHOLD_LEVEL: Short = 256
    }
}
