package com.fortuneweather.utils

import kotlinx.datetime.LocalDate
import kotlin.math.*

object SunTimes {
    // 천문 상수
    private const val EARTH_AXIAL_TILT = 23.45
    private const val ZENITH_ANGLE = -0.833 // 대기 굴절 보정 (도)
    private const val KST_REFERENCE_LONGITUDE = 135.0 // UTC+9 기준 경도
    private const val MINUTES_PER_DEGREE = 4.0
    private const val DEGREES_PER_HOUR = 15.0
    private const val DAYS_IN_YEAR = 365.0
    private const val EOT_DAYS_IN_YEAR = 364.0
    private const val EOT_OFFSET = 81
    private const val DECLINATION_OFFSET = 284

    data class SunriseSunset(val sunrise: String, val sunset: String)

    fun calculate(lat: Double, lon: Double, date: LocalDate): SunriseSunset {
        val dayOfYear = date.dayOfYear
        val degToRad = PI / 180.0

        val declination = EARTH_AXIAL_TILT * sin(2 * PI * (DECLINATION_OFFSET + dayOfYear) / DAYS_IN_YEAR)
        val declinationRad = declination * degToRad
        val latRad = lat * degToRad

        val zenithRad = ZENITH_ANGLE * degToRad
        val cosH = (sin(zenithRad) - sin(latRad) * sin(declinationRad)) / (cos(latRad) * cos(declinationRad))

        // 극지방 예외 처리
        if (cosH > 1.0) return SunriseSunset("06:00", "18:00")
        if (cosH < -1.0) return SunriseSunset("05:00", "20:00")

        val hDeg = acos(cosH) * 180.0 / PI

        val b = 2 * PI * (dayOfYear - EOT_OFFSET) / EOT_DAYS_IN_YEAR
        val eot = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)

        val longitudeCorrection = (lon - KST_REFERENCE_LONGITUDE) * MINUTES_PER_DEGREE
        val solarNoon = 12.0 - (longitudeCorrection + eot) / 60.0
        val transitOffset = hDeg / DEGREES_PER_HOUR

        val sunriseHour = solarNoon - transitOffset
        val sunsetHour = solarNoon + transitOffset

        return SunriseSunset(formatHour(sunriseHour), formatHour(sunsetHour))
    }

    private fun formatHour(hour: Double): String {
        val totalMinutes = (hour * 60).roundToInt()
        val h = ((totalMinutes / 60) % 24).let { if (it < 0) it + 24 else it }
        val m = (totalMinutes % 60).let { if (it < 0) it + 60 else it }
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }
}
