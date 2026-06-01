package com.fortuneweather

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.fortuneweather.data.repository.WeatherRepository
import com.fortuneweather.domain.model.WeatherInfo
import com.fortuneweather.ui.WeatherApp
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { 
                ignoreUnknownKeys = true 
                coerceInputValues = true
            })
        }
    }
    
    private val repository = WeatherRepository(client)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // AdMob 초기화
        MobileAds.initialize(this) {}

        setContent {
            var weatherState by remember { mutableStateOf<WeatherInfo?>(null) }
            val context = this
            val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
            
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions.values.any { it }) {
                    // 권한 획득 시 위치 가져오기 재시도 (아래 LaunchedEffect에서 처리됨)
                }
            }

            LaunchedEffect(Unit) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    try {
                        val location = fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY, null
                        ).await()
                        
                        if (location != null) {
                            weatherState = repository.getIntegratedWeather(location.latitude, location.longitude)
                        } else {
                            // 위치를 가져오지 못했을 때 기본값 (서울)
                            weatherState = repository.getIntegratedWeather(37.5665, 126.9780)
                        }
                    } catch (e: Exception) {
                        weatherState = repository.getIntegratedWeather(37.5665, 126.9780)
                    }
                } else {
                    launcher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            }

            weatherState?.let {
                WeatherApp(it)
            } ?: run {
                // 로딩 중 UI
            }
        }
    }
}
