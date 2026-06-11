package com.fortuneweather.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.fortuneweather.domain.location.DeviceLocation
import com.fortuneweather.domain.location.LocationTracker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

class AndroidLocationTracker(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LocationTracker {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): DeviceLocation? {
        return try {
            val location = fusedLocationClient.lastLocation.await()
                ?: fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()

            if (location != null) {
                val addressName = getAddressName(location.latitude, location.longitude)
                DeviceLocation(location.latitude, location.longitude, addressName)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getAddressName(lat: Double, lon: Double): String? {
        return withContext(ioDispatcher) {
            try {
                val addresses = Geocoder(context, Locale.KOREA).getFromLocation(lat, lon, 1)
                addresses?.firstOrNull()?.let { addr ->
                    "${addr.locality ?: ""} ${addr.subLocality ?: ""}".trim()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
