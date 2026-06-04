package com.fortuneweather.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorPalette = lightColors(
    primary = SunnyDark,
    primaryVariant = SunnyLight,
    secondary = FashionGreen,
    background = Color.White,
    surface = Color.White,
    error = ErrorDark,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

@Composable
fun FortuneWeatherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = LightColorPalette,
        typography = FortuneTypography, // 타이포그래피 등록
        content = content
    )
}
