package com.fortuneweather.utils

import kotlin.math.*

/**
 * WGS84 위경도를 한국 표준 TM(중부원점, GRS80) 좌표로 변환하는 유틸리티
 */
object CoordinateConverter {
    private const val SEMI_MAJOR_AXIS = 6378137.0
    private const val FLATTENING = 1 / 298.257222101
    private const val FALSE_EASTING = 200000.0
    private const val FALSE_NORTHING = 500000.0
    private const val ORIGIN_LATITUDE = 38.0
    private const val ORIGIN_LONGITUDE = 127.0

    fun wgs84ToTM(lat: Double, lon: Double): Pair<Double, Double> {
        val latRad = lat * PI / 180.0
        val lonRad = lon * PI / 180.0
        val originLatRad = ORIGIN_LATITUDE * PI / 180.0
        val originLonRad = ORIGIN_LONGITUDE * PI / 180.0

        val e2 = 2 * FLATTENING - FLATTENING * FLATTENING

        val n = FLATTENING / (2 - FLATTENING)
        val n2 = n * n
        val n3 = n2 * n
        val n4 = n2 * n2

        val m1 = 1 + n2 / 4 + n4 / 64
        val m2 = 3.0 / 2.0 * (n - n3 / 8)
        val m3 = 15.0 / 16.0 * (n2 - n4 / 4)
        val m4 = 35.0 / 48.0 * n3

        val m = SEMI_MAJOR_AXIS / (1 + n) * (m1 * latRad - m2 * sin(2 * latRad) + m3 * sin(4 * latRad) - m4 * sin(6 * latRad))
        val m0 = SEMI_MAJOR_AXIS / (1 + n) * (m1 * originLatRad - m2 * sin(2 * originLatRad) + m3 * sin(4 * originLatRad) - m4 * sin(6 * originLatRad))

        val nu = SEMI_MAJOR_AXIS / sqrt(1 - e2 * sin(latRad) * sin(latRad))
        val p = lonRad - originLonRad

        val cosLat = cos(latRad)
        val tanLat = tan(latRad)
        val tan2Lat = tanLat * tanLat
        val tan4Lat = tan2Lat * tan2Lat

        val eta2 = e2 / (1 - e2) * cosLat * cosLat

        val a1 = nu * cosLat
        val a2 = nu / 2 * sin(latRad) * cosLat
        val a3 = nu / 24 * sin(latRad) * cosLat * cosLat * cosLat * (5 - tan2Lat + 9 * eta2)
        val a4 = nu / 720 * sin(latRad) * cosLat * cosLat * cosLat * cosLat * cosLat * (61 - 58 * tan2Lat + tan4Lat)
        val a5 = nu / 6 * cosLat * cosLat * cosLat * (1 - tan2Lat + eta2)
        val a6 = nu / 120 * cosLat * cosLat * cosLat * cosLat * cosLat * (5 - 18 * tan2Lat + tan4Lat + 14 * eta2 - 58 * tan2Lat * eta2)

        val x = FALSE_EASTING + (a1 * p + a5 * p.pow(3) + a6 * p.pow(5))
        val y = FALSE_NORTHING + (m - m0 + a2 * p.pow(2) + a3 * p.pow(4) + a4 * p.pow(6))

        return Pair(x, y)
    }
}
