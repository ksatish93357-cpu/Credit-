package com.example.domain.engine

import com.example.domain.model.AllocationMode
import com.example.domain.model.Credit
import com.example.domain.model.CreditEvent
import com.example.domain.model.CreditEventType
import com.example.domain.model.CreditStatus
import com.example.domain.model.InterestMethod
import com.example.domain.model.Payment
import com.example.domain.model.PaymentType
import com.example.domain.model.Person
import com.example.domain.model.RatePeriod
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class CalendarDuration(
    val completedMonths: Int,
    val remainingDays: Int,
    val totalElapsedDays: Int,
    val completedYears: Int,
    val remainingDaysAfterYears: Int,
    val lastMonthAnniversary: LedgerDate,
    val lastYearAnniversary: LedgerDate
)

data class PeriodInterestResult(
    val interestPaise: Long,
    val exactInterestPaise: BigDecimal,
    val duration: CalendarDuration
)

data class PaymentAllocationResult(
    val principalBeforePaise: Long,
    val accruedInterestBeforePaise: Long,
    val misradhanBeforePaise: Long,
    val paymentAmountPaise: Long,
    val principalAfterPaise: Long,
    val unpaidInterestAfterPaise: Long,
    val advancePaise: Long,
    val isSettled: Boolean,
    val allocationMode: AllocationMode
)

data class AdjustmentPreviewResult(
    val effectiveDate: LedgerDate,
    val oldPrincipalPaise: Long,
    val accruedInterestToDatePaise: Long,
    val oldMisradhanPaise: Long,
    val newPrincipalPaise: Long,
    val oldRate: String,
    val oldRatePeriod: RatePeriod,
    val newRate: String,
    val newRatePeriod: RatePeriod
)

data class ReplayedEventState(
    val event: CreditEvent,
    val principalBeforePaise: Long,
    val interestBeforePaise: Long,
    val misradhanBeforePaise: Long,
    val principalAfterPaise: Long,
    val interestAfterPaise: Long,
    val advancePaise: Long
)

data class CreditBalanceSnapshot(
    val creditId: String,
    val creditNumber: String,
    val personId: String,
    val originalPrincipalPaise: Long,
    val currentPrincipalPaise: Long,
    val accruedUnpaidInterestPaise: Long,
    val misradhanPaise: Long,
    val totalPaidOnCreditPaise: Long,
    val advancePaise: Long,
    val activeRate: String,
    val activeRatePeriod: RatePeriod,
    val interestMethod: InterestMethod,
    val effectiveStartDate: LedgerDate,
    val asOfDate: LedgerDate,
    val durationSinceEffectiveStart: CalendarDuration,
    val status: CreditStatus,
    val nextMonthInterestEstimatePaise: Long,
    val replayedEvents: List<ReplayedEventState>
)

data class PersonFinancialSummary(
    val person: Person,
    val totalPrincipalPaise: Long,
    val totalAccruedUnpaidInterestPaise: Long,
    val totalMisradhanPaise: Long,
    val totalCreditPaymentsPaise: Long,
    val totalNormalPaymentsPaise: Long,
    val totalPaidAmountPaise: Long,
    val totalAdvancePaise: Long,
    val activeCreditsCount: Int,
    val closedCreditsCount: Int,
    val archivedCreditsCount: Int,
    val creditSnapshots: List<CreditBalanceSnapshot>
)

data class DashboardFinancialSummary(
    val totalPrincipalPaise: Long,
    val totalAccruedUnpaidInterestPaise: Long,
    val totalMisradhanPaise: Long,
    val totalPaidAmountPaise: Long,
    val profilesCount: Int,
    val activeCreditsCount: Int,
    val closedCreditsCount: Int,
    val totalCreditRecordsCount: Int,
    val todaysInterestAccrualPaise: Long,
    val totalOriginallyAdvancedPaise: Long,
    val periodCreditPaymentsPaise: Long,
    val periodNormalPaymentsPaise: Long,
    val periodOriginallyAdvancedPaise: Long,
    val overdueCredits: List<Pair<Credit, CreditBalanceSnapshot>>
)

