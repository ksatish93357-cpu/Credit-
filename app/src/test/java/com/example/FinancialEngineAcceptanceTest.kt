package com.example

import com.example.domain.engine.ExpressionEvaluator
import com.example.domain.engine.IndianNumberFormatter
import com.example.domain.engine.InterestEngine
import com.example.domain.engine.LedgerDate
import com.example.domain.model.AllocationMode
import com.example.domain.model.Credit
import com.example.domain.model.CreditEvent
import com.example.domain.model.CreditEventType
import com.example.domain.model.CreditStatus
import com.example.domain.model.InterestMethod
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentType
import com.example.domain.model.Person
import com.example.domain.model.RatePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialEngineAcceptanceTest {

    @Test
    fun `test 1 basic credit 1000 at 8_3333 monthly Jan 13 to Jul 13 2026`() {
        val start = InterestEngine.parseDateOnly("13/01/2026")
        val calc = InterestEngine.parseDateOnly("13/07/2026")
        val res = InterestEngine.calculateInterestForPeriod(
            principalPaise = 100_000L, // ₹1,000
            ratePercent = "8.3333",
            ratePeriod = RatePeriod.MONTHLY,
            interestMethod = InterestMethod.SIMPLE,
            startDate = start,
            endDate = calc
        )
        assertEquals(6, res.duration.completedMonths)
        assertEquals(0, res.duration.remainingDays)
        assertEquals(181, res.duration.totalElapsedDays)
        assertEquals(50_000L, res.interestPaise) // ₹500
        assertEquals(150_000L, InterestEngine.calculateMisradhan(100_000L, res.interestPaise)) // ₹1,500
    }

    @Test
    fun `test 2 principal adjustment misradhan less payment 1000 leaves 500 principal`() {
        val credit = Credit(
            id = "c1",
            personId = "p1",
            creditNumber = "CL-001",
            originalPrincipalPaise = 100_000L,
            currentPrincipalPaise = 100_000L,
            interestRate = "8.3333",
            ratePeriod = RatePeriod.MONTHLY,
            interestMethod = InterestMethod.SIMPLE,
            startDate = "2026-01-13",
            createdAt = 1L,
            updatedAt = 1L
        )
        val createdEvent = CreditEvent(
            id = "e0", personId = "p1", creditId = "c1",
            eventType = CreditEventType.CREDIT_CREATED, effectiveDate = "2026-01-13",
            createdAt = 1L, updatedAt = 1L,
            principalBeforePaise = 0L, interestBeforePaise = 0L, misradhanBeforePaise = 0L,
            amountPaise = 100_000L, principalAfterPaise = 100_000L,
            rateAfter = "8.3333", ratePeriodAfter = RatePeriod.MONTHLY
        )
        val paymentEvent = CreditEvent(
            id = "e1", personId = "p1", creditId = "c1",
            eventType = CreditEventType.CREDIT_PAYMENT, effectiveDate = "2026-07-13",
            createdAt = 2L, updatedAt = 2L,
            principalBeforePaise = 100_000L, interestBeforePaise = 50_000L, misradhanBeforePaise = 150_000L,
            amountPaise = 100_000L, principalAfterPaise = 50_000L,
            rateAfter = "8.3333", ratePeriodAfter = RatePeriod.MONTHLY,
            allocationMode = AllocationMode.PRINCIPAL_ADJUSTMENT,
            paymentMethod = PaymentMethod.CASH, receiptNumber = "RC-20260713-001"
        )

        val snapOnPaymentDay = InterestEngine.calculateCreditBalanceAsOfDate(
            credit, listOf(createdEvent, paymentEvent), LedgerDate(2026, 7, 13)
        )
        assertEquals(50_000L, snapOnPaymentDay.currentPrincipalPaise)
        assertEquals(0L, snapOnPaymentDay.accruedUnpaidInterestPaise)
        assertEquals(50_000L, snapOnPaymentDay.misradhanPaise)
        assertEquals(LedgerDate(2026, 7, 13), snapOnPaymentDay.effectiveStartDate)

        // Next month (13/08/2026) accrues on ₹500 only (approx ₹41.67)
        val snapNextMonth = InterestEngine.calculateCreditBalanceAsOfDate(
            credit, listOf(createdEvent, paymentEvent), LedgerDate(2026, 8, 13)
        )
        assertEquals(50_000L, snapNextMonth.currentPrincipalPaise)
        assertEquals(4_167L, snapNextMonth.accruedUnpaidInterestPaise)
        assertEquals(54_167L, snapNextMonth.misradhanPaise)
    }

    @Test
    fun `test 3 normal payment increases person total paid without touching any credit`() {
        val person = Person("p1", "Rahul", createdAt = 1L, updatedAt = 1L)
        val credit = Credit(
            id = "c1", personId = "p1", creditNumber = "CL-001",
            originalPrincipalPaise = 100_000L, currentPrincipalPaise = 100_000L,
            interestRate = "8.3333", ratePeriod = RatePeriod.MONTHLY,
            startDate = "2026-01-13", createdAt = 1L, updatedAt = 1L
        )
        val normalPay = Payment(
            id = "pay1", personId = "p1", creditId = null,
            paymentType = PaymentType.NORMAL, amountPaise = 50_000L,
            paymentDate = "2026-07-13", paymentMethod = PaymentMethod.UPI,
            receiptNumber = "RC-20260713-002", createdAt = 2L, updatedAt = 2L
        )

        val summary = InterestEngine.calculatePersonSummary(
            person = person,
            credits = listOf(credit),
            allEvents = emptyList(),
            allPayments = listOf(normalPay),
            asOfDate = LedgerDate(2026, 7, 13)
        )
        assertEquals(100_000L, summary.totalPrincipalPaise)
        assertEquals(50_000L, summary.totalAccruedUnpaidInterestPaise)
        assertEquals(150_000L, summary.totalMisradhanPaise)
        assertEquals(50_000L, summary.totalNormalPaymentsPaise)
        assertEquals(50_000L, summary.totalPaidAmountPaise)
    }

    @Test
    fun `test 4 independent credits for one person maintain separate balances`() {
        val person = Person("p1", "Rahul", createdAt = 1L, updatedAt = 1L)
        val c1 = Credit(
            id = "c1", personId = "p1", creditNumber = "CL-001",
            originalPrincipalPaise = 100_000L, currentPrincipalPaise = 100_000L,
            interestRate = "2", ratePeriod = RatePeriod.MONTHLY,
            startDate = "2026-01-01", createdAt = 1L, updatedAt = 1L
        )
        val c2 = Credit(
            id = "c2", personId = "p1", creditNumber = "CL-002",
            originalPrincipalPaise = 500_000L, currentPrincipalPaise = 500_000L,
            interestRate = "3", ratePeriod = RatePeriod.MONTHLY,
            startDate = "2026-02-01", createdAt = 2L, updatedAt = 2L
        )
        val summary = InterestEngine.calculatePersonSummary(
            person = person,
            credits = listOf(c1, c2),
            allEvents = emptyList(),
            allPayments = emptyList(),
            asOfDate = LedgerDate(2026, 4, 1)
        )
        assertEquals(2, summary.creditSnapshots.size)
        val s1 = summary.creditSnapshots.first { it.creditId == "c1" }
        val s2 = summary.creditSnapshots.first { it.creditId == "c2" }
        // c1: 3 months * 2% of 1000 = 60 -> 6000 paise
        assertEquals(6_000L, s1.accruedUnpaidInterestPaise)
        // c2: 2 months * 3% of 5000 = 300 -> 30000 paise
        assertEquals(30_000L, s2.accruedUnpaidInterestPaise)
        assertEquals(636_000L, summary.totalMisradhanPaise)
    }

    @Test
    fun `test 5 past dated payment recalculates event timeline from historical date`() {
        val credit = Credit(
            id = "c1", personId = "p1", creditNumber = "CL-001",
            originalPrincipalPaise = 100_000L, currentPrincipalPaise = 100_000L,
            interestRate = "10", ratePeriod = RatePeriod.MONTHLY,
            startDate = "2026-01-01", createdAt = 1L, updatedAt = 1L
        )
        // Past-dated payment of ₹600 on 2026-02-01 (1 month interest = ₹100, Misradhan = ₹1100, new principal = ₹500)
        val pastPay = CreditEvent(
            id = "p_past", personId = "p1", creditId = "c1",
            eventType = CreditEventType.CREDIT_PAYMENT, effectiveDate = "2026-02-01",
            createdAt = 10L, updatedAt = 10L,
            principalBeforePaise = 0L, interestBeforePaise = 0L, misradhanBeforePaise = 0L,
            amountPaise = 60_000L, principalAfterPaise = 0L,
            rateAfter = "10", ratePeriodAfter = RatePeriod.MONTHLY,
            allocationMode = AllocationMode.PRINCIPAL_ADJUSTMENT
        )
        // Evaluate on 2026-03-01: 1 month on ₹500 at 10% = ₹50 -> Misradhan ₹550
        val snap = InterestEngine.calculateCreditBalanceAsOfDate(credit, listOf(pastPay), LedgerDate(2026, 3, 1))
        assertEquals(50_000L, snap.currentPrincipalPaise)
        assertEquals(5_000L, snap.accruedUnpaidInterestPaise)
        assertEquals(55_000L, snap.misradhanPaise)
    }

    @Test
    fun `test 6 rate update retains old rate for earlier periods`() {
        val credit = Credit(
            id = "c1", personId = "p1", creditNumber = "CL-001",
            originalPrincipalPaise = 100_000L, currentPrincipalPaise = 100_000L,
            interestRate = "2", ratePeriod = RatePeriod.MONTHLY,
            startDate = "2026-01-01", createdAt = 1L, updatedAt = 1L
        )
        // Rate updated from 2% to 5% on 2026-03-01 (after 2 months at 2% = ₹40 preserved)
        val rateUpdate = CreditEvent(
            id = "adj1", personId = "p1", creditId = "c1",
            eventType = CreditEventType.INTEREST_RATE_UPDATE, effectiveDate = "2026-03-01",
            createdAt = 5L, updatedAt = 5L,
            principalBeforePaise = 100_000L, interestBeforePaise = 4_000L, misradhanBeforePaise = 104_000L,
            amountPaise = 0L, principalAfterPaise = 100_000L,
            rateAfter = "5", ratePeriodAfter = RatePeriod.MONTHLY
        )
        // As of 2026-04-01: 2 months at 2% (₹40) + 1 month at 5% (₹50) = ₹90 interest!
        val snap = InterestEngine.calculateCreditBalanceAsOfDate(credit, listOf(rateUpdate), LedgerDate(2026, 4, 1))
        assertEquals(9_000L, snap.accruedUnpaidInterestPaise)
        assertEquals(109_000L, snap.misradhanPaise)
    }

    @Test
    fun `test 7 calendar boundaries month ends and leap years`() {
        assertEquals(29, InterestEngine.calculateActualDaysInMonth(2024, 2))
        assertEquals(28, InterestEngine.calculateActualDaysInMonth(2025, 2))
        assertEquals(366, InterestEngine.calculateActualDaysInYear(2024))
        assertEquals(365, InterestEngine.calculateActualDaysInYear(2025))

        // Month-end anniversary: Jan 31 -> Feb 28 (1 month), Jan 31 -> Mar 31 (2 months)
        val d1 = InterestEngine.calculateCalendarDuration(LedgerDate(2026, 1, 31), LedgerDate(2026, 2, 28))
        assertEquals(1, d1.completedMonths)
        assertEquals(0, d1.remainingDays)

        val d2 = InterestEngine.calculateCalendarDuration(LedgerDate(2026, 1, 31), LedgerDate(2026, 3, 31))
        assertEquals(2, d2.completedMonths)
        assertEquals(0, d2.remainingDays)

        // Leap year Feb 29 2024 -> Feb 28 2025 = 12 completed months (1 year)
        val dLeap = InterestEngine.calculateCalendarDuration(LedgerDate(2024, 2, 29), LedgerDate(2025, 2, 28))
        assertEquals(12, dLeap.completedMonths)
        assertEquals(1, dLeap.completedYears)
        assertEquals(0, dLeap.remainingDays)
    }

    @Test
    fun `test 8 full settlement and overpayment records advance without negative principal`() {
        val alloc = InterestEngine.applyMisradhanLessPayment(
            principalBeforePaise = 100_000L,
            accruedInterestBeforePaise = 50_000L,
            paymentPaise = 175_000L
        )
        assertEquals(0L, alloc.principalAfterPaise)
        assertEquals(25_000L, alloc.advancePaise)
        assertTrue(alloc.isSettled)

        val allocInterestFirst = InterestEngine.applyInterestFirstPayment(
            principalBeforePaise = 100_000L,
            accruedInterestBeforePaise = 50_000L,
            paymentPaise = 80_000L
        )
        assertEquals(0L, allocInterestFirst.unpaidInterestAfterPaise)
        assertEquals(70_000L, allocInterestFirst.principalAfterPaise)
        assertEquals(0L, allocInterestFirst.advancePaise)
    }

    @Test
    fun `indian number formatter and normal calculator evaluator`() {
        assertEquals("₹1,00,000.00", IndianNumberFormatter.formatCurrency(10_000_000L, decimals = 2))
        assertEquals("₹1,000", IndianNumberFormatter.formatCompactCurrency(100_000L))
        assertEquals("10", ExpressionEvaluator.evaluate("2 + 2 + 2 + 2 + 2").getOrThrow())
        assertEquals("115", ExpressionEvaluator.evaluate("(1 + 15%) * 100").getOrThrow())
        assertEquals("110", ExpressionEvaluator.evaluate("100 + (100 * 10%)").getOrThrow())
    }

    @Test
    fun `future start date credit evaluates cleanly on start date and ignores reversed adjustments`() {
        val credit = Credit(
            id = "c_future",
            personId = "p1",
            creditNumber = "CL-999",
            originalPrincipalPaise = 200_000L,
            currentPrincipalPaise = 200_000L,
            interestRate = "3",
            ratePeriod = RatePeriod.MONTHLY,
            startDate = "2099-01-01",
            createdAt = 1L,
            updatedAt = 1L
        )
        val reversedAdj = CreditEvent(
            id = "adj_rev",
            personId = "p1",
            creditId = "c_future",
            eventType = CreditEventType.PRINCIPAL_ADJUSTMENT,
            effectiveDate = "2099-02-01",
            createdAt = 2L,
            updatedAt = 2L,
            principalBeforePaise = 200_000L,
            interestBeforePaise = 6_000L,
            misradhanBeforePaise = 206_000L,
            amountPaise = 300_000L,
            principalAfterPaise = 300_000L,
            rateAfter = "5",
            ratePeriodAfter = RatePeriod.MONTHLY,
            isReversed = true
        )
        val snapStart = InterestEngine.calculateCreditBalanceAsOfDate(credit, listOf(reversedAdj), LedgerDate(2099, 1, 1))
        assertEquals(200_000L, snapStart.currentPrincipalPaise)
        assertEquals(0L, snapStart.accruedUnpaidInterestPaise)

        val snapAfter = InterestEngine.calculateCreditBalanceAsOfDate(credit, listOf(reversedAdj), LedgerDate(2099, 3, 1))
        assertEquals(200_000L, snapAfter.currentPrincipalPaise)
        assertEquals("3", snapAfter.activeRate)
        assertEquals(12_000L, snapAfter.accruedUnpaidInterestPaise)
    }
}
