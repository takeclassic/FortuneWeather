package com.fortuneweather.domain.model

import kotlinx.serialization.Serializable
import com.fortuneweather.utils.WeatherConstants

@Serializable
data class HourlyForecast(
    val time: String,
    val temp: Double,
    val condition: String = WeatherConstants.NO_DATA,
    val precipitationProbability: Int = 0,
    val aqi: Int = 0,
    val rawTime: String? = null
)

@Serializable
data class DailyForecast(
    val date: String,
    val dayOfWeek: String,
    val precipitationProbability: Int,
    val morningCondition: String = WeatherConstants.NO_DATA,
    val afternoonCondition: String = WeatherConstants.NO_DATA,
    val minTemp: Double,
    val maxTemp: Double,
    val aqi: Int = 0,
    val hourlyDetails: List<HourlyForecast> = emptyList(),
    val rawDate: String? = null
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
    val temp: Double = WeatherConstants.TEMP_UNKNOWN,
    val humidity: Int = WeatherConstants.INT_UNKNOWN,
    val condition: String = WeatherConstants.NO_DATA,
    val locationName: String = WeatherConstants.NO_DATA,
    val hourly: List<HourlyForecast> = emptyList(),
    val daily: List<DailyForecast> = emptyList(),
    val windDirection: String = WeatherConstants.NO_DATA,
    val windSpeed: Double = WeatherConstants.DOUBLE_UNKNOWN,
    val rainAmount: Double = WeatherConstants.DOUBLE_UNKNOWN
)

data class WeatherCache(
    val weatherInfo: WeatherInfo,
    val cachedTimeMillis: Long,
    val lat: Double,
    val lon: Double
)

data class OwmForecastResult(
    val dailyList: List<DailyForecast> = emptyList(),
    val hourlyList: List<HourlyForecast> = emptyList(),
    val currentTemp: Double = WeatherConstants.TEMP_UNKNOWN,
    val currentCondition: String = WeatherConstants.NO_DATA,
    val currentHumidity: Int = WeatherConstants.INT_UNKNOWN,
    val currentWindSpeed: Double = WeatherConstants.DOUBLE_UNKNOWN,
    val currentWindDeg: Double = WeatherConstants.DOUBLE_UNKNOWN,
    val currentRain: Double = WeatherConstants.DOUBLE_UNKNOWN,
    val currentVisibility: Double = WeatherConstants.DOUBLE_UNKNOWN,
    val currentPressure: Double = WeatherConstants.DOUBLE_UNKNOWN,
    val cityName: String = WeatherConstants.NO_DATA
)
