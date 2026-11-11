package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NurElectricBlue,
    onPrimary = Color.White,
    primaryContainer = NurElectricBlueDarkSubtle,
    onPrimaryContainer = NurElectricBlueGlow,
    secondary = NurElectricBlueGlow,
    onSecondary = Color.White,
    secondaryContainer = NurDarkSurfaceVariant,
    onSecondaryContainer = NurDarkTextPrimary,
    background = NurDarkBackground,
    onBackground = NurDarkTextPrimary,
    surface = NurDarkSurface,
    onSurface = NurDarkTextPrimary,
    surfaceVariant = NurDarkSurfaceVariant,
    onSurfaceVariant = NurDarkTextSecondary,
    outline = NurDarkCardBorder,
    error = NurError,
    onError = Color.White,
    errorContainer = Color(0xFF371616),
    onErrorContainer = Color(0xFFFFB4AB)
)

private val LightColorScheme = lightColorScheme(
    primary = NurElectricBlue,
    onPrimary = Color.White,
    primaryContainer = NurElectricBlueSubtle,
    onPrimaryContainer = NurElectricBlue,
    secondary = Color(0xFF2563EB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF6FF),
    onSecondaryContainer = Color(0xFF1E40AF),
    background = NurLightBackground,
    onBackground = NurLightTextPrimary,
    surface = NurLightSurface,
    onSurface = NurLightTextPrimary,
    surfaceVariant = NurLightSurfaceVariant,
    onSurfaceVariant = NurLightTextSecondary,
    outline = NurLightCardBorder,
    error = NurError,
    onError = Color.White,
    errorContainer = NurErrorContainer,
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun NurHomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    NurHomeTheme(darkTheme = darkTheme, content = content)
}
