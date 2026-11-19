package com.classjam.ui.navigation

sealed class Screen {
    data object Home : Screen()
    data object HostLobby : Screen()
    data object QrScan : Screen()
    data object FallbackJoin : Screen()
    data class Connecting(val ssid: String? = null, val ip: String, val port: Int) : Screen()
    data object ClientSession : Screen()
    data object MusicLibrary : Screen()
    data object Queue : Screen()
    data object NowPlaying : Screen()
    data object Calibration : Screen()
    data object Settings : Screen()
    data object GameHostControls : Screen()
    data object GamePlayer : Screen()
}
