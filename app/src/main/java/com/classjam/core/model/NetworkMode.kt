package com.classjam.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class NetworkMode(val title: String, val description: String) {
    IN_APP_HOTSPOT(
        title = "In-App Hotspot",
        description = "Host creates a local Wi-Fi hotspot directly. Clients connect via QR."
    ),
    MANUAL_HOTSPOT(
        title = "Manual Hotspot / Router",
        description = "Host uses phone hotspot or external Wi-Fi router. Enter SSID/password once."
    ),
    SAME_WIFI(
        title = "Same Wi-Fi Already",
        description = "All phones are already connected to the same Wi-Fi network."
    )
}
