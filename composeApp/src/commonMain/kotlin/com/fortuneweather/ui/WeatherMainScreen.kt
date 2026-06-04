package com.fortuneweather.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fortuneweather.domain.model.*
import com.fortuneweather.ui.components.AdBanner
import com.fortuneweather.ui.components.WeatherAnimation
import com.fortuneweather.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun WeatherApp(weatherInfo: WeatherInfo, isRefreshing: Boolean, onRefresh: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    var sheetType by remember { mutableStateOf("fortune") }
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh)

    val isNight = remember(weatherInfo) {
        try {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val currentInMin = now.hour * 60 + now.minute
            val sunriseParts = (weatherInfo.sunrise ?: "06:30").split(":")
            val sunsetParts = (weatherInfo.sunset ?: "19:00").split(":")
            val sunriseInMin = sunriseParts[0].toInt() * 60 + sunriseParts[1].toInt()
            val sunsetInMin = sunsetParts[0].toInt() * 60 + sunsetParts[1].toInt()
            currentInMin < sunriseInMin || currentInMin > sunsetInMin
        } catch (e: Exception) {
            val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
            hour < 6 || hour > 18
        }
    }

    FortuneWeatherTheme {
        ModalBottomSheetLayout(
            sheetState = sheetState,
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            sheetContent = { BottomSheetContent(sheetType, weatherInfo) }
        ) {
            val backgroundColors = if (isNight) listOf(NightDeepBlue, NightBlackBlue) else listOf(SunnyLight, SunnyDark)
            Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = backgroundColors)).pullRefresh(pullRefreshState)) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    Spacer(modifier = Modifier.height(60.dp))
                    CurrentWeatherSection(weatherInfo, isNight)
                    Spacer(modifier = Modifier.height(24.dp))
                    ActionButtons(onFortuneClick = { sheetType = "fortune"; scope.launch { sheetState.show() } }, onFashionClick = { sheetType = "fashion"; scope.launch { sheetState.show() } })
                    Spacer(modifier = Modifier.height(32.dp))
                    SectionTitle("24시간 예보")
                    HourlyForecastSection(weatherInfo)
                    Spacer(modifier = Modifier.height(30.dp))
                    SectionTitle("5일간의 날씨")
                    DailyForecastSection(weatherInfo)
                    Spacer(modifier = Modifier.height(30.dp))
                    SectionTitle("상세 정보")
                    WeatherDetailGrid(weatherInfo)
                    Spacer(modifier = Modifier.height(80.dp))
                }
                PullRefreshIndicator(refreshing = isRefreshing, state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter), backgroundColor = Color.White, contentColor = SunnyDark)
                AdBanner(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(50.dp).background(Color.White))
            }
        }
    }
}

