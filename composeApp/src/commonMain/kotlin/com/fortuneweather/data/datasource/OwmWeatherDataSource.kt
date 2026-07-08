package com.fortuneweather.data.datasource

import com.fortuneweather.BuildKonfig
import com.fortuneweather.data.dto.*
import com.fortuneweather.domain.model.*
import com.fortuneweather.utils.Logger
import com.fortuneweather.utils.WeatherConstants
import io.ktor.client.*
import kotlinx.datetime.*
import kotlin.math.abs

class OwmWeatherDataSource(client: HttpClient) : BaseRemoteDataSource(client) {

    private val apiKey = BuildKonfig.OPENWEATHER_KEY
    private val systemZone = TimeZone.currentSystemDefault()

    suspend fun fetchOwmAirPollutionForecast(lat: Double, lon: Double): List<OwmAirPollutionItem> {
        return try {
            val url = "http://api.openweathermap.org/data/2.5/air_pollution/forecast?lat=$lat&lon=$lon&appid=$apiKey"
            val responseText = safeGet(url) ?: return emptyList()
            val decoded = json.decodeFromString<OwmAirPollutionResponse>(responseText)
            decoded.list
        } catch (e: Exception) {
            Logger.e("Failed to fetch OWM Air Pollution Forecast", e)
            emptyList()
        }
    }

    suspend fun fetchOwmForecast(
        lat: Double,
        lon: Double
    ): OwmForecastResult {
        return try {
            val url = "http://api.openweathermap.org/data/2.5/forecast?lat=$lat&lon=$lon&appid=$apiKey&units=metric&lang=kr"
            val responseText = safeGet(url) ?: return OwmForecastResult()
            val decoded = json.decodeFromString<OwmForecastResponse>(responseText)

            val owmItems = decoded.list
            val firstItem = owmItems.firstOrNull()

            val groups = owmItems.groupBy { item ->
                val instant = Instant.fromEpochSeconds(item.dt)
                val localDateTime = instant.toLocalDateTime(systemZone)
                getDateStr(localDateTime.date)
            }

            val today = Clock.System.now().toLocalDateTime(systemZone).date
            val targetDates = (0..4).map { today.plus(it, DateTimeUnit.DAY) }

            val dailyList = mutableListOf<DailyForecast>()

            // 해외 기준으로 0..4일 전체를 OWM 데이터로만 매핑 (결합은 레포지토리에서 처리)
            targetDates.forEachIndexed { index, localDate ->
                val dayOfWeek = if (index == 0) "오늘" else getDayOfWeekStr(localDate)
                val dayItems = getDayItemsForDate(localDate, groups, owmItems)
                dailyList.add(getOwmDailyForecast(localDate, dayOfWeek, dayItems, 0, emptyMap(), groups))
            }

            val hourlyList = owmItems.take(8).map { item ->
                val localTime = Instant.fromEpochSeconds(item.dt).toLocalDateTime(systemZone)
                val displayTime = "${localTime.hour.toString().padStart(2, '0')}시"
                val cond = WeatherConstants.normalizeCondition(
                    item.weather.firstOrNull()?.description ?: WeatherConstants.DEFAULT_CONDITION
                )
                val hourPop = ((item.pop ?: 0.0) * 100).toInt()

                HourlyForecast(
                    time = displayTime,
                    temp = item.main.temp,
                    condition = cond,
                    precipitationProbability = hourPop,
                    aqi = 0,
                    rawTime = "${localTime.year}${localTime.monthNumber.toString().padStart(2, '0')}${localTime.dayOfMonth.toString().padStart(2, '0')}${localTime.hour.toString().padStart(2, '0')}00"
                )
            }

            val tempVal = firstItem?.main?.temp ?: 0.0
            val condVal = firstItem?.weather?.firstOrNull()?.description
                ?.let { WeatherConstants.normalizeCondition(it) }
                ?: WeatherConstants.DEFAULT_CONDITION
            val humidityVal = firstItem?.main?.humidity ?: WeatherConstants.DEFAULT_HUMIDITY
            val windSpeedVal = firstItem?.wind?.speed ?: 0.0
            val windDegVal = firstItem?.wind?.deg ?: 0.0
            val rainVal = firstItem?.rain?.threeHour ?: 0.0
            val visibilityVal = firstItem?.visibility?.let { it / 1000.0 } ?: WeatherConstants.PLACEHOLDER_VISIBILITY
            val pressureVal = firstItem?.main?.pressure ?: WeatherConstants.PLACEHOLDER_PRESSURE

            OwmForecastResult(
                dailyList = dailyList,
                hourlyList = hourlyList,
                currentTemp = tempVal,
                currentCondition = condVal,
                currentHumidity = humidityVal,
                currentWindSpeed = windSpeedVal,
                currentWindDeg = windDegVal,
                currentRain = rainVal,
                currentVisibility = visibilityVal,
                currentPressure = pressureVal,
                cityName = decoded.city?.name ?: "해외 지역"
            )
        } catch (e: Exception) {
            Logger.e("Failed to fetch OWM Forecast", e)
            OwmForecastResult()
        }
    }

