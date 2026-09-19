package com.aura.music.data.network

import com.aura.music.data.prefs.DataUsagePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Why dynamic content is currently blocked (null when allowed). */
enum class BlockReason {
    /** No connectivity at all. */
    OFFLINE,
    /** Mobile data + the "don't use mobile data" option is on. */
    MOBILE_SAVER;

    /** Short user-facing line for toasts and placeholders. */
    fun message(): String = when (this) {
        OFFLINE -> "You're offline — connect to load this."
        MOBILE_SAVER -> "Mobile data is off — only downloads play. Change it in Settings."
    }
}

/** Point-in-time gate evaluation for one transport + prefs combo. */
data class GateSnapshot(
    val online: Boolean,
    val onWifi: Boolean,
    val allowed: Boolean,
    val reason: BlockReason?
)

/**
 * Single decision point for every dynamic-data activity (Discover feeds,
 * YouTube search, stream-URL resolution/prefetch, streaming playback,
 * remote artwork).
 *
 * Rules — deliberately minimal:
 * - Offline → blocked.
 * - Mobile data + "don't use mobile data" → blocked (downloads only).
 * - Everything else (Wi-Fi, unrestricted mobile) → allowed, full UX.
 */
@Singleton
class NetworkGate @Inject constructor(
    monitor: ConnectivityMonitor,
    prefs: DataUsagePreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<GateSnapshot> = combine(
        monitor.status,
        prefs.useMobileData
    ) { status, useMobileData ->
        evaluate(status, useMobileData)
    }.stateIn(scope, SharingStarted.Eagerly, GateSnapshot(false, false, false, BlockReason.OFFLINE))

    /** Synchronous snapshot for interceptors / prefetch (no side effects). */
    fun snapshot(): GateSnapshot = state.value

    /** True right now. Used by prefetch + image loading. */
    fun isAllowedNow(): Boolean = state.value.allowed

    /**
     * Runs [action] immediately when allowed. Returns true if it ran, false
     * when the gate held it back (caller toasts [BlockReason.message]).
     */
    fun runIfAllowed(action: () -> Unit): Boolean {
        if (state.value.allowed) {
            action()
            return true
        }
        return false
    }

    companion object {
        fun evaluate(status: NetStatus, useMobileData: Boolean): GateSnapshot {
            return when (status) {
                is NetStatus.Offline ->
                    GateSnapshot(false, false, false, BlockReason.OFFLINE)
                is NetStatus.Mobile ->
                    if (useMobileData) {
                        GateSnapshot(true, false, true, null)
                    } else {
                        GateSnapshot(true, false, false, BlockReason.MOBILE_SAVER)
                    }
                is NetStatus.Wifi, is NetStatus.Other ->
                    GateSnapshot(true, true, true, null)
            }
        }
    }
}
