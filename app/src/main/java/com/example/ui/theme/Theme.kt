package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElegantDarkPrimary,
    onPrimary = ElegantDarkOnPrimary,
    primaryContainer = ElegantDarkPrimaryContainer,
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = ElegantDarkSecondary,
    onSecondary = ElegantDarkOnSecondary,
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = ElegantDarkTertiary,
    background = ElegantDarkBackground,
    surface = ElegantDarkSurface,
    surfaceVariant = ElegantDarkSurfaceVariant,
    onBackground = ElegantDarkOnBackground,
    onSurface = ElegantDarkOnSurface,
    onSurfaceVariant = ElegantDarkOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = ElegantLightPrimary,
    secondary = ElegantLightSecondary,
    tertiary = ElegantLightTertiary,
    background = ElegantLightBackground,
    surface = ElegantLightSurface,
    onPrimary = ElegantLightOnPrimary,
    onSecondary = ElegantLightOnSecondary,
    onBackground = ElegantLightOnBackground,
    onSurface = ElegantLightOnSurface
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // We prioritize our beautiful emerald-gold palette for a serene thematic experience
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
