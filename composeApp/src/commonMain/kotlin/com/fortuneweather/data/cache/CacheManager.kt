package com.fortuneweather.data.cache

expect object CacheManager {
    fun saveCache(key: String, value: String)
    fun getCache(key: String): String?
}
