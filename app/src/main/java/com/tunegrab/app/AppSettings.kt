package com.tunegrab.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences, persisted in SharedPreferences and exposed as flows for
 * the UI. [DownloadService] reads the current values when building each
 * yt-dlp request, so changes apply to the next download immediately.
 *
 * Freemium rules live in [FreemiumRules] (unit-tested); this object only
 * persists choices and exposes state. Pro state itself is owned by
 * [LicenseGuard].
 */
object AppSettings {

    private const val PREFS = "ytflac_settings"
    private const val KEY_FILENAME = "filename_style"
    private const val KEY_FORMAT = "audio_format"
    private const val KEY_QUALITY = "audio_quality"
    private const val KEY_STATS = "stats_for_nerds"
    private const val KEY_PRO = "pro_unlocked"
    private const val KEY_PRO_VERIFIED_AT = "pro_verified_at"
    private const val KEY_CROSSFADE = "crossfade_seconds"
    private const val KEY_SKIP_SILENCE = "skip_silence"
    private const val KEY_EQ_ENABLED = "eq_enabled"
    private const val KEY_EQ_PRESET = "eq_preset"
    private const val KEY_EQ_BANDS = "eq_bands"

    private lateinit var prefs: SharedPreferences
    private var appContext: Context? = null

    private val _filenameStyle = MutableStateFlow(FilenameStyle.TITLE_ONLY)
    val filenameStyle: StateFlow<FilenameStyle> = _filenameStyle.asStateFlow()

    private val _audioFormat = MutableStateFlow(AudioFormat.FLAC)
    val audioFormat: StateFlow<AudioFormat> = _audioFormat.asStateFlow()

    private val _audioQuality = MutableStateFlow(AudioQuality.BEST)
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()

