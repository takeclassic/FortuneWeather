package com.fortuneweather.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 기상청 (KMA) 단기예보 / 초단기실황 DTO
 */
@Serializable
data class KmaWeatherResponse(val response: KmaBody)
@Serializable
data class KmaBody(val body: KmaItems?)
@Serializable
data class KmaItems(val items: KmaItemList?)
@Serializable
data class KmaItemList(val item: List<KmaItem>)
@Serializable
data class KmaItem(
    val category: String,
    val fcstValue: String,
    val fcstDate: String? = null,
    val fcstTime: String? = null,
    val obsrValue: String? = null // 초단기실황용 필드 추가
)

/**
 * OpenWeatherMap 대기오염 DTO
 */
@Serializable
data class OwmAirPollutionResponse(
    val list: List<OwmAirPollutionItem>
)

@Serializable
data class OwmAirPollutionItem(
    val dt: Long,
    val main: OwmAqi,
    val components: OwmComponents
)

@Serializable
data class OwmAqi(
    val aqi: Int
)

@Serializable
data class OwmComponents(
    val pm10: Double,
    @SerialName("pm2_5") val pm25: Double
)

/**
 * OpenWeatherMap 5-day / 3-hour Forecast DTO
 */
@Serializable
data class OwmForecastResponse(
    val list: List<OwmForecastItem>,
    val city: OwmCity? = null
)

@Serializable
data class OwmCity(
    val name: String,
    val country: String? = null
)

@Serializable
data class OwmForecastItem(
    val dt: Long,
    val main: OwmForecastMain,
    val weather: List<OwmForecastWeather>,
    val pop: Double? = 0.0,
    @SerialName("dt_txt") val dtTxt: String,
    val wind: OwmWind? = null,
    val rain: OwmRain? = null
)

@Serializable
data class OwmWind(
    val speed: Double,
    val deg: Double
)

@Serializable
data class OwmRain(
    @SerialName("3h") val threeHour: Double? = 0.0
)

@Serializable
data class OwmForecastMain(
    val temp: Double,
    @SerialName("temp_min") val tempMin: Double,
    @SerialName("temp_max") val tempMax: Double,
    val humidity: Int
)

@Serializable
data class OwmForecastWeather(
    val main: String,
    val description: String
)
