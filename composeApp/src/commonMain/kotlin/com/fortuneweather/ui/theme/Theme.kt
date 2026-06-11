package com.fortuneweather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SunnyDark,
    onPrimary = Color.White,
    primaryContainer = SunnyLight,
    onPrimaryContainer = Color.White,
    secondary = FashionGreen,
    onSecondary = Color.Black,
    secondaryContainer = FashionGreen,
    onSecondaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black,
    error = ErrorDark,
    onError = Color.White,
)

@Composable
fun FortuneWeatherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = FortuneTypography,
        content = content
    )
}
