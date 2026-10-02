package com.tunegrab.app.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which pro features have been unlocked via rewarded ads
 * in the current app session.
 *
 * Unlocks are NOT persisted — each app launch starts locked.
 * This keeps the implementation simple and avoids entitlement
 * bookkeeping.
 */
object AdUnlockManager {

    private val _equalizerUnlocked = MutableStateFlow(false)
    val equalizerUnlocked: StateFlow<Boolean> = _equalizerUnlocked.asStateFlow()

    private val _cleanupUnlocked = MutableStateFlow(false)
    val cleanupUnlocked: StateFlow<Boolean> = _cleanupUnlocked.asStateFlow()

    fun unlockEqualizer() {
        _equalizerUnlocked.value = true
    }

    fun unlockCleanup() {
        _cleanupUnlocked.value = true
    }

    fun isEqualizerUnlocked(): Boolean = _equalizerUnlocked.value

    fun isCleanupUnlocked(): Boolean = _cleanupUnlocked.value

    /** Resets all unlocks (e.g., for testing). */
    fun reset() {
        _equalizerUnlocked.value = false
        _cleanupUnlocked.value = false
    }
}
