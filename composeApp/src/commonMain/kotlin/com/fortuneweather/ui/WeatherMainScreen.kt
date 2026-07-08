@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
package com.fortuneweather.ui

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fortuneweather.domain.model.*
import com.fortuneweather.ui.components.AdBanner
import com.fortuneweather.ui.components.WeatherAnimation
import com.fortuneweather.ui.theme.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import fortuneweather.composeapp.generated.resources.Res
import fortuneweather.composeapp.generated.resources.aqi_good

import kotlinx.datetime.*

private fun aqiClassification(aqi: Int, isNight: Boolean): Triple<String, Color, Pair<DrawableResource?, String>> = when {
    aqi <= 30 -> {
        val color = if (isNight) AqiGood else Color(0xFF1B5E20) // 낮(밝은 하늘색)에는 진한 초록색
        Triple("좋음", color, Pair(Res.drawable.aqi_good, "😊"))
    }
    aqi <= 80 -> {
        val color = if (isNight) AqiModerate else Color(0xFFC49000) // 낮에는 어두운 황색
        Triple("보통", color, Pair(null, "😐"))
    }
    aqi <= 150 -> {
        val color = if (isNight) AqiBad else Color(0xFFE65100) // 낮에는 어두운 주황색
        Triple("나쁨", color, Pair(null, "😷"))
    }
    else -> {
        val color = if (isNight) AqiVeryBad else Color(0xFFB71C1C) // 낮에는 어두운 적색
        Triple("매우나쁨", color, Pair(null, "🤢"))
    }
}

private fun isNightTime(sunrise: String, sunset: String, now: LocalDateTime): Boolean {
    val currentInMin = now.hour * 60 + now.minute
    val sunriseHour = sunrise.substringBefore(":").toIntOrNull() ?: 6
    val sunriseMin = sunrise.substringAfter(":").toIntOrNull() ?: 0
    val sunsetHour = sunset.substringBefore(":").toIntOrNull() ?: 18
    val sunsetMin = sunset.substringAfter(":").toIntOrNull() ?: 0

    val sunriseInMin = sunriseHour * 60 + sunriseMin
    val sunsetInMin = sunsetHour * 60 + sunsetMin

    return currentInMin < sunriseInMin || currentInMin >= sunsetInMin
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherApp(
    weatherInfo: WeatherInfo,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onFortuneClick: () -> Unit,
    onFashionClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var sheetType by remember { mutableStateOf("fortune") }
    var showBottomSheet by remember { mutableStateOf(false) }

    val isNight = remember(weatherInfo) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Seoul"))
        isNightTime(weatherInfo.sunrise, weatherInfo.sunset, now)
    }

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            onRefresh()
        }
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(weatherInfo) {
        scrollState.scrollTo(0)
    }

    FortuneWeatherTheme {
        val backgroundColors = if (isNight) listOf(NightDeepBlue, NightBlackBlue) else listOf(SunnyLight, SunnyDark)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = Brush.verticalGradient(colors = backgroundColors))
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(60.dp))
                CurrentWeatherSection(weatherInfo, isNight)
                Spacer(modifier = Modifier.height(24.dp))
                ActionButtons(onFortuneClick = onFortuneClick, onFashionClick = onFashionClick)
                Spacer(modifier = Modifier.height(32.dp))
                SectionTitle("24시간 예보")
                HourlyForecastSection(weatherInfo, isNight)
                Spacer(modifier = Modifier.height(30.dp))
                SectionTitle("5일간의 날씨")
                DailyForecastSection(weatherInfo, isNight)
                Spacer(modifier = Modifier.height(30.dp))
                SectionTitle("상세 정보")
                WeatherDetailGrid(weatherInfo)
                Spacer(modifier = Modifier.height(80.dp))
            }
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Color.White,
                contentColor = SunnyDark
            )
            AdBanner(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(50.dp).background(Color.White))
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                BottomSheetContent(sheetType, weatherInfo)
            }
        }
    }
}

