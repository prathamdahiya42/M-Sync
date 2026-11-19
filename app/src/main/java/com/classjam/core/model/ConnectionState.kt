package com.classjam.core.model

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class ConnectingWifi(val ssid: String) : ConnectionState
    data object WifiConnected : ConnectionState
    data class ConnectingSocket(val ip: String, val port: Int) : ConnectionState
    data class Connected(val sessionToken: String, val role: Role, val roomName: String) : ConnectionState
    data class Disconnected(val reason: String) : ConnectionState
    data class Error(val message: String, val canRetry: Boolean = true) : ConnectionState
}
