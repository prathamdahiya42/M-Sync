package com.classjam.network.hotspot

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface WifiJoinState {
    data object Idle : WifiJoinState
    data class Connecting(val ssid: String) : WifiJoinState
    data class Connected(val ssid: String, val network: Network) : WifiJoinState
    data class Failed(val reason: String) : WifiJoinState
}

class WifiConnectManager(private val context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    private val _state = MutableStateFlow<WifiJoinState>(WifiJoinState.Idle)
    val state: StateFlow<WifiJoinState> = _state.asStateFlow()

    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectToWifi(ssid: String, password: String?) {
        disconnectAndUnbind()

        _state.value = WifiJoinState.Connecting(ssid)

        val specifierBuilder = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)

        if (!password.isNullOrBlank()) {
            specifierBuilder.setWpa2Passphrase(password)
        }

        val networkSpecifier = specifierBuilder.build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(networkSpecifier)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // Bind the entire process to this Wi-Fi network so all sockets/HTTP flow through it
                val bound = connectivityManager.bindProcessToNetwork(network)
                if (bound) {
                    _state.value = WifiJoinState.Connected(ssid, network)
                } else {
                    _state.value = WifiJoinState.Failed("Could not bind process to Wi-Fi network $ssid")
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                connectivityManager.bindProcessToNetwork(null)
                _state.value = WifiJoinState.Failed("Connection to $ssid was lost")
            }

            override fun onUnavailable() {
                super.onUnavailable()
                _state.value = WifiJoinState.Failed("Unable to connect to Wi-Fi $ssid (Timed out or user cancelled)")
            }
        }

        activeCallback = callback
        try {
            connectivityManager.requestNetwork(request, callback)
        } catch (e: SecurityException) {
            _state.value = WifiJoinState.Failed("Permission denied: Nearby devices or location permission required.")
        } catch (e: Exception) {
            _state.value = WifiJoinState.Failed("Wi-Fi join failed: ${e.localizedMessage}")
        }
    }

    fun disconnectAndUnbind() {
        try {
            activeCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            // Already unregistered
        } finally {
            activeCallback = null
            connectivityManager.bindProcessToNetwork(null)
            _state.value = WifiJoinState.Idle
        }
    }
}
