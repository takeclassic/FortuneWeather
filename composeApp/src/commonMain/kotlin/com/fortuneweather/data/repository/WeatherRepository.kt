package com.fortuneweather.data.repository

import com.fortuneweather.domain.model.WeatherInfo
import com.fortuneweather.domain.model.RawWeatherData
import com.fortuneweather.utils.LatLonToGrid
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

import com.fortuneweather.data.dto.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

import com.fortuneweather.domain.model.*

import com.fortuneweather.BuildKonfig

/**
 * 여러 날씨 API를 통합하여 평균 날씨와 운세를 산출하는 리포지토리 (확장판)
 */
class WeatherRepository(private val client: HttpClient) {

    private val openWeatherKey = BuildKonfig.OPENWEATHER_KEY
    private val weatherApiKey = BuildKonfig.WEATHERAPI_KEY
    private val kmaServiceKey = BuildKonfig.KMA_SERVICE_KEY

    suspend fun getIntegratedWeather(lat: Double, lon: Double): WeatherInfo = coroutineScope {
        val openWeatherDeferred = async { fetchOpenWeather(lat, lon) }
        val weatherApiDeferred = async { fetchWeatherApi(lat, lon) }
        val kmaDeferred = async { fetchKmaWeather(lat, lon) }
        val airKoreaDeferred = async { fetchAirKorea("종로구") }

        val resOW = openWeatherDeferred.await()
        val resWA = weatherApiDeferred.await()
        val resKMA = kmaDeferred.await()
        val results = listOfNotNull(resOW, resWA, resKMA)

        // 데이터가 하나도 없을 경우 대비 (안전장치)
        if (results.isEmpty()) {
            return@coroutineScope WeatherInfo(
                locationName = "알 수 없음",
                temp = 20.0, humidity = 50, condition = "맑음", aqi = 0,
                uvIndex = 0.0, windDirection = "북", pressure = 1013.0, visibility = 10.0,
                sunrise = "06:00", sunset = "18:00", hourly = emptyList(), daily = emptyList(),
                luckyColor = "Blue", fashionTip = "날씨 정보를 불러올 수 없습니다.",
                fortuneMsg = "네트워크 연결을 확인해주세요.", sourceCount = 0
            )
        }

        // 1. 핵심 지표 평균 산출
        val avgTemp = results.map { it.temp }.average()
        val avgHumidity = results.map { it.humidity }.average().toInt()
        val commonCondition = results.groupingBy { it.condition }.eachCount().maxByOrNull { it.value }?.key ?: "맑음"
        
        // 2. 상세 지표 (우선순위: WeatherAPI > OpenWeather)
        val uvIndex = resWA?.uvIndex ?: resOW?.uvIndex ?: 0.0
        val pressure = resWA?.pressure ?: resOW?.pressure ?: 1013.0
        val visibility = resWA?.visibility ?: resOW?.visibility ?: 10.0
        val windDir = resWA?.windDirection ?: resOW?.windDirection ?: "북"
        val sunrise = resWA?.sunrise ?: resOW?.sunrise ?: "06:00"
        val sunset = resWA?.sunset ?: resOW?.sunset ?: "18:00"
        
        val aqi = airKoreaDeferred.await() ?: 0

        // 3. 시간별/주간별 예보 (WeatherAPI 중심)
        val hourly = resWA?.hourly ?: emptyList()
        val daily = resWA?.daily ?: emptyList()

        // 4. 부가 서비스 (추천 로직)
        val fashionTip = getFashionRecommendation(avgTemp)
        val luckyColor = calculateLuckyColor(avgTemp, commonCondition)
        val fortuneMsg = "오늘의 기운이 $luckyColor 만큼 빛나네요! 행운의 날씨입니다."

        // 지역 이름 우선순위: WeatherAPI (위치 이름) > OpenWeather (도시 이름)
        val finalLocationName = resWA?.locationName ?: resOW?.locationName ?: "서울"

        // 에어코리아 측정을 위해 지역명 기반으로 측정소 매칭 (임시 고도화)
        val airKoreaStation = when {
            finalLocationName.contains("안양") -> "호계동"
            finalLocationName.contains("서울") -> "종로구"
            finalLocationName.contains("인천") -> "신흥"
            finalLocationName.contains("수원") -> "신풍동"
            else -> finalLocationName // 기본적으로는 도시 이름 시도
        }
        val actualAqi = fetchAirKorea(airKoreaStation) ?: aqi

        WeatherInfo(
            locationName = finalLocationName, 
            temp = avgTemp,
            humidity = avgHumidity,
            condition = commonCondition,
            aqi = actualAqi ?: 0,
            uvIndex = uvIndex,
            windDirection = windDir,
            pressure = pressure,
            visibility = visibility,
            sunrise = sunrise,
            sunset = sunset,
            hourly = hourly,
            daily = daily,
            luckyColor = luckyColor,
            fashionTip = fashionTip,
            fortuneMsg = fortuneMsg,
            sourceCount = results.size
        )
    }

