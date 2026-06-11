package com.fortuneweather.utils

/**
 * 앱 전용 정밀 로깅 시스템
 * Logcat 필터에 'weatherlog'를 입력하세요.
 */
object Logger {
    private const val TAG = "WeatherLog"

    fun d(message: String) {
        println("$TAG [DEBUG] => $message")
    }

    fun e(message: String, throwable: Throwable? = null) {
        println("$TAG [ERROR] !! => $message")
        throwable?.printStackTrace()
    }

    fun i(message: String) {
        println("$TAG [INFO] -> $message")
    }
}
