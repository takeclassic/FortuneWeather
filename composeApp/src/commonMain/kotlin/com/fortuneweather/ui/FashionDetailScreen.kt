package com.fortuneweather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fortuneweather.domain.model.WeatherInfo
import com.fortuneweather.ui.theme.*

data class FashionRecommendation(
    val category1: String, // 핵심 아우터/상의
    val item1: String,
    val category2: String, // 핵심 상의/하의/소품
    val item2: String,
    val styleTip: String
)

@Composable
fun FashionDetailScreen(
    weatherInfo: WeatherInfo,
    onBackClick: () -> Unit
) {
    val temp = weatherInfo.temp
    val condition = weatherInfo.condition
    val windSpeed = weatherInfo.windSpeed

    val todayForecast = remember(weatherInfo) {
        weatherInfo.daily.firstOrNull()
    }
    
    val maxTemp = todayForecast?.maxTemp ?: temp
    val minTemp = todayForecast?.minTemp ?: temp
    val morningCond = todayForecast?.morningCondition ?: condition
    val afternoonCond = todayForecast?.afternoonCondition ?: condition
    val rainProb = todayForecast?.precipitationProbability ?: 0

    val recommendation = remember(temp, condition, windSpeed) {
        getFashionRecommendation(temp, condition, windSpeed)
    }

    // 오늘 날씨에 대한 스마트 한 줄 요약 생성
    val weatherSummary = remember(minTemp, maxTemp, morningCond, afternoonCond) {
        getDailyWeatherSummary(minTemp, maxTemp, morningCond, afternoonCond)
    }

    // 패션 매거진 느낌의 트렌디하고 화사한 크림-베이지 톤 그라데이션 백그라운드
    val fashionLightGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFAF8F5), // Creamy Off-White
                Color(0xFFEBE6DC)  // Soft Warm Beige
            )
        )
    }

    // 밝은 배경에 세련되게 스며드는 어두운 차콜 계열 텍스트 색상
    val darkCharcoal = Color(0xFF1E1E1E)
    val mutedGray = Color(0xFF5A5A5A)
    val brownAccent = Color(0xFF8D7B68) // ◀ 날씨 백키용 어스 브라운 (Earth Brown)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(fashionLightGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. 헤더 영역
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "◀ 날씨",
                    color = brownAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier
                        .clickable { onBackClick() }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "오늘의 코디 추천",
                    color = darkCharcoal,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. 배경 일체형 세련된 인용 블록 (투박한 꺾쇠 대신, 얇은 에스 브라운 세로선 지시선 + 얇은 서체로 룩앤필 고급화)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(brownAccent.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = weatherSummary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = darkCharcoal,
                    lineHeight = 20.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. 종합 날씨 브리핑 카드
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "TEMPERATURE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = brownAccent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${minTemp.toInt()}°  /  ${maxTemp.toInt()}°",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = darkCharcoal,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "오전 예보  —  $morningCond\n오후 예보  —  $afternoonCond" + 
                               if (rainProb > 0) "\n강수확률  —  $rainProb%" else "",
                        fontSize = 13.sp,
                        color = mutedGray,
                        lineHeight = 22.sp,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 타이틀 (추천하는 옷차림)
            Text(
                text = "추천하는 옷차림",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = darkCharcoal,
                letterSpacing = 0.2.sp,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
            )

            // 4. 추천 의류 아이템 카드 2선 (이모지 완전 제거, 서체 굵기 대조를 극대화해 시크하게 표현)
            // 아이템 1
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = recommendation.category1.uppercase(),
                        fontSize = 11.sp,
                        color = brownAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = recommendation.item1,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = darkCharcoal,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            // 아이템 2
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = recommendation.category2.uppercase(),
                        fontSize = 11.sp,
                        color = brownAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = recommendation.item2,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = darkCharcoal,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            // 5. 종합 스타일 가이드 카드 (이모지 제거, 영문 서체 병기)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "STYLE GUIDE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = brownAccent,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = recommendation.styleTip,
                        fontSize = 13.sp,
                        color = mutedGray,
                        lineHeight = 22.sp,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}