@Composable
fun DailyForecastSection(info: WeatherInfo, isNight: Boolean) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = WhiteTrans15), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            info.daily.forEach { item ->
                DailyForecastItem(item, isNight)
                if (info.daily.last() != item) HorizontalDivider(color = WhiteTrans10, thickness = 0.5.dp)
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun DailyForecastItem(item: DailyForecast, isNight: Boolean) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().animateContentSize().clickable { isExpanded = !isExpanded }) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 1. 요일
            Text(text = item.dayOfWeek, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(42.dp), fontWeight = FontWeight.Normal)
            
            if (item.morningCondition == "데이터 준비중") {
                Spacer(modifier = Modifier.weight(1f))
                Text("정보를 불러오는 중입니다...", color = WhiteTrans80, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(3f))
            } else {
                // 2. 강수확률
                Row(modifier = Modifier.width(50.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "💧", fontSize = 10.sp, modifier = Modifier.padding(end = 2.dp))
                    Text(text = "${item.precipitationProbability}%", color = WhiteTrans80, style = MaterialTheme.typography.bodySmall)
                }
                
                // 3. 미세먼지 등급 표기
                val (aqiText, aqiColor, aqiAsset) = aqiClassification(item.aqi, isNight)
                val (aqiIcon, fallbackEmoji) = aqiAsset
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(56.dp)) {
                    if (aqiIcon != null) {
                        Image(
                            painter = painterResource(aqiIcon),
                            contentDescription = aqiText,
                            modifier = Modifier.size(14.dp).padding(end = 2.dp)
                        )
                    } else {
                        Text(text = fallbackEmoji, fontSize = 9.sp, modifier = Modifier.padding(end = 2.dp))
                    }
                    Text(text = aqiText, color = aqiColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                
                // 4. 아이콘들
                Row(modifier = Modifier.width(72.dp), verticalAlignment = Alignment.CenterVertically) {
                    WeatherAnimation(condition = item.morningCondition, isNight = false, animate = false, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    WeatherAnimation(condition = item.afternoonCondition, isNight = false, animate = false, modifier = Modifier.size(26.dp))
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 5. 온도 및 맨 우측 화살표 아이콘
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    Text(text = "${item.minTemp.toInt()}°", color = WhiteTrans80, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "${item.maxTemp.toInt()}°", color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        color = WhiteTrans80,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            if (item.hourlyDetails.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WhiteTrans10, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item.hourlyDetails.forEach { detail ->
                        // 1. 기온 비율 계산 (-15도 ~ 35도 기준)
                        val tempBase = -15.0
                        val tempRange = 50.0
                        val ratio = ((detail.temp - tempBase) / tempRange).toFloat().coerceIn(0f, 1f)
                        
                        // 2. 가로 폭 차오르는 애니메이션 적용 (Animatable & LaunchedEffect 이용)
                        val animRatio = remember { Animatable(0f) }
                        LaunchedEffect(isExpanded) {
                            if (isExpanded) {
                                animRatio.animateTo(
                                    targetValue = ratio,
                                    animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing)
                                )
                            } else {
                                animRatio.snapTo(0f)
                            }
                        }
                        
                        // 3. 온도 비례 게이지 색상 분기
                        val gaugeColor = when {
                            detail.temp < -10.0 -> Color(0xFF0D47A1)
                            detail.temp <= 0.0  -> Color(0xFF1565C0)
                            detail.temp <= 10.0 -> Color(0xFF2196F3)
                            detail.temp <= 15.0 -> Color(0xFF4FC3F7)
                            detail.temp <= 23.0 -> Color(0xFF2ECC71)
                            detail.temp <= 28.0 -> Color(0xFFFF9800)
                            else                -> Color(0xFFE53935)
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. 시간 (폭을 42dp로 단축하여 강수확률과의 간격을 줄임)
                            Text(
                                text = detail.time,
                                color = WhiteTrans80,
                                fontSize = 13.sp,
                                modifier = Modifier.width(42.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(2.dp))
                            
                            // 2. 강수확률 (폭을 45dp로 정밀 조정하여 콤팩트하게 밀착시킴)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.width(45.dp)
                            ) {
                                Text(text = "💧", fontSize = 11.sp, modifier = Modifier.padding(end = 2.dp))
                                Text(
                                    text = "${detail.precipitationProbability}%",
                                    color = WhiteTrans80,
                                    fontSize = 13.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(6.dp))
                            
                            // 3. 날씨 아이콘 (32dp로 유지하고 애니메이션 활성화)
                            val isDetailNight = remember(detail.time) {
                                val hr = detail.time.substringBefore("시").toIntOrNull() ?: 12
                                hr < 6 || hr >= 20
                            }
                            WeatherAnimation(
                                condition = detail.condition,
                                isNight = isDetailNight,
                                animate = true,
                                modifier = Modifier.size(32.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // 4. 기온 게이지 영역 (높이 24dp, 캡슐 폭 32dp, 폰트 11sp로 조정하여 슬라이딩 가용 범위 확보)
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .align(Alignment.CenterVertically)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            ) {
                                val capsuleWidth = 32.dp
                                val maxTravel = maxWidth - capsuleWidth
                                val currentOffset = maxTravel * animRatio.value
                                
                                Box(
                                    modifier = Modifier
                                        .size(width = capsuleWidth, height = 24.dp)
                                        .offset(x = currentOffset)
                                        .background(gaugeColor, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${detail.temp.toInt()}°",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "오늘 예보가 완료되었습니다.",
                    color = WhiteTrans80,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun HourlyForecastSection(info: WeatherInfo, isNight: Boolean) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = WhiteTrans10), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            items(info.hourly) { item ->
                val isItemNight = remember(item.time) {
                    try {
                        val isPm = item.time.contains("오후")
                        val rawHour = item.time.substringAfter(if (isPm) "오후 " else "오전 ").substringBefore("시").trim().toInt()
                        val hour = if (isPm) {
                            if (rawHour == 12) 12 else rawHour + 12
                        } else {
                            if (rawHour == 12) 0 else rawHour
                        }
                        hour < 6 || hour >= 20
                    } catch (e: Exception) {
                        false
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
                    Text(text = item.time, color = WhiteTrans80, fontSize = 10.sp, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    WeatherAnimation(condition = item.condition, isNight = isItemNight, animate = true, modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${item.temp.toInt()}°", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "💧", fontSize = 8.sp, modifier = Modifier.padding(end = 2.dp))
                        Text(text = "${item.precipitationProbability}%", color = WhiteTrans80, fontSize = 9.sp, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val (aqiText, aqiColor, aqiAsset) = aqiClassification(item.aqi, isNight)
                    val (aqiIcon, fallbackEmoji) = aqiAsset
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (aqiIcon != null) {
                            Image(
                                painter = painterResource(aqiIcon),
                                contentDescription = aqiText,
                                modifier = Modifier.size(12.dp).padding(end = 2.dp)
                            )
                        } else {
                            Text(text = fallbackEmoji, fontSize = 8.sp, modifier = Modifier.padding(end = 2.dp))
                        }
                        Text(text = aqiText, color = aqiColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentWeatherSection(info: WeatherInfo, isNight: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(info.locationName, style = MaterialTheme.typography.headlineMedium, color = WhiteTrans80)
        Spacer(modifier = Modifier.height(8.dp))
        WeatherAnimation(condition = info.condition, isNight = isNight, animate = true, modifier = Modifier.size(180.dp))
        Text(info.condition, style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text("${info.temp.toInt()}°", style = MaterialTheme.typography.displayLarge, color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("미세먼지 ", style = MaterialTheme.typography.bodyMedium, color = WhiteTrans80)
            val (text, color, aqiAsset) = aqiClassification(info.aqi, isNight)
            val (aqiIcon, fallbackEmoji) = aqiAsset
            if (aqiIcon != null) {
                Image(
                    painter = painterResource(aqiIcon),
                    contentDescription = text,
                    modifier = Modifier.size(24.dp).padding(end = 4.dp)
                )
            } else {
                Text(text = "$fallbackEmoji ", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 4.dp))
            }
            Text("$text (${info.aqi})", color = color, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun WeatherDetailGrid(info: WeatherInfo) {
    val currentHour = remember(info) {
        try {
            Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Seoul")).hour
        } catch (e: Exception) {
            12
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. 일출/일몰 궤적 카드 (Canvas 활용 아크 드로잉 및 시간별 태양 위치 시뮬레이션)
        SunriseSunsetCard(sunrise = info.sunrise, sunset = info.sunset, nowHour = currentHour)

        // 2. 미세먼지 및 초미세먼지 듀얼 디테일 카드
        AirQualityDetailCard(pm10 = info.pm10, pm25 = info.pm25)

        // 3. 달의 위상 카드 (이모지 및 천문학 위상 묘사 제공)
        MoonPhaseCard(phase = info.moonPhase)

        // 4. 체감온도 및 기본 정보들 (가로형 와이드 카드로 가독성 극대화)
        DetailRowCard(
            icon = "🌡️",
            title = "체감 온도",
            value = "${info.feelsLike.toInt()}°",
            description = "" // 피드백에 따라 구구절절한 설명 생략
        )

        val rainDesc = if (info.rainAmount > 0.0) "최근 1시간 동안 ${info.rainAmount}mm의 비가 내렸습니다." else "현재 비 예보가 없습니다."
        DetailRowCard(
            icon = "🌧️",
            title = "강수량",
            value = "${info.rainAmount}mm",
            description = rainDesc
        )

        DetailRowCard(
            icon = "💨",
            title = "바람 (초속)", // 풍향에 더해 풍속(초속) 데이터가 잘 보관되어 있음을 타이틀로 명시
            value = "${info.windSpeed}m/s",
            description = "현재 ${info.windDirection}풍이 불고 있습니다."
        )

        DetailRowCard(
            icon = "💦",
            title = "습도",
            value = "${info.humidity}%",
            description = if (info.humidity > 60) "습도가 높아 다소 꿉꿉할 수 있습니다." else if (info.humidity < 30) "공기가 건조하므로 수분을 보충하세요." else "적정한 실내외 습도 상태입니다."
        )

        val uvLevel = when {
            info.uvIndex < 3.0 -> "낮음"
            info.uvIndex < 6.0 -> "보통"
            info.uvIndex < 8.0 -> "높음"
            else -> "매우높음"
        }
        DetailRowCard(
            icon = "☀️",
            title = "자외선 지수",
            value = "${info.uvIndex.toInt()} ($uvLevel)",
            description = if (info.uvIndex >= 6.0) "자외선이 강하므로 선크림과 양산을 챙기세요." else "야외 활동을 하기에 좋은 자외선 지수입니다."
        )
        
        DetailRowCard(
            icon = "👁️",
            title = "가시거리",
            value = "${info.visibility.toInt()}km",
            description = if (info.visibility >= 10.0) "가시거리가 트여 있어 하늘이 맑게 잘 보입니다." else "시야가 다소 안개로 인해 가려질 수 있습니다."
        )
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun SunriseSunsetCard(sunrise: String, sunset: String, nowHour: Int, modifier: Modifier = Modifier) {
    var isVisible by remember { mutableStateOf(false) }
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val positionInWindow = coordinates.localToWindow(Offset.Zero).y
                val parentHeight = coordinates.parentLayoutCoordinates?.size?.height ?: 2000
                if (positionInWindow > 0 && positionInWindow < parentHeight) {
                    isVisible = true
                }
            }
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("☀️ 일출 및 일몰", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .height(100.dp)
            ) {
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()
                val density = androidx.compose.ui.platform.LocalDensity.current
                
                val widthDp = with(density) { constraints.maxWidth.toDp() }
                val heightDp = with(density) { constraints.maxHeight.toDp() }
                
                // 2. 태양 위치 비율 계산 및 애니메이션 적용 (실제 뷰포트에 감지되어 보일 때 스르륵 이동)
                val targetT = remember(nowHour) {
                    val isDaytime = nowHour in 6..19
                    if (isDaytime) {
                        ((nowHour - 6).toFloat() / 13f).coerceIn(0f, 1f) 
                    } else {
                        1f // 일몰 이후(밤)에는 끝지점(1.0f)까지 이동
                    }
                }
                
                val animT = remember { Animatable(0f) }
                LaunchedEffect(isVisible, targetT) {
                    if (isVisible) {
                        kotlinx.coroutines.delay(1000) // 1초 대기 후 애니메이션 시작
                        animT.animateTo(
                            targetValue = targetT,
                            animationSpec = tween(
                                durationMillis = 1500,
                                easing = LinearOutSlowInEasing
                            )
                        )
                    }
                }
                
                // 1. 궤적 그리는 Canvas (가파른 돔 모양 포물선 점선 아크 - 태양 이동에 맞춰 사르륵 밝아짐)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // 지평선
                    drawLine(
                        color = Color.White.copy(alpha = 0.2f),
                        start = Offset(0f, heightPx),
                        end = Offset(widthPx, heightPx),
                        strokeWidth = 2f
                    )
                    
                    // 태양 궤적 (반원보다 더 솟은 돔형 아크)
                    val path = Path().apply {
                        moveTo(0f, heightPx)
                        quadraticBezierTo(widthPx / 2, 0f, widthPx, heightPx)
                    }
                    val pathAlpha = 0.15f * (if (targetT > 0f) animT.value / targetT else 1f)
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = pathAlpha),
                        style = Stroke(
                            width = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    )
                }
                
                val isDaytime = nowHour in 6..19
                val alphaVal = if (isDaytime) 1f else 0.4f 
                val emojiSize = 34.dp
                
                // 이차 베지에 곡선상 Dp 좌표 산출 (애니메이션 적용된 animT.value 기준)
                val tF = animT.value
                val sunXDp = (1 - tF) * (1 - tF) * 0f + 2 * (1 - tF) * tF * (widthDp.value / 2) + tF * tF * widthDp.value
                val sunYDp = (1 - tF) * (1 - tF) * heightDp.value + 2 * (1 - tF) * tF * 0f + tF * tF * heightDp.value
                
                val finalXDp = (sunXDp.dp) - (emojiSize / 2)
                val finalYDp = (sunYDp.dp) - (emojiSize / 2)
                
                Box(
                    modifier = Modifier
                        .offset(x = finalXDp, y = finalYDp)
                        .size(emojiSize)
                        .alpha(alphaVal),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "☀️", fontSize = 24.sp) // 햇님 크기를 24.sp로 확대
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("일출", color = WhiteTrans80, fontSize = 13.sp)
                    Text(sunrise, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("일몰", color = WhiteTrans80, fontSize = 13.sp)
                    Text(sunset, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MoonPhaseCard(phase: Double, modifier: Modifier = Modifier) {
    val (emoji, name, description) = when {
        phase < 0.06 || phase >= 0.94 -> Triple("🌑", "그믐달 (신월)", "오늘 밤하늘은 달이 보이지 않는 시기입니다.")
        phase < 0.18 -> Triple("🌒", "초승달", "오른쪽 끝이 얇은 눈썹 모양으로 살짝 차오른 달입니다.")
        phase < 0.32 -> Triple("🌓", "상현달 (오른쪽 반달)", "오른쪽 절반이 둥글게 가득 찬 반달 모양입니다.")
        phase < 0.44 -> Triple("🌔", "상현망간의 달", "보름달이 되기 직전, 오른쪽이 통통하게 부풀어 오른 달입니다.")
        phase < 0.56 -> Triple("🌕", "보름달 (둥근달)", "밤하늘에 동그랗고 노랗게 가득 찬 둥근달입니다.")
        phase < 0.68 -> Triple("🌖", "하현망간의 달", "보름달에서 오른쪽 끝부분만 얇게 깎여나간 모양입니다.")
        phase < 0.82 -> Triple("🌗", "하현달 (왼쪽 반달)", "왼쪽 절반이 둥글게 차오른 반달 모양입니다.")
        else -> Triple("🌘", "그믐달 (눈썹달)", "왼쪽 끝만 얇은 실눈썹 모양으로 아스라하게 남은 달입니다.")
    }
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 44.sp, modifier = Modifier.padding(end = 16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("오늘의 달 모양", color = WhiteTrans80, fontSize = 13.sp)
                Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, color = WhiteTrans80, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AirQualityDetailCard(pm10: Int, pm25: Int, modifier: Modifier = Modifier) {
    val pm10Status = when {
        pm10 <= 30 -> "좋음" to AqiGood
        pm10 <= 80 -> "보통" to AqiModerate
        pm10 <= 150 -> "나쁨" to AqiBad
        else -> "매우나쁨" to AqiVeryBad
    }
    val pm25Status = when {
        pm25 <= 15 -> "좋음" to AqiGood
        pm25 <= 35 -> "보통" to AqiModerate
        pm25 <= 75 -> "나쁨" to AqiBad
        else -> "매우나쁨" to AqiVeryBad
    }
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("😷 미세먼지 및 초미세먼지", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) // 14.sp로 확대
            Spacer(modifier = Modifier.height(14.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(modifier = Modifier.weight(1f).background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text("미세먼지 (PM10)", color = WhiteTrans80, fontSize = 12.sp) // 12.sp로 확대
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${pm10}㎍/㎥", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) // 18.sp로 확대
                        Text(pm10Status.first, color = pm10Status.second, fontSize = 14.sp, fontWeight = FontWeight.Bold) // 14.sp로 확대
                    }
                }
                
                Column(modifier = Modifier.weight(1f).background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text("초미세먼지 (PM2.5)", color = WhiteTrans80, fontSize = 12.sp) // 12.sp로 확대
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${pm25}㎍/㎥", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) // 18.sp로 확대
                        Text(pm25Status.first, color = pm25Status.second, fontSize = 14.sp, fontWeight = FontWeight.Bold) // 14.sp로 확대
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRowCard(
    icon: String,
    title: String,
    value: String,
    description: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteTrans10),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 32.sp, modifier = Modifier.padding(end = 16.dp)) 
            Column(
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
            ) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) 
                if (description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(description, color = WhiteTrans80, fontSize = 12.sp) 
                }
            }
            Text(value, color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically)) 
        }
    }
}

@Composable
fun ActionButtons(onFortuneClick: () -> Unit, onFashionClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = onFortuneClick, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = FortuneGold), modifier = Modifier.weight(1f).height(50.dp)) {
            Text("🔮 오늘의 운세", style = MaterialTheme.typography.labelLarge)
        }
        Button(onClick = onFashionClick, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = FashionGreen), modifier = Modifier.weight(1f).height(50.dp)) {
            Text("👕 코디 추천", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun BottomSheetContent(type: String, info: WeatherInfo) {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
        Text(if (type == "fortune") "🔮 오늘의 운세" else "👕 오늘 뭐 입지?", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(if (type == "fortune") info.fortuneMsg else info.fashionTip, style = MaterialTheme.typography.bodyLarge)
        if (type == "fortune") {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("행운의 컬러: ", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Box(Modifier.size(24.dp).background(FortuneGold, RoundedCornerShape(4.dp)))
                Text(" ${info.luckyColor}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 12.dp))
}