object InterestEngine {

    private val MC = MathContext(24, RoundingMode.HALF_UP)

    fun parseDateOnly(input: String): LedgerDate {
        return LedgerDate.parseOrNull(input)
            ?: throw IllegalArgumentException("Invalid date format '$input'. Expected YYYY-MM-DD or DD/MM/YYYY.")
    }

    fun calculateActualDaysInMonth(year: Int, month: Int): Int {
        return LedgerDate.actualDaysInMonth(year, month)
    }

    fun calculateActualDaysInYear(year: Int): Int {
        return LedgerDate.actualDaysInYear(year)
    }

    /**
     * Deterministic month-anniversary calendar duration calculation.
     * If startDate.day does not exist in a later month (e.g., 31st in Feb/Apr),
     * that month's final day becomes the anniversary.
     */
    fun calculateCalendarDuration(startDate: LedgerDate, endDate: LedgerDate): CalendarDuration {
        if (endDate < startDate) {
            throw IllegalArgumentException(
                "Calculation date (${endDate.toDisplayString()}) cannot be earlier than effective start date (${startDate.toDisplayString()})."
            )
        }
        val totalDays = startDate.daysUntil(endDate).toInt()
        if (totalDays == 0) {
            return CalendarDuration(
                completedMonths = 0,
                remainingDays = 0,
                totalElapsedDays = 0,
                completedYears = 0,
                remainingDaysAfterYears = 0,
                lastMonthAnniversary = startDate,
                lastYearAnniversary = startDate
            )
        }

        // Approximate months then step cleanly using clamped anchor day
        var months = (endDate.year - startDate.year) * 12 + (endDate.month - startDate.month)
        if (months < 0) months = 0
        var monthAnniversary = startDate.plusMonthsClamped(months, startDate.day)
        while (monthAnniversary > endDate && months > 0) {
            months--
            monthAnniversary = startDate.plusMonthsClamped(months, startDate.day)
        }
        while (true) {
            val nextAnniversary = startDate.plusMonthsClamped(months + 1, startDate.day)
            if (nextAnniversary <= endDate) {
                months++
                monthAnniversary = nextAnniversary
            } else {
                break
            }
        }
        val remainingDaysAfterMonths = monthAnniversary.daysUntil(endDate).toInt()

        var years = endDate.year - startDate.year
        if (years < 0) years = 0
        var yearAnniversary = startDate.plusMonthsClamped(years * 12, startDate.day)
        while (yearAnniversary > endDate && years > 0) {
            years--
            yearAnniversary = startDate.plusMonthsClamped(years * 12, startDate.day)
        }
        while (true) {
            val nextYearAnniv = startDate.plusMonthsClamped((years + 1) * 12, startDate.day)
            if (nextYearAnniv <= endDate) {
                years++
                yearAnniversary = nextYearAnniv
            } else {
                break
            }
        }
        val remainingDaysAfterYears = yearAnniversary.daysUntil(endDate).toInt()

        return CalendarDuration(
            completedMonths = months,
            remainingDays = remainingDaysAfterMonths,
            totalElapsedDays = totalDays,
            completedYears = years,
            remainingDaysAfterYears = remainingDaysAfterYears,
            lastMonthAnniversary = monthAnniversary,
            lastYearAnniversary = yearAnniversary
        )
    }

    /**
     * Computes exact fractional months between [fromDate] and [toDate] by splitting across
     * actual calendar month lengths (28/29/30/31) so every day contributes 1 / daysInThatMonth.
     */
    private fun calculateFractionalMonths(fromDate: LedgerDate, toDate: LedgerDate): BigDecimal {
        if (toDate <= fromDate) return BigDecimal.ZERO
        var cursor = fromDate
        var fraction = BigDecimal.ZERO
        while (cursor < toDate) {
            val daysInMonth = calculateActualDaysInMonth(cursor.year, cursor.month)
            val monthEnd = LedgerDate(cursor.year, cursor.month, daysInMonth)
            val segmentEnd = if (toDate <= monthEnd) toDate else monthEnd.plusDays(1)
            val daysInSegment = cursor.daysUntil(segmentEnd)
            fraction = fraction.add(
                BigDecimal(daysInSegment).divide(BigDecimal(daysInMonth), MC),
                MC
            )
            cursor = segmentEnd
        }
        return fraction
    }

