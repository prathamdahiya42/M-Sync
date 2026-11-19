package com.classjam.network.hotspot

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface NetworkEvent {
    data class Available(val network: Network) : NetworkEvent
    data class Lost(val network: Network) : NetworkEvent
    data class CapabilitiesChanged(val network: Network, val capabilities: NetworkCapabilities) : NetworkEvent
}

class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkEvents = MutableSharedFlow<NetworkEvent>(extraBufferCapacity = 16)
    val networkEvents: SharedFlow<NetworkEvent> = _networkEvents.asSharedFlow()

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    fun startMonitoring() {
        if (activeCallback != null) return

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _networkEvents.tryEmit(NetworkEvent.Available(network))
            }

            override fun onLost(network: Network) {
                _networkEvents.tryEmit(NetworkEvent.Lost(network))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                _networkEvents.tryEmit(NetworkEvent.CapabilitiesChanged(network, networkCapabilities))
            }
        }

        activeCallback = callback
        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopMonitoring() {
        activeCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Ignored
            }
        }
        activeCallback = null
    }
}
