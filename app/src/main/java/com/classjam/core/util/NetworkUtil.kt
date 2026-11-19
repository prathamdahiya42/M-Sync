package com.classjam.core.util

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtil {

    /**
     * Inspects active network interfaces and determines the real IPv4 address
     * on the local Wi-Fi / AP interface. Does NOT hardcode 192.168.43.1.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            
            // Priority list for interface names (hotspot / tethering / wlan)
            val prioritizedInterfaces = interfaces.filter { iface ->
                iface.isUp && !iface.isLoopback
            }.sortedByDescending { iface ->
                val name = iface.name.lowercase()
                when {
                    name.startsWith("ap") || name.startsWith("softap") -> 3
                    name.startsWith("wlan") || name.startsWith("swlan") -> 2
                    name.startsWith("eth") || name.startsWith("rndis") -> 1
                    else -> 0
                }
            }

            for (networkInterface in prioritizedInterfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val hostAddress = address.hostAddress
                        // Ensure it's not a link-local dummy address
                        if (!hostAddress.isNullOrBlank() && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Generates a friendly 4-digit room code for manual fallback joining
     */
    fun generateRoomCode(): String {
        val number = (1000..9999).random()
        return number.toString()
    }
}