    /**
     * Computes exact fractional years between [fromDate] and [toDate] using 365 / 366 days per year.
     */
    private fun calculateFractionalYears(fromDate: LedgerDate, toDate: LedgerDate): BigDecimal {
        if (toDate <= fromDate) return BigDecimal.ZERO
        var cursor = fromDate
        var fraction = BigDecimal.ZERO
        while (cursor < toDate) {
            val daysInYear = calculateActualDaysInYear(cursor.year)
            val yearEnd = LedgerDate(cursor.year, 12, 31)
            val segmentEnd = if (toDate <= yearEnd) toDate else yearEnd.plusDays(1)
            val daysInSegment = cursor.daysUntil(segmentEnd)
            fraction = fraction.add(
                BigDecimal(daysInSegment).divide(BigDecimal(daysInYear), MC),
                MC
            )
            cursor = segmentEnd
        }
        return fraction
    }

    fun calculateInterestForPeriod(
        principalPaise: Long,
        ratePercent: String,
        ratePeriod: RatePeriod,
        interestMethod: InterestMethod,
        startDate: LedgerDate,
        endDate: LedgerDate
    ): PeriodInterestResult {
        val duration = calculateCalendarDuration(startDate, endDate)
        if (principalPaise <= 0L || startDate == endDate) {
            return PeriodInterestResult(0L, BigDecimal.ZERO, duration)
        }
        val rateBd = parseRateOrZero(ratePercent)
        if (rateBd <= BigDecimal.ZERO) {
            return PeriodInterestResult(0L, BigDecimal.ZERO, duration)
        }

        val principalBd = BigDecimal(principalPaise)
        val hundred = BigDecimal(100)
        val rateRatio = rateBd.divide(hundred, MC)

        val exactInterest = when (ratePeriod) {
            RatePeriod.MONTHLY -> {
                val fractional = calculateFractionalMonths(duration.lastMonthAnniversary, endDate)
                if (interestMethod == InterestMethod.SIMPLE) {
                    val totalPeriods = BigDecimal(duration.completedMonths).add(fractional, MC)
                    principalBd.multiply(rateRatio, MC).multiply(totalPeriods, MC)
                } else {
                    var balance = principalBd
                    val onePlusR = BigDecimal.ONE.add(rateRatio, MC)
                    repeat(duration.completedMonths) {
                        balance = balance.multiply(onePlusR, MC)
                    }
                    val partial = balance.multiply(rateRatio, MC).multiply(fractional, MC)
                    balance.subtract(principalBd, MC).add(partial, MC)
                }
            }
            RatePeriod.YEARLY -> {
                val fractional = calculateFractionalYears(duration.lastYearAnniversary, endDate)
                if (interestMethod == InterestMethod.SIMPLE) {
                    val totalPeriods = BigDecimal(duration.completedYears).add(fractional, MC)
                    principalBd.multiply(rateRatio, MC).multiply(totalPeriods, MC)
                } else {
                    var balance = principalBd
                    val onePlusR = BigDecimal.ONE.add(rateRatio, MC)
                    repeat(duration.completedYears) {
                        balance = balance.multiply(onePlusR, MC)
                    }
                    val partial = balance.multiply(rateRatio, MC).multiply(fractional, MC)
                    balance.subtract(principalBd, MC).add(partial, MC)
                }
            }
        }

        val clamped = exactInterest
            .max(BigDecimal.ZERO)
            .min(BigDecimal(Long.MAX_VALUE))
        val roundedPaise = clamped.setScale(0, RoundingMode.HALF_UP).longValueExact()
        return PeriodInterestResult(
            interestPaise = maxOf(0L, roundedPaise),
            exactInterestPaise = exactInterest.max(BigDecimal.ZERO),
            duration = duration
        )
    }

