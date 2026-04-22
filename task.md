# Pi Glass Terminal — Task List

## Phase 1: Raspberry Pi Setup Scripts
- [ ] `pi_setup.sh` — Security hardening, VNC local-bind, XFCE touch optimization, firewall rules
- [ ] `pi_wifi_autoconnect.sh` — wpa_supplicant auto-connect to phone hotspot

## Phase 2: Android Project Scaffolding
- [ ] Initialize Kotlin/Jetpack Compose project with Gradle (app-level `build.gradle.kts`)
- [ ] Configure dependencies: SSHJ, Coroutines, Compose, xterm.js / terminal view
- [ ] Create base project structure (packages, themes, navigation)

## Phase 3: Core Services (Kotlin)
- [ ] `KeyManager` — Ed25519 key generation + Android Keystore integration
- [ ] `NetworkScanner` — Subnet ping sweep + ARP/port-22 Pi discovery
- [ ] `SshTunnelService` — SSHJ connection, SSH tunnel (local port forward 5900), session management
- [ ] `ConnectionManager` — Orchestrates discovery → auth → tunnel lifecycle

## Phase 4: VNC Viewer (Jetpack Compose)
- [ ] VNC client using RFB protocol over SSH tunnel
- [ ] Compose `Canvas` rendering of framebuffer
- [ ] Gesture recognizer: tap, long-press, pinch-zoom, two-finger scroll, drag
- [ ] Coordinate mapping math (screen ↔ framebuffer with zoom/pan)

## Phase 5: SSH Terminal (Jetpack Compose)
- [ ] Terminal emulator view (WebView + xterm.js or native canvas)
- [ ] Hacker keyboard toolbar: Tab, Ctrl, Alt, Esc, Arrows, `|`, `/`
- [ ] SSH I/O stream bridging to terminal view

## Phase 6: App UI & Navigation
- [ ] Connection/Home screen with auto-discovery UI
- [ ] Settings screen (key management, Pi address override)
- [ ] Bottom nav or tab layout (Terminal ↔ VNC)
- [ ] Dark theme + cyberpunk aesthetic

## Phase 7: Verification
- [ ] Compile & build APK successfully
- [ ] Manual test: Pi discovery on hotspot
- [ ] Manual test: SSH connect + command execution
- [ ] Manual test: VNC stream + touch gestures
- [ ] Security audit: no plaintext passwords, VNC port locked to localhost
