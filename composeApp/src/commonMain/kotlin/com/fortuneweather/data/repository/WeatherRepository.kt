package com.fortuneweather.data.repository

import com.fortuneweather.data.cache.CacheManager
import com.fortuneweather.data.datasource.AirKoreaDataSource
import com.fortuneweather.data.datasource.KmaWeatherDataSource
import com.fortuneweather.data.datasource.OwmWeatherDataSource
import com.fortuneweather.data.datasource.GeminiSajuDataSource
import com.fortuneweather.data.datasource.SajuFortune
import com.fortuneweather.domain.model.RawWeatherData
import com.fortuneweather.domain.model.WeatherCache
import com.fortuneweather.domain.model.WeatherInfo
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
        // 1. 완전히 독립적인 API 4개를 병렬로 조회 개시
        val owmAirDef = async { owmDataSource.fetchOwmAirPollutionForecast(lat, lon) }
        val realtimeDef = async { kmaDataSource.fetchKmaRealtimeWeather(lat, lon) }
        val stationDef = async { kmaDataSource.fetchNearestStation(lat, lon) }
        val uvDef = async { kmaDataSource.fetchKmaUvIndex(addressName) }

        // 2. 관측소 정보가 오면 바로 이어서 에어코리아 미세먼지 조회를 비동기로 실행
        val stationName = stationDef.await()
        val aqiDef = async { airKoreaDataSource.fetchAirKoreaDetails(stationName) }
        val locationName = stationName

        // 3. 미세먼지 예보(owmAirList) 데이터를 토대로 기상청 24시간 단기예보 조회 실행
        val owmAirList = owmAirDef.await()
        val kma24HourForecast = kmaDataSource.fetchKma24HourForecast(lat, lon, owmAirList)
        
        // 4. 나머지 비동기 결과 수집 완료 후, 최종 OpenWeatherMap 5일 예보 조회 진행
        val realtime = realtimeDef.await()
        val aqiData = aqiDef.await()
        val owmResult = owmDataSource.fetchOwmForecast(lat, lon, owmAirList, kma24HourForecast)
        
        val now = Clock.System.now().toLocalDateTime(systemZone)
        val finalDailyList = owmResult.dailyList

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

        val kmaUv = uvDef.await()
        val uvVal = kmaUv ?: calculateUvIndex(now, finalCondition)
        val phase = calculateMoonPhase()

        val windSpeedVal = if (realtime.windSpeed != WeatherConstants.DOUBLE_UNKNOWN) {
            realtime.windSpeed
        } else {
            if (owmResult.currentWindSpeed != WeatherConstants.DOUBLE_UNKNOWN) owmResult.currentWindSpeed else 0.0
        }
        val rainAmountVal = if (realtime.rainAmount != WeatherConstants.DOUBLE_UNKNOWN) {
            realtime.rainAmount
        } else {
            if (owmResult.currentRain != WeatherConstants.DOUBLE_UNKNOWN) owmResult.currentRain else 0.0
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
        val finalHourlyList = kma24HourForecast.hourly

        val finalVisibility = if (owmResult.currentVisibility != WeatherConstants.DOUBLE_UNKNOWN) {
            owmResult.currentVisibility
        } else {
            WeatherConstants.PLACEHOLDER_VISIBILITY
        }

        val finalPressure = if (owmResult.currentPressure != WeatherConstants.DOUBLE_UNKNOWN) {
            owmResult.currentPressure
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
        // 해외 조회 시, 순차적인 흐름을 타므로 불필요한 async-await 코루틴 분기 처리를 걷어내고 
        // 다이렉트로 suspend 호출을 진행하여 불필요한 리소스 낭비를 차단합니다.
        val owmAirList = owmDataSource.fetchOwmAirPollutionForecast(lat, lon)
        val owmResult = owmDataSource.fetchOwmForecast(lat, lon, owmAirList, RawWeatherData())
        
        val locationName = owmResult.cityName
        val now = Clock.System.now().toLocalDateTime(systemZone)
        val finalDailyList = owmResult.dailyList

        val finalTemp = if (owmResult.currentTemp != WeatherConstants.TEMP_UNKNOWN) owmResult.currentTemp else 0.0
        val finalCondition = if (owmResult.currentCondition != WeatherConstants.NO_DATA) owmResult.currentCondition else WeatherConstants.DEFAULT_CONDITION
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

        val windSpeedVal = if (owmResult.currentWindSpeed != WeatherConstants.DOUBLE_UNKNOWN) owmResult.currentWindSpeed else 0.0
        val rainAmountVal = if (owmResult.currentRain != WeatherConstants.DOUBLE_UNKNOWN) owmResult.currentRain else 0.0
        val finalHumidity = if (owmResult.currentHumidity != WeatherConstants.INT_UNKNOWN) owmResult.currentHumidity else WeatherConstants.DEFAULT_HUMIDITY
        val windDirectionVal = WeatherConstants.degreeToDirection(owmResult.currentWindDeg)

        val feelsLikeVal = calculateFeelsLike(finalTemp, windSpeedVal, finalHumidity)
        val finalHourlyList = owmResult.hourlyList

        val finalVisibility = if (owmResult.currentVisibility != WeatherConstants.DOUBLE_UNKNOWN) {
            owmResult.currentVisibility
        } else {
            WeatherConstants.PLACEHOLDER_VISIBILITY
        }

        val finalPressure = if (owmResult.currentPressure != WeatherConstants.DOUBLE_UNKNOWN) {
            owmResult.currentPressure
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
        
        return try {
            val newSaju = geminiSajuDataSource.fetchSajuFortune(
                birthDate = birthDate,
                birthTime = birthTime,
                isLunar = isLunar,
                gender = gender,
                todayDate = todayDateStr
            )
            
            try {
                cacheManager.saveCache("saju_result_date", todayDateStr)
                cacheManager.saveCache("saju_result_input", inputCombo)
                cacheManager.saveCache("saju_result_cache", json.encodeToString(newSaju))
                Logger.i("--- Saju Cache Successfully Saved ---")
            } catch (e: Exception) {
                Logger.e("Failed to save saju cache", e)
            }
            
            newSaju
        } catch (apiException: Exception) {
            try {
                val cachedCombo = cacheManager.getCache("saju_result_input")
                val cachedJson = cacheManager.getCache("saju_result_cache")
                if (cachedCombo == inputCombo && !cachedJson.isNullOrBlank()) {
                    Logger.i("--- API Call Failed, but Found Previous Backup Cache ---")
                    val backupSaju = json.decodeFromString<SajuFortune>(cachedJson)
                    return backupSaju.copy(
                        overallMsg = backupSaju.overallMsg + "\n\n(※ 오늘의 분석 이용량 초과 또는 네트워크 지연으로 인해, 가장 최근에 받아보셨던 사주 분석 결과를 표시합니다.)"
                    )
                }
            } catch (cacheException: Exception) {
                Logger.e("Failed to read backup saju cache", cacheException)
            }
            throw apiException
        }
    }
}
