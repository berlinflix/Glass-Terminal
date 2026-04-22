package com.piterm.glassterminal.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piterm.glassterminal.ui.theme.*

/**
 * Status indicator showing connection state with animated effects.
 */
@Composable
fun StatusIndicator(
    isConnected: Boolean,
    isScanning: Boolean,
    statusText: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val scanAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isConnected) GreenDim else CyanDim
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .then(
                    if (isScanning) Modifier.scale(pulseScale).alpha(scanAlpha)
                    else Modifier
                )
                .clip(CircleShape)
                .background(
                    if (isConnected) NeonGreen else ElectricCyan
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = if (isConnected) NeonGreen else ElectricCyan,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        )
    }
}


/**
 * Card displaying a discovered Pi device.
 */
@Composable
fun ConnectionCard(
    hostname: String,
    ipAddress: String,
    discoveryMethod: String,
    isConnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "card")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderPulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = if (isConnecting) borderAlpha * 0.15f else 0.08f),
                        VividPurple.copy(alpha = if (isConnecting) borderAlpha * 0.1f else 0.05f),
                    )
                )
            )
            .background(CardSurface.copy(alpha = 0.7f))
            .then(
                Modifier.clickable(enabled = !isConnecting, onClick = onClick)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyanDim),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isConnecting) Icons.Default.WifiFind else Icons.Default.Wifi,
                    contentDescription = "Pi",
                    tint = ElectricCyan,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hostname.ifBlank { "Raspberry Pi" },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = ipAddress,
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = discoveryMethod,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            if (isConnecting) {
                StatusIndicator(
                    isConnected = false,
                    isScanning = true,
                    statusText = "Connecting…"
                )
            }
        }
    }
}
