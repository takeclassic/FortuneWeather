package com.fortuneweather.data.datasource

import com.fortuneweather.BuildKonfig
import com.fortuneweather.data.dto.*
import com.fortuneweather.domain.model.DailyForecast
import com.fortuneweather.domain.model.HourlyForecast
import com.fortuneweather.domain.model.RawWeatherData
import com.fortuneweather.utils.CoordinateConverter
import com.fortuneweather.utils.LatLonToGrid
import com.fortuneweather.utils.Logger
import com.fortuneweather.utils.WeatherConstants
import io.ktor.client.*
import kotlinx.datetime.*
import kotlinx.serialization.json.*

class KmaWeatherDataSource(client: HttpClient) : BaseRemoteDataSource(client) {

    private val kmaServiceKey = BuildKonfig.KMA_SERVICE_KEY
    private val kstZone = TimeZone.of("Asia/Seoul")

    suspend fun fetchNearestStation(lat: Double, lon: Double): String {
        return try {
            val (tmX, tmY) = CoordinateConverter.wgs84ToTM(lat, lon)
            val url = "http://apis.data.go.kr/B552584/MsrstnInfoInqireSvc/getNearbyMsrstnList?serviceKey=$kmaServiceKey&returnType=json&tmX=$tmX&tmY=$tmY&ver=1.0"
            val responseText = safeGet(url) ?: return WeatherConstants.DEFAULT_STATION
            val parsed = json.parseToJsonElement(responseText)
            val station = parsed.jsonObject["response"]?.jsonObject?.get("body")?.jsonObject?.get("items")
                ?.jsonObject?.get("item")?.jsonArray?.getOrNull(0)?.jsonObject?.get("stationName")
                ?.jsonPrimitive?.content
            station ?: WeatherConstants.DEFAULT_STATION
        } catch (e: Exception) {
            Logger.e("Failed to parse nearest station JSON", e)
            WeatherConstants.DEFAULT_STATION
        }
    }

    suspend fun fetchKmaRealtimeWeather(lat: Double, lon: Double): RawWeatherData {
        return try {
            val grid = LatLonToGrid.convert(lat, lon)
            val (baseDate, baseTime) = getKmaRealtimeBaseTime()
            val url = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst?serviceKey=$kmaServiceKey&dataType=JSON&base_date=$baseDate&base_time=$baseTime&nx=${grid.nx}&ny=${grid.ny}&numOfRows=20"
            val responseText = safeGet(url) ?: return RawWeatherData()
            val decoded = json.decodeFromString<KmaWeatherResponse>(responseText)
            val items = decoded.response.body?.items?.item ?: emptyList()
            if (items.isEmpty()) {
                RawWeatherData()
            } else {
                val temp = items.find { it.category == "T1H" }?.obsrValue?.toDoubleOrNull() ?: 0.0
                val reh = items.find { it.category == "REH" }?.obsrValue?.toIntOrNull()
                    ?: WeatherConstants.DEFAULT_HUMIDITY
                val pty = items.find { it.category == "PTY" }?.obsrValue ?: "0"
                val vec = items.find { it.category == "VEC" }?.obsrValue?.toDoubleOrNull()
                val windDirection = vec?.let { WeatherConstants.degreeToDirection(it) }
                    ?: WeatherConstants.DEFAULT_WIND_DIRECTION
                val windSpeedRaw = items.find { it.category == "WSD" }?.obsrValue
                val windSpeed = windSpeedRaw?.toDoubleOrNull() ?: 0.0
                Logger.d("KMA Realtime windSpeed: raw=$windSpeedRaw, parsed=$windSpeed")
                val rainValStr = items.find { it.category == "RN1" }?.obsrValue ?: ""
                val rainAmount = if (rainValStr.contains("없음") || rainValStr.isEmpty()) {
                    0.0
                } else {
                    rainValStr.replace("mm", "").trim().toDoubleOrNull() ?: 0.0
                }
                val condition = WeatherConstants.ptyToCondition(pty)
                RawWeatherData(
                    temp = temp,
                    humidity = reh,
                    condition = condition,
                    windDirection = windDirection,
                    windSpeed = windSpeed,
                    rainAmount = rainAmount
                )
            }
        } catch (e: Exception) {
            Logger.e("Failed to fetch KMA realtime weather", e)
            RawWeatherData()
        }
    }

