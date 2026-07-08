package com.fortuneweather.data.repository

import com.fortuneweather.data.cache.CacheManager
import com.fortuneweather.data.datasource.AirKoreaDataSource
import com.fortuneweather.data.datasource.KmaWeatherDataSource
import com.fortuneweather.data.datasource.OwmWeatherDataSource
import com.fortuneweather.data.datasource.GeminiSajuDataSource
import com.fortuneweather.data.datasource.SajuFortune
import com.fortuneweather.data.datasource.SajuHanja
import com.fortuneweather.utils.SajuCalculator
import com.fortuneweather.utils.LunarSolarConverter
import com.fortuneweather.utils.SolarDate
import com.fortuneweather.domain.model.RawWeatherData
import com.fortuneweather.domain.model.WeatherCache
import com.fortuneweather.domain.model.WeatherInfo
import com.fortuneweather.domain.model.HourlyForecast
import com.fortuneweather.domain.model.DailyForecast
import com.fortuneweather.utils.Logger
import com.fortuneweather.utils.SunTimes
import com.fortuneweather.utils.WeatherConstants
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.math.abs
import kotlin.math.pow

/**
 * 날씨 데이터를 통합하는 Repository.
 * DataSource는 외부(AppContainer)에서 주입받으며, 직접 생성하지 않는다.
 */