    fun calculateMisradhan(principalPaise: Long, accruedInterestPaise: Long): Long {
        return maxOf(0L, principalPaise) + maxOf(0L, accruedInterestPaise)
    }

    /**
     * Mode A: Interest First — pays accrued interest first, remaining amount reduces principal.
     * Unpaid interest remains separate without capitalization.
     */
    fun applyInterestFirstPayment(
        principalBeforePaise: Long,
        accruedInterestBeforePaise: Long,
        paymentPaise: Long
    ): PaymentAllocationResult {
        require(paymentPaise > 0L) { "Payment amount must be positive." }
        val misradhanBefore = calculateMisradhan(principalBeforePaise, accruedInterestBeforePaise)
        val interestPaid = minOf(accruedInterestBeforePaise, paymentPaise)
        val unpaidInterestAfter = maxOf(0L, accruedInterestBeforePaise - interestPaid)
        val remainingPayment = paymentPaise - interestPaid
        val principalPaid = minOf(principalBeforePaise, remainingPayment)
        val principalAfter = maxOf(0L, principalBeforePaise - principalPaid)
        val advance = maxOf(0L, remainingPayment - principalPaid)

        return PaymentAllocationResult(
            principalBeforePaise = principalBeforePaise,
            accruedInterestBeforePaise = accruedInterestBeforePaise,
            misradhanBeforePaise = misradhanBefore,
            paymentAmountPaise = paymentPaise,
            principalAfterPaise = principalAfter,
            unpaidInterestAfterPaise = unpaidInterestAfter,
            advancePaise = advance,
            isSettled = principalAfter == 0L && unpaidInterestAfter == 0L,
            allocationMode = AllocationMode.INTEREST_FIRST
        )
    }

    /**
     * Mode B: Principal Adjustment / Misradhan Less Payment (User's preferred method).
     * Old Misradhan = old principal + accrued interest.
     * New principal = max(0, old Misradhan - payment).
     * Unpaid interest is capitalized into the new principal balance and next interest period starts at payment date.
     */
    fun applyMisradhanLessPayment(
        principalBeforePaise: Long,
        accruedInterestBeforePaise: Long,
        paymentPaise: Long
    ): PaymentAllocationResult {
        require(paymentPaise > 0L) { "Payment amount must be positive." }
        val misradhanBefore = calculateMisradhan(principalBeforePaise, accruedInterestBeforePaise)
        val principalAfter = maxOf(0L, misradhanBefore - paymentPaise)
        val advance = maxOf(0L, paymentPaise - misradhanBefore)

        return PaymentAllocationResult(
            principalBeforePaise = principalBeforePaise,
            accruedInterestBeforePaise = accruedInterestBeforePaise,
            misradhanBeforePaise = misradhanBefore,
            paymentAmountPaise = paymentPaise,
            principalAfterPaise = principalAfter,
            unpaidInterestAfterPaise = 0L,
            advancePaise = advance,
            isSettled = principalAfter == 0L,
            allocationMode = AllocationMode.PRINCIPAL_ADJUSTMENT
        )
    }

    fun applyPrincipalAndRateAdjustment(
        credit: Credit,
        events: List<CreditEvent>,
        effectiveDate: LedgerDate,
        newPrincipalPaise: Long,
        newRate: String,
        newRatePeriod: RatePeriod
    ): AdjustmentPreviewResult {
        val currentSnapshot = calculateCreditBalanceAsOfDate(credit, events, effectiveDate)
        return AdjustmentPreviewResult(
            effectiveDate = effectiveDate,
            oldPrincipalPaise = currentSnapshot.currentPrincipalPaise,
            accruedInterestToDatePaise = currentSnapshot.accruedUnpaidInterestPaise,
            oldMisradhanPaise = currentSnapshot.misradhanPaise,
            newPrincipalPaise = maxOf(0L, newPrincipalPaise),
            oldRate = currentSnapshot.activeRate,
            oldRatePeriod = currentSnapshot.activeRatePeriod,
            newRate = newRate.trim(),
            newRatePeriod = newRatePeriod
        )
    }

