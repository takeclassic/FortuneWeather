package com.fortuneweather.ui

import com.fortuneweather.data.repository.WeatherRepository
import com.fortuneweather.domain.location.LocationTracker
import com.fortuneweather.domain.model.WeatherInfo
import com.fortuneweather.data.datasource.SajuFortune
import com.fortuneweather.data.datasource.SajuHanja
import com.fortuneweather.utils.Logger
import kotlinx.coroutines.coroutineScope
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

    sealed interface SajuUiState {
        object Initial : SajuUiState
        data class Loading(val previewHanja: SajuHanja? = null) : SajuUiState
        data class Success(val saju: SajuFortune) : SajuUiState
        data class Error(val message: String) : SajuUiState
    }

    private val _sajuState = MutableStateFlow<SajuUiState>(SajuUiState.Initial)
    val sajuState: StateFlow<SajuUiState> = _sajuState.asStateFlow()

    sealed interface SajuPreviewState {
        object Empty : SajuPreviewState
        object Loading : SajuPreviewState
        data class Success(val hanja: SajuHanja) : SajuPreviewState
        data class Error(val message: String) : SajuPreviewState
    }

    private val _previewHanjaState = MutableStateFlow<SajuPreviewState>(SajuPreviewState.Empty)
    val previewHanjaState: StateFlow<SajuPreviewState> = _previewHanjaState.asStateFlow()

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
                    addressName = location.addressName,
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

    fun loadSajuFortune(
        birthDate: String,
        birthTime: String,
        isLunar: Boolean,
        gender: String,
        forceRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            _sajuState.value = SajuUiState.Loading(null)
            
            val isCached = if (!forceRefresh) {
                repository.isSajuFortuneCached(birthDate, birthTime, isLunar, gender)
            } else {
                false
            }

            if (isCached) {
                try {
                    val saju = repository.getSajuFortune(
                        birthDate = birthDate,
                        birthTime = birthTime,
                        isLunar = isLunar,
                        gender = gender,
                        forceRefresh = false
                    )
                    _sajuState.value = SajuUiState.Success(saju)
                    return@launch
                } catch (e: Exception) {
                    Logger.e("Failed to load cached Saju", e)
                }
            }

            try {
                coroutineScope {
                    launch {
                        try {
                            val hanja = repository.getSajuHanjaOnly(
                                birthDate = birthDate,
                                birthTime = birthTime,
                                isLunar = isLunar,
                                gender = gender
                            )
                            _previewHanjaState.value = SajuPreviewState.Success(hanja)
                            val currentState = _sajuState.value
                            if (currentState is SajuUiState.Loading) {
                                _sajuState.value = SajuUiState.Loading(hanja)
                            }
                        } catch (e: Exception) {
                            Logger.e("Failed to load Saju Hanja Preview", e)
                        }
                    }

                    launch {
                        try {
                            val saju = repository.getSajuFortune(
                                birthDate = birthDate,
                                birthTime = birthTime,
                                isLunar = isLunar,
                                gender = gender,
                                forceRefresh = forceRefresh
                            )
                            _sajuState.value = SajuUiState.Success(saju)
                        } catch (e: Exception) {
                            Logger.e("Failed to load Saju fortune", e)
                            _sajuState.value = SajuUiState.Error(e.message ?: "사주 분석 중 오류가 발생했습니다.")
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e("Failed in loadSajuFortune scope", e)
                _sajuState.value = SajuUiState.Error(e.message ?: "사주 분석 중 오류가 발생했습니다.")
            }
        }
    }

    fun resetSaju() {
        _sajuState.value = SajuUiState.Initial
        _previewHanjaState.value = SajuPreviewState.Empty
    }

    fun prefetchSajuHanja(
        birthDate: String,
        birthTime: String,
        isLunar: Boolean,
        gender: String
    ) {
        _previewHanjaState.value = SajuPreviewState.Loading
        viewModelScope.launch {
            try {
                val hanja = repository.getSajuHanjaOnly(
                    birthDate = birthDate,
                    birthTime = birthTime,
                    isLunar = isLunar,
                    gender = gender
                )
                _previewHanjaState.value = SajuPreviewState.Success(hanja)
                Logger.i("Prefetched Saju Hanja: $hanja")
            } catch (e: Exception) {
                Logger.e("Failed to prefetch Saju Hanja", e)
                _previewHanjaState.value = SajuPreviewState.Error(e.message ?: "사주팔자 조회 실패")
            }
        }
    }

    fun clearPreviewHanja() {
        _previewHanjaState.value = SajuPreviewState.Empty
    }

    /** ViewModel이 더 이상 필요 없을 때 호출해 내부 coroutine들을 취소한다. */
    fun clear() {
        viewModelScope.cancel()
    }
}
