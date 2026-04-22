package com.piterm.glassterminal.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.piterm.glassterminal.model.DiscoveryMethod
import com.piterm.glassterminal.model.PiDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Discovers the Raspberry Pi on the local Wi-Fi hotspot network.
 *
 * Strategy:
 * 1. Primary: mDNS (NsdManager) — looks for _glassterminal._tcp service published by Avahi on Pi
 * 2. Fallback: Rapid TCP port-22 sweep of the hotspot's /24 subnet
 *
 * The mDNS path is instant (< 1 second). The port sweep finishes in ~3 seconds
 * using 32 parallel coroutines with 300ms socket connect timeouts.
 *
 * IMPORTANT: Acquires a WifiManager.MulticastLock for mDNS to work on Android.
 */
class NetworkScanner(private val context: Context) {

    companion object {
        private const val TAG = "NetworkScanner"
        private const val SERVICE_TYPE = "_glassterminal._tcp."
        private const val SSH_SERVICE_TYPE = "_ssh._tcp."
        private const val SSH_PORT = 22
        private const val SCAN_TIMEOUT_MS = 300L
        private const val SCAN_PARALLELISM = 32
    }

    private val _discoveredDevices = MutableStateFlow<List<PiDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<PiDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var scanJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Starts both mDNS and port-scan discovery concurrently.
     */
    fun startDiscovery(scope: CoroutineScope) {
        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        // Acquire multicast lock for mDNS to work on Android
        acquireMulticastLock()

        // Run both strategies in parallel
        scanJob = scope.launch {
            launch { startMdnsDiscovery() }
            launch {
                try {
                    startPortScan()
                } finally {
                    // Ensure isScanning is cleared even if port scan errors out
                    _isScanning.value = false
                }
            }
        }
    }

    /**
     * Stops all discovery mechanisms.
     */
    fun stopDiscovery() {
        _isScanning.value = false
        scanJob?.cancel()
        scanJob = null
        stopMdnsDiscovery()
        releaseMulticastLock()
    }

    /**
     * Add a manually specified device.
     */
    fun addManualDevice(ip: String) {
        val device = PiDevice(
            ipAddress = ip,
            hostname = ip,
            port = SSH_PORT,
            discoveryMethod = DiscoveryMethod.MANUAL
        )
        addDevice(device)
    }

