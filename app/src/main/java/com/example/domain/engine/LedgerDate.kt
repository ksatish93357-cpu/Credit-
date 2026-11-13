package com.example.domain.engine

import java.util.Calendar
import java.util.Locale

/**
 * Timezone-safe calendar date (YYYY-MM-DD) with no UTC day-shift bugs.
 */
data class LedgerDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int    // 1..31
) : Comparable<LedgerDate> {

    init {
        require(month in 1..12) { "Invalid month: $month" }
        val maxDay = actualDaysInMonth(year, month)
        require(day in 1..maxDay) { "Invalid day $day for $year-$month (max $maxDay)" }
    }

    fun toIsoString(): String =
        String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

    fun toDisplayString(): String =
        String.format(Locale.US, "%02d/%02d/%04d", day, month, year)

    fun toEpochDay(): Long {
        // Howard Hinnant's civil-from/to-days algorithm (pure integer calendar math, zero timezone)
        val y = if (month <= 2) year - 1L else year.toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400 // [0, 399]
        val m = month.toLong()
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + day - 1 // [0, 365]
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy // [0, 146096]
        return era * 146097 + doe - 719468
    }

    fun daysUntil(other: LedgerDate): Long = other.toEpochDay() - this.toEpochDay()

    fun plusDays(days: Long): LedgerDate = fromEpochDay(toEpochDay() + days)

    fun plusMonthsClamped(monthsToAdd: Int, anchorDay: Int = day): LedgerDate {
        val totalMonths = year * 12 + (month - 1) + monthsToAdd
        val newYear = Math.floorDiv(totalMonths, 12)
        val newMonth = Math.floorMod(totalMonths, 12) + 1
        val maxDay = actualDaysInMonth(newYear, newMonth)
        return LedgerDate(newYear, newMonth, minOf(anchorDay, maxDay))
    }

    override fun compareTo(other: LedgerDate): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return day.compareTo(other.day)
    }

    override fun toString(): String = toIsoString()

    companion object {
        fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

        fun actualDaysInMonth(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> throw IllegalArgumentException("Invalid month: $month")
        }

        fun actualDaysInYear(year: Int): Int = if (isLeapYear(year)) 366 else 365

        fun todayLocal(): LedgerDate {
            val cal = Calendar.getInstance()
            return LedgerDate(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun fromEpochMillisLocal(millis: Long): LedgerDate {
            val cal = Calendar.getInstance()
            cal.timeInMillis = millis
            return LedgerDate(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun fromEpochDay(epochDay: Long): LedgerDate {
            val z = epochDay + 719468
            val era = (if (z >= 0) z else z - 146096) / 146097
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val y = yoe + era * 400
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = mp + (if (mp < 10) 3 else -9)
            val finalYear = y + (if (m <= 2) 1 else 0)
            return LedgerDate(finalYear.toInt(), m.toInt(), d.toInt())
        }

        fun parseOrNull(raw: String?): LedgerDate? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            return try {
                when {
                    Regex("""^\d{4}-\d{2}-\d{2}$""").matches(trimmed) -> {
                        val parts = trimmed.split("-")
                        LedgerDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                    }
                    Regex("""^\d{2}/\d{2}/\d{4}$""").matches(trimmed) -> {
                        val parts = trimmed.split("/")
                        LedgerDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                    }
                    Regex("""^\d{2}-\d{2}-\d{4}$""").matches(trimmed) -> {
                        val parts = trimmed.split("-")
                        LedgerDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
