package com.fortuneweather.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenWeatherMap 응답 DTO (확장)
 */
@Serializable
data class OpenWeatherResponse(
    val name: String? = null,
    val main: MainData,
    val weather: List<WeatherDescription>,
    val visibility: Double? = null,
    val wind: WindData? = null,
    val sys: SysData? = null
)

@Serializable
data class MainData(val temp: Double, val humidity: Int, val pressure: Double? = null)

@Serializable
data class WeatherDescription(val main: String, val description: String? = null)

@Serializable
data class WindData(val speed: Double, val deg: Int)

@Serializable
data class SysData(val sunrise: Long, val sunset: Long)

/**
 * WeatherAPI 응답 DTO (Forecast 포함 확장)
 */
@Serializable
data class WeatherApiResponse(
    val location: Location? = null,
    val current: CurrentWeather,
    val forecast: ForecastData? = null
)

@Serializable
data class Location(
    val name: String,
    val region: String,
    val country: String
)

@Serializable
data class CurrentWeather(
    @SerialName("temp_c") val tempC: Double,
    val humidity: Int,
    val condition: Condition,
    @SerialName("uv") val uvIndex: Double? = null,
    @SerialName("vis_km") val visibilityKm: Double? = null,
    @SerialName("pressure_mb") val pressureMb: Double? = null,
    @SerialName("wind_dir") val windDir: String? = null
)

@Serializable
data class Condition(val text: String)

@Serializable
data class ForecastData(
    val forecastday: List<ForecastDay>
)

@Serializable
data class ForecastDay(
    val date: String,
    val day: DayInfo,
    val hour: List<HourInfo>? = null
)

@Serializable
data class DayInfo(
    @SerialName("maxtemp_c") val maxTemp: Double,
    @SerialName("mintemp_c") val minTemp: Double,
    val condition: Condition
)

@Serializable
data class HourInfo(
    val time: String,
    @SerialName("temp_c") val tempC: Double,
    val condition: Condition
)

/**
 * 기상청 및 에어코리아 DTO는 기존 유지 (필요시 추후 확장)
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
data class KmaItem(val category: String, val fcstValue: String)

@Serializable
data class AirKoreaResponse(val response: AirBody)
@Serializable
data class AirBody(val body: AirItems)
@Serializable
data class AirItems(val items: List<AirItem>)
@Serializable
data class AirItem(val khaiValue: String)

