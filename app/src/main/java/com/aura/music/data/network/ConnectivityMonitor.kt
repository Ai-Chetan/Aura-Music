package com.aura.music.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Coarse transport state driving the dynamic-data gate. */
sealed interface NetStatus {
    data object Offline : NetStatus
    data object Wifi : NetStatus
    data object Mobile : NetStatus
    /** Other transports (ethernet, VPN without underlying info, …) — treated as unmetered. */
    data object Other : NetStatus
}

/**
 * Process-wide connectivity observer (needs ACCESS_NETWORK_STATE, already
 * declared). Emits the current transport and updates on every change.
 */
@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow<NetStatus>(currentStatus())
    val status: StateFlow<NetStatus> = _status.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _status.value = currentStatus()
        }

        override fun onLost(network: Network) {
            _status.value = currentStatus()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            _status.value = currentStatus()
        }
    }

    init {
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            // Best-effort: snapshot reads still work without the callback.
        }
    }

    private fun currentStatus(): NetStatus {
        return try {
            val network = connectivityManager.activeNetwork ?: return NetStatus.Offline
            val caps = connectivityManager.getNetworkCapabilities(network)
                ?: return NetStatus.Offline
            // Optimistic: a live network with a transport counts as online.
            // Android strips INTERNET/VALIDATED capabilities on networks it
            // flags (captive portals, blocked validation probes, "no
            // internet" marks) even when they work — trusting those flags
            // reported "offline" on perfectly good Wi-Fi and bricked every
            // dynamic feature. Real failures surface as per-request errors,
            // each with its own retry, instead of a global dead gate.
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetStatus.Wifi
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetStatus.Mobile
                else -> NetStatus.Other
            }
        } catch (_: Exception) {
            NetStatus.Offline
        }
    }
}
