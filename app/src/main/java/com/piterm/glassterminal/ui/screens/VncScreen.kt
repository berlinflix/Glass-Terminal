package com.piterm.glassterminal.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.piterm.glassterminal.model.ConnectionState
import com.piterm.glassterminal.service.SshConnectionManager
import com.piterm.glassterminal.ui.theme.DeepNavy

/**
 * VNC viewer screen — noVNC in a WebView connected through SSH tunnel.
 *
 * The SSH tunnel forwards:
 *   localhost:16080 → Pi:127.0.0.1:6080 (websockify)
 *   websockify on Pi: 127.0.0.1:6080 → 127.0.0.1:5901 (VNC)
 *
 * noVNC connects to ws://localhost:16080 which travels through the
 * encrypted SSH tunnel before reaching the Pi's VNC server.
 *
 * Touch gestures are handled by noVNC's built-in touch support,
 * enhanced with our custom long-press right-click in vnc.html.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VncScreen(
    sshManager: SshConnectionManager,
    modifier: Modifier = Modifier
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isPageReady by remember { mutableStateOf(false) }
    var isVncConnected by remember { mutableStateOf(false) }
    
    val connectionState by sshManager.connectionState.collectAsState()

    // When the page is ready and SSH tunnel is up, initiate VNC connection
    LaunchedEffect(isPageReady, connectionState) {
        if (isPageReady && connectionState is ConnectionState.Connected) {
            // Trigger noVNC connection
            webView?.post {
                webView?.evaluateJavascript("connectVnc('')", null)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        useWideViewPort = true
                        loadWithOverviewMode = true

                        // Allow WebSocket connections to localhost
                        @SuppressLint("SetJavaScriptEnabled")
                        mediaPlaybackRequiresUserGesture = false
                    }
                    setBackgroundColor(0xFF0A0E17.toInt())
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()

                    // Bridge: noVNC → Kotlin
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onVncPageReady() {
                            Log.i("VNC", "noVNC page loaded and ready")
                            isPageReady = true
                        }

                        @JavascriptInterface
                        fun onVncConnected() {
                            Log.i("VNC", "VNC connected!")
                            isVncConnected = true
                        }

                        @JavascriptInterface
                        fun onVncDisconnected(reason: String) {
                            Log.w("VNC", "VNC disconnected: $reason")
                            isVncConnected = false
                        }
                    }, "AndroidBridge")

                    loadUrl("file:///android_asset/vnc.html")
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
