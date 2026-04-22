package com.piterm.glassterminal.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.piterm.glassterminal.model.ConnectionState
import com.piterm.glassterminal.model.VncServerState
import com.piterm.glassterminal.service.SshConnectionManager
import com.piterm.glassterminal.ui.theme.CardSurface
import com.piterm.glassterminal.ui.theme.DeepNavy
import com.piterm.glassterminal.ui.theme.ElectricCyan
import com.piterm.glassterminal.ui.theme.GreenDim
import com.piterm.glassterminal.ui.theme.HotPink
import com.piterm.glassterminal.ui.theme.NeonGreen
import com.piterm.glassterminal.ui.theme.TextMuted
import com.piterm.glassterminal.ui.theme.TextPrimary
import com.piterm.glassterminal.ui.theme.VividPurple
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Desktop-on-demand screen with five states:
 * 1. Not connected to the Pi
 * 2. Desktop stopped
 * 3. Desktop starting
 * 4. Desktop stopping
 * 5. Desktop running in a WebView-backed noVNC session
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VncScreen(
    sshManager: SshConnectionManager,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val connectionState by sshManager.connectionState.collectAsState()
    val vncServerState by sshManager.vncServerState.collectAsState()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var isPageReady by remember { mutableStateOf(false) }
    var isVncConnected by remember { mutableStateOf(false) }

    LaunchedEffect(vncServerState) {
        if (vncServerState !is VncServerState.Running) {
            releaseWebView(webView)
            webView = null
            isPageReady = false
            isVncConnected = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            releaseWebView(webView)
        }
    }

    LaunchedEffect(isPageReady, vncServerState) {
        if (isPageReady && vncServerState is VncServerState.Running) {
            val escapedPassword = JSONObject.quote(sshManager.getVncPassword())
            webView?.post {
                webView?.evaluateJavascript("connectVnc($escapedPassword)", null)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy),
    ) {
        when {
            connectionState !is ConnectionState.Connected -> {
                NotConnectedView()
            }

            vncServerState is VncServerState.Stopped || vncServerState is VncServerState.Error -> {
                DesktopStoppedView(
                    vncState = vncServerState,
                    onSpawnDesktop = {
                        scope.launch { sshManager.startVncServer() }
                    },
                )
            }

            vncServerState is VncServerState.Starting -> {
                DesktopStartingView()
            }

            vncServerState is VncServerState.Stopping -> {
                DesktopStoppingView()
            }

            vncServerState is VncServerState.Running -> {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mediaPlaybackRequiresUserGesture = false
                            }
                            setBackgroundColor(0xFF0A0E17.toInt())
                            val assetLoader = WebViewAssetLoader.Builder()
                                .addPathHandler(
                                    "/assets/",
                                    WebViewAssetLoader.AssetsPathHandler(ctx),
                                )
                                .build()
                            webViewClient = object : WebViewClientCompat() {
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): WebResourceResponse? {
                                    return assetLoader.shouldInterceptRequest(request.url)
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    url: String,
                                ): WebResourceResponse? {
                                    return assetLoader.shouldInterceptRequest(Uri.parse(url))
                                }
                            }
                            webChromeClient = WebChromeClient()

                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onVncPageReady() {
                                    Log.i("VNC", "noVNC page loaded and ready")
                                    isPageReady = true
                                }

                                @JavascriptInterface
                                fun onVncConnected() {
                                    Log.i("VNC", "VNC connected")
                                    isVncConnected = true
                                }

                                @JavascriptInterface
                                fun onVncDisconnected(reason: String) {
                                    Log.w("VNC", "VNC disconnected: $reason")
                                    isVncConnected = false
                                }
                            }, "AndroidBridge")

                            loadUrl("https://appassets.androidplatform.net/assets/vnc.html")
                            webView = this
                        }
                    },
                    update = { view -> webView = view },
                    modifier = Modifier.fillMaxSize(),
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    FloatingActionButton(
                        onClick = {
                            webView?.evaluateJavascript(
                                "window.disconnectVnc && window.disconnectVnc();",
                                null,
                            )
                            isPageReady = false
                            isVncConnected = false
                            scope.launch { sshManager.stopVncServer() }
                        },
                        containerColor = HotPink,
                        contentColor = TextPrimary,
                        shape = CircleShape,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Kill Desktop",
                        )
                    }
                }

                if (isVncConnected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 12.dp, end = 12.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(NeonGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(NeonGreen),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = "Desktop Active",
                                color = NeonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun releaseWebView(webView: WebView?) {
    webView ?: return
    webView.post {
        runCatching {
            webView.evaluateJavascript("window.disconnectVnc && window.disconnectVnc();", null)
        }
        runCatching { webView.stopLoading() }
        runCatching { webView.loadUrl("about:blank") }
        runCatching { webView.removeJavascriptInterface("AndroidBridge") }
        runCatching { webView.destroy() }
    }
}

@Composable
private fun NotConnectedView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = "Not connected",
            tint = TextMuted,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Not Connected",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Connect to your Pi via SSH first.\nThen spawn a desktop on demand.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun DesktopStoppedView(
    vncState: VncServerState,
    onSpawnDesktop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseScale",
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )

        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(pulseScale)
                .alpha(pulseAlpha)
                .border(
                    width = 2.dp,
                    brush = Brush.radialGradient(listOf(ElectricCyan, VividPurple)),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {}

        Box(
            modifier = Modifier
                .offset(y = (-160).dp)
                .size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            FilledIconButton(
                onClick = onSpawnDesktop,
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = CardSurface,
                    contentColor = ElectricCyan,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.DesktopWindows,
                    contentDescription = "Spawn Desktop",
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        Spacer(Modifier.height(0.dp))

        Text(
            text = "SPAWN DESKTOP",
            style = MaterialTheme.typography.titleLarge,
            color = ElectricCyan,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Pi is in pure CLI attack mode",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )

        Spacer(Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(GreenDim)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = "RAM",
                tint = NeonGreen,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "~80MB RAM | Maximum stealth",
                color = NeonGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Tap to spawn LXDE + VNC desktop\nAbout 120MB additional RAM usage",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )

        if (vncState is VncServerState.Error) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HotPink.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = HotPink,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = vncState.message,
                        color = HotPink,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopStartingView() {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Starting",
            tint = ElectricCyan,
            modifier = Modifier
                .size(64.dp)
                .rotate(rotation),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "SPAWNING DESKTOP",
            style = MaterialTheme.typography.titleLarge,
            color = ElectricCyan,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Starting vncserver + websockify...",
            color = TextMuted,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(24.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = ElectricCyan,
            trackColor = CardSurface,
        )
    }
}

@Composable
private fun DesktopStoppingView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.DeleteSweep,
            contentDescription = "Stopping",
            tint = HotPink,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "STOPPING DESKTOP",
            style = MaterialTheme.typography.titleLarge,
            color = HotPink,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Killing VNC server and reclaiming RAM...",
            color = TextMuted,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(24.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = HotPink,
            trackColor = CardSurface,
        )
    }
}
