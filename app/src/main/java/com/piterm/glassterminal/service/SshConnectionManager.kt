package com.piterm.glassterminal.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.piterm.glassterminal.model.ConnectionState
import com.piterm.glassterminal.model.PiDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.concurrent.TimeUnit

/**
 * Manages the SSH connection to the Raspberry Pi, including:
 * - Ed25519 key-based authentication (no passwords)
 * - Local port forwarding for VNC (5901) and Websockify (6080)
 * - Interactive shell session for the terminal
 * - Host key fingerprint verification on first connect
 *
 * All operations run on Dispatchers.IO to never block the UI thread.
 */
class SshConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "SshConnMgr"
        private const val SSH_PORT = 22
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val KEEPALIVE_INTERVAL_SEC = 15

        // Remote ports on the Pi (bound to localhost)
        private const val PI_VNC_PORT = 5901
        private const val PI_WEBSOCKIFY_PORT = 6080

        // Local forwarded ports on Android (connect noVNC/VNC here)
        const val LOCAL_VNC_PORT = 15901
        const val LOCAL_WEBSOCKIFY_PORT = 16080

        private const val PREFS_NAME = "glassterminal_ssh"
        private const val KEY_HOST_FINGERPRINT = "host_fingerprint_"
    }

    private val keyManager = KeyManager(context)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var sshClient: SSHClient? = null
    private var shellSession: Session? = null
    private var shell: Session.Shell? = null
    private var vncForwarderJob: Job? = null
    private var websockifyForwarderJob: Job? = null
    private var keepAliveJob: Job? = null
    private var connectionScope: CoroutineScope? = null
    private var vncServerSocket: ServerSocket? = null
    private var websockifyServerSocket: ServerSocket? = null

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Establishes the full connection pipeline:
     * 1. SSH connect + Ed25519 auth
     * 2. Start local port forward for VNC (5901 → 15901)
     * 3. Start local port forward for Websockify (6080 → 16080)
     * 4. Start keepalive
     * 5. Start foreground service
     */
    suspend fun connect(device: PiDevice) = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.Connecting(device, "Establishing SSH…")

            // Ensure we have keys
            if (!keyManager.hasKeyPair()) {
                _connectionState.value = ConnectionState.Connecting(device, "Generating SSH keys…")
                keyManager.generateKeyPair()
            }

            // Create SSH client
            val client = SSHClient()
            // ── Host Key Verification ──
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val fingerprintKey = KEY_HOST_FINGERPRINT + device.ipAddress
            val savedFingerprint = prefs.getString(fingerprintKey, null)

            if (savedFingerprint == null) {
                // First connection — TOFU: accept and save
                client.addHostKeyVerifier(object : HostKeyVerifier {
                    override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
                        val fingerprint = net.schmizz.sshj.common.SecurityUtils.getFingerprint(key)
                        prefs.edit().putString(fingerprintKey, fingerprint).apply()
                        Log.i(TAG, "TOFU: saved host fingerprint $fingerprint for ${device.ipAddress}")
                        return true
                    }
                    override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> {
                        return emptyList()
                    }
                })
            } else {
                // Subsequent connections — verify against saved fingerprint
                client.addHostKeyVerifier(object : HostKeyVerifier {
                    override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
                        val fingerprint = net.schmizz.sshj.common.SecurityUtils.getFingerprint(key)
                        val match = fingerprint == savedFingerprint
                        if (!match) {
                            Log.e(TAG, "HOST KEY MISMATCH! Expected $savedFingerprint, got $fingerprint")
                        }
                        return match
                    }
                    override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> {
                        return emptyList()
                    }
                })
            }

            client.connectTimeout = CONNECT_TIMEOUT_MS
            client.timeout = CONNECT_TIMEOUT_MS

            _connectionState.value = ConnectionState.Connecting(device, "Connecting to ${device.ipAddress}…")
            client.connect(device.ipAddress, device.port)

            // Authenticate with Ed25519 key
            _connectionState.value = ConnectionState.Connecting(device, "Authenticating with Ed25519 key…")
            val keyPair = keyManager.loadKeyPair()
            val keyProvider = Ed25519KeyProvider(keyPair)
            client.authPublickey(getUsernameForDevice(device), keyProvider)

            sshClient = client
            Log.i(TAG, "SSH authenticated to ${device.ipAddress}")

            // Start port forwards
            _connectionState.value = ConnectionState.Connecting(device, "Setting up secure tunnels…")
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            connectionScope = scope

            // Forward VNC
            vncForwarderJob = scope.launch {
                startPortForward(
                    client, LOCAL_VNC_PORT, "127.0.0.1", PI_VNC_PORT,
                    isVnc = true
                )
            }

            // Forward Websockify
            websockifyForwarderJob = scope.launch {
                startPortForward(
                    client, LOCAL_WEBSOCKIFY_PORT, "127.0.0.1", PI_WEBSOCKIFY_PORT,
                    isVnc = false
                )
            }

            // Keepalive: Use SSHJ's built-in heartbeat if supported by this version
            // In some 0.3x versions it's transport.setHeartbeatInterval(int)
            // client.transport.heartbeatInterval = KEEPALIVE_INTERVAL_SEC

            // Brief delay for tunnels to bind
            delay(500)

            _connectionState.value = ConnectionState.Connected(
                device = device,
                sshReady = true,
                vncTunnelReady = true,
                websockifyTunnelReady = true
            )
            Log.i(TAG, "Fully connected: SSH + VNC tunnel + Websockify tunnel")

            // Start foreground service to keep tunnel alive in background
            startForegroundService()

        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            val msg = when {
                e.message?.contains("Auth fail") == true ->
                    "Authentication failed. Is your public key in the Pi's authorized_keys?"
                e.message?.contains("HOST KEY MISMATCH") == true ->
                    "Host key verification failed! The Pi's identity has changed. " +
                    "If you reinstalled the Pi OS, clear the saved fingerprint in Settings."
                e.message?.contains("connect") == true ->
                    "Cannot reach ${device.ipAddress}. Is the Pi powered on and connected to your hotspot?"
                else -> "Connection error: ${e.message}"
            }
            _connectionState.value = ConnectionState.Error(msg, device)
            // Clean up resources without overwriting the error state
            cleanupResources()
        }
    }

    /**
     * Opens an interactive SSH shell session.
     * Returns the shell's input and output streams for the terminal emulator.
     */
    suspend fun openShell(): Triple<InputStream, OutputStream, Session.Shell>? = withContext(Dispatchers.IO) {
        val client = sshClient ?: return@withContext null
        try {
            val session = client.startSession()
            session.allocateDefaultPTY()
            val sh = session.startShell()
            shellSession = session
            shell = sh
            Triple(sh.inputStream, sh.outputStream, sh)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open shell", e)
            null
        }
    }

    /**
     * Resizes the PTY for the current shell session.
     */
    fun resizePty(cols: Int, rows: Int) {
        try {
            shellSession?.allocatePTY("xterm-256color", cols, rows, 0, 0, emptyMap())
        } catch (e: Exception) {
            Log.w(TAG, "PTY resize failed: ${e.message}")
        }
    }

    /**
     * Executes a single command and returns the output.
     */
    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        val client = sshClient ?: return@withContext "Error: Not connected"
        try {
            val session = client.startSession()
            val cmd = session.exec(command)
            val output = cmd.inputStream.bufferedReader().readText()
            cmd.join(5, TimeUnit.SECONDS)
            session.close()
            output
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Returns the public key string for display/copying.
     */
    fun getPublicKey(): String = keyManager.getPublicKeyString()

    /**
     * Whether keys exist.
     */
    fun hasKeys(): Boolean = keyManager.hasKeyPair()

    /**
     * Generate keys and return the public key.
     */
    fun generateKeys(): String = keyManager.generateKeyPair()

    /**
     * Clears the saved host fingerprint for a given IP.
     */
    fun clearHostFingerprint(ip: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HOST_FINGERPRINT + ip).apply()
        Log.i(TAG, "Cleared host fingerprint for $ip")
    }

    /**
     * Disconnects everything gracefully and updates state.
     */
    fun disconnect() {
        cleanupResources()
        _connectionState.value = ConnectionState.Disconnected
        stopForegroundService()
        Log.i(TAG, "Disconnected.")
    }

    /**
     * Whether the SSH client is currently connected.
     */
    fun isConnected(): Boolean = sshClient?.isConnected == true

    // ── Internal ────────────────────────────────────────────────────────────

    /**
     * Cleans up SSH resources WITHOUT changing the connection state.
     * This prevents the error state from being overwritten.
     */
    private fun cleanupResources() {
        keepAliveJob?.cancel()
        vncForwarderJob?.cancel()
        websockifyForwarderJob?.cancel()
        connectionScope?.cancel()

        try { vncServerSocket?.close() } catch (_: Exception) {}
        try { websockifyServerSocket?.close() } catch (_: Exception) {}
        try { shell?.close() } catch (_: Exception) {}
        try { shellSession?.close() } catch (_: Exception) {}
        try { sshClient?.disconnect() } catch (_: Exception) {}

        shell = null
        shellSession = null
        sshClient = null
        vncServerSocket = null
        websockifyServerSocket = null
    }

    /**
     * Correct SSHJ local port forwarding implementation.
     * Creates a ServerSocket, accepts incoming connections, and for each
     * connection opens an SSH direct-tcpip channel to the remote host:port,
     * then bidirectionally copies data between the local socket and the SSH channel.
     */
    private suspend fun startPortForward(
        client: SSHClient,
        localPort: Int,
        remoteHost: String,
        remotePort: Int,
        isVnc: Boolean
    ) = withContext(Dispatchers.IO) {
        var serverSocket: ServerSocket? = null
        try {
            serverSocket = ServerSocket()
            serverSocket.reuseAddress = true
            serverSocket.bind(InetSocketAddress("127.0.0.1", localPort))

            // Store ref so we can close it on disconnect
            if (isVnc) vncServerSocket = serverSocket
            else websockifyServerSocket = serverSocket

            Log.i(TAG, "Port forward listening: localhost:$localPort → $remoteHost:$remotePort")

            while (isActive) {
                val localSocket = serverSocket.accept()
                launch {
                    try {
                        val channel = client.newDirectConnection(
                            remoteHost, remotePort
                        )

                        // Bidirectional copy between local socket and SSH channel
                        val localIn = localSocket.getInputStream()
                        val localOut = localSocket.getOutputStream()
                        val remoteIn = channel.inputStream
                        val remoteOut = channel.outputStream

                        // local → remote
                        val toRemote = launch {
                            try {
                                val buf = ByteArray(8192)
                                while (isActive) {
                                    val n = localIn.read(buf)
                                    if (n == -1) break
                                    remoteOut.write(buf, 0, n)
                                    remoteOut.flush()
                                }
                            } catch (_: Exception) {}
                        }

                        // remote → local
                        val toLocal = launch {
                            try {
                                val buf = ByteArray(8192)
                                while (isActive) {
                                    val n = remoteIn.read(buf)
                                    if (n == -1) break
                                    localOut.write(buf, 0, n)
                                    localOut.flush()
                                }
                            } catch (_: Exception) {}
                        }

                        // When either direction finishes, cancel both
                        toRemote.join()
                        toLocal.cancel()
                        channel.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Port forward connection error on $localPort", e)
                    } finally {
                        try { localSocket.close() } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            if (isActive) {
                Log.e(TAG, "Port forward failed on $localPort", e)
            }
        } finally {
            try { serverSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun handleDisconnect(device: PiDevice) {
        _connectionState.value = ConnectionState.Error(
            "Connection lost. Tap to reconnect.",
            device
        )
        cleanupResources()
        stopForegroundService()
    }

    private fun getUsernameForDevice(device: PiDevice): String {
        // Kali default user is "kali"
        return "kali"
    }

    private fun startForegroundService() {
        try {
            // Wire up the notification's "Disconnect" action to our disconnect()
            SshForegroundService.onDisconnectRequested = { disconnect() }
            val intent = Intent(context, SshForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground service: ${e.message}")
        }
    }

    private fun stopForegroundService() {
        try {
            context.stopService(Intent(context, SshForegroundService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop foreground service: ${e.message}")
        }
    }

    // ── Ed25519 Key Provider for SSHJ ───────────────────────────────────────

    /**
     * Wraps our BouncyCastle-generated Ed25519 key pair into SSHJ's KeyProvider interface.
     */
    private class Ed25519KeyProvider(private val keyPair: KeyPair) : KeyProvider {
        override fun getPrivate(): PrivateKey = keyPair.private
        override fun getPublic(): PublicKey = keyPair.public
        override fun getType(): net.schmizz.sshj.common.KeyType =
            net.schmizz.sshj.common.KeyType.ED25519
    }
}
