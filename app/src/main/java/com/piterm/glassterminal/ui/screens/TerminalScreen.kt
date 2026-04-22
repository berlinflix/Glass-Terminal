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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.piterm.glassterminal.model.ConnectionState
import com.piterm.glassterminal.service.SshConnectionManager
import com.piterm.glassterminal.ui.components.HackerKeyboard
import com.piterm.glassterminal.ui.theme.DeepNavy
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream

/**
 * Terminal screen — xterm.js in a WebView connected to SSHJ shell streams.
 * Features:
 * - Full ANSI/VT100 terminal rendering via xterm.js
 * - Bidirectional bridge: user input → SSH stdin, SSH stdout → terminal
 * - Hacker keyboard toolbar for special keys
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(
    sshManager: SshConnectionManager,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var sshOutputStream by remember { mutableStateOf<OutputStream?>(null) }
    var isTerminalReady by remember { mutableStateOf(false) }

    val connectionState by sshManager.connectionState.collectAsState()

    // Open SSH shell session when terminal is ready and SSH is connected
    LaunchedEffect(isTerminalReady, connectionState) {
        if (!isTerminalReady) return@LaunchedEffect
        if (connectionState !is ConnectionState.Connected) return@LaunchedEffect

        val shellResult = sshManager.openShell() ?: run {
            Log.e("Terminal", "Failed to open shell")
            return@LaunchedEffect
        }

        val (inputStream, outputStream, _) = shellResult
        sshOutputStream = outputStream

        // Pump SSH output → xterm.js
        launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    // Escape for JavaScript string literal
                    val escaped = text
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")

                    withContext(Dispatchers.Main) {
                        webView?.evaluateJavascript("writeToTerminal('$escaped')", null)
                    }
                }
            } catch (e: Exception) {
                Log.w("Terminal", "SSH output stream ended", e)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        // ── WebView (xterm.js) ───────────────────────────────────────────
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
                        setSupportZoom(false)
                        builtInZoomControls = false
                    }
                    setBackgroundColor(0xFF0A0E17.toInt())
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()

                    // Bridge: xterm.js → Kotlin
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onTerminalInput(data: String) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    sshOutputStream?.write(data.toByteArray(Charsets.UTF_8))
                                    sshOutputStream?.flush()
                                } catch (e: Exception) {
                                    Log.e("Terminal", "Write error", e)
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onTerminalReady(cols: Int, rows: Int) {
                            Log.i("Terminal", "Terminal ready: ${cols}x${rows}")
                            isTerminalReady = true
                        }

                        @JavascriptInterface
                        fun onResize(cols: Int, rows: Int) {
                            Log.d("Terminal", "Resize: ${cols}x${rows}")
                            sshManager.resizePty(cols, rows)
                        }
                    }, "AndroidBridge")

                    loadUrl("file:///android_asset/terminal.html")
                    webView = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // ── Hacker Keyboard ──────────────────────────────────────────────
        HackerKeyboard(
            onKeyPress = { sequence ->
                scope.launch(Dispatchers.IO) {
                    try {
                        sshOutputStream?.write(sequence.toByteArray(Charsets.UTF_8))
                        sshOutputStream?.flush()
                    } catch (e: Exception) {
                        Log.e("Terminal", "Key inject error", e)
                    }
                }
            }
        )
    }
}
