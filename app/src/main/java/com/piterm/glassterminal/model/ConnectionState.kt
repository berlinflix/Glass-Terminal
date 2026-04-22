package com.piterm.glassterminal.model

/**
 * Represents the full lifecycle state of the connection to the Pi.
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Scanning : ConnectionState()
    data class Discovered(val device: PiDevice) : ConnectionState()
    data class Connecting(val device: PiDevice, val status: String = "Authenticating…") : ConnectionState()
    data class Connected(
        val device: PiDevice,
        val sshReady: Boolean = false,
        val vncTunnelReady: Boolean = false,
        val websockifyTunnelReady: Boolean = false,
    ) : ConnectionState()
    data class Error(val message: String, val device: PiDevice? = null) : ConnectionState()
}
