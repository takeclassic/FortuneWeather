package com.fortuneweather.data.repository

import com.fortuneweather.domain.model.WeatherInfo
import com.fortuneweather.domain.model.RawWeatherData
import com.fortuneweather.utils.LatLonToGrid
import com.fortuneweather.utils.CoordinateConverter
import com.fortuneweather.utils.Logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import com.fortuneweather.data.dto.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import com.fortuneweather.domain.model.*
import com.fortuneweather.BuildKonfig
import com.fortuneweather.utils.SunTimes
import kotlinx.datetime.*
import kotlin.math.*
import kotlin.math.pow

class WeatherRepository(private val client: HttpClient) {

    private val kmaServiceKey = BuildKonfig.KMA_SERVICE_KEY
    private val kstZone = TimeZone.of("Asia/Seoul")

    private fun convertDegreeToDirection(deg: Double): String {
        val index = (((deg + 22.5) / 45.0).toInt()) % 8
        val directions = listOf("북", "북동", "동", "남동", "남", "남서", "서", "북서")
        return directions.getOrElse(index) { "북" }
    }

    suspend fun getIntegratedWeather(lat: Double, lon: Double, customLocationName: String? = null): WeatherInfo = coroutineScope {
        Logger.i("--- Weather Pipeline Triggered ---")
        
        val realtimeDef = async { fetchKmaRealtimeWeather(lat, lon) }
        val owmAirDef = async { fetchOwmAirPollutionForecast(lat, lon) }
        val stationDef = async { fetchNearestStation(lat, lon) }

        val owmAirList = owmAirDef.await()
        val owmForecastDef = async { fetchOwmForecast(lat, lon, owmAirList) }
        val forecastDef = async { fetchKmaForecastWeather(lat, lon, owmAirList) }
        val realtime = realtimeDef.await()
        val forecastData = forecastDef.await()
        val stationName = stationDeferredName(stationDef.await())
        val locationName = customLocationName ?: stationName
        
        val aqiDef = async { fetchAirKoreaDetails(stationName) }
        val owmForecastList = owmForecastDef.await()
        val aqiData = aqiDef.await()
        
        val now = Clock.System.now().toLocalDateTime(kstZone)
        val finalDailyList = owmForecastList ?: emptyList()

        val finalTemp = realtime?.temp ?: forecastData?.temp ?: 0.0
        val finalCondition = if (realtime?.condition == "비" || realtime?.condition == "눈") realtime.condition else forecastData?.condition ?: "맑음"
        val sunTimes = SunTimes.calculateSunriseSunset(lat, lon, now.date)

        // 에어코리아 한국 대기질 실시간 관측소 수치 매핑
        val pm10Val = aqiData?.pm10 ?: 0
        val pm25Val = aqiData?.pm25 ?: 0
        val aqiVal = aqiData?.khaiValue ?: 0

        // 자외선 지수 계산 (낮 시간대 정오에 맑으면 높게 시뮬레이션)
        val uvVal = if (now.hour in 7..18) {
            val peak = if (finalCondition == "맑음") 7.5 else if (finalCondition == "구름많음") 4.0 else 1.5
            val factor = kotlin.math.sin((now.hour - 6) / 12.0 * kotlin.math.PI)
            (peak * factor).coerceIn(0.0, 10.0)
        } else {
            0.0
        }

        // 달의 위상 계산 (신월 기점 주기 기준)
        val cycle = 29.530588853 * 24 * 60 * 60
        val baseNewMoon = 592800L
        val diff = Clock.System.now().epochSeconds - baseNewMoon
        var phase = (diff % cycle) / cycle
        if (phase < 0) phase += 1.0

        val windSpeedVal = realtime?.windSpeed ?: 0.0
        val rainAmountVal = realtime?.rainAmount ?: 0.0
        val finalHumidity = realtime?.humidity ?: forecastData?.humidity ?: 50

        // 체감온도 계산
        val feelsLikeVal = if (finalTemp <= 10.0 && windSpeedVal > 1.3) {
            val vKmh = windSpeedVal * 3.6
            13.12 + 0.6215 * finalTemp - 11.37 * vKmh.pow(0.16) + 0.3965 * finalTemp * vKmh.pow(0.16)
        } else if (finalTemp >= 20.0) {
            finalTemp + 0.33 * (finalHumidity / 100.0 * 6.105 * kotlin.math.exp(17.27 * finalTemp / (237.7 + finalTemp))) - 4.0
        } else {
            finalTemp
        }

        WeatherInfo(
            locationName = locationName, temp = finalTemp, feelsLike = feelsLikeVal, humidity = finalHumidity,
            condition = finalCondition, aqi = aqiVal, pm10 = pm10Val, pm25 = pm25Val, uvIndex = uvVal, 
            windDirection = realtime?.windDirection ?: forecastData?.windDirection ?: "북", 
            windSpeed = windSpeedVal, rainAmount = rainAmountVal,
            pressure = 1013.0, visibility = 10.0, sunrise = sunTimes.first, sunset = sunTimes.second,
            moonPhase = phase,
            hourly = forecastData?.hourly ?: emptyList(), daily = finalDailyList,
            luckyColor = calculateLuckyColor(finalTemp, finalCondition), fashionTip = getFashionRecommendation(finalTemp),
            fortuneMsg = "기상청 통합 예보가 업데이트되었습니다.", sourceCount = 3
        )
    }


