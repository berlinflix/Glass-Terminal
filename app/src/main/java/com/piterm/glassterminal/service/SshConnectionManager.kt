package com.piterm.glassterminal.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.piterm.glassterminal.model.ConnectionState
import com.piterm.glassterminal.model.PiDevice
import com.piterm.glassterminal.model.VncServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
 * - Ed25519 key-based authentication
 * - Local port forwarding for VNC (5901) and websockify (6080)
 * - Interactive shell session for the terminal
 * - Host key fingerprint verification on first connect
 */
@Suppress("SpellCheckingInspection")
class SshConnectionManager(private val context: Context) {

    init {
        // Register EdDSA provider for SSHJ to support Ed25519 keys
        java.security.Security.addProvider(net.i2p.crypto.eddsa.EdDSASecurityProvider())
    }

    companion object {
        private const val TAG = "SshConnMgr"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val COMMAND_TIMEOUT_MS = 5_000L
        private const val VNC_START_TIMEOUT_MS = 15_000L
        private const val VNC_STOP_TIMEOUT_MS = 10_000L

        private const val PI_VNC_PORT = 5901
        private const val PI_WEBSOCKIFY_PORT = 6080

        const val LOCAL_VNC_PORT = 15901
        const val LOCAL_WEBSOCKIFY_PORT = 16080

        private const val KEY_VNC_PASSWORD = "vnc_password"

        private const val PREFS_NAME = "glassterminal_ssh"
        private const val KEY_HOST_FINGERPRINT = "host_fingerprint_"
        private const val KEY_SSH_USERNAME = "ssh_username"
    }

    private val keyManager = KeyManager(context)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _vncServerState = MutableStateFlow<VncServerState>(VncServerState.Stopped)
    val vncServerState: StateFlow<VncServerState> = _vncServerState.asStateFlow()

    private var sshClient: SSHClient? = null
    private var shellSession: Session? = null
    private var shell: Session.Shell? = null
    private var vncForwarderJob: Job? = null
    private var websockifyForwarderJob: Job? = null
    private var keepAliveJob: Job? = null
    private var connectionScope: CoroutineScope? = null
    private var vncServerSocket: ServerSocket? = null
    private var websockifyServerSocket: ServerSocket? = null

