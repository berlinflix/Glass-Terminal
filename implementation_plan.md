# Pi Glass Terminal — Refinement & Bug Fixes Plan

## Goal Description
The core architecture works, but switching tabs in a Compose `NavHost` destroys the `AndroidView` containing the `WebView`. This causes the terminal's SSH shell session and the noVNC WebSocket to die and reset every time the user navigates to Settings or Home. Additionally, the screen can fall asleep during use, breaking the terminal experience. We will refine the UI, fix these state bugs, and polish the app.

## Proposed Changes

### [Component: MainActivity & Window]
We need the app to keep the screen alive during terminal/VNC sessions.
#### [MODIFY] MainActivity.kt
- Add `window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)` in [onCreate](file:///c:/Users/suyas/Pi/app/src/main/java/com/piterm/glassterminal/service/SshForegroundService.kt#24-28) to prevent the device from sleeping while using the terminal or desktop.

### [Component: Navigation & State Persistence]
Using `NavHost` destroys WebViews on tab switch. We will refactor [AppNavigation.kt](file:///c:/Users/suyas/Pi/app/src/main/java/com/piterm/glassterminal/navigation/AppNavigation.kt) to use a persistent `Box` approach where tabs are hidden/shown rather than destroyed.
#### [MODIFY] AppNavigation.kt
- Remove `NavHost` and replace it with a `Box` where each screen is layered.
- Only the active screen will be visible. The other screens will be hidden using `Modifier.alpha(0f)` and pointer input disabled, or just `Modifier.offset` out of bounds, so the Compose tree keeps the `WebView` instances alive.

### [Component: Terminal & Hacker Keyboard]
#### [MODIFY] HackerKeyboard.kt
- Add missing useful keys: `PageUp`, `PageDown`, [Home](file:///c:/Users/suyas/Pi/app/src/main/java/com/piterm/glassterminal/ui/screens/HomeScreen.kt#42-260), `End` to the layout (useful for reading [man](file:///c:/Users/suyas/Pi/app/src/main/java/com/piterm/glassterminal/service/SshForegroundService.kt#29-48) pages and `htop`).
- Adjust styling to ensure it's easily tappable without taking up too much screen real estate.
#### [MODIFY] TerminalScreen.kt
- Expose the SSH output stream and correctly manage the lifecycle if the connection drops.
- Clean up the `LaunchedEffect(isTerminalReady)` so it doesn't open a shell if one is already open attached to the instance.

### [Component: Settings & UI Polish]
#### [MODIFY] SettingsScreen.kt
- Add a "Clear SSH/VNC Cache" or "Restart SSH Session" button to manually reset if things get stuck without restarting the whole app.
- Enhance the UI spacing and paddings to feel more polished and "Glassmorphic".

## Verification Plan
1. **Automated Analysis**: Review code to ensure no state leaks happen after the Navigation refactor.
2. **Manual Verification**: We will run a build, switch tabs between Terminal, VNC, and Settings, and verify that the `WebView` contents (text in xterm, screen in VNC) do NOT disappear when returning to the tab.
