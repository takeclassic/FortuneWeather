package com.fortuneweather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fortuneweather.data.repository.WeatherRepository
import com.fortuneweather.domain.model.WeatherInfo
import com.fortuneweather.ui.WeatherApp
import com.fortuneweather.ui.theme.*
import com.fortuneweather.utils.Logger
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val client = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; coerceInputValues = true }) }
    }
    private val repository = WeatherRepository(client)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.fortuneweather.data.cache.AppContext.context = this.applicationContext
        Logger.i("Application Started")
        MobileAds.initialize(this) {}

        setContent {
            FortuneWeatherTheme {
                var weatherState by remember { mutableStateOf<WeatherInfo?>(null) }
                var isRefreshing by remember { mutableStateOf(false) }
                var errorState by remember { mutableStateOf<String?>(null) }


                val context = this
                val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
                val scope = rememberCoroutineScope()

                suspend fun loadWeather(location: Location?, forceRefresh: Boolean = false) {
                    if (location == null) {
                        Logger.e("Location is Null")
                        errorState = "현재 위치를 확인할 수 없습니다."
                        return
                    }
                    try {
                        errorState = null
                        val weatherDeferred = scope.async { repository.getIntegratedWeather(location.latitude, location.longitude, forceRefresh = forceRefresh) }
                        val addressDeferred = scope.async(Dispatchers.IO) { getAddressName(context, location.latitude, location.longitude) }
                        val info = weatherDeferred.await()
                        val address = addressDeferred.await()
                        weatherState = if (address != null) info.copy(locationName = address) else info
                    } catch (e: Exception) {
                        Logger.e("Weather Pipeline Failed", e)
                        errorState = "날씨 정보를 불러올 수 없습니다."
                    }
                }

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        scope.launch {
                            isRefreshing = true
                            loadWeather(getCurrentLocation(fusedLocationClient))
                            isRefreshing = false
                        }
                    } else {
                        errorState = "위치 권한이 거부되었습니다. 날씨를 가져오려면 위치 권한이 필요합니다."
                    }
                }

                val handleRefreshAction = {
                    if (hasLocationPermission(context)) {
                        scope.launch { isRefreshing = true; loadWeather(getCurrentLocation(fusedLocationClient), forceRefresh = true); isRefreshing = false }
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }

                LaunchedEffect(Unit) {
                    if (hasLocationPermission(context)) {
                        isRefreshing = true; loadWeather(getCurrentLocation(fusedLocationClient)); isRefreshing = false
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }

                // UI 렌더링
                Box(modifier = Modifier.fillMaxSize()) {
                    if (weatherState != null) WeatherApp(weatherState!!, isRefreshing, { handleRefreshAction() })
                    else if (errorState != null) ErrorScreen(errorState!!, "다시 시도", { handleRefreshAction() })
                    else LoadingScreen()
                }
            }
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun getCurrentLocation(client: FusedLocationProviderClient): Location? {
        return try {
            client.lastLocation.await()
                ?: client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        } catch (e: Exception) {
            null
        }
    }

    private fun getAddressName(context: Context, lat: Double, lon: Double): String? {
        return try {
            val addresses = Geocoder(context, Locale.KOREA).getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let { addr ->
                "${addr.locality ?: ""} ${addr.subLocality ?: ""}".trim()
            }
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(SunnyLight, SunnyDark))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text("날씨 데이터를 받아오는 중...", color = Color.White, style = MaterialTheme.typography.body1)
        }
    }
}

@Composable
fun ErrorScreen(message: String, buttonText: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(ErrorLight, ErrorDark))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("⚠️", style = MaterialTheme.typography.h3)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.h6, lineHeight = 28.sp)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(backgroundColor = Color.White), shape = RoundedCornerShape(50), modifier = Modifier.height(50.dp).fillMaxWidth(0.7f)) {
                Text(buttonText, color = ErrorDark, style = MaterialTheme.typography.button)
            }
        }
    }
}
