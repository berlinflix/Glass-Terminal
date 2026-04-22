package com.piterm.glassterminal.model

/**
 * Represents the lifecycle state of the remote VNC desktop on the Pi.
 * The desktop only exists when explicitly spawned ("Desktop on Demand").
 */
sealed class VncServerState {
    /** No VNC server running — Pi is in pure CLI attack mode (~80MB RAM). */
    data object Stopped : VncServerState()

    /** VNC server + websockify are being spawned on the Pi. */
    data object Starting : VncServerState()

    /** VNC desktop is live and connectable on :5901 (~200MB RAM). */
    data object Running : VncServerState()

    /** Desktop is being killed and RAM reclaimed. */
    data object Stopping : VncServerState()

    /** Something went wrong spawning or killing the desktop. */
    data class Error(val message: String) : VncServerState()
}