class WeatherRepository(
    private val kmaDataSource: KmaWeatherDataSource,
    private val airKoreaDataSource: AirKoreaDataSource,
    private val owmDataSource: OwmWeatherDataSource,
    private val geminiSajuDataSource: GeminiSajuDataSource
) {

    private val systemZone = TimeZone.currentSystemDefault()
    private val cacheManager = CacheManager
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val MOON_CYCLE_SECONDS = 29.530588853 * 24 * 60 * 60
        const val BASE_NEW_MOON_EPOCH = 592800L
    }

    suspend fun getIntegratedWeather(
        lat: Double,
        lon: Double,
        addressName: String? = null,
        forceRefresh: Boolean = false
    ): WeatherInfo = coroutineScope {
        Logger.i("--- Weather Pipeline Triggered (forceRefresh=$forceRefresh) ---")
        
        if (!forceRefresh) {
            try {
                val cachedJson = cacheManager.getCache("weather_info_cache")
                if (cachedJson != null) {
                    val cache = json.decodeFromString<WeatherCache>(cachedJson)
                    val nowMillis = Clock.System.now().toEpochMilliseconds()
                    val isExpired = nowMillis - cache.cachedTimeMillis > WeatherConstants.CACHE_TTL_MILLIS
                    val isSameLocation = abs(cache.lat - lat) < WeatherConstants.LOCATION_DELTA_THRESHOLD && abs(cache.lon - lon) < WeatherConstants.LOCATION_DELTA_THRESHOLD
                    
                    if (!isExpired && isSameLocation) {
                        Logger.i("--- Valid Weather Cache Returned (lat=$lat, lon=$lon) ---")
                        return@coroutineScope cache.weatherInfo
                    } else {
                        Logger.i("--- Weather Cache Expired or Location Changed (isExpired=$isExpired, isSameLoc=$isSameLocation) ---")
                    }
                }
            } catch (e: Exception) {
                Logger.e("Failed to read weather cache", e)
            }
        } else {
            Logger.i("--- Force Refresh Requested. Bypassing Cache. ---")
        }
        
        val isSouthKorea = lat in WeatherConstants.KOREA_LAT_RANGE && lon in WeatherConstants.KOREA_LON_RANGE
        val finalWeatherInfo = if (isSouthKorea) {
            getKoreaWeather(lat, lon, addressName)
        } else {
            getGlobalWeather(lat, lon)
        }
        
        try {
            val cacheObj = WeatherCache(
                weatherInfo = finalWeatherInfo,
                cachedTimeMillis = Clock.System.now().toEpochMilliseconds(),
                lat = lat,
                lon = lon
            )
            val jsonStr = json.encodeToString(cacheObj)
            cacheManager.saveCache("weather_info_cache", jsonStr)
            Logger.i("--- Weather Cache Successfully Saved ---")
        } catch (e: Exception) {
            Logger.e("Failed to save weather cache", e)
        }
        
        finalWeatherInfo
    }

    private suspend fun getKoreaWeather(
        lat: Double,
        lon: Double,
        addressName: String?
    ): WeatherInfo = coroutineScope {
        // 1. 모든 독립적인 API를 동시에 병렬로 조회 시작!
        val owmAirDef = async { owmDataSource.fetchOwmAirPollutionForecast(lat, lon) }
        val realtimeDef = async { kmaDataSource.fetchKmaRealtimeWeather(lat, lon) }
        val stationDef = async { kmaDataSource.fetchNearestStation(lat, lon) }
        val uvDef = async { kmaDataSource.fetchKmaUvIndex(addressName) }
        val kma24HourForecastDef = async { kmaDataSource.fetchKma24HourForecast(lat, lon) }
        val owmResultDef = async { owmDataSource.fetchOwmForecast(lat, lon) }

        // 2. 관측소 정보가 오면 바로 이어서 에어코리아 미세먼지 조회를 비동기로 실행
        val stationName = stationDef.await()
        val aqiDef = async { airKoreaDataSource.fetchAirKoreaDetails(stationName) }
        val locationName = stationName

        // 3. 결과 수집
        val owmAirList = owmAirDef.await()
        val realtime = realtimeDef.await()
        val rawKma24HourForecast = kma24HourForecastDef.await()
        val rawOwmResult = owmResultDef.await()
        val aqiData = aqiDef.await()
        val kmaUv = uvDef.await()

        val now = Clock.System.now().toLocalDateTime(systemZone)

        // 4. 수집된 데이터를 바탕으로 병합 및 조인(Join) 처리
        // 4-1. AQI 맵 생성
        val owmAqiMap = owmAirList.associate { item ->
            val instant = Instant.fromEpochSeconds(item.dt)
            val localDateTime = instant.toLocalDateTime(systemZone)
            val key = "${localDateTime.year}" +
                "${localDateTime.monthNumber.toString().padStart(2, '0')}" +
                "${localDateTime.dayOfMonth.toString().padStart(2, '0')}" +
                "${localDateTime.hour.toString().padStart(2, '0')}00"
            key to item.components.pm10.toInt()
        }
        val simulatedHourlyAqiMap = owmAirList.associate { item ->
            val instant = Instant.fromEpochSeconds(item.dt)
            val localDateTime = instant.toLocalDateTime(systemZone)
            "${getDateStr(localDateTime.date)} ${localDateTime.hour.toString().padStart(2, '0')}" to item.components.pm10.toInt()
        }

        // 4-2. 기상청 단기예보의 Hourly 및 Daily에 AQI 데이터를 결합
        val mappedKmaHourly = rawKma24HourForecast.hourly.map { hourly ->
            val aqiVal = hourly.rawTime?.let { owmAqiMap[it] } ?: 0
            hourly.copy(aqi = aqiVal)
        }
        val mappedKmaDaily = rawKma24HourForecast.daily.map { daily ->
            val dateStr = daily.rawDate?.replace("-", "") ?: ""
            val aqiVal = owmAqiMap[dateStr] ?: 0
            daily.copy(aqi = aqiVal)
        }
        val kma24HourForecast = rawKma24HourForecast.copy(
            hourly = mappedKmaHourly,
            daily = mappedKmaDaily
        )

        // 4-3. 국내용 최종 dailyList 조립 (첫날은 기상청, 2~5일은 OWM에 AQI 융합)
        val today = now.date
        val todayAqi = aqiData.pm10 // 에어코리아 실시간 미세먼지 수치를 오늘 AQI 지수로 사용
        
        val dailyList = mutableListOf<DailyForecast>()
        if (kma24HourForecast.daily.isNotEmpty()) {
            dailyList.add(createFirstDayKmaForecast(today, kma24HourForecast, todayAqi))
            for (i in 1 until rawOwmResult.dailyList.size) {
                val owmDaily = rawOwmResult.dailyList[i]
                val dateStr = owmDaily.rawDate ?: ""
                val dayAqi = owmAqiMap[dateStr] ?: 0
                val mappedHourly = owmDaily.hourlyDetails.map { hourly ->
                    val hourInt = hourly.time.replace("시", "").trim().toIntOrNull() ?: 12
                    val hourStr = hourInt.toString().padStart(2, '0')
                    val hourAqiKey = "$dateStr $hourStr"
                    val hourAqi = simulatedHourlyAqiMap[hourAqiKey] ?: dayAqi
                    hourly.copy(aqi = hourAqi)
                }
                dailyList.add(owmDaily.copy(aqi = dayAqi, hourlyDetails = mappedHourly))
            }
        } else {
            // 기상청 예보가 실패한 경우 OWM 데이터로 5일치를 전부 융합하여 채움
            for (i in 0 until rawOwmResult.dailyList.size) {
                val owmDaily = rawOwmResult.dailyList[i]
                val dateStr = owmDaily.rawDate ?: ""
                val dayAqi = owmAqiMap[dateStr] ?: 0
                val mappedHourly = owmDaily.hourlyDetails.map { hourly ->
                    val hourInt = hourly.time.replace("시", "").trim().toIntOrNull() ?: 12
                    val hourStr = hourInt.toString().padStart(2, '0')
                    val hourAqiKey = "$dateStr $hourStr"
                    val hourAqi = simulatedHourlyAqiMap[hourAqiKey] ?: dayAqi
                    hourly.copy(aqi = hourAqi)
                }
                dailyList.add(owmDaily.copy(aqi = dayAqi, hourlyDetails = mappedHourly))
            }
        }

        val finalDailyList = dailyList
        val finalHourlyList = kma24HourForecast.hourly

        // 센티넬 값 및 NO_DATA 값을 기준으로 국내 기상 데이터 통합
        val finalTemp = if (realtime.temp != WeatherConstants.TEMP_UNKNOWN) {
            realtime.temp
        } else {
            if (kma24HourForecast.temp != WeatherConstants.TEMP_UNKNOWN) kma24HourForecast.temp else 0.0
        }
        
        val finalCondition = if (realtime.condition != WeatherConstants.NO_DATA && (realtime.condition == "비" || realtime.condition == "눈")) {
            realtime.condition
        } else {
            if (kma24HourForecast.condition != WeatherConstants.NO_DATA) kma24HourForecast.condition else WeatherConstants.DEFAULT_CONDITION
        }
        val sunTimes = SunTimes.calculate(lat, lon, now.date)

        val pm10Val = aqiData.pm10
        val pm25Val = aqiData.pm25
        val aqiVal = aqiData.khaiValue

        val uvVal = kmaUv ?: calculateUvIndex(now, finalCondition)
        val phase = calculateMoonPhase()

        val windSpeedVal = if (realtime.windSpeed != WeatherConstants.DOUBLE_UNKNOWN) {
            realtime.windSpeed
        } else {
            if (rawOwmResult.currentWindSpeed != WeatherConstants.DOUBLE_UNKNOWN) rawOwmResult.currentWindSpeed else 0.0
        }
        val rainAmountVal = if (realtime.rainAmount != WeatherConstants.DOUBLE_UNKNOWN) {
            realtime.rainAmount
        } else {
            if (rawOwmResult.currentRain != WeatherConstants.DOUBLE_UNKNOWN) rawOwmResult.currentRain else 0.0
        }
        
        val finalHumidity = if (realtime.humidity != WeatherConstants.INT_UNKNOWN) {
            realtime.humidity
        } else {
            if (kma24HourForecast.humidity != WeatherConstants.INT_UNKNOWN) kma24HourForecast.humidity else WeatherConstants.DEFAULT_HUMIDITY
        }
        
        val windDirectionVal = if (realtime.windDirection != WeatherConstants.NO_DATA) {
            realtime.windDirection
        } else {
            if (kma24HourForecast.windDirection != WeatherConstants.NO_DATA) kma24HourForecast.windDirection else WeatherConstants.DEFAULT_WIND_DIRECTION
        }

        val feelsLikeVal = calculateFeelsLike(finalTemp, windSpeedVal, finalHumidity)

        val finalVisibility = if (rawOwmResult.currentVisibility != WeatherConstants.DOUBLE_UNKNOWN) {
            rawOwmResult.currentVisibility
        } else {
            WeatherConstants.PLACEHOLDER_VISIBILITY
        }

        val finalPressure = if (rawOwmResult.currentPressure != WeatherConstants.DOUBLE_UNKNOWN) {
            rawOwmResult.currentPressure
        } else {
            WeatherConstants.PLACEHOLDER_PRESSURE
        }

        WeatherInfo(
            locationName = locationName, temp = finalTemp, feelsLike = feelsLikeVal, humidity = finalHumidity,
            condition = finalCondition, aqi = aqiVal, pm10 = pm10Val, pm25 = pm25Val, uvIndex = uvVal, 
            windDirection = windDirectionVal, 
            windSpeed = windSpeedVal, rainAmount = rainAmountVal,
            pressure = finalPressure, visibility = finalVisibility, sunrise = sunTimes.sunrise, sunset = sunTimes.sunset,
            moonPhase = phase,
            hourly = finalHourlyList, daily = finalDailyList,
            luckyColor = calculateLuckyColor(finalTemp, finalCondition), fashionTip = getFashionRecommendation(finalTemp),
            fortuneMsg = "기상청 통합 예보가 업데이트되었습니다.",
            sourceCount = 3
        )
    }

    private suspend fun getGlobalWeather(
        lat: Double,
        lon: Double
    ): WeatherInfo = coroutineScope {
        val owmAirDef = async { owmDataSource.fetchOwmAirPollutionForecast(lat, lon) }
        val owmResultDef = async { owmDataSource.fetchOwmForecast(lat, lon) }
        
        val owmAirList = owmAirDef.await()
        val rawOwmResult = owmResultDef.await()

        val locationName = rawOwmResult.cityName
        val now = Clock.System.now().toLocalDateTime(systemZone)

        val owmAqiMap = owmAirList.associate { item ->
            val instant = Instant.fromEpochSeconds(item.dt)
            val localDateTime = instant.toLocalDateTime(systemZone)
            getDateStr(localDateTime.date) to item.components.pm10.toInt()
        }
        val simulatedHourlyAqiMap = owmAirList.associate { item ->
            val instant = Instant.fromEpochSeconds(item.dt)
            val localDateTime = instant.toLocalDateTime(systemZone)
            "${getDateStr(localDateTime.date)} ${localDateTime.hour.toString().padStart(2, '0')}" to item.components.pm10.toInt()
        }

        // daily와 hourly에 AQI를 채워넣기
        val finalDailyList = rawOwmResult.dailyList.map { daily ->
            val dateStr = daily.rawDate ?: ""
            val dayAqi = owmAqiMap[dateStr] ?: 0
            val mappedHourly = daily.hourlyDetails.map { hourly ->
                val hourInt = hourly.time.replace("시", "").trim().toIntOrNull() ?: 12
                val hourStr = hourInt.toString().padStart(2, '0')
                val hourAqiKey = "$dateStr $hourStr"
                val hourAqi = simulatedHourlyAqiMap[hourAqiKey] ?: dayAqi
                hourly.copy(aqi = hourAqi)
            }
            daily.copy(aqi = dayAqi, hourlyDetails = mappedHourly)
        }

        val finalHourlyList = rawOwmResult.hourlyList.map { hourly ->
            val hourKey = hourly.rawTime?.let { 
                val yr = it.substring(0, 4)
                val mn = it.substring(4, 6)
                val dy = it.substring(6, 8)
                val hr = it.substring(8, 10)
                "$yr-$mn-$dy $hr"
            }
            val aqiVal = hourKey?.let { simulatedHourlyAqiMap[it] } ?: 0
            hourly.copy(aqi = aqiVal)
        }

        val finalTemp = if (rawOwmResult.currentTemp != WeatherConstants.TEMP_UNKNOWN) rawOwmResult.currentTemp else 0.0
        val finalCondition = if (rawOwmResult.currentCondition != WeatherConstants.NO_DATA) rawOwmResult.currentCondition else WeatherConstants.DEFAULT_CONDITION
        val sunTimes = SunTimes.calculate(lat, lon, now.date)

        val pm10Val = owmAirList.firstOrNull()?.components?.pm10?.toInt() ?: 0
        val pm25Val = owmAirList.firstOrNull()?.components?.pm25?.toInt() ?: 0
        val owmAqi = owmAirList.firstOrNull()?.main?.aqi ?: 1
        val aqiVal = when (owmAqi) {
            1 -> 25  // 좋음 (0..30)
            2 -> 65  // 보통 (31..80)
            3 -> 120 // 나쁨 (81..150)
            4 -> 180 // 나쁨
            else -> 280 // 매우나쁨 (151..)
        }

        val uvVal = calculateUvIndex(now, finalCondition)
        val phase = calculateMoonPhase()

        val windSpeedVal = if (rawOwmResult.currentWindSpeed != WeatherConstants.DOUBLE_UNKNOWN) rawOwmResult.currentWindSpeed else 0.0
        val rainAmountVal = if (rawOwmResult.currentRain != WeatherConstants.DOUBLE_UNKNOWN) rawOwmResult.currentRain else 0.0
        val finalHumidity = if (rawOwmResult.currentHumidity != WeatherConstants.INT_UNKNOWN) rawOwmResult.currentHumidity else WeatherConstants.DEFAULT_HUMIDITY
        val windDirectionVal = WeatherConstants.degreeToDirection(rawOwmResult.currentWindDeg)

        val feelsLikeVal = calculateFeelsLike(finalTemp, windSpeedVal, finalHumidity)

        val finalVisibility = if (rawOwmResult.currentVisibility != WeatherConstants.DOUBLE_UNKNOWN) {
            rawOwmResult.currentVisibility
        } else {
            WeatherConstants.PLACEHOLDER_VISIBILITY
        }

        val finalPressure = if (rawOwmResult.currentPressure != WeatherConstants.DOUBLE_UNKNOWN) {
            rawOwmResult.currentPressure
        } else {
            WeatherConstants.PLACEHOLDER_PRESSURE
        }

        WeatherInfo(
            locationName = locationName, temp = finalTemp, feelsLike = feelsLikeVal, humidity = finalHumidity,
            condition = finalCondition, aqi = aqiVal, pm10 = pm10Val, pm25 = pm25Val, uvIndex = uvVal, 
            windDirection = windDirectionVal, 
            windSpeed = windSpeedVal, rainAmount = rainAmountVal,
            pressure = finalPressure, visibility = finalVisibility, sunrise = sunTimes.sunrise, sunset = sunTimes.sunset,
            moonPhase = phase,
            hourly = finalHourlyList, daily = finalDailyList,
            luckyColor = calculateLuckyColor(finalTemp, finalCondition), fashionTip = getFashionRecommendation(finalTemp),
            fortuneMsg = "글로벌 기상 예보가 업데이트되었습니다.",
            sourceCount = 1
        )
    }

    private fun getDateStr(localDate: LocalDate): String {
        return "${localDate.year}-${localDate.monthNumber.toString().padStart(2, '0')}-${localDate.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun parseKoreanHour(timeStr: String): Int {
        val isPm = timeStr.contains("오후")
        val rawHour = timeStr
            .substringAfter(if (isPm) "오후 " else "오전 ")
            .substringBefore("시")
            .trim()
            .toIntOrNull() ?: 12
        return if (isPm) {
            if (rawHour == 12) 12 else rawHour + 12
        } else {
            if (rawHour == 12) 0 else rawHour
        }
    }

    private fun createFirstDayKmaForecast(
        today: LocalDate,
        kma24HourForecast: RawWeatherData,
        dayAqi: Int
    ): DailyForecast {
        val dateKey = "${today.monthNumber}/${today.dayOfMonth}"
        val kmaToday = kma24HourForecast.daily.firstOrNull { it.date == dateKey }
            ?: kma24HourForecast.daily.firstOrNull()

        val minTemp = kmaToday?.minTemp ?: 0.0
        val maxTemp = kmaToday?.maxTemp ?: 0.0
        val pop = kmaToday?.precipitationProbability ?: 0
        val morningCond = kmaToday?.morningCondition ?: WeatherConstants.DEFAULT_CONDITION
        val afternoonCond = kmaToday?.afternoonCondition ?: WeatherConstants.DEFAULT_CONDITION

        val targetHours = listOf(6, 9, 12, 15, 18, 21, 24)
        val hourlyDetails = targetHours.mapNotNull { targetHour ->
            val itemHour = if (targetHour == 24) 0 else targetHour

            val matchKma = kma24HourForecast.hourly.firstOrNull { item ->
                parseKoreanHour(item.time) == itemHour
            } ?: kma24HourForecast.hourly.minByOrNull { item ->
                abs(parseKoreanHour(item.time) - itemHour)
            } ?: return@mapNotNull null

            val displayTime = "${targetHour.toString().padStart(2, '0')}시"
            HourlyForecast(
                time = displayTime,
                temp = matchKma.temp,
                condition = matchKma.condition,
                precipitationProbability = matchKma.precipitationProbability,
                aqi = matchKma.aqi
            )
        }

        return DailyForecast(
            date = dateKey,
            dayOfWeek = "오늘",
            precipitationProbability = pop,
            morningCondition = morningCond,
            afternoonCondition = afternoonCond,
            minTemp = minTemp,
            maxTemp = maxTemp,
            aqi = dayAqi,
            hourlyDetails = hourlyDetails,
            rawDate = getDateStr(today)
        )
    }

    private fun calculateUvIndex(now: LocalDateTime, condition: String): Double {
        return if (now.hour in 7..18) {
            val peak = if (condition == "맑음") 7.5 else if (condition == "구름많음") 4.0 else 1.5
            val factor = kotlin.math.sin((now.hour - 6) / 12.0 * kotlin.math.PI)
            (peak * factor).coerceIn(0.0, 10.0)
        } else {
            0.0
        }
    }

    private fun calculateMoonPhase(): Double {
        val diff = Clock.System.now().epochSeconds - BASE_NEW_MOON_EPOCH
        var phase = (diff % MOON_CYCLE_SECONDS) / MOON_CYCLE_SECONDS
        if (phase < 0) phase += 1.0
        return phase
    }

    private fun calculateFeelsLike(temp: Double, windSpeed: Double, humidity: Int): Double {
        return if (temp <= 10.0 && windSpeed > 1.3) {
            val vKmh = windSpeed * 3.6
            13.12 + 0.6215 * temp - 11.37 * vKmh.pow(0.16) + 0.3965 * temp * vKmh.pow(0.16)
        } else if (temp >= 20.0) {
            temp + 0.33 * (humidity / 100.0 * 6.105 * kotlin.math.exp(17.27 * temp / (237.7 + temp))) - 4.0
        } else {
            temp
        }
    }

    private fun getFashionRecommendation(temp: Double): String = when {
        temp < 4 -> "패딩 필수!"
        temp < 15 -> "자켓이나 코트"
        temp < 23 -> "가벼운 셔츠"
        else -> "시원한 반팔"
    }

    private fun calculateLuckyColor(temp: Double, condition: String): String = when {
        condition == "비" || condition == "눈" -> "Silver"
        temp > 28 -> "Coral"
        temp > 20 -> "Yellow"
        temp > 10 -> "Sky Blue"
        else -> "Lavender"
    }

    suspend fun getSajuFortune(
        birthDate: String,
        birthTime: String,
        isLunar: Boolean,
        gender: String,
        forceRefresh: Boolean = false
    ): SajuFortune {
        val today = Clock.System.now().toLocalDateTime(systemZone)
        val todayDateStr = "${today.year}-${today.monthNumber}-${today.dayOfMonth}"
        val inputCombo = "${birthDate}_${birthTime}_${isLunar}_${gender}"
        
        if (!forceRefresh) {
            try {
                val cachedDate = cacheManager.getCache("saju_result_date")
                val cachedCombo = cacheManager.getCache("saju_result_input")
                val cachedJson = cacheManager.getCache("saju_result_cache")
                
                if (cachedDate == todayDateStr && cachedCombo == inputCombo && !cachedJson.isNullOrBlank()) {
                    Logger.i("--- Valid Saju Cache Found ---")
                    return json.decodeFromString<SajuFortune>(cachedJson)
                }
            } catch (e: Exception) {
                Logger.e("Failed to read saju cache", e)
            }
        }
        
        // 1. Get correct local Saju pillars first
        val localHanja = getSajuHanjaOnly(birthDate, birthTime, isLunar, gender)
        
        // 2. Fetch fortune using local Hanja as a fixed anchor
        val newSaju = geminiSajuDataSource.fetchSajuFortune(
            birthDate = birthDate,
            birthTime = birthTime,
            isLunar = isLunar,
            gender = gender,
            todayDate = todayDateStr,
            localHanja = localHanja
        )
        
        try {
            cacheManager.saveCache("saju_result_date", todayDateStr)
            cacheManager.saveCache("saju_result_input", inputCombo)
            cacheManager.saveCache("saju_result_cache", json.encodeToString(newSaju))
            Logger.i("--- Saju Cache Successfully Saved ---")
        } catch (e: Exception) {
            Logger.e("Failed to save saju cache", e)
        }
        
        return newSaju
    }

    fun isSajuFortuneCached(
        birthDate: String,
        birthTime: String,
        isLunar: Boolean,
        gender: String
    ): Boolean {
        val today = Clock.System.now().toLocalDateTime(systemZone)
        val todayDateStr = "${today.year}-${today.monthNumber}-${today.dayOfMonth}"
        val inputCombo = "${birthDate}_${birthTime}_${isLunar}_${gender}"
        return try {
            val cachedDate = cacheManager.getCache("saju_result_date")
            val cachedCombo = cacheManager.getCache("saju_result_input")
            val cachedJson = cacheManager.getCache("saju_result_cache")
            cachedDate == todayDateStr && cachedCombo == inputCombo && !cachedJson.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getSajuHanjaOnly(
        birthDate: String,
        birthTime: String,
        isLunar: Boolean,
        gender: String
    ): SajuHanja {
        val inputCombo = "${birthDate}_${birthTime}_${isLunar}_${gender}"
        try {
            val cachedHanjaJson = cacheManager.getCache("saju_hanja_preview_$inputCombo")
            if (!cachedHanjaJson.isNullOrBlank()) {
                Logger.i("--- Valid Saju Hanja Preview Cache Found ---")
                return json.decodeFromString<SajuHanja>(cachedHanjaJson)
            }
        } catch (e: Exception) {
            Logger.e("Failed to read saju hanja cache", e)
        }

        // Calculate Hanja 100% locally
        val dateParts = birthDate.split("-")
        val year = dateParts[0].toIntOrNull() ?: 1990
        val month = dateParts[1].toIntOrNull() ?: 1
        val day = dateParts[2].toIntOrNull() ?: 1
        
        val solarDate = if (isLunar) {
            try {
                LunarSolarConverter.convertLunarToSolar(year, month, day)
            } catch (e: Exception) {
                Logger.e("Lunar to Solar conversion failed, falling back to solar", e)
                SolarDate(year, month, day)
            }
        } else {
            SolarDate(year, month, day)
        }

        val pillars = SajuCalculator.calculate(
            solarYear = solarDate.year,
            solarMonth = solarDate.month,
            solarDay = solarDate.day,
            birthTime = birthTime
        )

        val newHanja = SajuHanja(
            yearHanja = pillars.yearHanja,
            monthHanja = pillars.monthHanja,
            dayHanja = pillars.dayHanja,
            hourHanja = pillars.hourHanja
        )

        try {
            cacheManager.saveCache("saju_hanja_preview_$inputCombo", json.encodeToString(newHanja))
            Logger.i("--- Saju Hanja Preview Cache Successfully Saved ---")
        } catch (e: Exception) {
            Logger.e("Failed to save saju hanja cache", e)
        }

        return newHanja
    }
}
