package com.piterm.glassterminal.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.piterm.glassterminal.service.NetworkScanner
import com.piterm.glassterminal.service.SshConnectionManager
import com.piterm.glassterminal.ui.screens.*
import com.piterm.glassterminal.ui.theme.*

/**
 * App navigation — routes between Home, Terminal, VNC, and Settings.
 * Bottom navigation bar with cyberpunk styling.
 */

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "Connect", Icons.Filled.Home, Icons.Outlined.Home)
    data object Terminal : Screen("terminal", "Terminal", Icons.Filled.Terminal, Icons.Outlined.Terminal)
    data object Vnc : Screen("vnc", "Desktop", Icons.Filled.DesktopWindows, Icons.Outlined.DesktopWindows)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

val screens = listOf(Screen.Home, Screen.Terminal, Screen.Vnc, Screen.Settings)

@Composable
fun AppNavigation(
    networkScanner: NetworkScanner,
    sshManager: SshConnectionManager
) {
    var currentRoute by rememberSaveable { mutableStateOf(Screen.Home.route) }

    Scaffold(
        containerColor = DeepNavy,
        bottomBar = {
            NavigationBar(
                containerColor = CardSurface.copy(alpha = 0.95f),
                tonalElevation = 0.dp,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                screens.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentRoute = screen.route },
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label
                            )
                        },
                        label = {
                            Text(
                                text = screen.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) ElectricCyan else TextMuted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricCyan,
                            unselectedIconColor = TextMuted,
                            indicatorColor = CyanDim
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Home Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = if (currentRoute == Screen.Home.route) 0.dp else 10000.dp)
                    .alpha(if (currentRoute == Screen.Home.route) 1f else 0f)
            ) {
                HomeScreen(
                    networkScanner = networkScanner,
                    sshManager = sshManager,
                    onConnected = {
                        currentRoute = Screen.Terminal.route
                    }
                )
            }

            // Terminal Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = if (currentRoute == Screen.Terminal.route) 0.dp else 10000.dp)
                    .alpha(if (currentRoute == Screen.Terminal.route) 1f else 0f)
            ) {
                TerminalScreen(sshManager = sshManager)
            }

            // VNC Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = if (currentRoute == Screen.Vnc.route) 0.dp else 10000.dp)
                    .alpha(if (currentRoute == Screen.Vnc.route) 1f else 0f)
            ) {
                VncScreen(sshManager = sshManager)
            }

            // Settings Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = if (currentRoute == Screen.Settings.route) 0.dp else 10000.dp)
                    .alpha(if (currentRoute == Screen.Settings.route) 1f else 0f)
            ) {
                SettingsScreen(
                    sshManager = sshManager,
                    onDisconnect = {
                        currentRoute = Screen.Home.route
                    }
                )
            }
        }
    }
}
