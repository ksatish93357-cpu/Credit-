package com.example.domain.engine

import com.example.domain.model.AllocationMode
import com.example.domain.model.AppLanguage
import com.example.domain.model.Credit
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentType
import com.example.domain.model.Person
import com.example.domain.model.RatePeriod

object StatementTextGenerator {

    fun formatPaymentMethod(method: PaymentMethod, lang: AppLanguage): String = when (method) {
        PaymentMethod.CASH -> if (lang == AppLanguage.HINDI) "नकद (Cash)" else "Cash"
        PaymentMethod.UPI -> "UPI"
        PaymentMethod.BANK_TRANSFER -> if (lang == AppLanguage.HINDI) "बैंक ट्रांसफर" else "Bank Transfer"
        PaymentMethod.OTHER -> if (lang == AppLanguage.HINDI) "अन्य" else "Other"
    }

    fun formatAllocationMode(mode: AllocationMode, lang: AppLanguage): String = when (mode) {
        AllocationMode.INTEREST_FIRST -> if (lang == AppLanguage.HINDI) "ब्याज पहले, फिर मूलधन" else "Interest First"
        AllocationMode.PRINCIPAL_ADJUSTMENT -> if (lang == AppLanguage.HINDI) "मिश्रधन से घटाकर नया मूलधन (Principal Adjustment)" else "Principal Adjustment (Misradhan Less)"
    }

    fun formatRatePeriod(period: RatePeriod, lang: AppLanguage): String = when (period) {
        RatePeriod.MONTHLY -> if (lang == AppLanguage.HINDI) "प्रति माह" else "Monthly"
        RatePeriod.YEARLY -> if (lang == AppLanguage.HINDI) "प्रति वर्ष" else "Yearly"
    }

    fun generateAutoPaymentNote(
        paymentAmountPaise: Long,
        paymentDate: LedgerDate,
        principalBeforePaise: Long,
        interestBeforePaise: Long,
        misradhanBeforePaise: Long,
        allocationMode: AllocationMode,
        principalAfterPaise: Long,
        advancePaise: Long,
        lang: AppLanguage
    ): String {
        val fmt = { p: Long -> IndianNumberFormatter.formatCompactCurrency(p) }
        val dateStr = paymentDate.toDisplayString()
        val modeLabel = formatAllocationMode(allocationMode, lang)
        return if (lang == AppLanguage.HINDI) {
            buildString {
                appendLine("भुगतान प्राप्त: ${fmt(paymentAmountPaise)}")
                appendLine("भुगतान तिथि: $dateStr")
                appendLine("पिछला मूलधन: ${fmt(principalBeforePaise)}")
                appendLine("अब तक का ब्याज: ${fmt(interestBeforePaise)}")
                appendLine("पिछला मिश्रधन: ${fmt(misradhanBeforePaise)}")
                appendLine("भुगतान विधि (आवंटन): $modeLabel")
                appendLine("नया मूलधन: ${fmt(principalAfterPaise)}")
                append("नई ब्याज प्रभावी तिथि: $dateStr")
                if (advancePaise > 0L) {
                    append("\nअग्रिम / अतिरिक्त राशि: ${fmt(advancePaise)}")
                }
            }
        } else {
            buildString {
                appendLine("Payment received: ${fmt(paymentAmountPaise)}")
                appendLine("Payment date: $dateStr")
                appendLine("Previous principal: ${fmt(principalBeforePaise)}")
                appendLine("Accrued interest: ${fmt(interestBeforePaise)}")
                appendLine("Previous Misradhan: ${fmt(misradhanBeforePaise)}")
                appendLine("Paid amount: ${fmt(paymentAmountPaise)}")
                appendLine("Allocation mode: $modeLabel")
                appendLine("New principal: ${fmt(principalAfterPaise)}")
                append("New interest effective date: $dateStr")
                if (advancePaise > 0L) {
                    append("\nAdvance / Overpayment: ${fmt(advancePaise)}")
                }
            }
        }
    }

