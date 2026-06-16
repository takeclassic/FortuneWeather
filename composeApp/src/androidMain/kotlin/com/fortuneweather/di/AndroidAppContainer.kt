package com.fortuneweather.di

import android.content.Context
import com.fortuneweather.data.datasource.AirKoreaDataSource
import com.fortuneweather.data.datasource.KmaWeatherDataSource
import com.fortuneweather.data.datasource.OwmWeatherDataSource
import com.fortuneweather.data.datasource.GeminiSajuDataSource
import com.fortuneweather.data.repository.WeatherRepository
import com.fortuneweather.domain.location.LocationTracker
import com.fortuneweather.location.AndroidLocationTracker
import com.fortuneweather.ui.WeatherViewModel
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

/**
 * Android 플랫폼용 AppContainer 구현체.
 * 앱 수명 주기 동안 단 하나의 인스턴스만 유지되며,
 * Application 클래스에서 초기화된다.
 */
class AndroidAppContainer(context: Context) : AppContainer {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main

    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
    }

    private val kmaDataSource = KmaWeatherDataSource(httpClient)
    private val airKoreaDataSource = AirKoreaDataSource(httpClient)
    private val owmDataSource = OwmWeatherDataSource(httpClient)
    private val geminiSajuDataSource = GeminiSajuDataSource(httpClient)

    override val weatherRepository: WeatherRepository = WeatherRepository(
        kmaDataSource = kmaDataSource,
        airKoreaDataSource = airKoreaDataSource,
        owmDataSource = owmDataSource,
        geminiSajuDataSource = geminiSajuDataSource
    )

    override val locationTracker: LocationTracker = AndroidLocationTracker(
        context = context,
        ioDispatcher = ioDispatcher
    )

    override fun createViewModel(): WeatherViewModel =
        WeatherViewModel(
            repository = weatherRepository,
            locationTracker = locationTracker,
            mainDispatcher = mainDispatcher
        )
}
