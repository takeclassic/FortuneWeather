package com.fortuneweather.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.*
import com.fortuneweather.R

@Composable
actual fun WeatherAnimation(condition: String, isNight: Boolean, modifier: Modifier, animate: Boolean) {
    val resId = when {
        condition.contains("눈") -> if (isNight) R.raw.sometimes_snow_night else R.raw.snowy
        condition.contains("낙뢰") || condition.contains("번개") || condition.contains("뇌우") -> {
            when {
                condition.contains("비") -> R.raw.lightning_rainy
                isNight -> R.raw.sometimes_lightning_rain
                else -> R.raw.lightning
            }
        }
        condition.contains("비") || condition.contains("소나기") || condition.contains("강수") -> {
            if (isNight) R.raw.sometimes_rain_night else R.raw.sometimes_rainy
        }
        condition.contains("흐림") -> if (condition.contains("바람")) R.raw.cloudy_windy else R.raw.cloudy
        condition.contains("구름많음") || condition.contains("구름") -> {
            if (isNight) R.raw.sometimes_cloudy_night else R.raw.sometimes_cloudy
        }
        else -> if (isNight) R.raw.sunny_night else R.raw.sunny
    }

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    
    if (animate) {
        val progress by animateLottieCompositionAsState(
            composition,
            iterations = LottieConstants.IterateForever
        )
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = modifier
        )
    } else {
        // 애니메이션 없이 중간 프레임(50% 지점)을 표시하여 그래픽 효과가 잘 보이도록 함
        LottieAnimation(
            composition = composition,
            progress = { 0.5f },
            modifier = modifier
        )
    }
}