    private fun stationDeferredName(res: String?): String = res ?: "종로구"

    private suspend fun fetchKmaMidTermWeatherWithRetry(landId: String, tempId: String): List<DailyForecast>? {
        val now = Clock.System.now().toLocalDateTime(kstZone)
        var currentDt = now.date
        for (attempt in 0..2) {
            val tmFc = if (now.hour < 6) {
                val yesterday = currentDt.minus(1, DateTimeUnit.DAY)
                "${yesterday.year}${yesterday.monthNumber.toString().padStart(2, '0')}${yesterday.dayOfMonth.toString().padStart(2, '0')}1800"
            } else if (now.hour < 18) {
                "${currentDt.year}${currentDt.monthNumber.toString().padStart(2, '0')}${currentDt.dayOfMonth.toString().padStart(2, '0')}0600"
            } else {
                "${currentDt.year}${currentDt.monthNumber.toString().padStart(2, '0')}${currentDt.dayOfMonth.toString().padStart(2, '0')}1800"
            }
            val res = fetchKmaMidTermWeather(landId, tempId, tmFc)
            if (!res.isNullOrEmpty()) return res
            currentDt = currentDt.minus(1, DateTimeUnit.DAY)
        }
        return null
    }

    private suspend fun fetchKmaMidTermWeather(landId: String, tempId: String, tmFc: String): List<DailyForecast>? = try {
        val landUrl = "http://apis.data.go.kr/1360000/MidFcstInfoService/getMidLandFcst?serviceKey=$kmaServiceKey&dataType=JSON&regId=$landId&tmFc=$tmFc"
        val tempUrl = "http://apis.data.go.kr/1360000/MidFcstInfoService/getMidTa?serviceKey=$kmaServiceKey&dataType=JSON&regId=$tempId&tmFc=$tmFc"
        val landRes = client.get(landUrl).bodyAsText()
        val tempRes = client.get(tempUrl).bodyAsText()
        val json = Json { ignoreUnknownKeys = true }
        fun getSafeObject(raw: String): JsonObject? {
            val root = try { json.parseToJsonElement(raw) as? JsonObject } catch(e: Exception) { return null }
            val response = root?.get("response") as? JsonObject ?: return null
            val body = response["body"] as? JsonObject ?: return null
            val items = body["items"]
            return if (items is JsonObject) items["item"]?.jsonArray?.getOrNull(0) as? JsonObject else null
        }
        val landItem = getSafeObject(landRes); val tempItem = getSafeObject(tempRes)
        if (landItem == null || tempItem == null) null
        else {
            val list = mutableListOf<DailyForecast>(); val baseDate = LocalDate(tmFc.substring(0, 4).toInt(), tmFc.substring(4, 6).toInt(), tmFc.substring(6, 8).toInt())
            for (i in 3..10) {
                val targetDate = baseDate.plus(i, DateTimeUnit.DAY)
                val morning: String
                val afternoon: String
                val pop: Int
                
                if (i <= 7) {
                    morning = landItem["wf${i}Am"]?.jsonPrimitive?.content ?: "맑음"
                    afternoon = landItem["wf${i}Pm"]?.jsonPrimitive?.content ?: "맑음"
                    val popAm = landItem["rnSt${i}Am"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val popPm = landItem["rnSt${i}Pm"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    pop = max(popAm, popPm)
                } else {
                    val cond = landItem["wf$i"]?.jsonPrimitive?.content ?: "맑음"
                    morning = cond
                    afternoon = cond
                    pop = landItem["rnSt$i"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                }
                
                fun fix(s: String) = when { s.contains("비") -> "비"; s.contains("눈") -> "눈"; s.contains("구름") -> "구름많음"; else -> "맑음" }
                list.add(DailyForecast("${targetDate.monthNumber}/${targetDate.dayOfMonth}", "", pop, fix(morning), fix(afternoon), tempItem["taMin$i"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0, tempItem["taMax$i"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0))
            }
            list
        }
    } catch (e: Exception) { null }

    private suspend fun fetchKmaForecastWeather(lat: Double, lon: Double, owmAirList: List<OwmAirPollutionItem>?): RawWeatherData? = try {
        val grid = LatLonToGrid.convert(lat, lon); val (baseDate, baseTime) = getKmaForecastBaseTime()
        val url = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst?serviceKey=$kmaServiceKey&dataType=JSON&base_date=$baseDate&base_time=$baseTime&nx=${grid.nx}&ny=${grid.ny}&numOfRows=1000"
        val responseStr = client.get(url).bodyAsText()
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<KmaWeatherResponse>(responseStr)
        val items = decoded.response.body?.items?.item ?: emptyList()
        if (items.isEmpty()) null
        else {
            val owmAqiMap = owmAirList?.associate { item ->
                val instant = Instant.fromEpochSeconds(item.dt)
                val localDateTime = instant.toLocalDateTime(kstZone)
                val key = "${localDateTime.year}${localDateTime.monthNumber.toString().padStart(2, '0')}${localDateTime.dayOfMonth.toString().padStart(2, '0')}${localDateTime.hour.toString().padStart(2, '0')}00"
                key to item.components.pm10.toInt()
            } ?: emptyMap()

            val now = Clock.System.now().toLocalDateTime(kstZone)
            val hourGroups = items.groupBy { (it.fcstDate ?: "") + (it.fcstTime ?: "") }
            val sortedKeys = hourGroups.keys.filter { it.length >= 10 }.sorted().filter { it >= "${now.year}${now.monthNumber.toString().padStart(2, '0')}${now.dayOfMonth.toString().padStart(2, '0')}${now.hour.toString().padStart(2, '0')}00" }
            val hourly = sortedKeys.take(24).map { key ->
                val group = hourGroups[key]!!; val hourInt = key.substring(8, 10).toInt()
                val displayTime = if (hourInt < 12) "am ${if(hourInt == 0) 12 else hourInt}시" else "pm ${if(hourInt == 12) 12 else hourInt-12}시"
                val sky = group.find { it.category == "SKY" }?.fcstValue ?: "1"; val pty = group.find { it.category == "PTY" }?.fcstValue ?: "0"
                val pop = group.find { it.category == "POP" }?.fcstValue?.toIntOrNull() ?: 0
                val pm10Val = owmAqiMap[key] ?: 0
                HourlyForecast(displayTime, group.find { it.category == "TMP" }?.fcstValue?.toDoubleOrNull() ?: 0.0, if(pty != "0") (if(pty == "2" || pty == "3") "눈" else "비") else (when(sky){"3"->"구름많음";"4"->"흐림";else->"맑음"}), pop, aqi = pm10Val)
            }
            val dayGroups = items.groupBy { it.fcstDate }
            val daily = dayGroups.keys.filterNotNull().sorted().map { dateStr ->
                val group = dayGroups[dateStr]!!; val month = dateStr.substring(4, 6).toInt(); val day = dateStr.substring(6, 8).toInt()
                fun getBestCond(start: Int, end: Int): String {
                    val period = group.filter { (it.fcstTime?.substring(0, 2)?.toInt() ?: 0) in start until end }
                    if (period.isEmpty()) return "맑음"
                    val ptyAny = period.find { it.category == "PTY" && it.fcstValue != "0" }
                    if (ptyAny != null) return if (ptyAny.fcstValue == "2" || ptyAny.fcstValue == "3") "눈" else "비"
                    val skyMax = period.filter { it.category == "SKY" }.maxByOrNull { it.fcstValue }?.fcstValue ?: "1"
                    return when(skyMax){"3"->"구름많음";"4"->"흐림";else->"맑음"}
                }
                DailyForecast("${month}/${day}", "", group.filter { it.category == "POP" }.map { it.fcstValue.toIntOrNull() ?: 0 }.maxOrNull() ?: 0, getBestCond(0, 12), getBestCond(12, 24), group.filter { it.category == "TMP" }.map { it.fcstValue.toDoubleOrNull() ?: 0.0 }.minOrNull() ?: 0.0, group.filter { it.category == "TMP" }.map { it.fcstValue.toDoubleOrNull() ?: 0.0 }.maxOrNull() ?: 0.0)
            }
            val currentVec = items.find { it.category == "VEC" }?.fcstValue?.toDoubleOrNull()
            val currentWindDir = currentVec?.let { convertDegreeToDirection(it) } ?: "북"
            RawWeatherData(hourly.firstOrNull()?.temp ?: 0.0, 50, hourly.firstOrNull()?.condition ?: "맑음", hourly = hourly, daily = daily, windDirection = currentWindDir)
        }
    } catch (e: Exception) { null }

    private fun getKmaRegionCodes(location: String): Pair<String, String> {
        val landCode = when {
            location.contains("강원") || location.contains("춘천") || location.contains("원주") || location.contains("강릉") -> {
                if (location.contains("강릉") || location.contains("속초") || location.contains("삼척")) "11D20000" else "11D10000"
            }
            location.contains("대전") || location.contains("세종") || location.contains("충남") || location.contains("천안") -> "11C20000"
            location.contains("충북") || location.contains("청주") || location.contains("충주") -> "11C10000"
            location.contains("전북") || location.contains("전주") || location.contains("군산") -> "11F10000"
            location.contains("광주") || location.contains("전남") || location.contains("여수") || location.contains("목포") -> "11F20000"
            location.contains("대구") || location.contains("경북") || location.contains("포항") || location.contains("구미") -> "11H10000"
            location.contains("부산") || location.contains("울산") || location.contains("경남") || location.contains("창원") || location.contains("진주") -> "11H20000"
            location.contains("제주") || location.contains("서귀포") -> "11G00000"
            else -> "11B00000"
        }
        val tempCode = when {
            location.contains("인천") -> "11B20201"
            location.contains("수원") -> "11B20601"
            location.contains("춘천") -> "11D10301"
            location.contains("강릉") -> "11D20501"
            location.contains("청주") -> "11C10301"
            location.contains("대전") -> "11C20401"
            location.contains("세종") -> "11C20404"
            location.contains("전주") -> "11F10201"
            location.contains("광주") -> "11F20501"
            location.contains("대구") -> "11H10701"
            location.contains("부산") -> "11H20201"
            location.contains("울산") -> "11H20101"
            location.contains("제주") -> "11G00201"
            location.contains("서귀포") -> "11G00401"
            else -> "11B10101"
        }
        return landCode to tempCode
    }

    private suspend fun fetchKmaRealtimeWeather(lat: Double, lon: Double): RawWeatherData? = try {
        val grid = LatLonToGrid.convert(lat, lon); val (baseDate, baseTime) = getKmaRealtimeBaseTime()
        val url = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst?serviceKey=$kmaServiceKey&dataType=JSON&base_date=$baseDate&base_time=$baseTime&nx=${grid.nx}&ny=${grid.ny}&numOfRows=20"
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<KmaWeatherResponse>(client.get(url).bodyAsText())
        val items = decoded.response.body?.items?.item ?: emptyList()
        if (items.isEmpty()) null
        else {
            val temp = items.find { it.category == "T1H" }?.obsrValue?.toDoubleOrNull() ?: 0.0
            val reh = items.find { it.category == "REH" }?.obsrValue?.toIntOrNull() ?: 50
            val pty = items.find { it.category == "PTY" }?.obsrValue ?: "0"
            val vec = items.find { it.category == "VEC" }?.obsrValue?.toDoubleOrNull()
            val windDirection = vec?.let { convertDegreeToDirection(it) } ?: "북"
            val windSpeedRaw = items.find { it.category == "WSD" }?.obsrValue
            val windSpeed = windSpeedRaw?.toDoubleOrNull() ?: 0.0
            Logger.d("KMA Realtime windSpeed: raw=$windSpeedRaw, parsed=$windSpeed")
            val rainValStr = items.find { it.category == "RN1" }?.obsrValue ?: ""
            val rainAmount = if(rainValStr.contains("없음") || rainValStr.isEmpty()) 0.0 else rainValStr.replace("mm", "").trim().toDoubleOrNull() ?: 0.0
            RawWeatherData(temp, reh, if(pty == "1" || pty == "4" || pty == "5") "비" else if(pty == "2" || pty == "3" || pty == "6" || pty == "7") "눈" else "맑음", windDirection = windDirection, windSpeed = windSpeed, rainAmount = rainAmount)
        }
    } catch (e: Exception) { null }

    private fun getKmaRealtimeBaseTime(): Pair<String, String> { val now = Clock.System.now().toLocalDateTime(kstZone); var date = now.date; var hour = now.hour; if (now.minute < 40) { if (hour == 0) { date = date.minus(1, DateTimeUnit.DAY); hour = 23 } else hour -= 1 }; return Pair("${date.year}${date.monthNumber.toString().padStart(2, '0')}${date.dayOfMonth.toString().padStart(2, '0')}", "${hour.toString().padStart(2, '0')}00") }
    private fun getKmaForecastBaseTime(): Pair<String, String> { val now = Clock.System.now().toLocalDateTime(kstZone); var date = now.date; var hour = now.hour; val baseTime = when { hour < 2 -> { date = date.minus(1, DateTimeUnit.DAY); "2300" }; hour < 5 -> "0200"; hour < 8 -> "0500"; hour < 11 -> "0800"; hour < 14 -> "1100"; hour < 17 -> "1400"; hour < 20 -> "1700"; else -> "2000" }; return Pair("${date.year}${date.monthNumber.toString().padStart(2, '0')}${date.dayOfMonth.toString().padStart(2, '0')}", baseTime) }
    private suspend fun fetchNearestStation(lat: Double, lon: Double): String? = try { val (tmX, tmY) = CoordinateConverter.wgs84ToTM(lat, lon); val responseText = client.get("http://apis.data.go.kr/B552584/MsrstnInfoInqireSvc/getNearbyMsrstnList?serviceKey=$kmaServiceKey&returnType=json&tmX=$tmX&tmY=$tmY&ver=1.0").bodyAsText(); Json { ignoreUnknownKeys = true }.parseToJsonElement(responseText).jsonObject["response"]?.jsonObject?.get("body")?.jsonObject?.get("items")?.jsonObject?.get("item")?.jsonArray?.getOrNull(0)?.jsonObject?.get("stationName")?.jsonPrimitive?.content } catch (e: Exception) { null }
    data class AirKoreaData(val pm10: Int, val pm25: Int, val khaiValue: Int)

    private suspend fun fetchAirKoreaDetails(station: String): AirKoreaData? = try {
        val responseStr = client.get("http://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty?serviceKey=$kmaServiceKey&returnType=json&stationName=$station&dataTerm=DAILY&ver=1.0").bodyAsText()
        val json = Json { ignoreUnknownKeys = true }
        val items = (json.parseToJsonElement(responseStr).jsonObject["response"]?.jsonObject?.get("body")?.jsonObject?.get("items") as? JsonArray)
        val firstItem = items?.getOrNull(0)?.jsonObject
        if (firstItem != null) {
            val pm10 = firstItem["pm10Value"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val pm25 = firstItem["pm25Value"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val khai = firstItem["khaiValue"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            AirKoreaData(pm10 = pm10, pm25 = pm25, khaiValue = khai)
        } else null
    } catch (e: Exception) { null }
    private fun getFashionRecommendation(temp: Double): String = when { temp < 4 -> "패딩 필수!"; temp < 15 -> "자켓이나 코트"; temp < 23 -> "가벼운 셔츠"; else -> "시원한 반팔" }
    private fun calculateLuckyColor(temp: Double, condition: String): String = if (temp > 20) "Yellow" else "Sky Blue"

    private suspend fun fetchOwmAirPollutionForecast(lat: Double, lon: Double): List<OwmAirPollutionItem>? = try {
        val apiKey = BuildKonfig.OPENWEATHER_KEY
        val url = "http://api.openweathermap.org/data/2.5/air_pollution/forecast?lat=$lat&lon=$lon&appid=$apiKey"
        val responseText = client.get(url).bodyAsText()
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<OwmAirPollutionResponse>(responseText)
        decoded.list
    } catch (e: Exception) {
        Logger.e("Failed to fetch OWM Air Pollution Forecast", e)
        null
    }

    private suspend fun fetchOwmForecast(lat: Double, lon: Double, owmAirList: List<OwmAirPollutionItem>?): List<DailyForecast>? = try {
        val apiKey = BuildKonfig.OPENWEATHER_KEY
        val url = "http://api.openweathermap.org/data/2.5/forecast?lat=$lat&lon=$lon&appid=$apiKey&units=metric&lang=kr"
        val responseText = client.get(url).bodyAsText()
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<OwmForecastResponse>(responseText)
        
        val owmItems = decoded.list
        
        val groups = owmItems.groupBy { item ->
            val instant = Instant.fromEpochSeconds(item.dt)
            val localDateTime = instant.toLocalDateTime(kstZone)
            "${localDateTime.year}-${localDateTime.monthNumber.toString().padStart(2, '0')}-${localDateTime.dayOfMonth.toString().padStart(2, '0')}"
        }
        
        val owmAqiMap = owmAirList?.associate { item ->
            val instant = Instant.fromEpochSeconds(item.dt)
            val localDateTime = instant.toLocalDateTime(kstZone)
            val key = "${localDateTime.year}-${localDateTime.monthNumber.toString().padStart(2, '0')}-${localDateTime.dayOfMonth.toString().padStart(2, '0')}"
            key to item.components.pm10.toInt()
        } ?: emptyMap()

        val simulatedHourlyAqiMap = owmAirList?.associate { item ->
            val instant = Instant.fromEpochSeconds(item.dt)
            val localDateTime = instant.toLocalDateTime(kstZone)
            val key = "${localDateTime.year}-${localDateTime.monthNumber.toString().padStart(2, '0')}-${localDateTime.dayOfMonth.toString().padStart(2, '0')} ${localDateTime.hour.toString().padStart(2, '0')}"
            key to item.components.pm10.toInt()
        } ?: emptyMap()
        
        val tomorrow = Clock.System.now().toLocalDateTime(kstZone).date.plus(1, DateTimeUnit.DAY)
        val targetDates = (0..4).map { tomorrow.plus(it, DateTimeUnit.DAY) }
        
        targetDates.mapIndexedNotNull { index, localDate ->
            val dateStr = "${localDate.year}-${localDate.monthNumber.toString().padStart(2, '0')}-${localDate.dayOfMonth.toString().padStart(2, '0')}"
            val month = localDate.monthNumber
            val dayVal = localDate.dayOfMonth
            val dateKey = "$month/$dayVal"
            
            var dayItems = groups[dateStr]
            if (dayItems.isNullOrEmpty()) {
                val fallbackItem = owmItems.firstOrNull()
                if (fallbackItem != null) {
                    dayItems = listOf(fallbackItem)
                }
            }
            
            if (dayItems.isNullOrEmpty()) {
                return@mapIndexedNotNull null
            }
            val nonNullItems = dayItems
            
            val dayOfWeek = when (localDate.dayOfWeek) {
                DayOfWeek.MONDAY -> "월"
                DayOfWeek.TUESDAY -> "화"
                DayOfWeek.WEDNESDAY -> "수"
                DayOfWeek.THURSDAY -> "목"
                DayOfWeek.FRIDAY -> "금"
                DayOfWeek.SATURDAY -> "토"
                DayOfWeek.SUNDAY -> "일"
                else -> "일"
            }
            
            val minTemp = nonNullItems.minOf { it.main.tempMin }
            val maxTemp = nonNullItems.maxOf { it.main.tempMax }
            
            val pop = (nonNullItems.maxOfOrNull { it.pop ?: 0.0 } ?: 0.0) * 100
            val dayAqi = owmAqiMap[dateStr] ?: 0
            
            val morningItem = nonNullItems.minByOrNull { item ->
                val localTime = Instant.fromEpochSeconds(item.dt).toLocalDateTime(kstZone)
                abs(localTime.hour - 9)
            }
            val afternoonItem = nonNullItems.minByOrNull { item ->
                val localTime = Instant.fromEpochSeconds(item.dt).toLocalDateTime(kstZone)
                abs(localTime.hour - 15)
            }
            
            fun fix(s: String) = when {
                s.contains("비") || s.contains("소나기") || s.contains("우") -> "비"
                s.contains("눈") || s.contains("진눈깨비") -> "눈"
                s.contains("흐림") || s.contains("안개") -> "흐림"
                s.contains("구름많음") || s.contains("구름") -> "구름많음"
                else -> "맑음"
            }
            
            val morningCond = morningItem?.weather?.firstOrNull()?.description?.let { fix(it) } ?: "맑음"
            val afternoonCond = afternoonItem?.weather?.firstOrNull()?.description?.let { fix(it) } ?: "맑음"
            
            val hourlyDetails = nonNullItems
                .filter { item ->
                    val localTime = Instant.fromEpochSeconds(item.dt).toLocalDateTime(kstZone)
                    localTime.hour in 6..22
                }
                .map { item ->
                    val localTime = Instant.fromEpochSeconds(item.dt).toLocalDateTime(kstZone)
                    val targetHour = localTime.hour
                    val displayTime = "${targetHour.toString().padStart(2, '0')}시"
                    val cond = fix(item.weather.firstOrNull()?.description ?: "맑음")
                    
                    val hourAqiKey = "${localTime.year}-${localTime.monthNumber.toString().padStart(2, '0')}-${localTime.dayOfMonth.toString().padStart(2, '0')} ${localTime.hour.toString().padStart(2, '0')}"
                    val hourAqi = simulatedHourlyAqiMap[hourAqiKey] ?: dayAqi
                    val hourPop = ((item.pop ?: 0.0) * 100).toInt()
                    
                    Logger.d("OWM pop mapped: dtTxt=${item.dtTxt}, rawPop=${item.pop}, calculated=$hourPop")
                    HourlyDetail(displayTime, item.main.temp, cond, aqi = hourAqi, precipitationProbability = hourPop)
                }
            
            DailyForecast(
                date = dateKey,
                dayOfWeek = dayOfWeek,
                precipitationProbability = pop.toInt(),
                morningCondition = morningCond,
                afternoonCondition = afternoonCond,
                minTemp = minTemp,
                maxTemp = maxTemp,
                aqi = dayAqi,
                hourlyDetails = hourlyDetails
            )
        }
    } catch (e: Exception) {
        Logger.e("Failed to fetch OWM Forecast", e)
        null
    }
}