    private val _statsForNerds = MutableStateFlow(false)
    val statsForNerds: StateFlow<Boolean> = _statsForNerds.asStateFlow()

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    // Pro Phase 1: crossfade length in seconds (0 = off). The effective
    // value is Pro-gated in CrossfadeController; free users always get 0.
    private val _crossfadeSeconds = MutableStateFlow(0)
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    // Skip silent intros/outros in real time via SilenceSkippingAudioProcessor.
    // Off by default; the toggle in Settings applies live to the player.
    private val _skipSilence = MutableStateFlow(false)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    // Equalizer: system audio effect attached to the player's audio session.
    // preset: 0-based preset index, or -1 for custom band levels (millibels).
    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()
    private val _eqPreset = MutableStateFlow(-1)
    val eqPreset: StateFlow<Int> = _eqPreset.asStateFlow()
    private val _eqBands = MutableStateFlow<List<Int>>(emptyList())
    val eqBands: StateFlow<List<Int>> = _eqBands.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _filenameStyle.value = readEnum(KEY_FILENAME, FilenameStyle.TITLE_ONLY)
        _audioFormat.value = readEnum(KEY_FORMAT, AudioFormat.FLAC)
        _audioQuality.value = readEnum(KEY_QUALITY, AudioQuality.BEST)
        _statsForNerds.value = prefs.getBoolean(KEY_STATS, false)
        _isPro.value = prefs.getBoolean(KEY_PRO, false)
        _crossfadeSeconds.value = prefs.getInt(KEY_CROSSFADE, 0).coerceIn(0, 12)
        _skipSilence.value = prefs.getBoolean(KEY_SKIP_SILENCE, false)
        _eqEnabled.value = prefs.getBoolean(KEY_EQ_ENABLED, false)
        _eqPreset.value = prefs.getInt(KEY_EQ_PRESET, -1)
        _eqBands.value = prefs.getString(KEY_EQ_BANDS, "")
            ?.split(",").orEmpty()
            .mapNotNull { it.toIntOrNull() }
        if (BuildConfig.FORCE_PRO || BuildConfig.INCLUDE_DOWNLOADER) {
            // Personal Pro build and direct (Ko-fi) build: Pro is
            // permanently on, never revoked. The launcher icon is baked
            // per flavor in the manifest, so no runtime swap happens here:
            // toggling the alias that launched this activity would tear it
            // down and look like a crash on first open.
            _isPro.value = true
        }
    }

    /** Crossfade seconds (0-12). Only takes effect for Pro users. */
    fun setCrossfadeSeconds(v: Int) {
        val clamped = v.coerceIn(0, 12)
        prefs.edit().putInt(KEY_CROSSFADE, clamped).apply()
        _crossfadeSeconds.value = clamped
    }

    /** Synchronous read for CrossfadeController. */
    internal fun crossfadeSecondsNow(): Int = _crossfadeSeconds.value

    /**
     * Silence-skip toggle. Persists the choice; read when the player is
     * built, so it takes effect when playback (re)starts.
     */
    fun setSkipSilence(v: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_SILENCE, v).apply()
        _skipSilence.value = v
    }

    /** Synchronous read for SilenceSkippingRenderersFactory. */
    internal fun skipSilenceNow(): Boolean = _skipSilence.value

    /** Equalizer master switch. Applies live to the player via EqualizerController. */
    fun setEqEnabled(v: Boolean) {
        prefs.edit().putBoolean(KEY_EQ_ENABLED, v).apply()
        _eqEnabled.value = v
    }

    /**
     * Equalizer preset index, or -1 for custom bands. Stored band levels are
     * kept so switching back to custom restores them.
     */
    fun setEqPreset(index: Int) {
        prefs.edit().putInt(KEY_EQ_PRESET, index).apply()
        _eqPreset.value = index
    }

    /** Custom equalizer band levels in millibels; also switches to custom preset. */
    fun setEqBands(levels: List<Int>) {
        prefs.edit().putString(KEY_EQ_BANDS, levels.joinToString(",")).apply()
        prefs.edit().putInt(KEY_EQ_PRESET, -1).apply()
        _eqBands.value = levels
        _eqPreset.value = -1
    }

    /**
     * Applies a device preset: persists its index (kept selected) plus its
     * band levels so the sliders show the preset's curve. Unlike [setEqBands],
     * this does not switch to the custom preset.
     */
    fun applyEqPreset(index: Int, levels: List<Int>) {
        prefs.edit().putInt(KEY_EQ_PRESET, index).apply()
        prefs.edit().putString(KEY_EQ_BANDS, levels.joinToString(",")).apply()
        _eqPreset.value = index
        _eqBands.value = levels
    }

    fun setFilenameStyle(v: FilenameStyle) {
        prefs.edit().putString(KEY_FILENAME, v.name).apply()
        _filenameStyle.value = v
    }

    fun setAudioFormat(v: AudioFormat) {
        prefs.edit().putString(KEY_FORMAT, v.name).apply()
        _audioFormat.value = v
    }

    fun setAudioQuality(v: AudioQuality) {
        prefs.edit().putString(KEY_QUALITY, v.name).apply()
        _audioQuality.value = v
    }

    fun setStatsForNerds(v: Boolean) {
        prefs.edit().putBoolean(KEY_STATS, v).apply()
        _statsForNerds.value = v
    }

    /**
     * Pro state is owned by [LicenseGuard] — never call these from UI code.
     * The boolean alone is not trusted: it is always paired with the
     * timestamp of the last live Play Billing confirmation, and
     * [LicenseGuard.refreshEntitlement] re-derives both from Play.
     */
    internal fun confirmProPurchase() {
        prefs.edit()
            .putBoolean(KEY_PRO, true)
            .putLong(KEY_PRO_VERIFIED_AT, System.currentTimeMillis())
            .apply()
        _isPro.value = true
    }

    internal fun revokePro(reason: String) {
        // The personal Pro build can never lose Pro.
        if (BuildConfig.FORCE_PRO) return
        android.util.Log.i("AppSettings", "Pro revoked: $reason")
        prefs.edit()
            .putBoolean(KEY_PRO, false)
            .putLong(KEY_PRO_VERIFIED_AT, 0L)
            .apply()
        _isPro.value = false
    }

    /** Last time Play Billing *live-confirmed* the purchase (0 = never). */
    internal fun lastProVerifiedAt(): Long = prefs.getLong(KEY_PRO_VERIFIED_AT, 0L)

    /** Synchronous read for [LicenseGuard]; UI should collect [isPro]. */
    internal fun isProNow(): Boolean =
        BuildConfig.FORCE_PRO || BuildConfig.INCLUDE_DOWNLOADER || _isPro.value

    // ---------------- freemium rules (delegated, unit-tested) ----------------

    /**
     * Free tier gets MP3 / 192 kbps / title-only filenames. Everything else
     * needs Pro. These "effective" accessors clamp the user's choices so a
     * locked option can never leak into a download.
     */
    fun effectiveFormat(): AudioFormat =
        FreemiumRules.effectiveFormat(_isPro.value, _audioFormat.value)

    fun effectiveQualityArgs(): List<Pair<String, String>> =
        FreemiumRules.effectiveQualityArgs(_isPro.value, _audioFormat.value, _audioQuality.value)

    fun effectiveTemplate(isCollection: Boolean): String =
        FreemiumRules.effectiveTemplate(_isPro.value, _filenameStyle.value, isCollection)

    fun isFormatAllowed(f: AudioFormat) = FreemiumRules.isFormatAllowed(_isPro.value, f)
    fun isQualityAllowed(q: AudioQuality) = FreemiumRules.isQualityAllowed(_isPro.value, q)
    fun isFilenameAllowed(s: FilenameStyle) = FreemiumRules.isFilenameAllowed(_isPro.value, s)

    /** Downloads are a Pro feature; the free tier is a music player. */
    fun canDownload(): Boolean = FreemiumRules.canDownload(_isPro.value)

    /** Radio is a Pro feature. */
    fun canUseRadio(): Boolean = FreemiumRules.canUseRadio(_isPro.value)

    /** The sleep timer is a Pro feature. */
    fun canUseSleepTimer(): Boolean = FreemiumRules.canUseSleepTimer(_isPro.value)

    // ---------------- persistence ----------------

    private inline fun <reified T : Enum<T>> readEnum(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(prefs.getString(key, fallback.name) ?: fallback.name) }
            .getOrDefault(fallback)
}
