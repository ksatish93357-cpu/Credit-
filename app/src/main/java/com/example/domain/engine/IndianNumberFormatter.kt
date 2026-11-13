package com.example.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

object IndianNumberFormatter {

    fun formatCurrency(paise: Long, decimals: Int = 2, trimTrailingZerosWhenWhole: Boolean = false): String {
        val negative = paise < 0
        val absPaise = abs(paise)
        val rupees = absPaise / 100
        val remPaise = (absPaise % 100).toInt()

        val formattedInt = formatIndianInteger(rupees)
        val sign = if (negative) "-" else ""

        if (decimals <= 0 || (trimTrailingZerosWhenWhole && remPaise == 0)) {
            val roundedRupees = if (decimals <= 0 && remPaise >= 50) rupees + 1 else rupees
            return "${sign}₹${formatIndianInteger(roundedRupees)}"
        }

        val paiseStr = remPaise.toString().padStart(2, '0')
        return "${sign}₹$formattedInt.$paiseStr"
    }

    fun formatCompactCurrency(paise: Long): String {
        return formatCurrency(paise, decimals = 2, trimTrailingZerosWhenWhole = true)
    }

    fun formatIndianInteger(value: Long): String {
        val str = value.toString()
        if (str.length <= 3) return str
        val lastThree = str.takeLast(3)
        val rest = str.dropLast(3)
        val sb = StringBuilder()
        var i = rest.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            if (sb.isNotEmpty()) sb.insert(0, ',')
            sb.insert(0, rest.substring(start, i))
            i -= 2
        }
        return "$sb,$lastThree"
    }

    fun rupeesInputToPaiseOrNull(input: String): Long? {
        val cleaned = input.trim().replace(",", "").replace("₹", "").trim()
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned)
            if (bd < BigDecimal.ZERO) return null
            bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    fun paiseToPlainRupeesString(paise: Long): String {
        val rupees = paise / 100
        val rem = abs(paise % 100)
        return if (rem == 0L) rupees.toString() else "$rupees.${rem.toString().padStart(2, '0')}"
    }
}