    suspend fun fetchKma24HourForecast(lat: Double, lon: Double, owmAirList: List<OwmAirPollutionItem>): RawWeatherData {
        return try {
            val grid = LatLonToGrid.convert(lat, lon)
            val (baseDate, baseTime) = getKmaForecastBaseTime()
            val url = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst?serviceKey=$kmaServiceKey&dataType=JSON&base_date=$baseDate&base_time=$baseTime&nx=${grid.nx}&ny=${grid.ny}&numOfRows=1000"
            val responseStr = safeGet(url) ?: return RawWeatherData()
            val decoded = json.decodeFromString<KmaWeatherResponse>(responseStr)
            val items = decoded.response.body?.items?.item ?: emptyList()
            if (items.isEmpty()) {
                RawWeatherData()
            } else {
                val owmAqiMap = owmAirList.associate { item ->
                    val instant = Instant.fromEpochSeconds(item.dt)
                    val localDateTime = instant.toLocalDateTime(kstZone)
                    val key = "${localDateTime.year}" +
                        "${localDateTime.monthNumber.toString().padStart(2, '0')}" +
                        "${localDateTime.dayOfMonth.toString().padStart(2, '0')}" +
                        "${localDateTime.hour.toString().padStart(2, '0')}00"
                    key to item.components.pm10.toInt()
                }

                val now = Clock.System.now().toLocalDateTime(kstZone)
                val hourGroups = items.groupBy { (it.fcstDate ?: "") + (it.fcstTime ?: "") }
                val nowKey = "${now.year}" +
                    "${now.monthNumber.toString().padStart(2, '0')}" +
                    "${now.dayOfMonth.toString().padStart(2, '0')}" +
                    "${now.hour.toString().padStart(2, '0')}00"
                val sortedKeys = hourGroups.keys
                    .filter { it.length >= 10 }
                    .sorted()
                    .filter { it >= nowKey }

                val hourly = sortedKeys.take(24).map { key ->
                    val group = hourGroups[key]!!
                    val hourInt = key.substring(8, 10).toInt()
                    val displayTime = if (hourInt < 12) {
                        "오전 ${if (hourInt == 0) 12 else hourInt}시"
                    } else {
                        "오후 ${if (hourInt == 12) 12 else hourInt - 12}시"
                    }
                    val sky = group.find { it.category == "SKY" }?.fcstValue ?: "1"
                    val pty = group.find { it.category == "PTY" }?.fcstValue ?: "0"
                    val pop = group.find { it.category == "POP" }?.fcstValue?.toIntOrNull() ?: 0
                    val pm10Val = owmAqiMap[key] ?: 0
                    val condition = if (pty != "0") {
                        WeatherConstants.ptyToCondition(pty)
                    } else {
                        WeatherConstants.skyToCondition(sky)
                    }
                    HourlyForecast(
                        time = displayTime,
                        temp = group.find { it.category == "TMP" }?.fcstValue?.toDoubleOrNull() ?: 0.0,
                        condition = condition,
                        precipitationProbability = pop,
                        aqi = pm10Val
                    )
                }

                val dayGroups = items.groupBy { it.fcstDate }
                val daily = dayGroups.keys.filterNotNull().sorted().map { dateStr ->
                    val group = dayGroups[dateStr]!!
                    val month = dateStr.substring(4, 6).toInt()
                    val day = dateStr.substring(6, 8).toInt()

                    fun getBestCond(start: Int, end: Int): String {
                        val period = group.filter {
                            (it.fcstTime?.substring(0, 2)?.toInt() ?: 0) in start until end
                        }
                        if (period.isEmpty()) return WeatherConstants.DEFAULT_CONDITION
                        val ptyAny = period.find { it.category == "PTY" && it.fcstValue != "0" }
                        if (ptyAny != null) {
                            return WeatherConstants.ptyToCondition(ptyAny.fcstValue ?: "0")
                        }
                        val skyMax = period.filter { it.category == "SKY" }
                            .maxByOrNull { it.fcstValue }?.fcstValue ?: "1"
                        return WeatherConstants.skyToCondition(skyMax)
                    }

                    val precipitationProbability = group
                        .filter { it.category == "POP" }
                        .map { it.fcstValue.toIntOrNull() ?: 0 }
                        .maxOrNull() ?: 0
                    val minTemp = group
                        .filter { it.category == "TMP" }
                        .map { it.fcstValue.toDoubleOrNull() ?: 0.0 }
                        .minOrNull() ?: 0.0
                    val maxTemp = group
                        .filter { it.category == "TMP" }
                        .map { it.fcstValue.toDoubleOrNull() ?: 0.0 }
                        .maxOrNull() ?: 0.0

                    DailyForecast(
                        date = "${month}/${day}",
                        dayOfWeek = "",
                        precipitationProbability = precipitationProbability,
                        morningCondition = getBestCond(0, 12),
                        afternoonCondition = getBestCond(12, 24),
                        minTemp = minTemp,
                        maxTemp = maxTemp
                    )
                }

                val currentVec = items.find { it.category == "VEC" }?.fcstValue?.toDoubleOrNull()
                val currentWindDir = currentVec?.let { WeatherConstants.degreeToDirection(it) }
                    ?: WeatherConstants.DEFAULT_WIND_DIRECTION

                RawWeatherData(
                    temp = hourly.firstOrNull()?.temp ?: 0.0,
                    humidity = WeatherConstants.DEFAULT_HUMIDITY,
                    condition = hourly.firstOrNull()?.condition ?: WeatherConstants.DEFAULT_CONDITION,
                    hourly = hourly,
                    daily = daily,
                    windDirection = currentWindDir
                )
            }
        } catch (e: Exception) {
            Logger.e("Failed to fetch KMA 24-hour forecast", e)
            RawWeatherData()
        }
    }

    private fun getKmaRealtimeBaseTime(): Pair<String, String> {
        val now = Clock.System.now().toLocalDateTime(kstZone)
        var date = now.date
        var hour = now.hour
        if (now.minute < 40) {
            if (hour == 0) {
                date = date.minus(1, DateTimeUnit.DAY)
                hour = 23
            } else hour -= 1
        }
        return Pair("${date.year}${date.monthNumber.toString().padStart(2, '0')}${date.dayOfMonth.toString().padStart(2, '0')}", "${hour.toString().padStart(2, '0')}00")
    }

    private fun getKmaForecastBaseTime(): Pair<String, String> {
        val now = Clock.System.now().toLocalDateTime(kstZone)
        var date = now.date
        var hour = now.hour
        val baseTime = when {
            hour < 2 -> {
                date = date.minus(1, DateTimeUnit.DAY)
                "2300"
            }
            hour < 5 -> "0200"
            hour < 8 -> "0500"
            hour < 11 -> "0800"
            hour < 14 -> "1100"
            hour < 17 -> "1400"
            hour < 20 -> "1700"
            else -> "2000"
        }
        return Pair("${date.year}${date.monthNumber.toString().padStart(2, '0')}${date.dayOfMonth.toString().padStart(2, '0')}", baseTime)
    }
}
