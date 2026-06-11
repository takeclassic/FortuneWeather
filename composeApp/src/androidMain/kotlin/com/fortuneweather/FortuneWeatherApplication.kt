package com.fortuneweather

import android.app.Application
import com.fortuneweather.data.cache.AppContext
import com.fortuneweather.di.AndroidAppContainer
import com.fortuneweather.di.AppContainer
import com.fortuneweather.utils.Logger

/**
 * Application 클래스.
 * AppContainer를 앱 수명 주기와 동일하게 유지하고,
 * Activity/Composable이 container를 통해 의존성을 가져간다.
 */
class FortuneWeatherApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppContext.context = applicationContext
        container = AndroidAppContainer(applicationContext)
        Logger.i("Application Created — AppContainer initialized")
    }
}