    private suspend fun fetchOpenWeather(lat: Double, lon: Double): RawWeatherData? = try {
        val response: OpenWeatherResponse = client.get("https://api.openweathermap.org/data/2.5/weather") {
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("appid", openWeatherKey)
            parameter("units", "metric")
            parameter("lang", "kr")
        }.body()
        RawWeatherData(
            temp = response.main.temp,
            humidity = response.main.humidity,
            condition = response.weather.firstOrNull()?.description ?: "맑음",
            pressure = response.main.pressure,
            visibility = response.visibility?.div(1000.0),
            locationName = response.name
        )
    } catch (e: Exception) { null }

    private suspend fun fetchWeatherApi(lat: Double, lon: Double): RawWeatherData? = try {
        val response: WeatherApiResponse = client.get("https://api.weatherapi.com/v1/forecast.json") {
            parameter("key", weatherApiKey)
            parameter("q", "$lat,$lon")
            parameter("days", 7)
            parameter("aqi", "no")
            parameter("alerts", "no")
            parameter("lang", "ko")
        }.body()
        
        val current = response.current
        val forecast = response.forecast?.forecastday?.firstOrNull()
        
        RawWeatherData(
            temp = current.tempC,
            humidity = current.humidity,
            condition = current.condition.text,
            uvIndex = current.uvIndex,
            pressure = current.pressureMb,
            visibility = current.visibilityKm,
            windDirection = current.windDir,
            locationName = response.location?.name ?: "서울",
            hourly = forecast?.hour?.map { 
                HourlyForecast(it.time.substringAfter(" "), it.tempC, it.condition.text) 
            } ?: emptyList(),
            daily = response.forecast?.forecastday?.map {
                DailyForecast(it.date, it.day.maxTemp, it.day.minTemp, it.day.condition.text)
            } ?: emptyList()
        )
    } catch (e: Exception) { null }

    private suspend fun fetchKmaWeather(lat: Double, lon: Double): RawWeatherData? = try {
        val grid = LatLonToGrid.convert(lat, lon)
        // 현재 날짜와 시간 계산 로직이 필요하지만, 우선 API 호출 구조만 유지
        // 실제로는 LocalDateTime 등을 사용해 base_date, base_time을 생성해야 함
        val response: KmaWeatherResponse = client.get("http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst") {
            parameter("serviceKey", kmaServiceKey)
            parameter("numOfRows", 10)
            parameter("dataType", "JSON")
            parameter("nx", grid.nx)
            parameter("ny", grid.ny)
            parameter("base_date", "20240601") // 임시로 현재 근접 날짜로 변경 (실제론 동적 생성 필요)
            parameter("base_time", "0500")
        }.body()
        
        val items = response.response.body?.items?.item ?: emptyList()
        val temp = items.find { it.category == "TMP" || it.category == "T1H" }?.fcstValue?.toDouble() ?: 0.0
        val reh = items.find { it.category == "REH" }?.fcstValue?.toInt() ?: 0
        RawWeatherData(temp, reh, "Cloudy")
    } catch (e: Exception) { null }

    private suspend fun fetchAirKorea(station: String): Int? = try {
        val response: AirKoreaResponse = client.get("http://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty") {
            parameter("serviceKey", kmaServiceKey)
            parameter("returnType", "json")
            parameter("stationName", station)
            parameter("dataTerm", "DAILY")
            parameter("ver", "1.0")
        }.body()
        response.response.body.items.firstOrNull()?.khaiValue?.toInt()
    } catch (e: Exception) { null }

    private fun getFashionRecommendation(temp: Double): String = when {
        temp < 4 -> "두꺼운 패딩, 목도리, 장갑 필수!"
        temp < 9 -> "코트, 가죽 자켓, 히트텍을 챙기세요."
        temp < 12 -> "자켓, 트렌치 코트, 니트가 적당해요."
        temp < 17 -> "얇은 가디건, 맨투맨, 청바지가 좋아요."
        temp < 20 -> "긴팔 티셔츠, 면바지, 가디건을 추천해요."
        temp < 23 -> "반팔, 얇은 셔츠, 반바지가 시원해요."
        temp < 28 -> "민소매, 반바지, 린넨 소재가 최고!"
        else -> "매우 더워요! 통풍이 잘 되는 옷을 입으세요."
    }

    private fun calculateLuckyColor(temp: Double, condition: String): String {
        return if (temp > 20) "Yellow" else "Sky Blue"
    }
}