    // ── Multicast Lock ──────────────────────────────────────────────────────

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("glassterminal_mdns").apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.d(TAG, "MulticastLock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire MulticastLock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
            multicastLock = null
            Log.d(TAG, "MulticastLock released")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release MulticastLock", e)
        }
    }

    // ── mDNS Discovery ──────────────────────────────────────────────────────

    private fun startMdnsDiscovery() {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "mDNS discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "mDNS service found: ${serviceInfo.serviceName}")
                // Resolve to get the IP address
                nsdManager?.resolveService(serviceInfo, createResolveListener())
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "mDNS service lost: ${serviceInfo.serviceName}")
                _discoveredDevices.value = _discoveredDevices.value.filter {
                    it.serviceName != serviceInfo.serviceName
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "mDNS discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "mDNS start failed: error $errorCode")
                // Try the SSH service type as fallback
                if (serviceType == SERVICE_TYPE) {
                    try {
                        nsdManager?.discoverServices(
                            SSH_SERVICE_TYPE,
                            NsdManager.PROTOCOL_DNS_SD,
                            createFallbackDiscoveryListener()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "SSH mDNS fallback also failed", e)
                    }
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "mDNS stop failed: error $errorCode")
            }
        }

        try {
            nsdManager?.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start mDNS discovery", e)
        }
    }

    private fun createFallbackDiscoveryListener(): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // Filter for Pi devices by checking txt record or service name
                val name = serviceInfo.serviceName.lowercase()
                if (name.contains("glass") || name.contains("kali") || name.contains("pi")) {
                    nsdManager?.resolveService(serviceInfo, createResolveListener())
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
    }

    private fun createResolveListener(): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "mDNS resolve failed: ${serviceInfo.serviceName}, error $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                Log.i(TAG, "mDNS resolved: ${serviceInfo.serviceName} → $host:${serviceInfo.port}")

                val device = PiDevice(
                    ipAddress = host,
                    hostname = serviceInfo.serviceName,
                    port = serviceInfo.port,
                    serviceName = serviceInfo.serviceName,
                    discoveryMethod = DiscoveryMethod.MDNS
                )
                addDevice(device)
            }
        }
    }

    private fun stopMdnsDiscovery() {
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping mDNS", e)
        }
        discoveryListener = null
    }

    // ── Port Scan Fallback ──────────────────────────────────────────────────

    /**
     * Scans the hotspot's /24 subnet for hosts with port 22 open.
     * Uses 32 parallel coroutines with 300ms timeouts for speed.
     */
    private suspend fun startPortScan() = withContext(Dispatchers.IO) {
        val subnetIp = getHotspotSubnet() ?: run {
            Log.w(TAG, "Cannot determine subnet for port scan")
            return@withContext
        }

        Log.i(TAG, "Starting port scan on subnet ${subnetIp}.0/24")

        // Skip .1 (gateway/phone) and scan .2–.254 in parallel batches
        val addresses = (2..254).map { "$subnetIp.$it" }

        // Use a semaphore-like approach with chunked parallelism
        addresses.chunked(SCAN_PARALLELISM).forEach { chunk ->
            if (!isActive) return@withContext

            val results = chunk.map { ip ->
                async {
                    if (isPortOpen(ip, SSH_PORT)) ip else null
                }
            }.awaitAll().filterNotNull()

            results.forEach { ip ->
                val device = PiDevice(
                    ipAddress = ip,
                    hostname = "pi@$ip",
                    port = SSH_PORT,
                    discoveryMethod = DiscoveryMethod.PORT_SCAN
                )
                addDevice(device)
            }
        }

        Log.i(TAG, "Port scan complete. Found ${_discoveredDevices.value.size} device(s).")
    }

    /**
     * Checks if a TCP port is open on the given host with a fast timeout.
     */
    private fun isPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), SCAN_TIMEOUT_MS.toInt())
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the subnet of the hotspot interface.
     * When the phone IS the hotspot host, WifiManager.dhcpInfo won't have a valid
     * gateway. Instead, we enumerate network interfaces to find the hotspot's IP,
     * which is typically on wlan0/ap0/swlan0 with a 192.168.x.x address.
     */
    private fun getHotspotSubnet(): String? {
        // Strategy 1: Try WifiManager.dhcpInfo (works when phone is a Wi-Fi client)
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wifiManager.dhcpInfo
            val gateway = dhcp.gateway
            if (gateway != 0) {
                val ip = String.format(
                    "%d.%d.%d.%d",
                    gateway and 0xFF,
                    (gateway shr 8) and 0xFF,
                    (gateway shr 16) and 0xFF,
                    (gateway shr 24) and 0xFF
                )
                return ip.substringBeforeLast(".")
            }
        } catch (e: Exception) {
            Log.d(TAG, "DHCP info unavailable", e)
        }

        // Strategy 2: Enumerate network interfaces (works when phone IS the hotspot)
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            for (iface in interfaces) {
                // Look for typical hotspot interface names
                val name = iface.name.lowercase()
                if (name.contains("ap") || name.contains("wlan") || name.contains("swlan")) {
                    for (addr in iface.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress ?: continue
                            Log.i(TAG, "Found hotspot interface $name with IP $ip")
                            return ip.substringBeforeLast(".")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enumerate interfaces", e)
        }

        // Strategy 3: Common hotspot subnets as last resort
        Log.w(TAG, "Falling back to common hotspot subnet 192.168.43")
        return "192.168.43"
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @Synchronized
    private fun addDevice(device: PiDevice) {
        val current = _discoveredDevices.value.toMutableList()
        // Deduplicate by IP
        if (current.none { it.ipAddress == device.ipAddress }) {
            current.add(device)
            _discoveredDevices.value = current
        }
    }
}
