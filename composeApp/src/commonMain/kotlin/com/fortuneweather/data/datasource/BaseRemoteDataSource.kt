package com.fortuneweather.data.datasource

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import com.fortuneweather.utils.Logger

abstract class BaseRemoteDataSource(protected val client: HttpClient) {

    protected val json = Json { ignoreUnknownKeys = true }

    protected suspend fun safeGet(url: String): String? = try {
        val response = client.get(url)
        if (response.status.value in 200..299) {
            response.bodyAsText()
        } else {
            Logger.e("HTTP Request Failed [${response.status.value}]: URL=$url\nResponse: ${response.bodyAsText()}")
            null
        }
    } catch (e: Exception) {
        Logger.e("Network Connection Exception: URL=$url", e)
        null
    }
}
