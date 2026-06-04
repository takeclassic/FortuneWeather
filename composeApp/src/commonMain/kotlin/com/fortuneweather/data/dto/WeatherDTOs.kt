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
data class MainData(
    val temp: Double,
    val humidity: Int,
    val pressure: Double? = null,
    @SerialName("temp_min") val tempMin: Double? = null,
    @SerialName("temp_max") val tempMax: Double? = null
)

@Serializable
data class WeatherDescription(val main: String, val description: String? = null)

@Serializable
data class WindData(val speed: Double, val deg: Int)

@Serializable
data class SysData(val sunrise: Long, val sunset: Long)

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
data class WeatherApiAirQuality(
    val pm10: Double? = null,
    @SerialName("pm2_5") val pm25: Double? = null
)

@Serializable
data class CurrentWeather(
    @SerialName("temp_c") val tempC: Double,
    val humidity: Int,
    val condition: Condition,
    @SerialName("uv") val uvIndex: Double? = null,
    @SerialName("vis_km") val visibilityKm: Double? = null,
    @SerialName("pressure_mb") val pressureMb: Double? = null,
    @SerialName("wind_dir") val windDir: String? = null,
    @SerialName("air_quality") val airQuality: WeatherApiAirQuality? = null
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
    val condition: Condition,
    @SerialName("daily_chance_of_rain") val dailyChanceOfRain: Int? = null,
    @SerialName("daily_chance_of_snow") val dailyChanceOfSnow: Int? = null,
    @SerialName("air_quality") val airQuality: WeatherApiAirQuality? = null
)

@Serializable
data class HourInfo(
    val time: String,
    @SerialName("temp_c") val tempC: Double,
    val condition: Condition,
    @SerialName("chance_of_rain") val chanceOfRain: Int? = null,
    @SerialName("chance_of_snow") val chanceOfSnow: Int? = null,
    @SerialName("air_quality") val airQuality: WeatherApiAirQuality? = null
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
data class KmaItem(
    val category: String,
    val fcstValue: String,
    val fcstDate: String? = null,
    val fcstTime: String? = null,
    val obsrValue: String? = null // 초단기실황용 필드 추가
)

@Serializable
data class AirKoreaResponse(val response: AirBody)
@Serializable
data class AirBody(val body: AirItems)
@Serializable
data class AirItems(val items: List<AirItem>)
@Serializable
data class AirItem(val khaiValue: String)

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

@Serializable
data class OwmForecastResponse(
    val list: List<OwmForecastItem>
)

@Serializable
data class OwmForecastItem(
    val dt: Long,
    val main: OwmForecastMain,
    val weather: List<OwmForecastWeather>,
    val pop: Double? = 0.0,
    @SerialName("dt_txt") val dtTxt: String
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


@Serializable
data class OpenMeteoForecastResponse(
    val hourly: OmfHourly
)

@Serializable
data class OmfHourly(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature2m: List<Double>,
    @SerialName("weather_code") val weatherCode: List<Int>
)




