# Implementation Plan - Desktop on Demand Refinement and Offline Support

Refine the "Desktop on Demand" architecture by fixing identified bugs, improving terminal stability, and ensuring full offline capability for the terminal view.

## Proposed Changes

### SSH Service & Model

#### [SshConnectionManager.kt](file:///C:/Users/suyas/Pi/app/src/main/java/com/piterm/glassterminal/service/SshConnectionManager.kt)
- Add `saveUsername(username)` and `getUsername()` using SharedPreferences.
- Default username to "pi" (more common for Pi OS) but allow override.
- Update `getUsernameForDevice` to return the saved username.

### UI & Screens

#### [SettingsScreen.kt](file:///C:/Users/suyas/Pi/app/src/main/java/com/piterm/glassterminal/ui/screens/SettingsScreen.kt)
- Add "SSH Username" field in the "Connection" or "Desktop on Demand" section.
- Persist username to `SshConnectionManager`.

#### [TerminalScreen.kt](file:///C:/Users/suyas/Pi/app/src/main/java/com/piterm/glassterminal/ui/screens/TerminalScreen.kt)
- Replace manual JS escaping with `JSONObject.quote(text)` for robustness.
- Implement `WebViewAssetLoader` to serve terminal assets via `https://appassets.androidplatform.net/assets/`.
- This ensures consistency with `VncScreen.kt` and better security/module support.

### Assets (Offline Support)

#### [terminal.html](file:///C:/Users/suyas/Pi/app/src/main/assets/terminal.html)
- Update `<link>` and `<script>` tags to point to local `/assets/xterm/` paths.

#### [NEW] xterm assets
- Create `app/src/main/assets/xterm/` directory.
- Add `xterm.css`, `xterm.js`, `xterm-addon-fit.js`, and `xterm-addon-web-links.js` fetched from CDN.

## Verification Plan

### Automated Tests
- No automated tests available in this environment.

### Manual Verification
1. **Verify Username Override**: Change username in Settings, connect to a device, and check logs/state to see if it uses the new username.
2. **Verify Terminal Stability**: Run commands with special characters (e.g., `ls -l`, `top`, or echo with quotes) and ensure the terminal doesn't hang or crash.
3. **Verify Offline Support**:
    - Disconnect computer from internet (if possible, or just observe if assets load from local path in WebView logs).
    - Check if `terminal.html` loads successfully and xterm is initialized.
    - Check if `vnc.html` (already updated) continues to work.
4. **Build Verification**:
    - Check for syntax errors in Kotlin files.
    - Ensure all assets are in the correct place.
