package com.piterm.glassterminal.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piterm.glassterminal.ui.theme.*

/**
 * Mobile "Hacker Keyboard" — a floating toolbar with special keys
 * that injects ANSI escape sequences into the SSH stream.
 *
 * Keys: Tab, Ctrl, Alt, Esc, ↑, ↓, ←, →, |, /
 * Ctrl is a toggle — when active, next key is sent as Ctrl+X.
 */
@Composable
fun HackerKeyboard(
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = CardSurface.copy(alpha = 0.95f),
            )
            .padding(horizontal = 2.dp, vertical = 4.dp)
    ) {
        // Row 1: Modifier keys + special characters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            HackerKey("Esc", isActive = false, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onKeyPress("\u001B") // ESC
            }
            HackerKey("Tab", isActive = false, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onKeyPress("\t")
            }
            HackerKey("Ctrl", isActive = ctrlActive, isToggle = true, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                ctrlActive = !ctrlActive
            }
            HackerKey("Alt", isActive = altActive, isToggle = true, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                altActive = !altActive
            }
            HackerKey("|", isActive = false, modifier = Modifier.weight(0.7f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("|", ctrlActive, altActive, onKeyPress) { ctrlActive = false; altActive = false }
            }
            HackerKey("/", isActive = false, modifier = Modifier.weight(0.7f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("/", ctrlActive, altActive, onKeyPress) { ctrlActive = false; altActive = false }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Row 2: Navigation Keys
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            HackerKey("Home", isActive = false, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("\u001B[H", ctrlActive, altActive, onKeyPress) { ctrlActive = false; altActive = false }
            }
            HackerKey("End", isActive = false, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("\u001B[F", ctrlActive, altActive, onKeyPress) { ctrlActive = false; altActive = false }
            }
            HackerKey("PgUp", isActive = false, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("\u001B[5~", ctrlActive, altActive, onKeyPress) { ctrlActive = false; altActive = false }
            }
            HackerKey("PgDn", isActive = false, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("\u001B[6~", ctrlActive, altActive, onKeyPress) { ctrlActive = false; altActive = false }
            }
            HackerKey("~", isActive = false, modifier = Modifier.weight(0.7f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("~", ctrlActive, altActive, onKeyPress) { ctrlActive = false; altActive = false }
            }
            HackerKey("-", isActive = false, modifier = Modifier.weight(0.7f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("-", ctrlActive, altActive, onKeyPress) { ctrlActive = false; altActive = false }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Row 3: Arrow keys + extras
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            HackerKey("↑", isActive = false, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("\u001B[A", ctrlActive, altActive, onKeyPress) { ctrlActive = false; altActive = false }
            }
            HackerKey("↓", isActive = false, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("\u001B[B", ctrlActive, altActive, onKeyPress) { ctrlActive = false; altActive = false }
            }
            HackerKey("←", isActive = false, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("\u001B[D", ctrlActive, altActive, onKeyPress) { ctrlActive = false; altActive = false }
            }
            HackerKey("→", isActive = false, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("\u001B[C", ctrlActive, altActive, onKeyPress) { ctrlActive = false; altActive = false }
            }
            HackerKey("C", isActive = false, modifier = Modifier.weight(0.7f)) { // Clear screen hotkey shortcut
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("clear\n", false, false, onKeyPress) { ctrlActive = false; altActive = false }
            }
            HackerKey("C-C", isActive = false, modifier = Modifier.weight(0.7f)) { // SIGINT shortcut
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sendKey("C", true, false, onKeyPress) { ctrlActive = false; altActive = false }
            }
        }
    }
}

@Composable
private fun HackerKey(
    label: String,
    isActive: Boolean,
    isToggle: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isActive -> ElectricCyan.copy(alpha = 0.25f)
            isPressed -> ElectricCyan.copy(alpha = 0.12f)
            else -> ElevatedSurface
        },
        label = "keyBg"
    )

    val textColor = when {
        isActive -> ElectricCyan
        else -> TextSecondary
    }

    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            fontFamily = JetBrainsMonoFamily,
            maxLines = 1
        )
    }
}

/**
 * Sends a key with Ctrl/Alt modifiers applied.
 * Ctrl+X is sent as the character (X - 64) in ASCII.
 * Alt+X is sent as ESC + X.
 */
private fun sendKey(
    key: String,
    ctrlActive: Boolean,
    altActive: Boolean,
    onKeyPress: (String) -> Unit,
    resetModifiers: () -> Unit
) {
    val sequence = buildString {
        if (altActive) append("\u001B") // ESC prefix for Alt

        if (ctrlActive && key.length == 1) {
            // Ctrl+character: send ASCII control code
            val char = key[0].uppercaseChar()
            if (char in 'A'..'Z') {
                append((char.code - 64).toChar())
            } else {
                append(key)
            }
        } else {
            append(key)
        }
    }
    onKeyPress(sequence)
    if (ctrlActive || altActive) resetModifiers()
}
