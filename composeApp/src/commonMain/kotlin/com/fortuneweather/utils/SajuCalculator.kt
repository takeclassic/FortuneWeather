package com.fortuneweather.utils

import kotlin.math.sin
import kotlin.math.PI

data class SolarDate(val year: Int, val month: Int, val day: Int)

expect object LunarSolarConverter {
    fun convertLunarToSolar(year: Int, month: Int, day: Int, isLeapMonth: Boolean = false): SolarDate
}

data class SajuPillars(
    val yearHanja: String,
    val monthHanja: String,
    val dayHanja: String,
    val hourHanja: String
)

object SajuCalculator {
    private val stems = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    private val branches = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

    private fun getJulianDay(year: Int, month: Int, day: Int): Int {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = y / 100
        val b = a / 4
        val c = 2 - a + b
        val d = (365.25 * (y + 4716)).toInt()
        val e = (30.6001 * (m + 1)).toInt()
        return c + d + e + day - 1524
    }

    private fun getSunLongitude(year: Int, month: Int, day: Int, hour: Int, minute: Int): Double {
        var utcHour = hour - 9
        var utcY = year
        var utcM = month
        var utcD = day
        
        if (utcHour < 0) {
            utcHour += 24
            utcD -= 1
            if (utcD < 1) {
                utcM -= 1
                if (utcM < 1) {
                    utcM = 12
                    utcY -= 1
                }
                utcD = when (utcM) {
                    2 -> if ((utcY % 4 == 0 && utcY % 100 != 0) || utcY % 400 == 0) 29 else 28
                    4, 6, 9, 11 -> 30
                    else -> 31
                }
            }
        }
        
        val dayFraction = utcHour / 24.0 + minute / 1440.0
        val jd = getJulianDay(utcY, utcM, utcD) - 0.5 + dayFraction
        val d = jd - 2451545.0
        val rad = PI / 180.0
        
        var g = (357.529 + 0.98560028 * d) % 360.0
        if (g < 0) g += 360.0
        
        var q = (280.459 + 0.98564736 * d) % 360.0
        if (q < 0) q += 360.0
        
        var L = (q + 1.915 * sin(g * rad) + 0.020 * sin(2.0 * g * rad)) % 360.0
        if (L < 0) L += 360.0
        
        return L
    }

    fun calculate(
        solarYear: Int,
        solarMonth: Int,
        solarDay: Int,
        birthTime: String // "HH:MM" or "모름"
    ): SajuPillars {
        val hour: Int
        val min: Int
        val isTimeUnknown = birthTime == "모름" || !birthTime.contains(":")
        
        if (isTimeUnknown) {
            hour = 12
            min = 0
        } else {
            val parts = birthTime.split(":")
            hour = parts[0].toIntOrNull() ?: 12
            min = parts[1].toIntOrNull() ?: 0
        }
        
        val sunLong = getSunLongitude(solarYear, solarMonth, solarDay, hour, min)
        
        val sajuYear = if (solarMonth == 1) {
            solarYear - 1
        } else if (solarMonth == 2) {
            if (sunLong >= 315.0) solarYear else solarYear - 1
        } else {
            solarYear
        }
        
        val yearIdx = (sajuYear - 1984) % 60
        val finalYearIdx = if (yearIdx < 0) yearIdx + 60 else yearIdx
        val yearPillar = "${stems[finalYearIdx % 10]}${branches[finalYearIdx % 12]}"
        
        val monthIdx = (sunLong - 315.0 + 720.0) % 360.0
        val monthEnumIdx = (monthIdx / 30.0).toInt().coerceIn(0, 11)
        
        val yearStemIdx = finalYearIdx % 10
        val baseMonthStemIdx = when (yearStemIdx % 5) {
            0 -> 2 // 甲, 己 -> 丙 (2)
            1 -> 4 // 乙, 庚 -> 戊 (4)
            2 -> 6 // 丙, 辛 -> 庚 (6)
            3 -> 8 // 丁, 壬 -> 壬 (8)
            4 -> 0 // 戊, 癸 -> 甲 (0)
            else -> 0
        }
        val monthStemIdx = (baseMonthStemIdx + monthEnumIdx) % 10
        val monthBranchIdx = (monthEnumIdx + 2) % 12
        val monthPillar = "${stems[monthStemIdx]}${branches[monthBranchIdx]}"
        
        var targetYear = solarYear
        var targetMonth = solarMonth
        var targetDay = solarDay
        
        if (!isTimeUnknown && (hour > 23 || (hour == 23 && min >= 30))) {
            val daysInMonth = when (solarMonth) {
                2 -> if ((solarYear % 4 == 0 && solarYear % 100 != 0) || solarYear % 400 == 0) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
            targetDay += 1
            if (targetDay > daysInMonth) {
                targetDay = 1
                targetMonth += 1
                if (targetMonth > 12) {
                    targetMonth = 1
                    targetYear += 1
                }
            }
        }
        
        val jdn = getJulianDay(targetYear, targetMonth, targetDay)
        val dayIdx = (jdn + 49) % 60
        val finalDayIdx = if (dayIdx < 0) dayIdx + 60 else dayIdx
        val dayPillar = "${stems[finalDayIdx % 10]}${branches[finalDayIdx % 12]}"
        
        val hourPillar = if (isTimeUnknown) {
            "？？"
        } else {
            var minutes = hour * 60 + min
            minutes = (minutes + 30) % 1440
            val branchIdx = minutes / 120
            
            val dayStemIdx = finalDayIdx % 10
            val baseHourStemIdx = when (dayStemIdx % 5) {
                0 -> 0 // 甲, 己 -> 甲 (0)
                1 -> 2 // 乙, 庚 -> 丙 (2)
                2 -> 4 // 丙, 辛 -> 戊 (4)
                3 -> 6 // 丁, 壬 -> 庚 (6)
                4 -> 8 // 戊, 癸 -> 壬 (8)
                else -> 0
            }
            val hourStemIdx = (baseHourStemIdx + branchIdx) % 10
            "${stems[hourStemIdx]}${branches[branchIdx]}"
        }
        
        return SajuPillars(
            yearHanja = yearPillar,
            monthHanja = monthPillar,
            dayHanja = dayPillar,
            hourHanja = hourPillar
        )
    }
}
