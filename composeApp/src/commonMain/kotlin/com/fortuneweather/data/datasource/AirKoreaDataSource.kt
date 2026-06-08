package com.fortuneweather.data.datasource

import com.fortuneweather.BuildKonfig
import com.fortuneweather.utils.Logger
import io.ktor.client.*
import kotlinx.serialization.json.*

data class AirKoreaData(
    val pm10: Int = 0,
    val pm25: Int = 0,
    val khaiValue: Int = 0
)

class AirKoreaDataSource(client: HttpClient) : BaseRemoteDataSource(client) {

    private val kmaServiceKey = BuildKonfig.KMA_SERVICE_KEY

    suspend fun fetchAirKoreaDetails(station: String): AirKoreaData {
        return try {
            val url = "http://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty?serviceKey=$kmaServiceKey&returnType=json&stationName=$station&dataTerm=DAILY&ver=1.0"
            val responseStr = safeGet(url) ?: return AirKoreaData()
            val items = (json.parseToJsonElement(responseStr).jsonObject["response"]?.jsonObject?.get("body")?.jsonObject?.get("items") as? JsonArray)
            val firstItem = items?.getOrNull(0)?.jsonObject
            if (firstItem != null) {
                val pm10 = firstItem["pm10Value"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val pm25 = firstItem["pm25Value"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val khai = firstItem["khaiValue"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                AirKoreaData(pm10 = pm10, pm25 = pm25, khaiValue = khai)
            } else AirKoreaData()
        } catch (e: Exception) {
            Logger.e("Failed to fetch AirKorea Details for station: $station", e)
            AirKoreaData()
        }
    }
}
