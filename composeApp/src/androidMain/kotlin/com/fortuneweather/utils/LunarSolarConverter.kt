package com.fortuneweather.utils

import android.icu.util.ChineseCalendar
import android.icu.util.Calendar

actual object LunarSolarConverter {
    actual fun convertLunarToSolar(year: Int, month: Int, day: Int, isLeapMonth: Boolean): SolarDate {
        val cc = ChineseCalendar()
        // ChineseCalendar EXTENDED_YEAR = Year + 2637 (epoch is 2637 BC)
        cc.set(ChineseCalendar.EXTENDED_YEAR, year + 2637)
        cc.set(ChineseCalendar.MONTH, month - 1)
        cc.set(ChineseCalendar.DAY_OF_MONTH, day)
        cc.set(ChineseCalendar.IS_LEAP_MONTH, if (isLeapMonth) 1 else 0)
        
        val timeInMillis = cc.timeInMillis
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeInMillis
        
        return SolarDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}
