package com.fortuneweather.ui

import com.fortuneweather.data.repository.WeatherRepository
import com.fortuneweather.domain.location.LocationTracker
import com.fortuneweather.domain.model.WeatherInfo
import com.fortuneweather.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val weatherInfo: WeatherInfo) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

/**
 * 날씨 UI 상태를 관리하는 ViewModel.
 * 자체 CoroutineScope를 소유하며, 외부 scope를 파라미터로 받지 않는다.
 * 더 이상 필요하지 않을 때 [clear]를 호출해 scope를 취소한다.
 */
class WeatherViewModel(
    private val repository: WeatherRepository,
    private val locationTracker: LocationTracker,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun loadWeather(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                _isRefreshing.value = true
            } else {
                _uiState.value = WeatherUiState.Loading
            }

            try {
                val location = locationTracker.getCurrentLocation()
                if (location == null) {
                    _uiState.value = WeatherUiState.Error("현재 위치를 확인할 수 없습니다.")
                    return@launch
                }

                val info = repository.getIntegratedWeather(
                    location.latitude,
                    location.longitude,
                    forceRefresh = forceRefresh
                )

                val finalInfo = if (!location.addressName.isNullOrBlank()) {
                    info.copy(locationName = location.addressName)
                } else {
                    info
                }

                _uiState.value = WeatherUiState.Success(finalInfo)
            } catch (e: Exception) {
                Logger.e("Weather Pipeline Failed in ViewModel", e)
                _uiState.value = WeatherUiState.Error("날씨 정보를 불러올 수 없습니다.")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun showError(message: String) {
        _uiState.value = WeatherUiState.Error(message)
    }

    /** ViewModel이 더 이상 필요 없을 때 호출해 내부 coroutine들을 취소한다. */
    fun clear() {
        viewModelScope.cancel()
    }
}
