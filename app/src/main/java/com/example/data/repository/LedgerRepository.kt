package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.CreditLoggerDatabase
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.engine.InterestEngine
import com.example.domain.engine.LedgerDate
import com.example.domain.engine.StatementTextGenerator
import com.example.domain.model.Adjustment
import com.example.domain.model.AllocationMode
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppSettings
import com.example.domain.model.AuditLog
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
import com.example.domain.model.Reminder
import com.example.domain.model.ReminderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import java.util.UUID

class LedgerRepository(
    private val db: CreditLoggerDatabase,
    private val appContext: Context
) {
    private val writeMutex = Mutex()

    val personsFlow: Flow<List<Person>> = db.personDao().observeAll().map { list -> list.map { it.toDomain() } }
    val creditsFlow: Flow<List<Credit>> = db.creditDao().observeAll().map { list -> list.map { it.toDomain() } }
    val eventsFlow: Flow<List<CreditEvent>> = db.creditEventDao().observeAll().map { list -> list.map { it.toDomain() } }
    val paymentsFlow: Flow<List<Payment>> = db.paymentDao().observeAll().map { list -> list.map { it.toDomain() } }
    val adjustmentsFlow: Flow<List<Adjustment>> = db.adjustmentDao().observeAll().map { list -> list.map { it.toDomain() } }
    val auditLogsFlow: Flow<List<AuditLog>> = db.auditLogDao().observeAll().map { list -> list.map { it.toDomain() } }
    val remindersFlow: Flow<List<Reminder>> = db.reminderDao().observeAll().map { list -> list.map { it.toDomain() } }
    val settingsFlow: Flow<AppSettings> = db.settingsDao().observeSettings().map { it?.toDomain() ?: AppSettings() }

    suspend fun getSettings(): AppSettings = db.settingsDao().getSettings()?.toDomain() ?: AppSettings()

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = writeMutex.withLock {
        val current = getSettings()
        val updated = transform(current).copy(updatedAt = System.currentTimeMillis())
        db.settingsDao().upsert(updated.toEntity())
    }

    suspend fun generateNextCreditNumber(): String {
        val existing = db.creditDao().getAll().map { it.creditNumber }.toSet()
        var idx = existing.size + 1
        while (true) {
            val candidate = String.format(Locale.US, "CL-%03d", idx)
            if (candidate !in existing) return candidate
            idx++
        }
    }

    suspend fun isCreditNumberUnique(creditNumber: String, excludeCreditId: String? = null): Boolean {
        val existing = db.creditDao().getByCreditNumber(creditNumber.trim())
        return existing == null || existing.id == excludeCreditId
    }

    suspend fun savePerson(
        existingId: String? = null,
        name: String,
        phone: String,
        address: String,
        photoRef: String,
        note: String
    ): Person = writeMutex.withLock {
        require(name.isNotBlank()) { "Name is required" }
        val now = System.currentTimeMillis()
        val existing = existingId?.let { db.personDao().getById(it)?.toDomain() }
        val person = if (existing != null) {
            existing.copy(
                name = name.trim(),
                phone = phone.trim(),
                address = address.trim(),
                photoRef = photoRef.trim(),
                note = note.trim(),
                updatedAt = now
            )
        } else {
            Person(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                phone = phone.trim(),
                address = address.trim(),
                photoRef = photoRef.trim(),
                note = note.trim(),
                createdAt = now,
                updatedAt = now
            )
        }
        db.withTransaction {
            db.personDao().upsert(person.toEntity())
            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "PERSON",
                    entityId = person.id,
                    action = if (existing == null) "CREATE" else "UPDATE",
                    oldData = existing?.name ?: "",
                    newData = "${person.name} (${person.phone})",
                    timestamp = now
                ).toEntity()
            )
        }
        person
    }

    suspend fun setPersonArchived(personId: String, archived: Boolean) = writeMutex.withLock {
        val existing = db.personDao().getById(personId)?.toDomain() ?: return@withLock
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            archivedAt = if (archived) now else null,
            updatedAt = now
        )
        db.withTransaction {
            db.personDao().upsert(updated.toEntity())
            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "PERSON",
                    entityId = personId,
                    action = if (archived) "ARCHIVE" else "RESTORE",
                    oldData = existing.archivedAt?.toString() ?: "active",
                    newData = updated.archivedAt?.toString() ?: "active",
                    timestamp = now
                ).toEntity()
            )
        }
    }

    suspend fun permanentlyDeletePerson(personId: String) = writeMutex.withLock {
        val existing = db.personDao().getById(personId)?.toDomain() ?: return@withLock
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.reminderDao().deleteByPersonId(personId)
            db.adjustmentDao().deleteByPersonId(personId)
            db.paymentDao().deleteByPersonId(personId)
            db.creditEventDao().deleteByPersonId(personId)
            db.creditDao().deleteByPersonId(personId)
            db.personDao().deleteById(personId)
            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "PERSON",
                    entityId = personId,
                    action = "DELETE_PERMANENT",
                    oldData = existing.name,
                    newData = "",
                    timestamp = now
                ).toEntity()
            )
        }
    }

    suspend fun createCredit(
        personId: String,
        creditNumber: String,
        principalPaise: Long,
        interestRate: String,
        ratePeriod: RatePeriod,
        interestMethod: InterestMethod,
        startDate: LedgerDate,
        dueDate: LedgerDate?,
        note: String
    ): Credit = writeMutex.withLock {
        require(principalPaise > 0L) { "Principal must be positive" }
        val rateBd = InterestEngine.parseRateOrZero(interestRate)
        require(rateBd >= java.math.BigDecimal.ZERO) { "Rate cannot be negative" }
        val cleanNumber = creditNumber.trim()
        require(cleanNumber.isNotEmpty()) { "Credit number is required" }
        require(db.creditDao().getByCreditNumber(cleanNumber) == null) {
            "Credit number '$cleanNumber' already exists"
        }

        val now = System.currentTimeMillis()
        val creditId = UUID.randomUUID().toString()
        val credit = Credit(
            id = creditId,
            personId = personId,
            creditNumber = cleanNumber,
            originalPrincipalPaise = principalPaise,
            currentPrincipalPaise = principalPaise,
            interestRate = interestRate.trim(),
            ratePeriod = ratePeriod,
            interestMethod = interestMethod,
            startDate = startDate.toIsoString(),
            dueDate = dueDate?.toIsoString(),
            status = CreditStatus.ACTIVE,
            note = note.trim(),
            createdAt = now,
            updatedAt = now
        )
        val createdEvent = CreditEvent(
            id = UUID.randomUUID().toString(),
            personId = personId,
            creditId = creditId,
            eventType = CreditEventType.CREDIT_CREATED,
            effectiveDate = startDate.toIsoString(),
            createdAt = now,
            updatedAt = now,
            principalBeforePaise = 0L,
            interestBeforePaise = 0L,
            misradhanBeforePaise = 0L,
            amountPaise = principalPaise,
            principalAfterPaise = principalPaise,
            interestAfterPaise = 0L,
            rateAfter = interestRate.trim(),
            ratePeriodAfter = ratePeriod,
            systemNote = "Credit Created: $cleanNumber",
            note = note.trim()
        )
        db.withTransaction {
            db.creditDao().upsert(credit.toEntity())
            db.creditEventDao().upsert(createdEvent.toEntity())
            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "CREDIT",
                    entityId = creditId,
                    action = "CREATE",
                    oldData = "",
                    newData = "$cleanNumber | $principalPaise paise | $interestRate% ${ratePeriod.name}",
                    timestamp = now
                ).toEntity()
            )
        }
        credit
    }

    suspend fun updateCreditDetails(
        creditId: String,
        dueDate: LedgerDate?,
        note: String
    ) = writeMutex.withLock {
        val existing = db.creditDao().getById(creditId)?.toDomain() ?: return@withLock
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            dueDate = dueDate?.toIsoString(),
            note = note.trim(),
            updatedAt = now
        )
        db.withTransaction {
            db.creditDao().upsert(updated.toEntity())
            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "CREDIT",
                    entityId = creditId,
                    action = "UPDATE_DETAILS",
                    oldData = "dueDate=${existing.dueDate}, note=${existing.note}",
                    newData = "dueDate=${updated.dueDate}, note=${updated.note}",
                    timestamp = now
                ).toEntity()
            )
        }
    }

    suspend fun setCreditArchived(creditId: String, archived: Boolean) = writeMutex.withLock {
        val existing = db.creditDao().getById(creditId)?.toDomain() ?: return@withLock
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            status = if (archived) CreditStatus.ARCHIVED else CreditStatus.ACTIVE,
            archivedAt = if (archived) now else null,
            updatedAt = now
        )
        db.withTransaction {
            db.creditDao().upsert(updated.toEntity())
            if (!archived) {
                recalculateCreditTimelineInTransaction(creditId, now)
            }
            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "CREDIT",
                    entityId = creditId,
                    action = if (archived) "ARCHIVE" else "RESTORE",
                    oldData = existing.status.name,
                    newData = updated.status.name,
                    timestamp = now
                ).toEntity()
            )
        }
    }

    suspend fun closeOrReopenCredit(creditId: String, close: Boolean, effectiveDate: LedgerDate, note: String = "") = writeMutex.withLock {
        val credit = db.creditDao().getById(creditId)?.toDomain() ?: return@withLock
        val now = System.currentTimeMillis()
        val existingEvents = db.creditEventDao().getByCreditId(creditId).map { it.toDomain() }
        val snap = InterestEngine.calculateCreditBalanceAsOfDate(credit, existingEvents, effectiveDate)
        val event = CreditEvent(
            id = UUID.randomUUID().toString(),
            personId = credit.personId,
            creditId = creditId,
            eventType = if (close) CreditEventType.CREDIT_CLOSED else CreditEventType.CREDIT_REOPENED,
            effectiveDate = effectiveDate.toIsoString(),
            createdAt = now,
            updatedAt = now,
            principalBeforePaise = snap.currentPrincipalPaise,
            interestBeforePaise = snap.accruedUnpaidInterestPaise,
            misradhanBeforePaise = snap.misradhanPaise,
            amountPaise = 0L,
            principalAfterPaise = snap.currentPrincipalPaise,
            interestAfterPaise = snap.accruedUnpaidInterestPaise,
            rateAfter = snap.activeRate,
            ratePeriodAfter = snap.activeRatePeriod,
            systemNote = if (close) "Credit Closed" else "Credit Reopened",
            note = note.trim()
        )
        db.withTransaction {
            db.creditEventDao().upsert(event.toEntity())
            recalculateCreditTimelineInTransaction(creditId, now)
            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "CREDIT",
                    entityId = creditId,
                    action = if (close) "CLOSE" else "REOPEN",
                    oldData = credit.status.name,
                    newData = if (close) "CLOSED" else "ACTIVE",
                    timestamp = now
                ).toEntity()
            )
        }
    }

    suspend fun recordCreditPayment(
        creditId: String,
        amountPaise: Long,
        paymentDate: LedgerDate,
        paymentMethod: PaymentMethod,
        allocationMode: AllocationMode,
        manualNote: String,
        lang: AppLanguage
    ): Payment = writeMutex.withLock {
        require(amountPaise > 0L) { "Payment amount must be positive" }
        val credit = db.creditDao().getById(creditId)?.toDomain()
            ?: throw IllegalArgumentException("Credit not found")
        val creditStart = InterestEngine.parseDateOnly(credit.startDate)
        require(paymentDate >= creditStart) {
            "Payment date (${paymentDate.toDisplayString()}) cannot be earlier than credit start date (${creditStart.toDisplayString()})."
        }

        val now = System.currentTimeMillis()
        val existingEvents = db.creditEventDao().getByCreditId(creditId).map { it.toDomain() }
        val preSnapshot = InterestEngine.calculateCreditBalanceAsOfDate(credit, existingEvents, paymentDate)
        val alloc = if (allocationMode == AllocationMode.INTEREST_FIRST) {
            InterestEngine.applyInterestFirstPayment(
                preSnapshot.currentPrincipalPaise,
                preSnapshot.accruedUnpaidInterestPaise,
                amountPaise
            )
        } else {
            InterestEngine.applyMisradhanLessPayment(
                preSnapshot.currentPrincipalPaise,
                preSnapshot.accruedUnpaidInterestPaise,
                amountPaise
            )
        }

        val receiptNumber = generateUniqueReceiptNumber(paymentDate)
        val systemNote = StatementTextGenerator.generateAutoPaymentNote(
            paymentAmountPaise = amountPaise,
            paymentDate = paymentDate,
            principalBeforePaise = alloc.principalBeforePaise,
            interestBeforePaise = alloc.accruedInterestBeforePaise,
            misradhanBeforePaise = alloc.misradhanBeforePaise,
            allocationMode = allocationMode,
            principalAfterPaise = alloc.principalAfterPaise,
            advancePaise = alloc.advancePaise,
            lang = lang
        )

        val paymentId = UUID.randomUUID().toString()
        val payment = Payment(
            id = paymentId,
            personId = credit.personId,
            creditId = creditId,
            paymentType = PaymentType.CREDIT,
            amountPaise = amountPaise,
            paymentDate = paymentDate.toIsoString(),
            paymentMethod = paymentMethod,
            receiptNumber = receiptNumber,
            allocationMode = allocationMode,
            systemNote = systemNote,
            note = manualNote.trim(),
            createdAt = now,
            updatedAt = now,
            status = "COMPLETED"
        )

        val event = CreditEvent(
            id = paymentId, // link 1:1 with paymentId for clean lookup and audit
            personId = credit.personId,
            creditId = creditId,
            eventType = CreditEventType.CREDIT_PAYMENT,
            effectiveDate = paymentDate.toIsoString(),
            createdAt = now,
            updatedAt = now,
            principalBeforePaise = alloc.principalBeforePaise,
            interestBeforePaise = alloc.accruedInterestBeforePaise,
            misradhanBeforePaise = alloc.misradhanBeforePaise,
            amountPaise = amountPaise,
            principalAfterPaise = alloc.principalAfterPaise,
            interestAfterPaise = alloc.unpaidInterestAfterPaise,
            rateAfter = preSnapshot.activeRate,
            ratePeriodAfter = preSnapshot.activeRatePeriod,
            allocationMode = allocationMode,
            paymentMethod = paymentMethod,
            receiptNumber = receiptNumber,
            advancePaise = alloc.advancePaise,
            systemNote = systemNote,
            note = manualNote.trim()
        )

        db.withTransaction {
            db.paymentDao().upsert(payment.toEntity())
            db.creditEventDao().upsert(event.toEntity())
            recalculateCreditTimelineInTransaction(creditId, now)
            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "PAYMENT",
                    entityId = paymentId,
                    action = "CREATE_CREDIT_PAYMENT",
                    oldData = "principal=${alloc.principalBeforePaise}, interest=${alloc.accruedInterestBeforePaise}",
                    newData = "paid=$amountPaise, newPrincipal=${alloc.principalAfterPaise}, mode=${allocationMode.name}, receipt=$receiptNumber",
                    timestamp = now
                ).toEntity()
            )
        }
        payment
    }

    suspend fun recordNormalPayment(
        personId: String,
        amountPaise: Long,
        paymentDate: LedgerDate,
        paymentMethod: PaymentMethod,
        note: String
    ): Payment = writeMutex.withLock {
        require(amountPaise > 0L) { "Payment amount must be positive" }
        val now = System.currentTimeMillis()
        val receiptNumber = generateUniqueReceiptNumber(paymentDate)
        val paymentId = UUID.randomUUID().toString()
        val systemNote = "Normal Payment — Not linked to a credit"

        val payment = Payment(
            id = paymentId,
            personId = personId,
            creditId = null,
            paymentType = PaymentType.NORMAL,
            amountPaise = amountPaise,
            paymentDate = paymentDate.toIsoString(),
            paymentMethod = paymentMethod,
            receiptNumber = receiptNumber,
            allocationMode = null,
            systemNote = systemNote,
            note = note.trim(),
            createdAt = now,
            updatedAt = now,
            status = "COMPLETED"
        )
        val event = CreditEvent(
            id = paymentId,
            personId = personId,
            creditId = "NORMAL",
            eventType = CreditEventType.NORMAL_PAYMENT,
            effectiveDate = paymentDate.toIsoString(),
            createdAt = now,
            updatedAt = now,
            principalBeforePaise = 0L,
            interestBeforePaise = 0L,
            misradhanBeforePaise = 0L,
            amountPaise = amountPaise,
            principalAfterPaise = 0L,
            interestAfterPaise = 0L,
            rateAfter = "0",
            ratePeriodAfter = RatePeriod.MONTHLY,
            allocationMode = null,
            paymentMethod = paymentMethod,
            receiptNumber = receiptNumber,
            advancePaise = 0L,
            systemNote = systemNote,
            note = note.trim()
        )
        db.withTransaction {
            db.paymentDao().upsert(payment.toEntity())
            db.creditEventDao().upsert(event.toEntity())
            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "PAYMENT",
                    entityId = paymentId,
                    action = "CREATE_NORMAL_PAYMENT",
                    oldData = "",
                    newData = "person=$personId, amount=$amountPaise, receipt=$receiptNumber",
                    timestamp = now
                ).toEntity()
            )
        }
        payment
    }

    suspend fun applyMainCreditUpdate(
        creditId: String,
        newPrincipalPaise: Long,
        newRate: String,
        newRatePeriod: RatePeriod,
        effectiveDate: LedgerDate,
        note: String
    ) = writeMutex.withLock {
        val credit = db.creditDao().getById(creditId)?.toDomain()
            ?: throw IllegalArgumentException("Credit not found")
        val creditStart = InterestEngine.parseDateOnly(credit.startDate)
        require(effectiveDate >= creditStart) {
            "Effective date cannot be earlier than credit start date"
        }
        require(newPrincipalPaise >= 0L) { "Principal cannot be negative" }
        val now = System.currentTimeMillis()
        val existingEvents = db.creditEventDao().getByCreditId(creditId).map { it.toDomain() }
        val preview = InterestEngine.applyPrincipalAndRateAdjustment(
            credit = credit,
            events = existingEvents,
            effectiveDate = effectiveDate,
            newPrincipalPaise = newPrincipalPaise,
            newRate = newRate,
            newRatePeriod = newRatePeriod
        )

        val adjustmentId = UUID.randomUUID().toString()
        val adjustment = Adjustment(
            id = adjustmentId,
            creditId = creditId,
            personId = credit.personId,
            effectiveDate = effectiveDate.toIsoString(),
            oldPrincipalPaise = preview.oldPrincipalPaise,
            newPrincipalPaise = preview.newPrincipalPaise,
            oldRate = preview.oldRate,
            newRate = preview.newRate,
            oldRatePeriod = preview.oldRatePeriod,
            newRatePeriod = preview.newRatePeriod,
            note = note.trim(),
            createdAt = now,
            updatedAt = now
        )

        val eventType = if (preview.oldPrincipalPaise != preview.newPrincipalPaise) {
            CreditEventType.PRINCIPAL_ADJUSTMENT
        } else {
            CreditEventType.INTEREST_RATE_UPDATE
        }

        val event = CreditEvent(
            id = adjustmentId,
            personId = credit.personId,
            creditId = creditId,
            eventType = eventType,
            effectiveDate = effectiveDate.toIsoString(),
            createdAt = now,
            updatedAt = now,
            principalBeforePaise = preview.oldPrincipalPaise,
            interestBeforePaise = preview.accruedInterestToDatePaise,
            misradhanBeforePaise = preview.oldMisradhanPaise,
            amountPaise = kotlin.math.abs(preview.newPrincipalPaise - preview.oldPrincipalPaise),
            principalAfterPaise = preview.newPrincipalPaise,
            interestAfterPaise = preview.accruedInterestToDatePaise,
            rateAfter = preview.newRate,
            ratePeriodAfter = preview.newRatePeriod,
            systemNote = "Terms updated: Principal ${preview.oldPrincipalPaise / 100} -> ${preview.newPrincipalPaise / 100}, Rate ${preview.oldRate}% -> ${preview.newRate}%",
            note = note.trim()
        )

        db.withTransaction {
            db.adjustmentDao().upsert(adjustment.toEntity())
            db.creditEventDao().upsert(event.toEntity())
            recalculateCreditTimelineInTransaction(creditId, now)
            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "CREDIT_ADJUSTMENT",
                    entityId = creditId,
                    action = "MAIN_UPDATE",
                    oldData = "principal=${preview.oldPrincipalPaise}, rate=${preview.oldRate}% ${preview.oldRatePeriod.name}",
                    newData = "principal=${preview.newPrincipalPaise}, rate=${preview.newRate}% ${preview.newRatePeriod.name}, eff=${effectiveDate.toIsoString()}",
                    timestamp = now
                ).toEntity()
            )
        }
    }

    /**
     * Edits only the user manual note on a payment without touching the system-generated note.
     */
    suspend fun updatePaymentManualNote(paymentId: String, newManualNote: String) = writeMutex.withLock {
        val payment = db.paymentDao().getById(paymentId)?.toDomain() ?: return@withLock
        val now = System.currentTimeMillis()
        val updatedPayment = payment.copy(note = newManualNote.trim(), updatedAt = now)
        val event = db.creditEventDao().getById(paymentId)?.toDomain()?.copy(
            note = newManualNote.trim(),
            updatedAt = now
        )
        db.withTransaction {
            db.paymentDao().upsert(updatedPayment.toEntity())
            if (event != null) db.creditEventDao().upsert(event.toEntity())
            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "PAYMENT",
                    entityId = paymentId,
                    action = "EDIT_NOTE",
                    oldData = payment.note,
                    newData = updatedPayment.note,
                    timestamp = now
                ).toEntity()
            )
        }
    }

    /**
     * Reverses a payment or adjustment event, creates a CORRECTION_REVERSAL audit trail,
     * and rebuilds the entire credit timeline from its start date forward.
     */
    suspend fun reverseEvent(eventId: String, reason: String) = writeMutex.withLock {
        val event = db.creditEventDao().getById(eventId)?.toDomain() ?: return@withLock
        require(!event.isReversed) { "Event is already reversed" }
        require(event.eventType != CreditEventType.CREDIT_CREATED) {
            "Cannot reverse initial credit creation event"
        }
        val now = System.currentTimeMillis()
        val markedReversed = event.copy(isReversed = true, updatedAt = now)
        val reversalEvent = CreditEvent(
            id = UUID.randomUUID().toString(),
            personId = event.personId,
            creditId = event.creditId,
            eventType = CreditEventType.CORRECTION_REVERSAL,
            effectiveDate = event.effectiveDate,
            createdAt = now,
            updatedAt = now,
            principalBeforePaise = event.principalAfterPaise,
            interestBeforePaise = event.interestAfterPaise,
            misradhanBeforePaise = event.principalAfterPaise + event.interestAfterPaise,
            amountPaise = event.amountPaise,
            principalAfterPaise = event.principalBeforePaise,
            interestAfterPaise = event.interestBeforePaise,
            rateAfter = event.rateAfter,
            ratePeriodAfter = event.ratePeriodAfter,
            receiptNumber = event.receiptNumber,
            systemNote = "Reversed ${event.eventType.name} (${event.receiptNumber ?: event.id.take(8)})",
            note = reason.trim(),
            reversesEventId = event.id,
            isReversed = false
        )

        db.withTransaction {
            db.creditEventDao().upsert(markedReversed.toEntity())
            db.creditEventDao().upsert(reversalEvent.toEntity())
            val linkedPayment = db.paymentDao().getById(eventId)?.toDomain()
            if (linkedPayment != null) {
                db.paymentDao().upsert(
                    linkedPayment.copy(status = "REVERSED", updatedAt = now).toEntity()
                )
            }
            if (event.creditId != "NORMAL") {
                recalculateCreditTimelineInTransaction(event.creditId, now)
            }
            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "EVENT",
                    entityId = eventId,
                    action = "REVERSE",
                    oldData = "type=${event.eventType.name}, amount=${event.amountPaise}, date=${event.effectiveDate}",
                    newData = "reason=${reason.trim()}",
                    timestamp = now
                ).toEntity()
            )
        }
    }

    /**
     * Replays all non-reversed events for [creditId] up to today and updates stored snapshots
     * and the CreditEntity's currentPrincipalPaise & status atomically.
     */
    private suspend fun recalculateCreditTimelineInTransaction(creditId: String, now: Long) {
        val credit = db.creditDao().getById(creditId)?.toDomain() ?: return
        val events = db.creditEventDao().getByCreditId(creditId).map { it.toDomain() }
        val today = LedgerDate.todayLocal()
        val maxEventDate = events
            .mapNotNull { LedgerDate.parseOrNull(it.effectiveDate) }
            .maxOrNull()
        val replayEndDate = if (maxEventDate != null && maxEventDate > today) maxEventDate else today

        val snapshot = InterestEngine.calculateCreditBalanceAsOfDate(credit, events, replayEndDate)

        // Update stored snapshots on active events so history cards stay exact
        snapshot.replayedEvents.forEach { state ->
            val updatedEvent = state.event.copy(
                principalBeforePaise = state.principalBeforePaise,
                interestBeforePaise = state.interestBeforePaise,
                misradhanBeforePaise = state.misradhanBeforePaise,
                principalAfterPaise = state.principalAfterPaise,
                interestAfterPaise = state.interestAfterPaise,
                advancePaise = state.advancePaise,
                updatedAt = now
            )
            db.creditEventDao().upsert(updatedEvent.toEntity())
        }

        val updatedCredit = credit.copy(
            currentPrincipalPaise = snapshot.currentPrincipalPaise,
            interestRate = snapshot.activeRate,
            ratePeriod = snapshot.activeRatePeriod,
            status = snapshot.status,
            updatedAt = now
        )
        db.creditDao().upsert(updatedCredit.toEntity())
    }

    suspend fun saveReminder(
        existingId: String? = null,
        personId: String,
        creditId: String?,
        reminderDate: LedgerDate,
        reminderTime: String,
        note: String,
        status: ReminderStatus = ReminderStatus.UPCOMING
    ): Reminder = writeMutex.withLock {
        val now = System.currentTimeMillis()
        val reminder = Reminder(
            id = existingId ?: UUID.randomUUID().toString(),
            personId = personId,
            creditId = creditId,
            reminderDate = reminderDate.toIsoString(),
            reminderTime = reminderTime,
            note = note.trim(),
            status = status,
            createdAt = now,
            updatedAt = now
        )
        db.reminderDao().upsert(reminder.toEntity())
        reminder
    }

    suspend fun updateReminderStatus(reminderId: String, status: ReminderStatus) = writeMutex.withLock {
        val all = db.reminderDao().getAll().map { it.toDomain() }
        val found = all.firstOrNull { it.id == reminderId } ?: return@withLock
        db.reminderDao().upsert(found.copy(status = status, updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteReminder(reminderId: String) = writeMutex.withLock {
        db.reminderDao().deleteById(reminderId)
    }

    suspend fun getDatabaseSizeBytes(): Long {
        val dbFile = appContext.getDatabasePath(CreditLoggerDatabase.DATABASE_NAME)
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        return (if (dbFile.exists()) dbFile.length() else 0L) +
            (if (walFile.exists()) walFile.length() else 0L) +
            (if (shmFile.exists()) shmFile.length() else 0L)
    }

    private suspend fun generateUniqueReceiptNumber(date: LedgerDate): String {
        val compactDate = String.format(Locale.US, "%04d%02d%02d", date.year, date.month, date.day)
        var seq = db.paymentDao().getAll().size + 1
        while (true) {
            val candidate = String.format(Locale.US, "RC-%s-%03d", compactDate, seq)
            if (db.paymentDao().getByReceiptNumber(candidate) == null) return candidate
            seq++
        }
    }
}
