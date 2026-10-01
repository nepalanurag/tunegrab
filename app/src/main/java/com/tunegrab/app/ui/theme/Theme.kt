package com.tunegrab.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Pixel-native Material 3 theme with an Oto-music-inspired dark scheme:
 * deep maroon surfaces with a dusty-rose accent, matching the reference
 * screenshots. Dark mode always uses the Oto palette (no dynamic color);
 * light mode keeps Material You dynamic color on Android 12+.
 *
 * The theme also sets the default content color to onBackground. The
 * app's top-level layout is plain Box/Column containers (no root
 * Surface), and MaterialTheme alone does not provide LocalContentColor
 * (it defaults to black) — without this, every Text without an explicit
 * color renders black, which is invisible on the dark maroon theme.
 */
@Composable
fun TuneGrabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> otoDarkColorScheme()
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicLightColorScheme(LocalContext.current)
        }
        else -> lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            CompositionLocalProvider(
                LocalContentColor provides colorScheme.onBackground,
                content = content,
            )
        }
    )
}

/** Oto Music look: dark maroon background, dusty-rose accent. */
private fun otoDarkColorScheme() = darkColorScheme(
    primary = Color(0xFFD98C8C),
    onPrimary = Color(0xFF2A0E0E),
    primaryContainer = Color(0xFF5A2A2A),
    onPrimaryContainer = Color(0xFFF5D5D5),
    secondary = Color(0xFFC99A9A),
    onSecondary = Color(0xFF2A0E0E),
    secondaryContainer = Color(0xFF4A2222),
    onSecondaryContainer = Color(0xFFF0D0D0),
    tertiary = Color(0xFFE0B48F),
    background = Color(0xFF2A1010),
    onBackground = Color(0xFFF3E2E2),
    surface = Color(0xFF301413),
    onSurface = Color(0xFFF3E2E2),
    surfaceVariant = Color(0xFF3B1515),
    onSurfaceVariant = Color(0xFFD8B4B4),
    surfaceContainer = Color(0xFF331414),
    surfaceContainerHigh = Color(0xFF3E1818),
    surfaceContainerHighest = Color(0xFF4A1E1E),
    outline = Color(0xFF6B3A3A),
    outlineVariant = Color(0xFF4A2626),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)
