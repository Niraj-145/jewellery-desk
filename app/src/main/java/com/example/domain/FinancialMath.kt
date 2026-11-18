package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Decimal-safe financial and weight calculations using BigDecimal and integer paise/micrograms.
 * Never uses Double or Float for monetary arithmetic.
 */
object FinancialMath {

    private val HUNDRED = BigDecimal("100")
    private val MILLION = BigDecimal("1000000")
    private val TEN_THOUSAND = BigDecimal("10000")

    /**
     * Formula: Item Amount = Net Weight (g) * Today's Rate (₹/g) * Percentage / 100
     *
     * Inputs:
     * - [netWeightUg]: weight in micrograms (1 g = 1,000,000 µg; e.g., 0.434 g = 434,000 µg)
     * - [ratePerGramPaise]: rate in integer paise (₹14,000 = 1,400,000 paise)
     * - [percentageBps]: percentage in basis points (72% = 7,200 bps; 72.50% = 7,250 bps)
     *
     * Returns item amount in integer paise rounded HALF_UP to nearest paisa.
     * Example: 0.434 g * ₹14,000 * 72 / 100 = ₹4,374.72 = 437,472 paise.
     */
    fun calculateItemAmountPaise(
        netWeightUg: Long,
        ratePerGramPaise: Long,
        percentageBps: Int
    ): Long {
        if (netWeightUg <= 0L || ratePerGramPaise <= 0L || percentageBps <= 0) return 0L
        val weightGrams = BigDecimal.valueOf(netWeightUg).divide(MILLION, 6, RoundingMode.HALF_UP)
        val rateRupees = BigDecimal.valueOf(ratePerGramPaise).divide(HUNDRED, 2, RoundingMode.HALF_UP)
        val pctDecimal = BigDecimal.valueOf(percentageBps.toLong()).divide(TEN_THOUSAND, 4, RoundingMode.HALF_UP)
        val amountRupees = weightGrams.multiply(rateRupees).multiply(pctDecimal)
        return amountRupees.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    fun calculateRemainingBalancePaise(finalAmountPaise: Long, amountReceivedPaise: Long): Long {
        return (finalAmountPaise - amountReceivedPaise).coerceAtLeast(0L)
    }

    fun derivePaymentStatus(finalAmountPaise: Long, amountReceivedPaise: Long): String {
        return when {
            finalAmountPaise <= 0L && amountReceivedPaise <= 0L -> "PAID"
            amountReceivedPaise >= finalAmountPaise -> "PAID"
            amountReceivedPaise > 0L -> "PARTIALLY_PAID"
            else -> "UNPAID"
        }
    }

    /**
     * Parses user input string like "14000", "4374.72", or "-500" into integer paise.
     * Returns null if invalid.
     */
    fun parseRupeesToPaise(input: String, allowNegative: Boolean = false): Long? {
        val cleaned = input.trim().replace(",", "").replace("₹", "")
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned)
            if (!allowNegative && bd < BigDecimal.ZERO) return null
            bd.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses weight in grams string (e.g., "0.434", "12.500") into integer micrograms.
     */
    fun parseGramsToUg(input: String): Long? {
        val cleaned = input.trim().replace(",", "").lowercase().removeSuffix("g").trim()
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned)
            if (bd < BigDecimal.ZERO) return null
            bd.multiply(MILLION).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses percentage string (e.g., "72", "72.5") into basis points (7200, 7250).
     */
    fun parsePercentageToBps(input: String): Int? {
        val cleaned = input.trim().replace("%", "")
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned)
            if (bd < BigDecimal.ZERO || bd > BigDecimal("1000")) return null
            bd.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).intValueExact()
        } catch (_: Exception) {
            null
        }
    }

    fun paiseToPlainRupeesString(paise: Long): String {
        val bd = BigDecimal.valueOf(paise).divide(HUNDRED, 2, RoundingMode.HALF_UP)
        val stripped = bd.stripTrailingZeros()
        return if (stripped.scale() <= 0) stripped.toPlainString() else bd.toPlainString()
    }

    fun ugToPlainGramsString(ug: Long): String {
        val bd = BigDecimal.valueOf(ug).divide(MILLION, 6, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (bd.scale() <= 0) "${bd.toPlainString()}.000" else {
            if (bd.scale() < 3) bd.setScale(3, RoundingMode.HALF_UP).toPlainString() else bd.toPlainString()
        }
    }

    fun bpsToPlainPercentString(bps: Int): String {
        val bd = BigDecimal.valueOf(bps.toLong()).divide(HUNDRED, 2, RoundingMode.HALF_UP).stripTrailingZeros()
        return bd.toPlainString()
    }

    /**
     * Formats integer paise in authentic Indian numbering format: ₹1,24,500.00
     */
    fun formatInr(paise: Long, symbol: String = "₹", showDecimalsIfZero: Boolean = true): String {
        val isNegative = paise < 0L
        val absPaise = abs(paise)
        val rupeesPart = absPaise / 100L
        val paisePart = (absPaise % 100L).toInt()
        val formattedInt = formatIndianInteger(rupeesPart)
        val decimalStr = if (!showDecimalsIfZero && paisePart == 0) {
            ""
        } else {
            ".${paisePart.toString().padStart(2, '0')}"
        }
        val sign = if (isNegative) "-" else ""
        return "$sign$symbol$formattedInt$decimalStr"
    }

    private fun formatIndianInteger(value: Long): String {
        val str = value.toString()
        if (str.length <= 3) return str
        val lastThree = str.takeLast(3)
        val rest = str.dropLast(3)
        val sb = StringBuilder()
        var idx = rest.length
        while (idx > 0) {
            val start = (idx - 2).coerceAtLeast(0)
            if (sb.isNotEmpty()) sb.insert(0, ',')
            sb.insert(0, rest.substring(start, idx))
            idx = start
        }
        return "$sb,$lastThree"
    }

    fun formatWeightGrams(ug: Long): String {
        val bd = BigDecimal.valueOf(ug).divide(MILLION, 3, RoundingMode.HALF_UP)
        return "${bd.toPlainString()} g"
    }

    fun formatPercentage(bps: Int): String {
        return "${bpsToPlainPercentString(bps)}%"
    }
}
