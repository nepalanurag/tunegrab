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
 * Only true digital silence is skipped: any silent stretch longer than
 * ~125 ms is tightened down to ~125 ms plus 5% of the excess, so a 5-second
 * dead-air outro becomes ~0.4 s while brief pauses pass through untouched.
 * A small stub of each silence is retained so the cut doesn't sound chopped.
 *
 * Controlled by the "Skip silence" toggle in Settings
 * ([AppSettings.skipSilence], off by default). The toggle is read when the
 * player is built, so it takes effect when playback (re)starts — ExoPlayer
 * only re-evaluates which processors are active on flush, so flipping it
 * mid-stream cannot apply reliably.
 */
class SilenceSkippingRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

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
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf<AudioProcessor>(processor))
            .build()
    }

    companion object {
        /** Only silences of at least this long are skipped (protects musical pauses). */
        private const val MINIMUM_SILENCE_DURATION_US = 500_000L

        /** Fraction of each skipped silence kept, so transitions sound natural. */
        private const val SILENCE_RETENTION_RATIO = 0.05f

        private const val MAX_SILENCE_TO_KEEP_US = 2_000_000L
        private const val MIN_VOLUME_TO_KEEP_PERCENTAGE = 10
        private const val SILENCE_THRESHOLD_LEVEL: Short = 1024
    }
}