    fun calculateAccruedInterestAsOfDate(
        credit: Credit,
        events: List<CreditEvent>,
        asOfDate: LedgerDate
    ): Long {
        return calculateCreditBalanceAsOfDate(credit, events, asOfDate).accruedUnpaidInterestPaise
    }

    /**
     * Replays the credit's ordered event ledger piece-by-piece up to [asOfDate].
     * Never calculates past periods using future principals or rates.
     */
    fun calculateCreditBalanceAsOfDate(
        credit: Credit,
        events: List<CreditEvent>,
        asOfDate: LedgerDate
    ): CreditBalanceSnapshot {
        val creditStart = parseDateOnly(credit.startDate)
        if (asOfDate < creditStart) {
            throw IllegalArgumentException(
                "Calculation date (${asOfDate.toDisplayString()}) cannot be earlier than credit start date (${creditStart.toDisplayString()})."
            )
        }

        // Exclude reversed events and reversal markers themselves from forward basis; keep ordered by effectiveDate, createdAt
        val reversedIds = events.mapNotNull { it.reversesEventId }.toSet()
        val orderedEvents = events
            .filter {
                it.creditId == credit.id &&
                    !it.isReversed &&
                    it.id !in reversedIds &&
                    it.eventType != CreditEventType.CORRECTION_REVERSAL &&
                    it.eventType != CreditEventType.NORMAL_PAYMENT
            }
            .filter { parseDateOnly(it.effectiveDate) <= asOfDate }
            .sortedWith(compareBy({ parseDateOnly(it.effectiveDate) }, { it.createdAt }))

        var runningPrincipal = credit.originalPrincipalPaise
        var carriedUnpaidInterest = 0L
        var runningRate = credit.interestRate
        var runningRatePeriod = credit.ratePeriod
        var runningMethod = credit.interestMethod
        var segmentStart = creditStart
        var totalPaid = 0L
        var totalAdvance = 0L
        var statusAsOfDate = if (credit.status == CreditStatus.ARCHIVED) CreditStatus.ARCHIVED else CreditStatus.ACTIVE
        val replayedList = mutableListOf<ReplayedEventState>()

        for (event in orderedEvents) {
            if (event.eventType == CreditEventType.CREDIT_CREATED) {
                if (event.rateAfter.isNotBlank()) {
                    runningRate = event.rateAfter
                    runningRatePeriod = event.ratePeriodAfter
                }
                if (event.principalAfterPaise > 0L) {
                    runningPrincipal = event.principalAfterPaise
                }
                replayedList.add(
                    ReplayedEventState(
                        event = event,
                        principalBeforePaise = 0L,
                        interestBeforePaise = 0L,
                        misradhanBeforePaise = 0L,
                        principalAfterPaise = runningPrincipal,
                        interestAfterPaise = 0L,
                        advancePaise = 0L
                    )
                )
                continue
            }

            val eventDate = parseDateOnly(event.effectiveDate)
            val periodInterest = if (statusAsOfDate == CreditStatus.ACTIVE && runningPrincipal > 0L) {
                calculateInterestForPeriod(
                    principalPaise = runningPrincipal,
                    ratePercent = runningRate,
                    ratePeriod = runningRatePeriod,
                    interestMethod = runningMethod,
                    startDate = segmentStart,
                    endDate = eventDate
                ).interestPaise
            } else {
                0L
            }

            val interestBefore = carriedUnpaidInterest + periodInterest
            val principalBefore = runningPrincipal
            val misradhanBefore = calculateMisradhan(principalBefore, interestBefore)

            when (event.eventType) {
                CreditEventType.CREDIT_PAYMENT -> {
                    val mode = event.allocationMode ?: AllocationMode.PRINCIPAL_ADJUSTMENT
                    val alloc = if (mode == AllocationMode.INTEREST_FIRST) {
                        applyInterestFirstPayment(principalBefore, interestBefore, event.amountPaise)
                    } else {
                        applyMisradhanLessPayment(principalBefore, interestBefore, event.amountPaise)
                    }
                    runningPrincipal = alloc.principalAfterPaise
                    carriedUnpaidInterest = alloc.unpaidInterestAfterPaise
                    totalPaid += event.amountPaise
                    totalAdvance += alloc.advancePaise
                    segmentStart = eventDate
                    if (alloc.isSettled && statusAsOfDate != CreditStatus.ARCHIVED) {
                        statusAsOfDate = CreditStatus.CLOSED
                    }
                    replayedList.add(
                        ReplayedEventState(
                            event = event,
                            principalBeforePaise = principalBefore,
                            interestBeforePaise = interestBefore,
                            misradhanBeforePaise = misradhanBefore,
                            principalAfterPaise = runningPrincipal,
                            interestAfterPaise = carriedUnpaidInterest,
                            advancePaise = alloc.advancePaise
                        )
                    )
                }
                CreditEventType.PRINCIPAL_ADJUSTMENT,
                CreditEventType.INTEREST_RATE_UPDATE -> {
                    // Preserve interest accrued up to effectiveDate under old terms
                    carriedUnpaidInterest = interestBefore
                    runningPrincipal = event.principalAfterPaise
                    runningRate = event.rateAfter
                    runningRatePeriod = event.ratePeriodAfter
                    segmentStart = eventDate
                    replayedList.add(
                        ReplayedEventState(
                            event = event,
                            principalBeforePaise = principalBefore,
                            interestBeforePaise = interestBefore,
                            misradhanBeforePaise = misradhanBefore,
                            principalAfterPaise = runningPrincipal,
                            interestAfterPaise = carriedUnpaidInterest,
                            advancePaise = 0L
                        )
                    )
                }
                CreditEventType.CREDIT_CLOSED -> {
                    carriedUnpaidInterest = interestBefore
                    segmentStart = eventDate
                    if (statusAsOfDate != CreditStatus.ARCHIVED) {
                        statusAsOfDate = CreditStatus.CLOSED
                    }
                    replayedList.add(
                        ReplayedEventState(
                            event = event,
                            principalBeforePaise = principalBefore,
                            interestBeforePaise = interestBefore,
                            misradhanBeforePaise = misradhanBefore,
                            principalAfterPaise = runningPrincipal,
                            interestAfterPaise = carriedUnpaidInterest,
                            advancePaise = 0L
                        )
                    )
                }
                CreditEventType.CREDIT_REOPENED -> {
                    carriedUnpaidInterest = interestBefore
                    segmentStart = eventDate
                    if (statusAsOfDate != CreditStatus.ARCHIVED) {
                        statusAsOfDate = CreditStatus.ACTIVE
                    }
                    replayedList.add(
                        ReplayedEventState(
                            event = event,
                            principalBeforePaise = principalBefore,
                            interestBeforePaise = interestBefore,
                            misradhanBeforePaise = misradhanBefore,
                            principalAfterPaise = runningPrincipal,
                            interestAfterPaise = carriedUnpaidInterest,
                            advancePaise = 0L
                        )
                    )
                }
                else -> Unit
            }
        }

        // Accrue from last event effective date to asOfDate
        val finalDuration = calculateCalendarDuration(segmentStart, asOfDate)
        val tailInterest = if (statusAsOfDate == CreditStatus.ACTIVE && runningPrincipal > 0L) {
            calculateInterestForPeriod(
                principalPaise = runningPrincipal,
                ratePercent = runningRate,
                ratePeriod = runningRatePeriod,
                interestMethod = runningMethod,
                startDate = segmentStart,
                endDate = asOfDate
            ).interestPaise
        } else {
            0L
        }

        val totalAccruedUnpaidInterest = if (statusAsOfDate == CreditStatus.CLOSED && runningPrincipal == 0L) {
            0L
        } else {
            carriedUnpaidInterest + tailInterest
        }
        val misradhan = calculateMisradhan(runningPrincipal, totalAccruedUnpaidInterest)

        // Next 1-month interest estimate on current principal
        val nextMonthEstimate = if (statusAsOfDate == CreditStatus.ACTIVE && runningPrincipal > 0L) {
            val oneMonthEnd = asOfDate.plusMonthsClamped(1, asOfDate.day)
            calculateInterestForPeriod(
                principalPaise = runningPrincipal,
                ratePercent = runningRate,
                ratePeriod = runningRatePeriod,
                interestMethod = runningMethod,
                startDate = asOfDate,
                endDate = oneMonthEnd
            ).interestPaise
        } else {
            0L
        }

        val effectiveStatus = if (credit.status == CreditStatus.ARCHIVED) CreditStatus.ARCHIVED else statusAsOfDate

        return CreditBalanceSnapshot(
            creditId = credit.id,
            creditNumber = credit.creditNumber,
            personId = credit.personId,
            originalPrincipalPaise = credit.originalPrincipalPaise,
            currentPrincipalPaise = runningPrincipal,
            accruedUnpaidInterestPaise = totalAccruedUnpaidInterest,
            misradhanPaise = misradhan,
            totalPaidOnCreditPaise = totalPaid,
            advancePaise = totalAdvance,
            activeRate = runningRate,
            activeRatePeriod = runningRatePeriod,
            interestMethod = runningMethod,
            effectiveStartDate = segmentStart,
            asOfDate = asOfDate,
            durationSinceEffectiveStart = finalDuration,
            status = effectiveStatus,
            nextMonthInterestEstimatePaise = nextMonthEstimate,
            replayedEvents = replayedList
        )
    }

