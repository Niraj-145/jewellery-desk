package com.example

import com.example.domain.FinancialMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinancialMathTest {

    @Test
    fun `prompt billing formula matches exact paise calculation`() {
        // Example from specification:
        // Net Weight = 0.434 grams -> 434,000 ug
        // Today's Rate = ₹14,000 -> 1,400,000 paise
        // Percentage = 72% -> 7,200 bps
        // Expected: 0.434 * 14,000 * 72 / 100 = ₹4,374.72 -> 437,472 paise
        val weightUg = FinancialMath.parseGramsToUg("0.434")!!
        val ratePaise = FinancialMath.parseRupeesToPaise("14000")!!
        val pctBps = FinancialMath.parsePercentageToBps("72")!!

        val amountPaise = FinancialMath.calculateItemAmountPaise(
            netWeightUg = weightUg,
            ratePerGramPaise = ratePaise,
            percentageBps = pctBps
        )

        assertEquals(437472L, amountPaise)
        assertEquals("₹4,374.72", FinancialMath.formatInr(amountPaise))
    }

    @Test
    fun `indian rupee formatting handles lakhs and negative balances`() {
        assertEquals("₹1,24,500.00", FinancialMath.formatInr(12450000L))
        assertEquals("₹12,34,567.89", FinancialMath.formatInr(123456789L))
        assertEquals("-₹500.50", FinancialMath.formatInr(-50050L))
        assertEquals("₹0.00", FinancialMath.formatInr(0L))
    }

    @Test
    fun `payment status and remaining balance derive accurately`() {
        assertEquals("PAID", FinancialMath.derivePaymentStatus(437000L, 437000L))
        assertEquals("PARTIALLY_PAID", FinancialMath.derivePaymentStatus(437000L, 200000L))
        assertEquals("UNPAID", FinancialMath.derivePaymentStatus(437000L, 0L))
        assertEquals(237000L, FinancialMath.calculateRemainingBalancePaise(437000L, 200000L))
        assertEquals(0L, FinancialMath.calculateRemainingBalancePaise(437000L, 500000L))
    }

    @Test
    fun `invalid inputs return null cleanly`() {
        assertNull(FinancialMath.parseRupeesToPaise("-10", allowNegative = false))
        assertNull(FinancialMath.parseGramsToUg("abc"))
        assertNull(FinancialMath.parsePercentageToBps("1500"))
    }
}
