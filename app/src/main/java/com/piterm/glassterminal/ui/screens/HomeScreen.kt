package com.piterm.glassterminal.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piterm.glassterminal.model.ConnectionState
import com.piterm.glassterminal.model.PiDevice
import com.piterm.glassterminal.service.NetworkScanner
import com.piterm.glassterminal.service.SshConnectionManager
import com.piterm.glassterminal.ui.components.ConnectionCard
import com.piterm.glassterminal.ui.components.StatusIndicator
import com.piterm.glassterminal.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Home screen — Pi auto-discovery with radar animation,
 * discovered device cards, manual IP entry, and connection initiation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    networkScanner: NetworkScanner,
    sshManager: SshConnectionManager,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val discoveredDevices by networkScanner.discoveredDevices.collectAsState()
    val isScanning by networkScanner.isScanning.collectAsState()
    val connectionState by sshManager.connectionState.collectAsState()

    var manualIp by remember { mutableStateOf("") }
    var showManualInput by remember { mutableStateOf(false) }

    // Auto-navigate when connected
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            onConnected()
        }
    }

    // Auto-start scanning
    LaunchedEffect(Unit) {
        networkScanner.startDiscovery(scope)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // ── Title ────────────────────────────────────────────────────────
        Text(
            text = "GLASS",
            style = MaterialTheme.typography.displayLarge,
            color = ElectricCyan,
            fontWeight = FontWeight.Black,
            letterSpacing = 8.sp
        )
        Text(
            text = "TERMINAL",
            style = MaterialTheme.typography.headlineMedium,
            color = VividPurple,
            fontWeight = FontWeight.Light,
            letterSpacing = 12.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Radar Scanner Animation ──────────────────────────────────────
        RadarScanner(isScanning = isScanning)

        Spacer(modifier = Modifier.height(24.dp))

        // ── Status ───────────────────────────────────────────────────────
        val statusText = when (val state = connectionState) {
            is ConnectionState.Disconnected ->
                if (isScanning) "Scanning for Pi…" else "Tap to scan"
            is ConnectionState.Scanning -> "Scanning network…"
            is ConnectionState.Discovered -> "Pi found!"
            is ConnectionState.Connecting -> state.status
            is ConnectionState.Connected -> "Connected!"
            is ConnectionState.Error -> state.message
        }

        StatusIndicator(
            isConnected = connectionState is ConnectionState.Connected,
            isScanning = isScanning || connectionState is ConnectionState.Connecting,
            statusText = statusText
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Error message ────────────────────────────────────────────────
        if (connectionState is ConnectionState.Error) {
            val error = connectionState as ConnectionState.Error
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = HotPink.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = HotPink,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = error.message,
                        color = HotPink,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Discovered Devices ───────────────────────────────────────────
        discoveredDevices.forEach { device ->
            ConnectionCard(
                hostname = device.hostname,
                ipAddress = device.ipAddress,
                discoveryMethod = when (device.discoveryMethod) {
                    com.piterm.glassterminal.model.DiscoveryMethod.MDNS -> "via mDNS/Avahi"
                    com.piterm.glassterminal.model.DiscoveryMethod.PORT_SCAN -> "via Port Scan"
                    com.piterm.glassterminal.model.DiscoveryMethod.MANUAL -> "Manual Entry"
                },
                isConnecting = connectionState is ConnectionState.Connecting &&
                        (connectionState as? ConnectionState.Connecting)?.device?.ipAddress == device.ipAddress,
                onClick = {
                    scope.launch {
                        networkScanner.stopDiscovery()
                        sshManager.connect(device)
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Scan / Rescan Button ─────────────────────────────────────────
        if (!isScanning && connectionState !is ConnectionState.Connecting) {
            OutlinedButton(
                onClick = { networkScanner.startDiscovery(scope) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ElectricCyan
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(
                        listOf(ElectricCyan.copy(alpha = 0.5f), VividPurple.copy(alpha = 0.5f))
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                Spacer(Modifier.width(8.dp))
                Text("Rescan Network")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Manual IP Entry ──────────────────────────────────────────────
        TextButton(
            onClick = { showManualInput = !showManualInput },
            colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
        ) {
            Icon(
                if (showManualInput) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Toggle manual",
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Enter IP manually", fontSize = 13.sp)
        }

        if (showManualInput) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("192.168.43.x", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = ElectricCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (manualIp.isNotBlank()) {
                                networkScanner.addManualDevice(manualIp.trim())
                            }
                        }
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (manualIp.isNotBlank()) {
                            networkScanner.addManualDevice(manualIp.trim())
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ElectricCyan,
                        contentColor = DeepNavy
                    )
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Connect")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Animated radar scanner with concentric pulse rings.
 */
@Composable
private fun RadarScanner(isScanning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Concentric pulse rings
        if (isScanning) {
            for (i in 0..2) {
                val delay = i * 600
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1800, delayMillis = delay, easing = EaseOut),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "ring$i"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1800, delayMillis = delay, easing = EaseOut),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "ringAlpha$i"
                )

                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(scale)
                        .alpha(alpha)
                        .border(
                            width = 2.dp,
                            brush = Brush.radialGradient(
                                listOf(ElectricCyan, VividPurple)
                            ),
                            shape = CircleShape
                        )
                )
            }
        }

        // Center icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(CyanDim, PurpleDim)
                    )
                )
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Default.WifiFind else Icons.Default.Wifi,
                contentDescription = "Scanner",
                tint = ElectricCyan,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