    fun calculatePersonSummary(
        person: Person,
        credits: List<Credit>,
        allEvents: List<CreditEvent>,
        allPayments: List<Payment>,
        asOfDate: LedgerDate
    ): PersonFinancialSummary {
        val personCredits = credits.filter { it.personId == person.id }
        val snapshots = personCredits.mapNotNull { credit ->
            val start = parseDateOnly(credit.startDate)
            if (asOfDate < start) null
            else calculateCreditBalanceAsOfDate(credit, allEvents.filter { it.creditId == credit.id }, asOfDate)
        }

        val activeSnapshots = snapshots.filter { it.status == CreditStatus.ACTIVE }
        val closedCount = snapshots.count { it.status == CreditStatus.CLOSED }
        val archivedCount = snapshots.count { it.status == CreditStatus.ARCHIVED }

        val validPayments = allPayments.filter { it.personId == person.id && it.status == "COMPLETED" }
        val creditPaymentsTotal = validPayments.filter { it.paymentType == PaymentType.CREDIT }.sumOf { it.amountPaise }
        val normalPaymentsTotal = validPayments.filter { it.paymentType == PaymentType.NORMAL }.sumOf { it.amountPaise }

        return PersonFinancialSummary(
            person = person,
            totalPrincipalPaise = activeSnapshots.sumOf { it.currentPrincipalPaise },
            totalAccruedUnpaidInterestPaise = activeSnapshots.sumOf { it.accruedUnpaidInterestPaise },
            totalMisradhanPaise = activeSnapshots.sumOf { it.misradhanPaise },
            totalCreditPaymentsPaise = creditPaymentsTotal,
            totalNormalPaymentsPaise = normalPaymentsTotal,
            totalPaidAmountPaise = creditPaymentsTotal + normalPaymentsTotal,
            totalAdvancePaise = snapshots.sumOf { it.advancePaise },
            activeCreditsCount = activeSnapshots.size,
            closedCreditsCount = closedCount,
            archivedCreditsCount = archivedCount,
            creditSnapshots = snapshots
        )
    }

