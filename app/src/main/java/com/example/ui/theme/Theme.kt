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
import com.example.data.model.ThemeMode

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondary = LightSecondary,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = Color(0xFFCBD5E1)
)

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = Color(0xFF334155)
)

private val OledBlackScheme = darkColorScheme(
    primary = OledPrimary,
    onPrimary = OledOnPrimary,
    secondary = OledSecondary,
    background = OledBackground,
    surface = OledSurface,
    surfaceVariant = OledSurfaceVariant,
    onSurface = OledOnSurface,
    onSurfaceVariant = OledOnSurfaceVariant,
    outline = Color(0xFF27272A)
)

@Composable
fun ApkExtractorTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()

    val colorScheme = when (themeMode) {
        ThemeMode.OLED_BLACK -> OledBlackScheme
        ThemeMode.DARK -> DarkScheme
        ThemeMode.LIGHT -> LightScheme
        ThemeMode.SYSTEM -> if (systemInDark) DarkScheme else LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
