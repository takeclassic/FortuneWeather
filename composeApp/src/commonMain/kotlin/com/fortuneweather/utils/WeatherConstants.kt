package com.fortuneweather.utils

object WeatherConstants {
    // Sentinel values — 데이터 미수신 표시용
    const val TEMP_UNKNOWN = -999.0
    const val INT_UNKNOWN = -1
    const val DOUBLE_UNKNOWN = -1.0
    const val NO_DATA = "NO_DATA"

    // Fallback defaults
    const val DEFAULT_HUMIDITY = 50
    const val DEFAULT_STATION = "종로구"
    const val DEFAULT_CONDITION = "맑음"
    const val DEFAULT_WIND_DIRECTION = "북"

    // Cache
    const val CACHE_TTL_MILLIS = 2 * 60 * 60 * 1000L
    const val LOCATION_DELTA_THRESHOLD = 0.01

    // Placeholder (실제 데이터 연동 전까지 사용)
    const val PLACEHOLDER_PRESSURE = 1013.0
    const val PLACEHOLDER_VISIBILITY = 10.0

    // 한국 영역 판별
    val KOREA_LAT_RANGE = 33.0..39.0
    val KOREA_LON_RANGE = 124.0..132.0

    // Wind direction
    private val WIND_DIRECTIONS = listOf("북", "북동", "동", "남동", "남", "남서", "서", "북서")

    fun degreeToDirection(deg: Double): String {
        if (deg == DOUBLE_UNKNOWN) return NO_DATA
        val index = (((deg + 22.5) / 45.0).toInt()) % 8
        return WIND_DIRECTIONS.getOrElse(index) { DEFAULT_WIND_DIRECTION }
    }

    // 날씨 상태 정규화 (한국어)
    fun normalizeCondition(description: String): String = when {
        description.contains("비") || description.contains("소나기") || description.contains("우") -> "비"
        description.contains("눈") || description.contains("진눈깨비") -> "눈"
        description.contains("흐림") || description.contains("안개") -> "흐림"
        description.contains("구름많음") || description.contains("구름") -> "구름많음"
        else -> DEFAULT_CONDITION
    }

    // 기상청 PTY 코드 → 날씨 상태
    fun ptyToCondition(pty: String): String = when (pty) {
        "1", "4", "5" -> "비"
        "2", "3", "6", "7" -> "눈"
        else -> DEFAULT_CONDITION
    }

    // 기상청 SKY 코드 → 날씨 상태
    fun skyToCondition(sky: String): String = when (sky) {
        "3" -> "구름많음"
        "4" -> "흐림"
        else -> DEFAULT_CONDITION
    }
}
