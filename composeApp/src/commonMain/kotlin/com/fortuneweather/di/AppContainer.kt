package com.fortuneweather.di

import com.fortuneweather.data.repository.WeatherRepository
import com.fortuneweather.domain.location.LocationTracker
import com.fortuneweather.ui.WeatherViewModel

/**
 * 앱 전체에서 공유되는 의존성 컨테이너 인터페이스.
 * 플랫폼별 구현체(AndroidAppContainer 등)가 이를 구현한다.
 */
interface AppContainer {
    val weatherRepository: WeatherRepository
    val locationTracker: LocationTracker
    fun createViewModel(): WeatherViewModel
}
