package com.fortuneweather.data.cache

import android.content.Context

object AppContext {
    lateinit var context: Context
}

actual class CacheManager actual constructor() {
    private val prefs by lazy {
        AppContext.context.getSharedPreferences("fortune_weather_prefs", Context.MODE_PRIVATE)
    }

    actual fun saveCache(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun getCache(key: String): String? {
        return prefs.getString(key, null)
    }
}