// 기상 상태 및 기온 데이터를 조합하여 자연스러운 한국어 날씨 한 줄 요약을 생성하는 룰 엔진
private fun getDailyWeatherSummary(
    minTemp: Double,
    maxTemp: Double,
    morningCond: String,
    afternoonCond: String
): String {
    val morningLower = morningCond.lowercase()
    val afternoonLower = afternoonCond.lowercase()
    val isRainy = morningLower.contains("비") || morningLower.contains("소나기") || 
                  afternoonLower.contains("비") || afternoonLower.contains("소나기")
    val isSnowy = morningLower.contains("눈") || afternoonLower.contains("눈")
    val isThunder = morningLower.contains("낙뢰") || morningLower.contains("뇌우") || morningLower.contains("번개") ||
                     afternoonLower.contains("낙뢰") || afternoonLower.contains("뇌우") || afternoonLower.contains("번개")

    return when {
        isThunder -> "번개나 천둥을 동반한 요란한 비바람이 내리는 하루입니다. 외출 시 꼭 주의하세요."
        isSnowy -> "온 세상이 하얗게 변하는 눈 소식이 있는 하루입니다. 빙판길 조심해서 외출하세요."
        isRainy -> "촉촉한 비나 소나기가 내리는 하루입니다. 외출할 때 우산 꼭 챙기세요."
        maxTemp >= 30.0 -> "30도를 웃도는 푹푹 찌는 찜통더위가 찾아오는 무더운 하루입니다."
        maxTemp >= 27.0 -> "햇볕이 내리쬐어 후덥지근하고 다소 더운 기운이 맴도는 하루입니다."
        maxTemp - minTemp >= 10.0 && minTemp <= 14.0 -> "아침저녁은 쌀쌀하고 낮에는 포근해 일교차가 10도 이상 벌어지는 하루입니다. 감기 조심하세요."
        maxTemp >= 20.0 -> "따스한 햇살과 기분 좋은 선선함이 공존해 야외로 나가기 좋은 완벽한 하루입니다."
        maxTemp >= 16.0 -> "선선한 바람이 불며 가을/봄의 정취를 느끼기 좋은 선선한 하루입니다."
        maxTemp >= 10.0 -> "옷깃 사이로 차가운 기운이 스며들어 도톰한 아우터가 필요한 쌀쌀한 하루입니다."
        minTemp <= -5.0 -> "꽁꽁 얼어붙는 매서운 한파와 칼바람이 불어오는 아주 추운 영하권 하루입니다."
        minTemp <= 0.0 -> "코끝 시린 겨울의 공기가 몸을 움츠러들게 만드는 추운 하루입니다."
        else -> "찬 기운이 은근히 맴돌아 외출 시 따뜻하게 입어야 하는 쌀쌀한 겨울 하루입니다."
    }
}

