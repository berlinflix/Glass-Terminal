# Pi Glass Terminal - Project Architecture Summary

This document provides a comprehensive overview of the "Pi Glass Terminal" project. You can provide this to any AI assistant to instantly give them the full context of the codebase, architecture, and design constraints.

## 1. High-Level Architecture
**Concept:** A "Dumb Terminal" architecture where an Android phone provides power, internet, and a highly secure touch-optimized user interface to a headless Raspberry Pi Zero W running Kali Linux.
- **Hardware:** Android Phone + Raspberry Pi Zero W (connected to a power bank, no physical data connection to the phone).
- **Network:** The Phone acts as a Wi-Fi Hotspot. The Pi is configured to automatically connect to this hotspot on boot. All communication happens over this local subnet.
- **App Stack:** Native Android app built with Kotlin and Jetpack Compose (Material 3).
- **Pi OS:** Kali Linux (headless, but running a VNC server).

## 2. Strict Security Model (Zero-Trust Local Network)
Because the Pi runs Kali (a penetration testing OS), the connection must be highly secure even on the local hotspot.
- **No Plaintext Passwords:** SSH authentication strictly uses Ed25519 cryptographic keys. The Android app generates the keypair and stores it securely in the Android Keystore.
- **Single Open Port:** The Pi's firewall (`ufw`) is configured to ONLY allow SSH (Port 22) connections from the hotspot subnet.
- **Secure Tunneling:** VNC (Port 5901) and Websockify (Port 6080) are bound exclusively to `localhost` on the Pi. They are completely inaccessible from the outside. The Android app creates secure SSH tunnels (Local Port Forwarding) to access these services securely.
- **TOFU (Trust On First Use):** The app pins the Pi's SSH host key fingerprint on the first connection and verifies it on all subsequent connections to prevent MITM attacks.

## 3. Core Technologies & Dependencies
### Android App (`/app`)
- **UI Framework:** Jetpack Compose (BOM 2024.12.01).
- **SSH Client:** `SSHJ` (v0.39.0) with BouncyCastle and EdDSA for Ed25519 key support.
- **WebViews for Output:** 
  - The Interactive Terminal uses `xterm.js` and `xterm-addon-fit`.
  - The VNC Desktop uses `noVNC` (HTML5 VNC client).
- **Coroutines & Flow:** Used extensively for managing asynchronous SSH connections and network scanning.
- **Foreground Service:** Keeps the SSH tunnels alive when the app is backgrounded to prevent Android from killing the connection during long running tasks.

### Raspberry Pi Configuration (`pi_setup.sh`)
- **Display/GUI:** TigerVNC server (`vncserver`), XFCE desktop environment.
- **Bridge:** `websockify` (translates WebSocket traffic from `noVNC` into pure TCP for the VNC server).
- **Network Discovery:** Avahi-daemon (mDNS) broadcasting a `_glassterminal._tcp` service.

## 4. Key Android Components
- **`MainActivity.kt`:** The entry point. Keeps the screen on (`FLAG_KEEP_SCREEN_ON`).
- **`AppNavigation.kt`:** Uses an offset-based `Box` layout instead of a standard `NavHost` to keep WebViews alive and persistent when switching between the Terminal and Desktop tabs.
- **`SshConnectionManager.kt`:** The brain of the operation. Handles the SSH connection, Ed25519 authentication, TOFU host key verification, PTY shell execution, and bidirectional Local Port Forwarding for VNC and Websockify using SSHJ's `newDirectConnection`.
- **`NetworkScanner.kt`:** Discovers the Pi on the hotspot. Primary method is mDNS (`NsdManager` with a `WifiManager.MulticastLock`). Fallback method is a rapid, parallelized TCP port 22 sweep across the hotspot subnet.
- **`SshForegroundService.kt`:** An Android Foreground Service that displays an ongoing notification to keep the SSH connection alive in the background and provides a "Disconnect" action.
- **`HackerKeyboard.kt`:** A custom row of buttons injected above the soft keyboard, providing essential terminal keys (ESC, TAB, CTRL, ALT, Arrow Keys, PgUp/Dn, SIGINT).
- **`WebViews` (`terminal.html`, `vnc.html`):** Local HTML assets that load the JavaScript clients for xterm.js and noVNC. They communicate with Kotlin via JavascriptInterfaces (`AndroidBridge`).

## 5. Development Details
- **Gradle:** Uses Kotlin DSL (`build.gradle.kts`) with modern `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`. Gradle wrapper is v8.13, AGP is v8.13.2, Kotlin is v2.3.20.
- **Design Aesthetic:** A custom "Cyberpunk Deep Navy" theme (Dark background, Cyan accents, JetBrains Mono font). 

## 6. How to Run/Resume Work
If you are asked to make changes to this repository, understand that the connection logic is delicate. Any changes to SSHJ port forwarding, shell session initialization, or network discovery timeouts should be made with extreme caution, respecting the background thread architecture (`Dispatchers.IO`). UI changes should follow modern Jetpack Compose paradigms and utilize the centralized `Color.kt` and `Type.kt` files.
