package com.fortuneweather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fortuneweather.di.AppContainer
import com.fortuneweather.ui.WeatherApp
import com.fortuneweather.ui.WeatherViewModel
import com.fortuneweather.ui.WeatherUiState
import com.fortuneweather.ui.theme.*
import com.fortuneweather.utils.Logger
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {

    // Application에서 관리하는 컨테이너를 통해 ViewModel을 취득한다.
    private val container: AppContainer
        get() = (application as FortuneWeatherApplication).container

    // Activity 수명 주기와 동일하게 ViewModel을 유지한다.
    private lateinit var viewModel: WeatherViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.i("MainActivity — onCreate")
        MobileAds.initialize(this) {}

        viewModel = container.createViewModel()

        setContent {
            FortuneWeatherTheme {
                val context = this
                val uiState by viewModel.uiState.collectAsState()
                val isRefreshing by viewModel.isRefreshing.collectAsState()

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        viewModel.loadWeather()
                    } else {
                        viewModel.showError("위치 권한이 거부되었습니다. 날씨를 가져오려면 위치 권한이 필요합니다.")
                    }
                }

                val handleRefreshAction = {
                    if (hasLocationPermission(context)) {
                        viewModel.loadWeather(forceRefresh = true)
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }

                LaunchedEffect(Unit) {
                    if (hasLocationPermission(context)) {
                        viewModel.loadWeather()
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (val state = uiState) {
                        is WeatherUiState.Loading -> LoadingScreen()
                        is WeatherUiState.Success -> WeatherApp(state.weatherInfo, isRefreshing, { handleRefreshAction() })
                        is WeatherUiState.Error -> ErrorScreen(state.message, "다시 시도", { handleRefreshAction() })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Activity가 완전히 종료될 때만 ViewModel의 scope를 취소한다.
        if (isFinishing) {
            viewModel.clear()
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(SunnyLight, SunnyDark))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text("날씨 데이터를 받아오는 중...", color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ErrorScreen(message: String, buttonText: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(ErrorLight, ErrorDark))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("⚠️", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge, lineHeight = 28.sp)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(50), modifier = Modifier.height(50.dp).fillMaxWidth(0.7f)) {
                Text(buttonText, color = ErrorDark, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
