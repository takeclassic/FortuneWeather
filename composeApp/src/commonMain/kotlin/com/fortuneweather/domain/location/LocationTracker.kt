package com.fortuneweather.domain.location

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val addressName: String?
)

interface LocationTracker {
    suspend fun getCurrentLocation(): DeviceLocation?
}
