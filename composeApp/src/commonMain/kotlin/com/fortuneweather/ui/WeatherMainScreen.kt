package com.fortuneweather.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fortuneweather.domain.model.WeatherInfo
import com.fortuneweather.ui.components.AdBanner
import com.fortuneweather.ui.components.SunnyAnimation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun WeatherApp(weatherInfo: WeatherInfo) {
    val sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    var sheetType by remember { mutableStateOf("fortune") } // "fortune" or "fashion"
    val scope = rememberCoroutineScope()

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContent = {
            BottomSheetContent(sheetType, weatherInfo)
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF64B5F6), Color(0xFF1976D2))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(60.dp))
                
                // 1. 현재 날씨 메인
                CurrentWeatherSection(weatherInfo)

                Spacer(modifier = Modifier.height(24.dp))

                // 신규: 버튼 섹션 (운세, 코디 추천)
                ActionButtons(
                    onFortuneClick = {
                        sheetType = "fortune"
                        scope.launch { sheetState.show() }
                    },
                    onFashionClick = {
                        sheetType = "fashion"
                        scope.launch { sheetState.show() }
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 2. 시간별 예보 (가로 스크롤)
                SectionTitle("24시간 예보")
                HourlyForecastSection(weatherInfo)

                Spacer(modifier = Modifier.height(30.dp))

                // 3. 주간 예보
                SectionTitle("7일간의 날씨")
                DailyForecastSection(weatherInfo)

                Spacer(modifier = Modifier.height(30.dp))

                // 4. 상세 지표 그리드
                SectionTitle("상세 정보")
                WeatherDetailGrid(weatherInfo)

                Spacer(modifier = Modifier.height(60.dp)) // 하단 광고 여백
            }

            // 하단 고정 광고만 유지
            AdBanner(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun CurrentWeatherSection(info: WeatherInfo) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(info.locationName, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f))
        Spacer(modifier = Modifier.height(8.dp))
        
//        if (info.condition.contains("Sun", ignoreCase = true) || info.condition.contains("맑음")) {
//            SunnyAnimation(modifier = Modifier.size(200.dp))
//        }
        SunnyAnimation(modifier = Modifier.size(200.dp))

        Text(info.condition, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("${info.temp.toInt()}°", fontSize = 90.sp, fontWeight = FontWeight.Thin, color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("미세먼지 ", color = Color.White.copy(alpha = 0.8f))
            val aqiText = when {
                info.aqi <= 30 -> "좋음"
                info.aqi <= 80 -> "보통"
                info.aqi <= 150 -> "나쁨"
                else -> "매우나쁨"
            }
            val aqiColor = when {
                info.aqi <= 30 -> Color(0xFF81C784)
                info.aqi <= 80 -> Color(0xFFFFD54F)
                info.aqi <= 150 -> Color(0xFFFFB74D)
                else -> Color(0xFFE57373)
            }
            Text(aqiText, color = aqiColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HourlyForecastSection(info: WeatherInfo) {
    LazyRow(contentPadding = PaddingValues(vertical = 10.dp)) {
        items(info.hourly) { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 20.dp)
            ) {
                Text(item.time, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${item.temp.toInt()}°", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DailyForecastSection(info: WeatherInfo) {
    Card(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color.White.copy(alpha = 0.15f),
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            info.daily.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item.date.substring(5), color = Color.White, modifier = Modifier.width(60.dp))
                    Text(item.condition, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                    Text("${item.maxTemp.toInt()}°", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("${item.minTemp.toInt()}°", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun WeatherDetailGrid(info: WeatherInfo) {
    val details = listOf(
        "습도" to "${info.humidity}%",
        "자외선" to "${info.uvIndex}",
        "풍향" to info.windDirection,
        "기압" to "${info.pressure.toInt()}hPa",
        "가시거리" to "${info.visibility}km",
        "일출" to info.sunrise
    )
    
    Column {
        for (i in details.indices step 2) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                DetailCard(details[i].first, details[i].second, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(16.dp))
                if (i + 1 < details.size) {
                    DetailCard(details[i+1].first, details[i+1].second, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun DetailCard(label: String, value: String, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color.White.copy(alpha = 0.1f),
        elevation = 0.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ActionButtons(
    onFortuneClick: () -> Unit,
    onFashionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = onFortuneClick,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFFD54F)),
            modifier = Modifier.weight(1f).height(50.dp)
        ) {
            Text("🔮 오늘의 운세", fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onFashionClick,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF81C784)),
            modifier = Modifier.weight(1f).height(50.dp)
        ) {
            Text("👕 코디 추천", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BottomSheetContent(type: String, info: WeatherInfo) {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
        Text(
            if (type == "fortune") "🔮 오늘의 운세" else "👕 오늘 뭐 입지?",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (type == "fortune") info.fortuneMsg else info.fashionTip,
            fontSize = 18.sp,
            lineHeight = 26.sp
        )
        if (type == "fortune") {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("행운의 컬러: ", fontWeight = FontWeight.Bold)
                Box(Modifier.size(24.dp).background(Color.Yellow, RoundedCornerShape(4.dp)))
                Text(" ${info.luckyColor}")
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        title,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}
