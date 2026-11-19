package com.classjam.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightRedColorScheme = lightColorScheme(
    primary = JamPrimary,
    onPrimary = Color.White,
    primaryContainer = JamSurfaceVariant,
    onPrimaryContainer = JamPrimaryVariant,
    secondary = JamSecondary,
    onSecondary = Color.White,
    tertiary = JamAccent,
    background = JamBackground,
    surface = JamSurface,
    surfaceVariant = JamSurfaceVariant,
    onBackground = JamOnSurface,
    onSurface = JamOnSurface,
    onSurfaceVariant = JamOnSurfaceMuted,
    outline = JamSurfaceBorder,
    error = JamError
)

@Composable
fun ClassJamTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightRedColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = JamBackground.toArgb()
            window.navigationBarColor = JamBackground.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
