package com.tunegrab.app

import android.app.Application
import com.tunegrab.app.ads.AdsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Application class for both flavors. Crash reporting and settings init
 * happen here; download-engine init is delegated to the flavor's
 * [DirectBridge] (a no-op in the Play Store build, which contains no
 * downloader code).
 */
class YtFlacApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Initializing)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        CrashReporter.milestone(this, "YtFlacApp.onCreate start")
        AppSettings.init(this)
        CrashReporter.milestone(this, "AppSettings.init done")
        // AdMob: initialize for rewarded ads in the free Play Store build.
        // The Pro build (FORCE_PRO) never calls this. The F-Droid build
        // uses a no-op stub (no ads allowed) and hides the ad-gated
        // features entirely — see SettingsScreen.
        AdsManager.init(this)
        DirectBridge.onAppCreate(this)
    }

    /** Flavor bridge hook: run engine init work on the app IO scope. */
    internal fun launchEngineInit(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    /** Flavor bridge hook: publish download-engine readiness. */
    internal fun setEngineState(state: EngineState) {
        _engineState.value = state
    }
}

sealed interface EngineState {
    data object Initializing : EngineState
    data object Ready : EngineState
    data class Failed(val reason: String) : EngineState
}
