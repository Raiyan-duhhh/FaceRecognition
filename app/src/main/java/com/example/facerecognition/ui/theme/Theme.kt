package com.example.facerecognition.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═════════════════════════════════════════════════════════════════════════════
// FaceAttend V3.0 — Dark Glassmorphic Material 3 Theme
// ═════════════════════════════════════════════════════════════════════════════

private val FaceAttendDarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Zinc900,
    onPrimaryContainer = Color.White,

    secondary = AccentGreen,
    onSecondary = Color.White,
    secondaryContainer = Zinc900,
    onSecondaryContainer = Color.White,

    tertiary = AccentAmber,
    onTertiary = Color.White,

    background = Zinc950,
    onBackground = Color.White,

    surface = Zinc900,
    onSurface = Color.White,
    surfaceVariant = Zinc800,
    onSurfaceVariant = TextSecondary,

    error = AccentRed,
    onError = Color.White,

    outline = GlassBorder,
    outlineVariant = Zinc700
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun FaceRecognitionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Force dark scheme for FaceAttend — this is a dark-first app
        darkTheme -> FaceAttendDarkScheme
        else -> FaceAttendDarkScheme // Always dark for premium feel
    }

    // Make the status bar and nav bar transparent for edge-to-edge
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}