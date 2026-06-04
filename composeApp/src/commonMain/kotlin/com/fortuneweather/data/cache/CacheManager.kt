package com.fortuneweather.data.cache

expect class CacheManager() {
    fun saveCache(key: String, value: String)
    fun getCache(key: String): String?
}
