package com.fortuneweather.domain.model

import kotlinx.serialization.Serializable

/**
 * 시간별 날씨 정보
 */
@Serializable
data class HourlyForecast(
    val time: String,
    val temp: Double,
    val condition: String
)

/**
 * 주간 날씨 정보
 */
@Serializable
data class DailyForecast(
    val date: String,
    val maxTemp: Double,
    val minTemp: Double,
    val condition: String
)

/**
 * 포춘웨더 통합 날씨 정보 모델 (확장판)
 */
@Serializable
data class WeatherInfo(
    val locationName: String,   // 지역 이름 (추가)
    val temp: Double,           // 평균 기온 (Celsius)
    val humidity: Int,          // 평균 습도 (%)
    val condition: String,      // 날씨 상태 (맑음, 비, 눈 등)
    val aqi: Int,               // 미세먼지 지수
    
    // 상세 지표 (신규 추가)
    val uvIndex: Double,        // 자외선 지수
    val windDirection: String,  // 풍향
    val pressure: Double,       // 기압 (hPa)
    val visibility: Double,     // 가시거리 (km)
    val sunrise: String,        // 일출 시간
    val sunset: String,         // 일몰 시간
    
    // 예보 정보 (신규 추가)
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    
    // 부가 서비스 (버튼 클릭 시 노출)
    val luckyColor: String,     // 오늘의 럭키 컬러
    val fashionTip: String,     // 옷차림 추천
    val fortuneMsg: String,     // 오늘의 행운 메시지
    
    val sourceCount: Int        // 데이터 합산 API 소스 개수
)

/**
 * API별 가공되지 않은 날씨 데이터 (확장판)
 */
data class RawWeatherData(
    val temp: Double,
    val humidity: Int,
    val condition: String,
    val locationName: String? = null,
    val uvIndex: Double? = null,
    val pressure: Double? = null,
    val visibility: Double? = null,
    val windDirection: String? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val hourly: List<HourlyForecast> = emptyList(),
    val daily: List<DailyForecast> = emptyList()
)
