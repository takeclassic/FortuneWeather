package com.fortuneweather.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun WeatherAnimation(
    condition: String, 
    isNight: Boolean, 
    modifier: Modifier = Modifier,
    animate: Boolean = true // 애니메이션 여부 제어 파라미터 추가
)
