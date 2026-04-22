package com.piterm.glassterminal.model

/**
 * Represents a discovered Raspberry Pi device on the local network.
 */
data class PiDevice(
    val ipAddress: String,
    val hostname: String = "",
    val port: Int = 22,
    val serviceName: String = "",
    val discoveryMethod: DiscoveryMethod = DiscoveryMethod.MDNS
)

enum class DiscoveryMethod {
    MDNS,       // Found via Avahi/NSD
    PORT_SCAN,  // Found via port-22 scan
    MANUAL      // User-entered IP
}