    private fun getDateStr(localDate: LocalDate): String {
        return "${localDate.year}-${localDate.monthNumber.toString().padStart(2, '0')}-${localDate.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun getDayOfWeekStr(localDate: LocalDate): String {
        return when (localDate.dayOfWeek) {
            DayOfWeek.MONDAY -> "월"
            DayOfWeek.TUESDAY -> "화"
            DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"
            DayOfWeek.FRIDAY -> "금"
            DayOfWeek.SATURDAY -> "토"
            DayOfWeek.SUNDAY -> "일"
            else -> "일"
        }
    }

    private fun getDayItemsForDate(
        localDate: LocalDate,
        groups: Map<String, List<OwmForecastItem>>,
        owmItems: List<OwmForecastItem>
    ): List<OwmForecastItem> {
        val dateStr = getDateStr(localDate)
        return groups[dateStr] ?: listOfNotNull(owmItems.firstOrNull())
    }

    private fun getOwmDailyForecast(
        localDate: LocalDate,
        dayOfWeek: String,
        dayItems: List<OwmForecastItem>,
        dayAqi: Int,
        simulatedHourlyAqiMap: Map<String, Int>,
        groups: Map<String, List<OwmForecastItem>>
    ): DailyForecast {
        val dateKey = "${localDate.monthNumber}/${localDate.dayOfMonth}"

        val minTemp = dayItems.minOfOrNull { it.main.tempMin } ?: 0.0
        val maxTemp = dayItems.maxOfOrNull { it.main.tempMax } ?: 0.0
        val pop = (dayItems.maxOfOrNull { it.pop ?: 0.0 } ?: 0.0) * 100

        val morningItem = dayItems.minByOrNull { item ->
            val localTime = Instant.fromEpochSeconds(item.dt).toLocalDateTime(systemZone)
            abs(localTime.hour - 9)
        }
        val afternoonItem = dayItems.minByOrNull { item ->
            val localTime = Instant.fromEpochSeconds(item.dt).toLocalDateTime(systemZone)
            abs(localTime.hour - 15)
        }

        val morningCond = morningItem?.weather?.firstOrNull()?.description
            ?.let { WeatherConstants.normalizeCondition(it) }
            ?: WeatherConstants.DEFAULT_CONDITION
        val afternoonCond = afternoonItem?.weather?.firstOrNull()?.description
            ?.let { WeatherConstants.normalizeCondition(it) }
            ?: WeatherConstants.DEFAULT_CONDITION

        val targetHours = listOf(6, 9, 12, 15, 18, 21, 24)
        val hourlyDetails = targetHours.mapNotNull { targetHour ->
            val itemHour = if (targetHour == 24) 0 else targetHour

            val targetDayItems = if (targetHour == 24) {
                val nextDate = localDate.plus(1, DateTimeUnit.DAY)
                val nextDateStr = getDateStr(nextDate)
                groups[nextDateStr] ?: dayItems
            } else {
                dayItems
            }

            val matchItem = targetDayItems.firstOrNull { item ->
                val localTime = Instant.fromEpochSeconds(item.dt).toLocalDateTime(systemZone)
                localTime.hour == itemHour
            } ?: targetDayItems.minByOrNull { item ->
                val localTime = Instant.fromEpochSeconds(item.dt).toLocalDateTime(systemZone)
                abs(localTime.hour - itemHour)
            } ?: return@mapNotNull null

            val localTime = Instant.fromEpochSeconds(matchItem.dt).toLocalDateTime(systemZone)
            val displayTime = "${targetHour.toString().padStart(2, '0')}시"
            val cond = WeatherConstants.normalizeCondition(
                matchItem.weather.firstOrNull()?.description ?: WeatherConstants.DEFAULT_CONDITION
            )

            val hourAqiKey = "${getDateStr(localTime.date)} ${localTime.hour.toString().padStart(2, '0')}"
            val hourAqi = simulatedHourlyAqiMap[hourAqiKey] ?: dayAqi
            val hourPop = ((matchItem.pop ?: 0.0) * 100).toInt()

            HourlyForecast(
                time = displayTime,
                temp = matchItem.main.temp,
                condition = cond,
                precipitationProbability = hourPop,
                aqi = hourAqi,
                rawTime = "${localTime.year}${localTime.monthNumber.toString().padStart(2, '0')}${localTime.dayOfMonth.toString().padStart(2, '0')}${localTime.hour.toString().padStart(2, '0')}00"
            )
        }

        return DailyForecast(
            date = dateKey,
            dayOfWeek = dayOfWeek,
            precipitationProbability = pop.toInt(),
            morningCondition = morningCond,
            afternoonCondition = afternoonCond,
            minTemp = minTemp,
            maxTemp = maxTemp,
            aqi = dayAqi,
            hourlyDetails = hourlyDetails,
            rawDate = getDateStr(localDate)
        )
    }
}
