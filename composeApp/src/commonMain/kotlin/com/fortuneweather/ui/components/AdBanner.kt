package com.fortuneweather.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 플랫폼별 AdMob 배너를 띄우기 위한 공통 선언 (expect)
 */
@Composable
expect fun AdBanner(modifier: Modifier = Modifier)
