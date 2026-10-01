package com.tunegrab.app.player

import android.media.audiofx.Equalizer
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Drives the platform [Equalizer] audio effect for the library player.
 *
 * The effect is attached to the ExoPlayer's audio session, so it only
 * colors this app's playback (never other apps). ExoPlayer allocates its
 * audio session lazily, so the controller listens for
 * [Player.Listener.onAudioSessionIdChanged] and (re)binds the effect
 * whenever the session changes. Settings come from [com.tunegrab.app.AppSettings]
 * and are applied live — no playback restart needed.
 *
 * Everything is defensive: on devices without an equalizer (or when the
 * effect can't be created) the controller silently does nothing.
 */
class EqualizerController {

    /** Device capabilities, probed once for the settings UI. */
    data class EqInfo(
        val bandCount: Int,
        val bandLabels: List<String>,
        val minDb: Int,
        val maxDb: Int,
        val presetNames: List<String>,
    )

    private var equalizer: Equalizer? = null
    private var sessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var player: ExoPlayer? = null

    private var enabled = false
    private var preset = -1
    private var bandLevels: List<Int> = emptyList()

    private val listener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            bind(audioSessionId)
        }
    }

    fun attachTo(player: ExoPlayer) {
        detach()
        this.player = player
        player.addListener(listener)
        bind(player.audioSessionId)
    }

    fun detach() {
        player?.removeListener(listener)
        player = null
        releaseEffect()
    }

    private fun bind(newSessionId: Int) {
        if (newSessionId == sessionId) return
        releaseEffect()
        sessionId = newSessionId
        if (newSessionId == C.AUDIO_SESSION_ID_UNSET) return
        equalizer = runCatching { Equalizer(0, newSessionId) }.getOrNull()
        apply()
    }

    private fun releaseEffect() {
        runCatching { equalizer?.release() }
        equalizer = null
        sessionId = C.AUDIO_SESSION_ID_UNSET
    }

    /**
     * @param enabled master switch
     * @param preset 0-based preset index, or -1 for [bandLevels]
     * @param bandLevels custom band levels in millibels
     */
    fun applySettings(enabled: Boolean, preset: Int, bandLevels: List<Int>) {
        this.enabled = enabled
        this.preset = preset
        this.bandLevels = bandLevels
        apply()
    }

    private fun apply() {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = enabled
            if (!enabled) return@runCatching
            if (preset >= 0 && preset < eq.numberOfPresets) {
                eq.usePreset(preset.toShort())
            } else {
                val n = minOf(bandLevels.size, eq.numberOfBands.toInt())
                for (b in 0 until n) {
                    eq.setBandLevel(b.toShort(), bandLevels[b].toShort())
                }
            }
        }
    }

    companion object {
        /**
         * Probes the device's equalizer capabilities for the settings UI.
         * Creates a throwaway effect on the global mix and releases it
         * immediately; never enables it. Returns null when unsupported.
         */
        fun probe(): EqInfo? {
            val eq = runCatching { Equalizer(0, 0) }.getOrNull() ?: return null
            return try {
                val range = eq.bandLevelRange // millibels, e.g. [-1500, 1500]
                val bands = eq.numberOfBands.toInt()
                EqInfo(
                    bandCount = bands,
                    bandLabels = (0 until bands).map { formatFreq(eq.getCenterFreq(it.toShort())) },
                    minDb = range[0] / 100,
                    maxDb = range[1] / 100,
                    presetNames = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
                )
            } catch (_: Exception) {
                null
            } finally {
                runCatching { eq.release() }
            }
        }

        /** "60000" millihertz -> "60 Hz"; pure and unit-testable. */
        fun formatFreq(milliHz: Int): String {
            val hz = milliHz / 1000
            return if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
        }

        /**
         * Reads a device preset's band levels (millibels) via a throwaway
         * effect on the global mix; never enables it. Returns null when the
         * effect or preset is unavailable. Used so the settings UI's sliders
         * can mirror the curve of the selected preset.
         */
        fun presetBandLevels(preset: Int): List<Int>? {
            val eq = runCatching { Equalizer(0, 0) }.getOrNull() ?: return null
            return try {
                if (preset < 0 || preset >= eq.numberOfPresets) return null
                eq.usePreset(preset.toShort())
                (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()).toInt() }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { eq.release() }
            }
        }
    }
}
