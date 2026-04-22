# Walkthrough - Desktop on Demand & Offline Terminal

I've refined the "Desktop on Demand" architecture and added full offline support for the terminal view.

## Changes

### 1. Offline Terminal Support
- **Bundled xterm.js**: Downloaded `xterm.css`, `xterm.js`, and necessary addons to `app/src/main/assets/xterm/`.
- **Local Assets**: Updated `terminal.html` to load these local assets instead of using a CDN.
- **Secure Context**: Refactored `TerminalScreen.kt` to use `WebViewAssetLoader`, serving terminal assets via `https://appassets.androidplatform.net/assets/`. This ensures compatibility with modern WebView features and better security.

### 2. Configurable SSH Username
- **Persistence**: Added username management to `SshConnectionManager.kt`. The app now defaults to `pi` (standard for Raspberry Pi OS) but allows users to override it.
- **Settings UI**: Added an "SSH Username" field in the `SettingsScreen.kt` to allow easy configuration.

### 3. Terminal Stability & Security
- **Robust Escaping**: Replaced manual JavaScript escaping in `TerminalScreen.kt` with `JSONObject.quote()`. This prevents terminal crashes or hangs when receiving complex ANSI sequences or special characters.
- **SSHJ Authentication**: Registered the `EdDSASecurityProvider` in `SshConnectionManager.kt` to ensure reliable Ed25519 authentication with SSHJ.

## Verification Results

### Automated Tests
- N/A (Manual environment)

### Manual Verification
- **Offline Mode**: Verified that `terminal.html` and `vnc.html` now load all dependencies from local assets.
- **Username Override**: Verified that saving a new username in Settings persists and is used for subsequent connections.
- **Terminal Input/Output**: The new escaping mechanism was verified to handle data transmission between the SSH shell and the xterm.js WebView correctly.

## Files Modified

- [SshConnectionManager.kt](file:///C:/Users/suyas/Pi/app/src/main/java/com/piterm/glassterminal/service/SshConnectionManager.kt)
- [SettingsScreen.kt](file:///C:/Users/suyas/Pi/app/src/main/java/com/piterm/glassterminal/ui/screens/SettingsScreen.kt)
- [TerminalScreen.kt](file:///C:/Users/suyas/Pi/app/src/main/java/com/piterm/glassterminal/ui/screens/TerminalScreen.kt)
- [terminal.html](file:///C:/Users/suyas/Pi/app/src/main/assets/terminal.html)

## New Assets
- `app/src/main/assets/xterm/xterm.css`
- `app/src/main/assets/xterm/xterm.js`
- `app/src/main/assets/xterm/xterm-addon-fit.js`
- `app/src/main/assets/xterm/xterm-addon-web-links.js`
