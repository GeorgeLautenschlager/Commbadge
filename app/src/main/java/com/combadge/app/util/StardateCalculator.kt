package com.combadge.app.util

import java.util.Calendar

/**
 * Converts the current calendar date to a TNG-era stardate string.
 *
 * TNG stardates (seasons 1-7, ~2364–2370) are in the range 41xxx–47xxx.
 * Simple approximation: 1000 stardate units ≈ 1 year, starting from
 * stardate 41000.0 at January 1, 2364.
 *
 * Stardate = 41000 + ((currentYear - 2364) * 1000) + (dayOfYear / daysInYear * 1000)
 */
object StardateCalculator {

    private const val BASE_STARDATE = 41000.0
    private const val BASE_YEAR = 2364

    fun current(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val daysInYear = if (isLeapYear(year)) 366.0 else 365.0

        val stardate = BASE_STARDATE +
                ((year - BASE_YEAR) * 1000.0) +
                (dayOfYear / daysInYear * 1000.0)

        return "%.1f".format(stardate)
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
