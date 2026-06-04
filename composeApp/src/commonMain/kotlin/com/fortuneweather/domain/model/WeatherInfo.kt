package com.fortuneweather.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class HourlyForecast(
    val time: String,
    val temp: Double,
    val condition: String,
    val precipitationProbability: Int, // 시간별 강수확률 추가
    val aqi: Int = 0 // 시간별 미세먼지 수치 추가
)

@Serializable
data class HourlyDetail(
    val time: String,
    val temp: Double,
    val condition: String,
    val aqi: Int = 0,
    val precipitationProbability: Int = 0
)

@Serializable
data class DailyForecast(
    val date: String,
    val dayOfWeek: String,
    val precipitationProbability: Int,
    val morningCondition: String,
    val afternoonCondition: String,
    val minTemp: Double,
    val maxTemp: Double,
    val aqi: Int = 0,
    val hourlyDetails: List<HourlyDetail> = emptyList()
)

@Serializable
data class WeatherInfo(
    val locationName: String,
    val temp: Double,
    val feelsLike: Double,
    val humidity: Int,
    val condition: String,
    val aqi: Int,
    val pm10: Int,
    val pm25: Int,
    val uvIndex: Double,
    val windDirection: String,
    val windSpeed: Double,
    val rainAmount: Double,
    val pressure: Double,
    val visibility: Double,
    val sunrise: String,
    val sunset: String,
    val moonPhase: Double,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val luckyColor: String,
    val fashionTip: String,
    val fortuneMsg: String,
    val sourceCount: Int
)

data class RawWeatherData(
    val temp: Double,
    val humidity: Int,
    val condition: String,
    val locationName: String? = null,
    val hourly: List<HourlyForecast> = emptyList(),
    val daily: List<DailyForecast> = emptyList(),
    val windDirection: String? = null,
    val windSpeed: Double = 0.0,
    val rainAmount: Double = 0.0
)

@Serializable
data class WeatherCache(
    val weatherInfo: WeatherInfo,
    val cachedTimeMillis: Long,
    val lat: Double,
    val lon: Double
)