    fun generatePaymentReceiptText(
        person: Person,
        payment: Payment,
        creditNumber: String?,
        previousBalancePaise: Long?,
        newBalancePaise: Long?,
        lang: AppLanguage = AppLanguage.HINDI
    ): String {
        val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, trimTrailingZerosWhenWhole = true) }
        val dateStr = LedgerDate.parseOrNull(payment.paymentDate)?.toDisplayString() ?: payment.paymentDate
        val methodStr = formatPaymentMethod(payment.paymentMethod, lang)

        return if (lang == AppLanguage.HINDI) {
            buildString {
                appendLine("════════════════════════════")
                appendLine("CREDIT LOGGER — भुगतान रसीद")
                appendLine("════════════════════════════")
                appendLine("रसीद नंबर: ${payment.receiptNumber}")
                appendLine("व्यक्ति: ${person.name}")
                if (payment.paymentType == PaymentType.NORMAL || creditNumber == null) {
                    appendLine("प्रकार: सामान्य भुगतान (क्रेडिट से संबंधित नहीं)")
                } else {
                    appendLine("क्रेडिट नंबर: $creditNumber")
                }
                appendLine("प्राप्त राशि: ${fmt(payment.amountPaise)}")
                appendLine("भुगतान तिथि: $dateStr")
                appendLine("भुगतान विधि: $methodStr")
                if (previousBalancePaise != null && newBalancePaise != null) {
                    appendLine("पिछला बकाया (मिश्रधन): ${fmt(previousBalancePaise)}")
                    appendLine("नया बकाया (मूलधन): ${fmt(newBalancePaise)}")
                }
                payment.allocationMode?.let {
                    appendLine("आवंटन विधि: ${formatAllocationMode(it, lang)}")
                }
                if (payment.note.isNotBlank()) {
                    appendLine("नोट: ${payment.note}")
                }
                append("════════════════════════════")
            }
        } else {
            buildString {
                appendLine("════════════════════════════")
                appendLine("CREDIT LOGGER — PAYMENT RECEIPT")
                appendLine("════════════════════════════")
                appendLine("Receipt No: ${payment.receiptNumber}")
                appendLine("Person: ${person.name}")
                if (payment.paymentType == PaymentType.NORMAL || creditNumber == null) {
                    appendLine("Reference: Normal Payment (Not linked to a credit)")
                } else {
                    appendLine("Credit No: $creditNumber")
                }
                appendLine("Amount Received: ${fmt(payment.amountPaise)}")
                appendLine("Payment Date: $dateStr")
                appendLine("Payment Method: $methodStr")
                if (previousBalancePaise != null && newBalancePaise != null) {
                    appendLine("Previous Balance: ${fmt(previousBalancePaise)}")
                    appendLine("New Balance: ${fmt(newBalancePaise)}")
                }
                payment.allocationMode?.let {
                    appendLine("Allocation Mode: ${formatAllocationMode(it, lang)}")
                }
                if (payment.note.isNotBlank()) {
                    appendLine("Note: ${payment.note}")
                }
                append("════════════════════════════")
            }
        }
    }

    fun generateCreditStatementText(
        person: Person,
        credit: Credit,
        snapshot: CreditBalanceSnapshot,
        payments: List<Payment>,
        lang: AppLanguage = AppLanguage.HINDI
    ): String {
        val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, trimTrailingZerosWhenWhole = true) }
        val startStr = LedgerDate.parseOrNull(credit.startDate)?.toDisplayString() ?: credit.startDate
        val effStr = snapshot.effectiveStartDate.toDisplayString()
        val calcStr = snapshot.asOfDate.toDisplayString()
        val periodStr = formatRatePeriod(snapshot.activeRatePeriod, lang)
        val creditPayments = payments
            .filter { it.creditId == credit.id && it.status == "COMPLETED" }
            .sortedBy { it.paymentDate }

        return if (lang == AppLanguage.HINDI) {
            buildString {
                appendLine("════════════════════════════")
                appendLine("CREDIT LOGGER — क्रेडिट स्टेटमेंट")
                appendLine("════════════════════════════")
                appendLine("व्यक्ति: ${person.name}")
                if (person.phone.isNotBlank()) appendLine("मोबाइल: ${person.phone}")
                appendLine("क्रेडिट नंबर: ${credit.creditNumber}")
                appendLine("प्रारंभिक मूलधन: ${fmt(credit.originalPrincipalPaise)}")
                appendLine("वर्तमान मूलधन: ${fmt(snapshot.currentPrincipalPaise)}")
                appendLine("ब्याज दर: ${snapshot.activeRate}% $periodStr")
                appendLine("प्रारंभ तिथि: $startStr (प्रभावी: $effStr)")
                appendLine("गणना तिथि: $calcStr")
                appendLine("अवधि: ${snapshot.durationSinceEffectiveStart.completedMonths} माह, ${snapshot.durationSinceEffectiveStart.remainingDays} दिन (कुल ${snapshot.durationSinceEffectiveStart.totalElapsedDays} दिन)")
                appendLine("अब तक का ब्याज: ${fmt(snapshot.accruedUnpaidInterestPaise)}")
                appendLine("कुल मिश्रधन: ${fmt(snapshot.misradhanPaise)}")
                appendLine("कुल भुगतान: ${fmt(snapshot.totalPaidOnCreditPaise)}")
                if (snapshot.advancePaise > 0L) appendLine("अग्रिम राशि: ${fmt(snapshot.advancePaise)}")
                appendLine("वर्तमान बकाया: ${fmt(snapshot.misradhanPaise)}")
                appendLine("────────────────────────────")
                appendLine("भुगतान इतिहास (${creditPayments.size}):")
                if (creditPayments.isEmpty()) {
                    appendLine("  कोई भुगतान दर्ज नहीं")
                } else {
                    creditPayments.forEachIndexed { idx, p ->
                        val pDate = LedgerDate.parseOrNull(p.paymentDate)?.toDisplayString() ?: p.paymentDate
                        appendLine("  ${idx + 1}. $pDate — ${fmt(p.amountPaise)} (${formatPaymentMethod(p.paymentMethod, lang)}) [${p.receiptNumber}]")
                    }
                }
                if (credit.note.isNotBlank()) {
                    appendLine("────────────────────────────")
                    appendLine("नोट: ${credit.note}")
                }
                append("════════════════════════════")
            }
        } else {
            buildString {
                appendLine("════════════════════════════")
                appendLine("CREDIT LOGGER — CREDIT STATEMENT")
                appendLine("════════════════════════════")
                appendLine("Person: ${person.name}")
                if (person.phone.isNotBlank()) appendLine("Phone: ${person.phone}")
                appendLine("Credit Number: ${credit.creditNumber}")
                appendLine("Original Principal: ${fmt(credit.originalPrincipalPaise)}")
                appendLine("Current Principal: ${fmt(snapshot.currentPrincipalPaise)}")
                appendLine("Interest Rate: ${snapshot.activeRate}% $periodStr")
                appendLine("Start Date: $startStr (Effective: $effStr)")
                appendLine("Statement Date: $calcStr")
                appendLine("Duration: ${snapshot.durationSinceEffectiveStart.completedMonths} months, ${snapshot.durationSinceEffectiveStart.remainingDays} days (${snapshot.durationSinceEffectiveStart.totalElapsedDays} total days)")
                appendLine("Accrued Interest: ${fmt(snapshot.accruedUnpaidInterestPaise)}")
                appendLine("Total Misradhan: ${fmt(snapshot.misradhanPaise)}")
                appendLine("Total Payments: ${fmt(snapshot.totalPaidOnCreditPaise)}")
                if (snapshot.advancePaise > 0L) appendLine("Advance Balance: ${fmt(snapshot.advancePaise)}")
                appendLine("Current Outstanding: ${fmt(snapshot.misradhanPaise)}")
                appendLine("────────────────────────────")
                appendLine("Payment History (${creditPayments.size}):")
                if (creditPayments.isEmpty()) {
                    appendLine("  No payments recorded")
                } else {
                    creditPayments.forEachIndexed { idx, p ->
                        val pDate = LedgerDate.parseOrNull(p.paymentDate)?.toDisplayString() ?: p.paymentDate
                        appendLine("  ${idx + 1}. $pDate — ${fmt(p.amountPaise)} (${formatPaymentMethod(p.paymentMethod, lang)}) [${p.receiptNumber}]")
                    }
                }
                if (credit.note.isNotBlank()) {
                    appendLine("────────────────────────────")
                    appendLine("Note: ${credit.note}")
                }
                append("════════════════════════════")
            }
        }
    }

    fun generateProfileStatementText(
        summary: PersonFinancialSummary,
        credits: List<Credit>,
        payments: List<Payment>,
        asOfDate: LedgerDate,
        lang: AppLanguage = AppLanguage.HINDI
    ): String {
        val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, trimTrailingZerosWhenWhole = true) }
        val person = summary.person
        val creditPayments = payments.filter { it.personId == person.id && it.paymentType == PaymentType.CREDIT && it.status == "COMPLETED" }
        val normalPayments = payments.filter { it.personId == person.id && it.paymentType == PaymentType.NORMAL && it.status == "COMPLETED" }

        return if (lang == AppLanguage.HINDI) {
            buildString {
                appendLine("════════════════════════════")
                appendLine("CREDIT LOGGER — संपूर्ण प्रोफाइल स्टेटमेंट")
                appendLine("════════════════════════════")
                appendLine("नाम: ${person.name}")
                if (person.phone.isNotBlank()) appendLine("मोबाइल: ${person.phone}")
                if (person.address.isNotBlank()) appendLine("पता: ${person.address}")
                appendLine("स्टेटमेंट तिथि: ${asOfDate.toDisplayString()}")
                appendLine("────────────────────────────")
                appendLine("कुल सक्रिय मूलधन: ${fmt(summary.totalPrincipalPaise)}")
                appendLine("कुल बकाया ब्याज: ${fmt(summary.totalAccruedUnpaidInterestPaise)}")
                appendLine("कुल मिश्रधन (बकाया): ${fmt(summary.totalMisradhanPaise)}")
                appendLine("क्रेडिट भुगतान प्राप्त: ${fmt(summary.totalCreditPaymentsPaise)}")
                appendLine("सामान्य भुगतान प्राप्त: ${fmt(summary.totalNormalPaymentsPaise)}")
                appendLine("कुल प्राप्त राशि: ${fmt(summary.totalPaidAmountPaise)}")
                appendLine("────────────────────────────")
                appendLine("क्रेडिट रिकॉर्ड (${summary.creditSnapshots.size}):")
                summary.creditSnapshots.forEach { snap ->
                    val c = credits.firstOrNull { it.id == snap.creditId }
                    appendLine("• ${snap.creditNumber} (${snap.status}): मूलधन ${fmt(snap.currentPrincipalPaise)}, ब्याज ${fmt(snap.accruedUnpaidInterestPaise)}, मिश्रधन ${fmt(snap.misradhanPaise)}, दर ${snap.activeRate}% ${formatRatePeriod(snap.activeRatePeriod, lang)}")
                    val cPays = creditPayments.filter { it.creditId == snap.creditId }
                    cPays.forEach { p ->
                        val d = LedgerDate.parseOrNull(p.paymentDate)?.toDisplayString() ?: p.paymentDate
                        appendLine("   - भुगतान: $d | ${fmt(p.amountPaise)} | ${p.receiptNumber}")
                    }
                    if (c?.note?.isNotBlank() == true) appendLine("   नोट: ${c.note}")
                }
                appendLine("────────────────────────────")
                appendLine("सामान्य भुगतान — क्रेडिट से संबंधित नहीं (${normalPayments.size}):")
                if (normalPayments.isEmpty()) {
                    appendLine("  कोई सामान्य भुगतान नहीं")
                } else {
                    normalPayments.forEach { p ->
                        val d = LedgerDate.parseOrNull(p.paymentDate)?.toDisplayString() ?: p.paymentDate
                        appendLine("  • $d — ${fmt(p.amountPaise)} (${formatPaymentMethod(p.paymentMethod, lang)}) [${p.receiptNumber}]")
                    }
                }
                append("════════════════════════════")
            }
        } else {
            buildString {
                appendLine("════════════════════════════")
                appendLine("CREDIT LOGGER — COMPLETE PROFILE STATEMENT")
                appendLine("════════════════════════════")
                appendLine("Name: ${person.name}")
                if (person.phone.isNotBlank()) appendLine("Phone: ${person.phone}")
                if (person.address.isNotBlank()) appendLine("Address: ${person.address}")
                appendLine("Statement Date: ${asOfDate.toDisplayString()}")
                appendLine("────────────────────────────")
                appendLine("Total Principal: ${fmt(summary.totalPrincipalPaise)}")
                appendLine("Total Accrued Interest: ${fmt(summary.totalAccruedUnpaidInterestPaise)}")
                appendLine("Total Misradhan (Outstanding): ${fmt(summary.totalMisradhanPaise)}")
                appendLine("Credit Payments Received: ${fmt(summary.totalCreditPaymentsPaise)}")
                appendLine("Normal Payments Received: ${fmt(summary.totalNormalPaymentsPaise)}")
                appendLine("Total Received: ${fmt(summary.totalPaidAmountPaise)}")
                appendLine("────────────────────────────")
                appendLine("Credit Records (${summary.creditSnapshots.size}):")
                summary.creditSnapshots.forEach { snap ->
                    val c = credits.firstOrNull { it.id == snap.creditId }
                    appendLine("• ${snap.creditNumber} (${snap.status}): Principal ${fmt(snap.currentPrincipalPaise)}, Interest ${fmt(snap.accruedUnpaidInterestPaise)}, Misradhan ${fmt(snap.misradhanPaise)}, Rate ${snap.activeRate}% ${formatRatePeriod(snap.activeRatePeriod, lang)}")
                    val cPays = creditPayments.filter { it.creditId == snap.creditId }
                    cPays.forEach { p ->
                        val d = LedgerDate.parseOrNull(p.paymentDate)?.toDisplayString() ?: p.paymentDate
                        appendLine("   - Payment: $d | ${fmt(p.amountPaise)} | ${p.receiptNumber}")
                    }
                    if (c?.note?.isNotBlank() == true) appendLine("   Note: ${c.note}")
                }
                appendLine("────────────────────────────")
                appendLine("Normal Payments — Not linked to a credit (${normalPayments.size}):")
                if (normalPayments.isEmpty()) {
                    appendLine("  No normal payments")
                } else {
                    normalPayments.forEach { p ->
                        val d = LedgerDate.parseOrNull(p.paymentDate)?.toDisplayString() ?: p.paymentDate
                        appendLine("  • $d — ${fmt(p.amountPaise)} (${formatPaymentMethod(p.paymentMethod, lang)}) [${p.receiptNumber}]")
                    }
                }
                append("════════════════════════════")
            }
        }
    }

    fun generateInterestCalculatorBreakdownText(
        principalPaise: Long,
        ratePercent: String,
        ratePeriod: RatePeriod,
        startDate: LedgerDate,
        endDate: LedgerDate,
        result: PeriodInterestResult,
        lang: AppLanguage
    ): String {
        val fmt = { p: Long -> IndianNumberFormatter.formatCompactCurrency(p) }
        val misradhan = InterestEngine.calculateMisradhan(principalPaise, result.interestPaise)
        val periodStr = formatRatePeriod(ratePeriod, lang)
        return if (lang == AppLanguage.HINDI) {
            buildString {
                appendLine("मूलधन: ${fmt(principalPaise)}")
                appendLine("ब्याज दर: $ratePercent% $periodStr")
                appendLine("प्रारंभ तिथि: ${startDate.toDisplayString()}")
                appendLine("गणना तिथि: ${endDate.toDisplayString()}")
                appendLine("अवधि: ${result.duration.completedMonths} कैलेंडर माह, ${result.duration.remainingDays} दिन (कुल ${result.duration.totalElapsedDays} दिन)")
                appendLine("ब्याज: ${fmt(result.interestPaise)}")
                append("मिश्रधन: ${fmt(misradhan)}")
            }
        } else {
            buildString {
                appendLine("Principal: ${fmt(principalPaise)}")
                appendLine("Rate: $ratePercent% $periodStr")
                appendLine("Start Date: ${startDate.toDisplayString()}")
                appendLine("Calculation Date: ${endDate.toDisplayString()}")
                appendLine("Duration: ${result.duration.completedMonths} calendar months, ${result.duration.remainingDays} days (${result.duration.totalElapsedDays} total days)")
                appendLine("Interest: ${fmt(result.interestPaise)}")
                append("Misradhan: ${fmt(misradhan)}")
            }
        }
    }
}
