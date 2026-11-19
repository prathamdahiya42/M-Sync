package com.classjam.network.hotspot

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HotspotState {
    data object Idle : HotspotState
    data object Starting : HotspotState
    data class Active(val ssid: String, val password: String?) : HotspotState
    data class Error(val message: String) : HotspotState
}

class LocalOnlyHotspotManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _state = MutableStateFlow<HotspotState>(HotspotState.Idle)
    val state: StateFlow<HotspotState> = _state.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startHotspot() {
        if (hotspotReservation != null) {
            return
        }
        _state.value = HotspotState.Starting

        val handler = Handler(Looper.getMainLooper())
        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    super.onStarted(reservation)
                    hotspotReservation = reservation
                    var ssid = ""
                    var password: String? = null

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val config = reservation.softApConfiguration
                            ssid = config?.ssid ?: ""
                            password = config?.passphrase
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }

                    // Fallback for API 29 or if softApConfiguration is empty
                    if (ssid.isBlank()) {
                        @Suppress("DEPRECATION")
                        val config = reservation.wifiConfiguration
                        ssid = config?.SSID ?: ""
                        password = config?.preSharedKey
                    }

                    // Clean quotes if present
                    val cleanSsid = ssid.removeSurrounding("\"")
                    val cleanPassword = password?.removeSurrounding("\"")

                    _state.value = HotspotState.Active(
                        ssid = cleanSsid,
                        password = cleanPassword
                    )
                }

                override fun onStopped() {
                    super.onStopped()
                    hotspotReservation = null
                    _state.value = HotspotState.Idle
                }

                override fun onFailed(reason: Int) {
                    super.onFailed(reason)
                    hotspotReservation = null
                    val msg = when (reason) {
                        ERROR_NO_CHANNEL -> "Hotspot failed: No channel available."
                        ERROR_GENERIC -> "Hotspot failed: Generic system error."
                        ERROR_INCOMPATIBLE_MODE -> "Hotspot failed: Incompatible mode (tethering active)."
                        ERROR_TETHERING_DISALLOWED -> "Hotspot failed: Tethering disallowed by system policy."
                        else -> "Hotspot failed with code $reason"
                    }
                    _state.value = HotspotState.Error(msg)
                }
            }, handler)
        } catch (e: SecurityException) {
            _state.value = HotspotState.Error("Location or Nearby Devices permission required to start hotspot.")
        } catch (e: Exception) {
            _state.value = HotspotState.Error("Failed to start hotspot: ${e.localizedMessage}")
        }
    }

    fun stopHotspot() {
        try {
            hotspotReservation?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            hotspotReservation = null
            _state.value = HotspotState.Idle
        }
    }
}