@Composable
fun DailyForecastSection(info: WeatherInfo) {
    Card(shape = RoundedCornerShape(16.dp), backgroundColor = WhiteTrans15, elevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            info.daily.forEach { item ->
                DailyForecastItem(item)
                if (info.daily.last() != item) Divider(color = WhiteTrans10, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun DailyForecastItem(item: DailyForecast) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().animateContentSize().clickable { isExpanded = !isExpanded }) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 1. 요일
            Text(text = item.dayOfWeek, color = Color.White, style = MaterialTheme.typography.body1, modifier = Modifier.width(42.dp), fontWeight = FontWeight.Normal)
            
            if (item.morningCondition == "데이터 준비중") {
                Spacer(modifier = Modifier.weight(1f))
                Text("정보를 불러오는 중입니다...", color = WhiteTrans80, style = MaterialTheme.typography.caption, modifier = Modifier.weight(3f))
            } else {
                // 2. 강수확률
                Row(modifier = Modifier.width(50.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "💧", fontSize = 10.sp, modifier = Modifier.padding(end = 2.dp))
                    Text(text = "${item.precipitationProbability}%", color = WhiteTrans80, style = MaterialTheme.typography.caption)
                }
                
                // 3. 미세먼지 등급 표기
                val (aqiText, aqiColor) = when {
                    item.aqi <= 30 -> "좋음" to AqiGood
                    item.aqi <= 80 -> "보통" to AqiModerate
                    item.aqi <= 150 -> "나쁨" to AqiBad
                    else -> "매우나쁨" to AqiVeryBad
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(50.dp)) {
                    Text(text = "😷", fontSize = 9.sp, modifier = Modifier.padding(end = 1.dp))
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
                    Text(text = "${item.minTemp.toInt()}°", color = WhiteTrans80, style = MaterialTheme.typography.body1)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "${item.maxTemp.toInt()}°", color = Color.White, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Bold)
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
                        val animRatio = remember { androidx.compose.animation.core.Animatable(0f) }
                        LaunchedEffect(isExpanded) {
                            if (isExpanded) {
                                animRatio.animateTo(
                                    targetValue = ratio,
                                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 800, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
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
                            // 1. 시간 (고정 폭 55dp로 어긋남 방지)
                            Text(
                                text = detail.time,
                                color = WhiteTrans80,
                                fontSize = 11.sp,
                                modifier = Modifier.width(55.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            // 2. 강수확률 (고정 폭 40dp로 어긋남 방지)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.width(40.dp)
                            ) {
                                Text(text = "💧", fontSize = 9.sp, modifier = Modifier.padding(end = 2.dp))
                                Text(
                                    text = "${detail.precipitationProbability}%",
                                    color = WhiteTrans80,
                                    fontSize = 11.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            // 3. 날씨 아이콘 (22dp)
                            WeatherAnimation(
                                condition = detail.condition,
                                isNight = detail.time.contains("오후 8시") || detail.time.contains("오후 10시"),
                                animate = false,
                                modifier = Modifier.size(22.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(10.dp))
                            
                            // 4. 기온 게이지 영역 (우측 끝까지 쭉 뻗은 트랙)
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .align(Alignment.CenterVertically)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            ) {
                                val capsuleWidth = 36.dp
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
fun HourlyForecastSection(info: WeatherInfo) {
    Card(shape = RoundedCornerShape(16.dp), backgroundColor = WhiteTrans10, elevation = 0.dp, modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            items(info.hourly) { item ->
                val isItemNight = remember(item.time) {
                    try {
                        val isPm = item.time.contains("pm")
                        val rawHour = item.time.substringAfter(if (isPm) "pm " else "am ").substringBefore("시").trim().toInt()
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
                    Text(text = item.time, color = WhiteTrans80, fontSize = 10.sp, style = MaterialTheme.typography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    WeatherAnimation(condition = item.condition, isNight = isItemNight, animate = true, modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${item.temp.toInt()}°", color = Color.White, style = MaterialTheme.typography.h4)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "💧", fontSize = 8.sp, modifier = Modifier.padding(end = 2.dp))
                        Text(text = "${item.precipitationProbability}%", color = WhiteTrans80, fontSize = 9.sp, style = MaterialTheme.typography.caption)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val (aqiText, aqiColor) = when {
                        item.aqi <= 30 -> "좋음" to AqiGood
                        item.aqi <= 80 -> "보통" to AqiModerate
                        item.aqi <= 150 -> "나쁨" to AqiBad
                        else -> "매우나쁨" to AqiVeryBad
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "😷", fontSize = 8.sp, modifier = Modifier.padding(end = 2.dp))
                        Text(text = "$aqiText(${item.aqi})", color = aqiColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentWeatherSection(info: WeatherInfo, isNight: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(info.locationName, style = MaterialTheme.typography.h3, color = WhiteTrans80)
        Spacer(modifier = Modifier.height(8.dp))
        WeatherAnimation(condition = info.condition, isNight = isNight, animate = true, modifier = Modifier.size(180.dp))
        Text(info.condition, style = MaterialTheme.typography.h2, color = Color.White)
        Text("${info.temp.toInt()}°", style = MaterialTheme.typography.h1, color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("미세먼지 ", style = MaterialTheme.typography.body2, color = WhiteTrans80)
            val (text, color) = when {
                info.aqi <= 30 -> "좋음" to AqiGood
                info.aqi <= 80 -> "보통" to AqiModerate
                info.aqi <= 150 -> "나쁨" to AqiBad
                else -> "매우나쁨" to AqiVeryBad
            }
            Text("$text(${info.aqi})", color = color, style = MaterialTheme.typography.body1)
        }
    }
}

@Composable
fun WeatherDetailGrid(info: WeatherInfo) {
    val currentHour = remember(info) {
        try {
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
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

@Composable
fun SunriseSunsetCard(sunrise: String, sunset: String, nowHour: Int, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = WhiteTrans10,
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("☀️ 일출 및 일몰", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            // 가로 너비를 짧게 줄이고, 포물선 각(높이)을 높이기 위해 padding(horizontal = 48.dp)과 height(100.dp)를 적용
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
                
                // 1. 궤적 그리는 Canvas (가파른 돔 모양 포물선 점선 아크)
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    // 지평선
                    drawLine(
                        color = Color.White.copy(alpha = 0.2f),
                        start = androidx.compose.ui.geometry.Offset(0f, heightPx),
                        end = androidx.compose.ui.geometry.Offset(widthPx, heightPx),
                        strokeWidth = 2f
                    )
                    
                    // 태양 궤적 (반원보다 더 솟은 돔형 아크)
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, heightPx)
                        quadraticBezierTo(widthPx / 2, 0f, widthPx, heightPx)
                    }
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.15f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    )
                }
                
                // 2. 태양 위치 비율 계산
                val t = when {
                    nowHour < 6 -> 0f       
                    nowHour >= 20 -> 1f     
                    else -> ((nowHour - 6).toFloat() / 13f).coerceIn(0f, 1f) 
                }
                
                val isDaytime = nowHour in 6..19
                val alphaVal = if (isDaytime) 1f else 0.4f 
                val emojiSize = 34.dp
                
                // 이차 베지에 곡선상 Dp 좌표 산출
                val tF = t
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
        backgroundColor = WhiteTrans10,
        elevation = 0.dp,
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
        backgroundColor = WhiteTrans10,
        elevation = 0.dp,
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
        backgroundColor = WhiteTrans10,
        elevation = 0.dp,
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
        Button(onClick = onFortuneClick, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(backgroundColor = FortuneGold), modifier = Modifier.weight(1f).height(50.dp)) {
            Text("🔮 오늘의 운세", style = MaterialTheme.typography.button)
        }
        Button(onClick = onFashionClick, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(backgroundColor = FashionGreen), modifier = Modifier.weight(1f).height(50.dp)) {
            Text("👕 코디 추천", style = MaterialTheme.typography.button)
        }
    }
}

@Composable
fun BottomSheetContent(type: String, info: WeatherInfo) {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
        Text(if (type == "fortune") "🔮 오늘의 운세" else "👕 오늘 뭐 입지?", style = MaterialTheme.typography.h3)
        Spacer(modifier = Modifier.height(16.dp))
        Text(if (type == "fortune") info.fortuneMsg else info.fashionTip, style = MaterialTheme.typography.body1)
        if (type == "fortune") {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("행운의 컬러: ", style = MaterialTheme.typography.body1, fontWeight = FontWeight.Bold)
                Box(Modifier.size(24.dp).background(FortuneGold, RoundedCornerShape(4.dp)))
                Text(" ${info.luckyColor}", style = MaterialTheme.typography.body1)
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, color = Color.White, style = MaterialTheme.typography.h4, modifier = Modifier.padding(vertical = 12.dp))
}
