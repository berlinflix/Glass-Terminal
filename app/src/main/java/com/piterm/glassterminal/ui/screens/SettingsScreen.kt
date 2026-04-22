package com.piterm.glassterminal.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piterm.glassterminal.model.ConnectionState
import com.piterm.glassterminal.service.SshConnectionManager
import com.piterm.glassterminal.ui.theme.*

/**
 * Settings screen — SSH key management, public key display/copy,
 * connection info, and app configuration.
 */
@Composable
fun SettingsScreen(
    sshManager: SshConnectionManager,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by sshManager.connectionState.collectAsState()
    var publicKey by remember { mutableStateOf(sshManager.getPublicKey()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Connection Status ────────────────────────────────────────────
        SettingsSection(title = "Connection") {
            val (statusText, statusColor) = when (connectionState) {
                is ConnectionState.Connected -> "Connected" to NeonGreen
                is ConnectionState.Connecting -> "Connecting…" to AmberGlow
                else -> "Disconnected" to TextMuted
            }

            SettingsRow(
                icon = Icons.Default.Wifi,
                label = "Status",
                value = statusText,
                valueColor = statusColor
            )

            if (connectionState is ConnectionState.Connected) {
                val device = (connectionState as ConnectionState.Connected).device
                SettingsRow(
                    icon = Icons.Default.Router,
                    label = "Pi Address",
                    value = device.ipAddress,
                    valueColor = ElectricCyan
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        sshManager.disconnect()
                        onDisconnect()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HotPink.copy(alpha = 0.15f),
                        contentColor = HotPink
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = "Disconnect")
                    Spacer(Modifier.width(8.dp))
                    Text("Disconnect")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── SSH Keys ─────────────────────────────────────────────────────
        SettingsSection(title = "SSH Keys") {
            if (sshManager.hasKeys()) {
                Text(
                    text = "Your Ed25519 Public Key",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Key display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElevatedSurface)
                        .padding(12.dp)
                ) {
                    Text(
                        text = publicKey.ifBlank { "No key found" },
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonGreen,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("SSH Public Key", publicKey)
                            )
                            Toast.makeText(context, "Public key copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Key", fontSize = 13.sp)
                    }

                    Spacer(Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HotPink),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Regenerate", fontSize = 13.sp)
                    }
                }
            } else {
                Text(
                    text = "No SSH keys generated yet. Keys will be created automatically on first connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        publicKey = sshManager.generateKeys()
                        Toast.makeText(context, "Keys generated!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = DeepNavy
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = "Generate")
                    Spacer(Modifier.width(8.dp))
                    Text("Generate SSH Keys", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Instructions ─────────────────────────────────────────────────
        SettingsSection(title = "Setup Guide") {
            val steps = listOf(
                "1. Run pi_setup.sh on your Pi (as root)",
                "2. Generate SSH keys in this app",
                "3. Copy the public key to Pi's ~/.ssh/authorized_keys",
                "4. Turn on your phone's Wi-Fi hotspot",
                "5. Power on the Pi — it will auto-connect",
                "6. Open this app — Pi will be discovered automatically"
            )
            steps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 3.dp),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    // ── Delete Confirmation Dialog ───────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Regenerate Keys?", color = TextPrimary) },
            text = {
                Text(
                    "This will delete your current SSH keys and generate new ones. " +
                    "You'll need to copy the new public key to your Pi's authorized_keys file.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    sshManager.disconnect()
                    publicKey = sshManager.generateKeys()
                    showDeleteConfirm = false
                    Toast.makeText(context, "New keys generated!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Regenerate", color = HotPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = ElectricCyan,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = label,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            fontSize = 13.sp
        )
    }
}
