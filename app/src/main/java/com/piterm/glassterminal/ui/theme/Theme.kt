package com.piterm.glassterminal.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GlassColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepNavy,
    primaryContainer = CyanDim,
    onPrimaryContainer = ElectricCyan,
    secondary = VividPurple,
    onSecondary = DeepNavy,
    secondaryContainer = PurpleDim,
    onSecondaryContainer = VividPurple,
    tertiary = NeonGreen,
    onTertiary = DeepNavy,
    tertiaryContainer = GreenDim,
    onTertiaryContainer = NeonGreen,
    error = HotPink,
    onError = DeepNavy,
    errorContainer = ErrorRed,
    background = DeepNavy,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    outlineVariant = TextMuted,
    inverseSurface = TextPrimary,
    inverseOnSurface = DeepNavy,
    inversePrimary = ElectricCyan,
    surfaceTint = ElectricCyan,
)

@Composable
fun GlassTerminalTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DeepNavy.toArgb()
            window.navigationBarColor = DeepNavy.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = GlassColorScheme,
        typography = GlassTypography,
        content = content
    )
}