    private data class RemoteCommandResult(
        val stdout: String = "",
        val stderr: String = "",
        val exitStatus: Int? = null,
        val timedOut: Boolean = false,
        val transportError: String? = null,
    ) {
        val combinedOutput: String
            get() = listOf(stdout, stderr, transportError)
                .map { it?.trim().orEmpty() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")

        val succeeded: Boolean
            get() = !timedOut && transportError == null && (exitStatus == null || exitStatus == 0)
    }

    /**
     * Establishes the full connection pipeline:
     * 1. SSH connect + Ed25519 auth
     * 2. Start local port forward for VNC (5901 -> 15901)
     * 3. Start local port forward for websockify (6080 -> 16080)
     * 4. Publish the connected state
     * 5. Start the foreground service
     */
    suspend fun connect(device: PiDevice) = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.Connecting(device, "Establishing SSH...")

            if (!keyManager.hasKeyPair()) {
                _connectionState.value = ConnectionState.Connecting(device, "Generating SSH keys...")
                keyManager.generateKeyPair()
            }

            val client = SSHClient()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val fingerprintKey = KEY_HOST_FINGERPRINT + device.ipAddress
            val savedFingerprint = prefs.getString(fingerprintKey, null)

            if (savedFingerprint == null) {
                client.addHostKeyVerifier(object : HostKeyVerifier {
                    override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
                        val fingerprint = net.schmizz.sshj.common.SecurityUtils.getFingerprint(key)
                        prefs.edit { putString(fingerprintKey, fingerprint) }
                        Log.i(TAG, "TOFU saved host fingerprint $fingerprint for ${device.ipAddress}")
                        return true
                    }

                    override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> {
                        return emptyList()
                    }
                })
            } else {
                client.addHostKeyVerifier(object : HostKeyVerifier {
                    override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
                        val fingerprint = net.schmizz.sshj.common.SecurityUtils.getFingerprint(key)
                        val match = fingerprint == savedFingerprint
                        if (!match) {
                            Log.e(TAG, "Host key mismatch. Expected $savedFingerprint, got $fingerprint")
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

            _connectionState.value = ConnectionState.Connecting(
                device,
                "Connecting to ${device.ipAddress}..."
            )
            client.connect(device.ipAddress, device.port)

            _connectionState.value = ConnectionState.Connecting(
                device,
                "Authenticating with Ed25519 key..."
            )
            val keyPair = keyManager.loadKeyPair()
            val keyProvider = Ed25519KeyProvider(keyPair)
            client.authPublickey(getUsernameForDevice(), keyProvider)

            sshClient = client
            Log.i(TAG, "SSH authenticated to ${device.ipAddress}")

            _connectionState.value = ConnectionState.Connecting(device, "Setting up secure tunnels...")
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            connectionScope = scope

            vncForwarderJob = scope.launch {
                startPortForward(
                    client = client,
                    localPort = LOCAL_VNC_PORT,
                    remoteHost = "127.0.0.1",
                    remotePort = PI_VNC_PORT,
                    isVnc = true,
                )
            }

            websockifyForwarderJob = scope.launch {
                startPortForward(
                    client = client,
                    localPort = LOCAL_WEBSOCKIFY_PORT,
                    remoteHost = "127.0.0.1",
                    remotePort = PI_WEBSOCKIFY_PORT,
                    isVnc = false,
                )
            }

            delay(500)

            _connectionState.value = ConnectionState.Connected(
                device = device,
                sshReady = true,
                vncTunnelReady = true,
                websockifyTunnelReady = true,
            )

            scope.launch { checkVncStatus() }
            Log.i(TAG, "Fully connected: SSH, VNC tunnel, and websockify tunnel")

            startForegroundService()
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            val msg = when {
                e.message?.contains("Auth fail") == true ->
                    "Authentication failed. Is your public key in the Pi's authorized_keys?"
                e.message?.contains("HOST KEY MISMATCH") == true ->
                    "Host key verification failed. Clear the saved fingerprint if the Pi was reinstalled."
                e.message?.contains("connect") == true ->
                    "Cannot reach ${device.ipAddress}. Is the Pi powered on and connected to your hotspot?"
                else -> "Connection error: ${e.message}"
            }
            _connectionState.value = ConnectionState.Error(msg, device)
            cleanupResources()
        }
    }

    /**
     * Opens an interactive SSH shell session.
     */
    suspend fun openShell(): Triple<InputStream, OutputStream, Session.Shell>? =
        withContext(Dispatchers.IO) {
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

    fun getPublicKey(): String = keyManager.getPublicKeyString()

    fun hasKeys(): Boolean = keyManager.hasKeyPair()

    fun generateKeys(): String = keyManager.generateKeyPair()

    /**
     * Disconnects everything gracefully and updates state.
     */
    fun disconnect() {
        cleanupResources()
        _connectionState.value = ConnectionState.Disconnected
        _vncServerState.value = VncServerState.Stopped
        stopForegroundService()
        Log.i(TAG, "Disconnected.")
    }

    /**
     * Spawns a virtual VNC desktop on the Pi and ensures websockify is running.
     */
    suspend fun startVncServer(): Boolean = withContext(Dispatchers.IO) {
        _vncServerState.value = VncServerState.Starting
        try {
            val passwordConfigured = execResult("test -f ~/.vnc/passwd")
            if (!passwordConfigured.succeeded) {
                _vncServerState.value = VncServerState.Error(
                    "VNC password not configured. Run `vncserver :1` once over SSH first."
                )
                return@withContext false
            }

            val websockifyInstalled = execResult("command -v websockify >/dev/null 2>&1")
            if (!websockifyInstalled.succeeded) {
                _vncServerState.value = VncServerState.Error("websockify is not installed on the Pi.")
                return@withContext false
            }

            val vncResult = execResult("vncserver :1", timeoutMs = VNC_START_TIMEOUT_MS)
            Log.i(TAG, "vncserver output: ${vncResult.combinedOutput}")

            val vncAlreadyRunning =
                vncResult.combinedOutput.contains("already running", ignoreCase = true)

            if (vncResult.timedOut) {
                _vncServerState.value = VncServerState.Error(
                    "vncserver timed out. Make sure the VNC password is already configured on the Pi."
                )
                return@withContext false
            }

            if (!vncResult.succeeded && !vncAlreadyRunning) {
                val message = vncResult.combinedOutput.ifBlank { "Unknown VNC error" }
                _vncServerState.value = VncServerState.Error("VNC failed: $message")
                return@withContext false
            }

            val websockifyStart = if (isWebsockifyRunning()) {
                RemoteCommandResult(exitStatus = 0)
            } else {
                execResult("websockify --daemon 6080 localhost:5901")
            }
            Log.i(TAG, "websockify output: ${websockifyStart.combinedOutput}")

            val initialWebsockifyRunning = isWebsockifyRunning()
            if (!websockifyStart.succeeded && !initialWebsockifyRunning) {
                val message = websockifyStart.combinedOutput.ifBlank { "Unknown websockify error" }
                _vncServerState.value = VncServerState.Error("websockify failed: $message")
                return@withContext false
            }

            delay(750)
            val vncRunning = isVncRunning()
            val websockifyRunning = isWebsockifyRunning()
            if (vncRunning && websockifyRunning) {
                _vncServerState.value = VncServerState.Running
                Log.i(TAG, "Desktop spawned: VNC and websockify active")
                true
            } else {
                val message = when {
                    !vncRunning -> "VNC process not found after start"
                    else -> "websockify did not stay running after start"
                }
                _vncServerState.value = VncServerState.Error(message)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VNC server", e)
            _vncServerState.value = VncServerState.Error("Start failed: ${e.message}")
            false
        }
    }

    /**
     * Stops the virtual VNC desktop and any matching websockify bridge.
     */
    suspend fun stopVncServer(): Boolean = withContext(Dispatchers.IO) {
        _vncServerState.value = VncServerState.Stopping
        try {
            val result = execResult(
                "vncserver -kill :1 >/dev/null 2>&1 || true; " +
                    "pkill -f 'websockify.*6080.*5901' >/dev/null 2>&1 || true",
                timeoutMs = VNC_STOP_TIMEOUT_MS,
            )
            Log.i(TAG, "Kill desktop output: ${result.combinedOutput}")

            delay(250)
            val vncStillRunning = isVncRunning()
            val websockifyStillRunning = isWebsockifyRunning()

            if (!vncStillRunning && !websockifyStillRunning) {
                _vncServerState.value = VncServerState.Stopped
                Log.i(TAG, "Desktop stopped and RAM reclaimed")
                true
            } else {
                val message = buildString {
                    if (vncStillRunning) append("VNC server is still running. ")
                    if (websockifyStillRunning) append("websockify is still running.")
                }.trim()
                _vncServerState.value = VncServerState.Error(message)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop VNC server", e)
            _vncServerState.value = VncServerState.Error("Kill failed: ${e.message}")
            false
        }
    }

    /**
     * Checks whether the VNC server is running and ensures websockify is available.
     */
    suspend fun checkVncStatus() = withContext(Dispatchers.IO) {
        try {
            val vncRunning = isVncRunning()
            if (!vncRunning) {
                _vncServerState.value = VncServerState.Stopped
                Log.i(TAG, "No VNC server running")
                return@withContext
            }

            if (!isWebsockifyRunning()) {
                execResult("websockify --daemon 6080 localhost:5901")
            }

            if (isWebsockifyRunning()) {
                _vncServerState.value = VncServerState.Running
                Log.i(TAG, "VNC server and websockify are running")
            } else {
                _vncServerState.value = VncServerState.Error(
                    "Desktop is running, but websockify is unavailable."
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check VNC status: ${e.message}")
        }
    }

    fun saveVncPassword(password: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_VNC_PASSWORD, password) }
    }

    fun getVncPassword(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_VNC_PASSWORD, "") ?: ""
    }

    fun saveUsername(username: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_SSH_USERNAME, username) }
    }

    fun getUsername(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SSH_USERNAME, "pi") ?: "pi"
    }

    private suspend fun execResult(
        command: String,
        timeoutMs: Long = COMMAND_TIMEOUT_MS,
    ): RemoteCommandResult = withContext(Dispatchers.IO) {
        val client = sshClient ?: return@withContext RemoteCommandResult(transportError = "Not connected")

        var session: Session? = null
        try {
            session = client.startSession()
            val cmd = session.exec(command)

            val stdoutDeferred = async(Dispatchers.IO) {
                cmd.inputStream.bufferedReader().use { it.readText() }
            }
            val stderrDeferred = async(Dispatchers.IO) {
                cmd.errorStream.bufferedReader().use { it.readText() }
            }

            cmd.join(timeoutMs, TimeUnit.MILLISECONDS)
            val timedOut = cmd.isOpen
            runCatching { cmd.close() }

            val stdout = withTimeoutOrNull(1_000) { stdoutDeferred.await() }.orEmpty()
            val stderr = withTimeoutOrNull(1_000) { stderrDeferred.await() }.orEmpty()

            if (!stdoutDeferred.isCompleted) stdoutDeferred.cancel()
            if (!stderrDeferred.isCompleted) stderrDeferred.cancel()

            RemoteCommandResult(
                stdout = stdout.trimEnd(),
                stderr = stderr.trimEnd(),
                exitStatus = if (timedOut) null else cmd.exitStatus,
                timedOut = timedOut,
            )
        } catch (e: Exception) {
            RemoteCommandResult(transportError = e.message ?: "Unknown SSH error")
        } finally {
            try {
                session?.close()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun isVncRunning(): Boolean {
        val result = execResult("pgrep -f 'Xtightvnc.*:1'")
        return result.succeeded
    }

    private suspend fun isWebsockifyRunning(): Boolean {
        val result = execResult("pgrep -f 'websockify.*6080.*5901'")
        return result.succeeded
    }

    /**
     * Cleans up SSH resources without changing the connection state.
     */
    private fun cleanupResources() {
        keepAliveJob?.cancel()
        vncForwarderJob?.cancel()
        websockifyForwarderJob?.cancel()
        connectionScope?.cancel()

        try {
            vncServerSocket?.close()
        } catch (_: Exception) {
        }
        try {
            websockifyServerSocket?.close()
        } catch (_: Exception) {
        }
        try {
            shell?.close()
        } catch (_: Exception) {
        }
        try {
            shellSession?.close()
        } catch (_: Exception) {
        }
        try {
            sshClient?.disconnect()
        } catch (_: Exception) {
        }

        shell = null
        shellSession = null
        sshClient = null
        vncServerSocket = null
        websockifyServerSocket = null
    }

    /**
     * Local port forwarding via SSH direct-tcpip channels.
     */
    private suspend fun startPortForward(
        client: SSHClient,
        localPort: Int,
        remoteHost: String,
        remotePort: Int,
        isVnc: Boolean,
    ) = withContext(Dispatchers.IO) {
        var serverSocket: ServerSocket? = null
        try {
            serverSocket = ServerSocket()
            serverSocket.reuseAddress = true
            serverSocket.bind(InetSocketAddress("127.0.0.1", localPort))

            if (isVnc) {
                vncServerSocket = serverSocket
            } else {
                websockifyServerSocket = serverSocket
            }

            Log.i(TAG, "Port forward listening: localhost:$localPort -> $remoteHost:$remotePort")

            while (isActive) {
                val localSocket = serverSocket.accept()
                launch {
                    try {
                        val channel = client.newDirectConnection(remoteHost, remotePort)

                        val localIn = localSocket.getInputStream()
                        val localOut = localSocket.getOutputStream()
                        val remoteIn = channel.inputStream
                        val remoteOut = channel.outputStream

                        val toRemote = launch {
                            try {
                                val buf = ByteArray(8192)
                                while (isActive) {
                                    val n = localIn.read(buf)
                                    if (n == -1) break
                                    remoteOut.write(buf, 0, n)
                                    remoteOut.flush()
                                }
                            } catch (_: Exception) {
                            }
                        }

                        val toLocal = launch {
                            try {
                                val buf = ByteArray(8192)
                                while (isActive) {
                                    val n = remoteIn.read(buf)
                                    if (n == -1) break
                                    localOut.write(buf, 0, n)
                                    localOut.flush()
                                }
                            } catch (_: Exception) {
                            }
                        }

                        toRemote.join()
                        toLocal.cancel()
                        channel.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Port forward connection error on $localPort", e)
                    } finally {
                        try {
                            localSocket.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (isActive) {
                Log.e(TAG, "Port forward failed on $localPort", e)
            }
        } finally {
            try {
                serverSocket?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun getUsernameForDevice(): String {
        return getUsername()
    }

    private fun startForegroundService() {
        try {
            SshForegroundService.onDisconnectRequested = { disconnect() }
            val intent = Intent(context, SshForegroundService::class.java)
            context.startForegroundService(intent)
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

    /**
     * Wraps our Ed25519 key pair into SSHJ's KeyProvider interface.
     */
    private class Ed25519KeyProvider(private val keyPair: KeyPair) : KeyProvider {
        override fun getPrivate(): PrivateKey = keyPair.private

        override fun getPublic(): PublicKey = keyPair.public

        override fun getType(): net.schmizz.sshj.common.KeyType {
            return net.schmizz.sshj.common.KeyType.ED25519
        }
    }
}