    fun calculateDashboardSummary(
        persons: List<Person>,
        credits: List<Credit>,
        allEvents: List<CreditEvent>,
        allPayments: List<Payment>,
        asOfDate: LedgerDate,
        filterStartDate: LedgerDate? = null,
        filterEndDate: LedgerDate? = null
    ): DashboardFinancialSummary {
        val activePersons = persons.filter { !it.isArchived }
        val activePersonIds = activePersons.map { it.id }.toSet()
        val activeCreditsList = credits.filter { it.personId in activePersonIds && it.status != CreditStatus.ARCHIVED }

        val snapshots = activeCreditsList.mapNotNull { credit ->
            val start = parseDateOnly(credit.startDate)
            if (asOfDate < start) null
            else credit to calculateCreditBalanceAsOfDate(credit, allEvents.filter { it.creditId == credit.id }, asOfDate)
        }

        val activeSnapshots = snapshots.filter { it.second.status == CreditStatus.ACTIVE }
        val closedSnapshots = snapshots.filter { it.second.status == CreditStatus.CLOSED }

        val completedPayments = allPayments.filter { it.status == "COMPLETED" }
        val totalPaidAll = completedPayments.sumOf { it.amountPaise }

        val yesterday = asOfDate.plusDays(-1)
        val todaysInterest = activeSnapshots.sumOf { (credit, snap) ->
            val start = parseDateOnly(credit.startDate)
            if (yesterday < start) {
                snap.accruedUnpaidInterestPaise
            } else {
                val yesterdaySnap = calculateCreditBalanceAsOfDate(
                    credit,
                    allEvents.filter { it.creditId == credit.id },
                    yesterday
                )
                maxOf(0L, snap.accruedUnpaidInterestPaise - yesterdaySnap.accruedUnpaidInterestPaise)
            }
        }

        val inRange: (String) -> Boolean = { dateStr ->
            val d = LedgerDate.parseOrNull(dateStr)
            if (d == null) false
            else (filterStartDate == null || d >= filterStartDate) && (filterEndDate == null || d <= filterEndDate)
        }

        val periodCreditPayments = completedPayments
            .filter { it.paymentType == PaymentType.CREDIT && inRange(it.paymentDate) }
            .sumOf { it.amountPaise }

        val periodNormalPayments = completedPayments
            .filter { it.paymentType == PaymentType.NORMAL && inRange(it.paymentDate) }
            .sumOf { it.amountPaise }

        val periodAdvanced = activeCreditsList
            .filter { inRange(it.startDate) }
            .sumOf { it.originalPrincipalPaise }

        val overdue = activeSnapshots.filter { (credit, snap) ->
            val due = credit.dueDate?.let { LedgerDate.parseOrNull(it) }
            due != null && due < asOfDate && snap.misradhanPaise > 0L
        }

        return DashboardFinancialSummary(
            totalPrincipalPaise = activeSnapshots.sumOf { it.second.currentPrincipalPaise },
            totalAccruedUnpaidInterestPaise = activeSnapshots.sumOf { it.second.accruedUnpaidInterestPaise },
            totalMisradhanPaise = activeSnapshots.sumOf { it.second.misradhanPaise },
            totalPaidAmountPaise = totalPaidAll,
            profilesCount = activePersons.size,
            activeCreditsCount = activeSnapshots.size,
            closedCreditsCount = closedSnapshots.size,
            totalCreditRecordsCount = activeCreditsList.size,
            todaysInterestAccrualPaise = todaysInterest,
            totalOriginallyAdvancedPaise = activeCreditsList.sumOf { it.originalPrincipalPaise },
            periodCreditPaymentsPaise = periodCreditPayments,
            periodNormalPaymentsPaise = periodNormalPayments,
            periodOriginallyAdvancedPaise = periodAdvanced,
            overdueCredits = overdue
        )
    }

    fun parseRateOrZero(rate: String): BigDecimal {
        return try {
            BigDecimal(rate.trim().ifEmpty { "0" })
        } catch (_: Exception) {
            BigDecimal.ZERO
        }
    }
}
