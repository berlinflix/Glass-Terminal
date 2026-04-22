package com.piterm.glassterminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.piterm.glassterminal.navigation.AppNavigation
import com.piterm.glassterminal.service.NetworkScanner
import com.piterm.glassterminal.service.SshConnectionManager
import com.piterm.glassterminal.ui.theme.GlassTerminalTheme

class MainActivity : ComponentActivity() {

    private lateinit var networkScanner: NetworkScanner
    private lateinit var sshManager: SshConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        networkScanner = NetworkScanner(applicationContext)
        sshManager = SshConnectionManager(applicationContext)

        setContent {
            GlassTerminalTheme {
                AppNavigation(
                    networkScanner = networkScanner,
                    sshManager = sshManager
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkScanner.stopDiscovery()
        sshManager.disconnect()
    }
}
