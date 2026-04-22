package com.piterm.glassterminal.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piterm.glassterminal.model.ConnectionState
import com.piterm.glassterminal.model.VncServerState
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
    val vncServerState by sshManager.vncServerState.collectAsState()
    var publicKey by remember { mutableStateOf(sshManager.getPublicKey()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var vncPassword by remember { mutableStateOf(sshManager.getVncPassword()) }
    var sshUsername by remember { mutableStateOf(sshManager.getUsername()) }

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
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "SSH Username",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = sshUsername,
                    onValueChange = { sshUsername = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. pi or kali", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = ElectricCyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        sshManager.saveUsername(sshUsername)
                        Toast.makeText(context, "Username saved!", Toast.LENGTH_SHORT).show()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ElectricCyan,
                        contentColor = DeepNavy
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                }
            }

            if (connectionState is ConnectionState.Connected) {
                Spacer(modifier = Modifier.height(16.dp))
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

        // ── Desktop on Demand ────────────────────────────────────────────
        SettingsSection(title = "Desktop on Demand") {
            // VNC Desktop Status
            val (desktopStatusText, desktopStatusColor) = when (vncServerState) {
                is VncServerState.Running -> "Desktop Active" to NeonGreen
                is VncServerState.Starting -> "Spawning…" to AmberGlow
                is VncServerState.Stopping -> "Killing…" to HotPink
                is VncServerState.Error -> "Error" to HotPink
                else -> "Stopped" to TextMuted
            }

            SettingsRow(
                icon = Icons.Default.DesktopWindows,
                label = "Desktop Status",
                value = desktopStatusText,
                valueColor = desktopStatusColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            // VNC Password
            Text(
                text = "VNC Password",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Set the password you used with vncserver on the Pi (8 chars max).",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = vncPassword,
                    onValueChange = { if (it.length <= 8) vncPassword = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("VNC password", color = TextMuted) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = ElectricCyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        sshManager.saveVncPassword(vncPassword)
                        Toast.makeText(context, "VNC password saved!", Toast.LENGTH_SHORT).show()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ElectricCyan,
                        contentColor = DeepNavy
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Reference
            Text(
                text = "Operations",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            OperationRow(
                emoji = "⚡",
                title = "Attack Mode",
                desc = "Pure CLI • ~80MB RAM"
            )
            OperationRow(
                emoji = "🖥️",
                title = "Graphical Mode",
                desc = "Tap Spawn Desktop • ~200MB RAM"
            )
            OperationRow(
                emoji = "💀",
                title = "Kill Desktop",
                desc = "Nuke VNC • Reclaim RAM instantly"
            )
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
                "1. Flash Pi OS Lite (64-bit) with SSH + Wi-Fi",
                "2. Run mutation script for Kali repositories",
                "3. Install: sudo apt install -y lxde-core lxterminal tightvncserver",
                "4. Run: vncserver :1 (set 8-char password, decline view-only)",
                "5. Install websockify: sudo apt install -y websockify",
                "6. Save the VNC password above in this app",
                "7. Generate SSH keys and copy to Pi",
                "8. Power on Pi → Connect → Spawn Desktop on demand!"
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

@Composable
private fun OperationRow(
    emoji: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}
