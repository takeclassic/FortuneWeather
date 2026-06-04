package com.fortuneweather.utils

import kotlinx.datetime.LocalDate
import kotlin.math.*

/**
 * 위도, 경도 및 날짜를 기반으로 일출/일몰 시간을 계산하는 유틸리티
 */
object SunTimes {
    fun calculateSunriseSunset(lat: Double, lon: Double, date: LocalDate): Pair<String, String> {
        val dayOfYear = date.dayOfYear

        // 태양 적위 (declination) 계산 (라디안)
        val declination = 23.45 * sin(2 * PI * (284 + dayOfYear) / 365.0)
        val declinationRad = declination * PI / 180.0
        val latRad = lat * PI / 180.0

        // -0.833도 = 태양이 지평선 아래로 내려가는 각도 (대기 굴절 등 고려)
        val zenith = -0.833 * PI / 180.0
        val cosH = (sin(zenith) - sin(latRad) * sin(declinationRad)) / (cos(latRad) * cos(declinationRad))

        // 극야 또는 백야 처리
        if (cosH > 1.0) {
            return Pair("06:00", "18:00") // 태양이 뜨지 않는 경우 기본값
        } else if (cosH < -1.0) {
            return Pair("05:00", "20:00") // 태양이 지지 않는 경우 기본값
        }

        val hRad = acos(cosH)
        val hDeg = hRad * 180.0 / PI

        // 균시차 (Equation of Time) 계산 (분 단위)
        val b = 2 * PI * (dayOfYear - 81) / 364.0
        val eot = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)

        // 경도 보정: 대한민국 표준시는 동경 135도 (UTC+9) 기준
        val longitudeCorrection = (lon - 135.0) * 4.0

        // 남중 시간 (Local Solar Noon) = 12시 - (경도 보정 + 균시차) / 60
        val solarNoon = 12.0 - (longitudeCorrection + eot) / 60.0

        // H를 시간 단위로 변환 (15도 = 1시간)
        val transitOffset = hDeg / 15.0

        val sunriseHour = solarNoon - transitOffset
        val sunsetHour = solarNoon + transitOffset

        fun formatHour(hour: Double): String {
            val totalMinutes = (hour * 60).roundToInt()
            val h = (totalMinutes / 60) % 24
            val m = totalMinutes % 60
            val cleanH = if (h < 0) h + 24 else h
            val cleanM = if (m < 0) m + 60 else m
            return "${cleanH.toString().padStart(2, '0')}:${cleanM.toString().padStart(2, '0')}"
        }

        return Pair(formatHour(sunriseHour), formatHour(sunsetHour))
    }
}