private fun getFashionRecommendation(temp: Double, condition: String, windSpeed: Double): FashionRecommendation {
    val rec = when {
        temp >= 27.0 -> FashionRecommendation(
            category1 = "Top",
            item1 = "반팔 티셔츠 / 린넨 셔츠",
            category2 = "Bottom",
            item2 = "반바지 / 가벼운 면스커트",
            styleTip = "햇볕이 뜨거운 한여름 날씨입니다. 통풍이 잘되는 가벼운 소재의 의류를 선택하시고, 자외선 차단제와 선글라스를 함께 매치해 시원함과 스타일을 모두 챙겨보세요."
        )
        temp >= 23.0 -> FashionRecommendation(
            category1 = "Top",
            item1 = "반팔 셔츠 / 얇은 긴팔",
            category2 = "Bottom",
            item2 = "청바지 / 린넨 슬랙스",
            styleTip = "낮에는 살짝 덥고 아침저녁은 선선한 전형적인 초여름 날씨입니다. 가볍고 얇은 반팔 의류에 아침저녁 덧입을 얇은 셔츠를 레이어드하는 스타일을 권장합니다."
        )
        temp >= 20.0 -> FashionRecommendation(
            category1 = "Top",
            item1 = "긴팔 셔츠 / 얇은 가디건",
            category2 = "Bottom",
            item2 = "치노 팬츠 / 코튼 슬랙스",
            styleTip = "활동하기 아주 포근하고 쾌적한 날씨입니다. 얇은 니트나 정갈한 셔츠를 매칭하여 단정하고 캐주얼한 세미 포멀 스타일을 연출하기 좋습니다."
        )
        temp >= 17.0 -> FashionRecommendation(
            category1 = "Outer",
            item1 = "니트 가디건 / 얇은 자켓",
            category2 = "Bottom",
            item2 = "청바지 / 면바지",
            styleTip = "선선한 기운이 도는 봄/가을철 날씨입니다. 바람을 막아줄 얇은 아우터가 필요하며, 이너웨어로는 가벼운 긴팔이나 얇은 스웨터를 겹쳐 입는 것이 좋습니다."
        )
        temp >= 12.0 -> FashionRecommendation(
            category1 = "Outer",
            item1 = "트렌치코트 / 가죽 자켓",
            category2 = "Inner",
            item2 = "도톰한 스웨터 / 니트웨어",
            styleTip = "환절기 특유의 쌀쌀한 바람이 느껴집니다. 트렌치코트나 블루종 자켓 안에 니트나 맨투맨을 입어 바람을 차단하고, 포근한 컬러감의 팬츠로 가을 분위기를 내보세요."
        )
        temp >= 9.0 -> FashionRecommendation(
            category1 = "Outer",
            item1 = "울 코트 / 가벼운 패딩 자켓",
            category2 = "Inner",
            item2 = "터틀넥 스웨터 / 니트",
            styleTip = "한기가 느껴지며 겨울의 문턱으로 접어드는 시기입니다. 도톰한 울 코트를 꺼내 입기 좋은 기온이며, 목을 보호할 수 있는 터틀넥이나 가벼운 머플러를 조합해보세요."
        )
        temp >= 5.0 -> FashionRecommendation(
            category1 = "Outer",
            item1 = "두꺼운 코트 / 경량 패딩",
            category2 = "Accent",
            item2 = "가죽 장갑 / 캐시미어 목도리",
            styleTip = "본격적인 추위가 시작되는 초겨울 날씨입니다. 두꺼운 코트를 메인 아우터로 입으시고, 패딩 베스트(경량 조끼)를 레이어드하여 세련됨과 보온성을 모두 챙겨보세요."
        )
        else -> FashionRecommendation(
            category1 = "Outer",
            item1 = "헤비 다운 패딩 / 롱패딩",
            category2 = "Inner",
            item2 = "기모 팬츠 / 터틀넥 풀오버",
            styleTip = "매우 추운 겨울철 날씨입니다. 보온성이 뛰어난 구스다운 패딩을 착용하시고 모자, 목도리, 장갑 등으로 공기 통로를 막아 체온 유지에 총력을 기울여주세요."
        )
    }

    // 날씨 특성 추가 결합 (이모지 완전 제거)
    val conditionTip = when {
        condition.contains("비") || condition.contains("소나기") -> {
            "\n\n[우천 가이드] 비 소식이 있으니 방수가 잘 되는 아우터나 신발을 착용하시고, 외출 시 큰 우산을 꼭 준비하세요."
        }
        condition.contains("눈") -> {
            "\n\n[폭설 가이드] 눈으로 인해 길이 미끄러울 수 있으니, 굽이 낮고 접지력이 좋은 방한 워커나 부츠를 챙겨 신으세요."
        }
        windSpeed >= 15.0 -> {
            "\n\n[강풍 가이드] 강한 바람이 불고 있으니, 윈드브레이커(바람막이) 재질의 조밀한 외투를 선택하여 방풍 효과를 높이세요."
        }
        else -> ""
    }

    return rec.copy(styleTip = rec.styleTip + conditionTip)
}
