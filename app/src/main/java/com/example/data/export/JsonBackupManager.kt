package com.example.data.export

import androidx.room.withTransaction
import com.example.data.local.CreditLoggerDatabase
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.engine.InterestEngine
import com.example.domain.engine.LedgerDate
import com.example.domain.model.Adjustment
import com.example.domain.model.AllocationMode
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppSettings
import com.example.domain.model.AppThemeMode
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ExportScope {
    ALL_DATA,
    SINGLE_PERSON,
    SINGLE_CREDIT,
    TRANSACTIONS_ONLY
}

enum class ImportMode {
    SAFE_MERGE,
    REPLACE_ALL
}

data class ParsedBackupBundle(
    val schemaVersion: Int,
    val exportScope: String,
    val exportTimestamp: Long,
    val persons: List<Person>,
    val credits: List<Credit>,
    val payments: List<Payment>,
    val events: List<CreditEvent>,
    val adjustments: List<Adjustment>,
    val auditLogs: List<AuditLog>,
    val reminders: List<Reminder>,
    val settings: AppSettings?
)

data class ImportPreview(
    val bundle: ParsedBackupBundle,
    val duplicatePersonIds: Int,
    val duplicateCreditIds: Int,
    val duplicatePaymentIds: Int,
    val duplicateEventIds: Int,
    val missingReferenceWarnings: List<String>,
    val sampleNames: List<String>
)

data class ImportResultSummary(
    val importedPersons: Int,
    val importedCredits: Int,
    val importedPayments: Int,
    val importedEvents: Int,
    val importedReminders: Int,
    val warnings: List<String>
)

class JsonBackupManager(private val db: CreditLoggerDatabase) {

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_IMPORT_BYTES = 10 * 1024 * 1024 // 10 MB guard
    }

    suspend fun exportJson(
        scope: ExportScope,
        personId: String? = null,
        creditId: String? = null
    ): String {
        val allPersons = db.personDao().getAll().map { it.toDomain() }
        val allCredits = db.creditDao().getAll().map { it.toDomain() }
        val allPayments = db.paymentDao().getAll().map { it.toDomain() }
        val allEvents = db.creditEventDao().getAll().map { it.toDomain() }
        val allAdjustments = db.adjustmentDao().getAll().map { it.toDomain() }
        val allAuditLogs = db.auditLogDao().getAll().map { it.toDomain() }
        val allReminders = db.reminderDao().getAll().map { it.toDomain() }
        val settings = db.settingsDao().getSettings()?.toDomain()

        val persons: List<Person>
        val credits: List<Credit>
        val payments: List<Payment>
        val events: List<CreditEvent>
        val adjustments: List<Adjustment>
        val auditLogs: List<AuditLog>
        val reminders: List<Reminder>
        val exportedSettings: AppSettings?

        when (scope) {
            ExportScope.ALL_DATA -> {
                persons = allPersons
                credits = allCredits
                payments = allPayments
                events = allEvents
                adjustments = allAdjustments
                auditLogs = allAuditLogs
                reminders = allReminders
                exportedSettings = settings
            }
            ExportScope.SINGLE_PERSON -> {
                persons = allPersons.filter { it.id == personId }
                credits = allCredits.filter { it.personId == personId }
                payments = allPayments.filter { it.personId == personId }
                events = allEvents.filter { it.personId == personId }
                adjustments = allAdjustments.filter { it.personId == personId }
                auditLogs = emptyList()
                reminders = allReminders.filter { it.personId == personId }
                exportedSettings = null
            }
            ExportScope.SINGLE_CREDIT -> {
                val targetCredit = allCredits.firstOrNull { it.id == creditId }
                credits = listOfNotNull(targetCredit)
                persons = if (targetCredit != null) allPersons.filter { it.id == targetCredit.personId } else emptyList()
                payments = allPayments.filter { it.creditId == creditId }
                events = allEvents.filter { it.creditId == creditId }
                adjustments = allAdjustments.filter { it.creditId == creditId }
                auditLogs = emptyList()
                reminders = allReminders.filter { it.creditId == creditId }
                exportedSettings = null
            }
            ExportScope.TRANSACTIONS_ONLY -> {
                persons = emptyList()
                credits = emptyList()
                payments = allPayments
                events = allEvents
                adjustments = allAdjustments
                auditLogs = emptyList()
                reminders = emptyList()
                exportedSettings = null
            }
        }

        val root = JSONObject().apply {
            put("app", "CREDIT LOGGER")
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("exportScope", scope.name)
            put("exportTimestamp", System.currentTimeMillis())
            put("persons", JSONArray(persons.map { personToJson(it) }))
            put("credits", JSONArray(credits.map { creditToJson(it) }))
            put("payments", JSONArray(payments.map { paymentToJson(it) }))
            put("events", JSONArray(events.map { eventToJson(it) }))
            put("adjustments", JSONArray(adjustments.map { adjustmentToJson(it) }))
            put("auditLogs", JSONArray(auditLogs.map { auditToJson(it) }))
            put("reminders", JSONArray(reminders.map { reminderToJson(it) }))
            // Never export PIN secrets, salts, auth tokens, or Firebase credentials!
            if (exportedSettings != null) {
                put("preferences", settingsToJson(exportedSettings))
            }
        }
        return root.toString(2)
    }

    suspend fun parseAndPreviewImport(rawJson: String): ImportPreview {
        require(rawJson.toByteArray(Charsets.UTF_8).size <= MAX_IMPORT_BYTES) {
            "File exceeds maximum allowed backup size (10 MB)."
        }
        val root = JSONObject(rawJson)
        val schemaVersion = root.optInt("schemaVersion", -1)
        require(schemaVersion in 1..CURRENT_SCHEMA_VERSION) {
            "Unsupported or missing schemaVersion: $schemaVersion"
        }

        val persons = parseArray(root.optJSONArray("persons")) { jsonToPerson(it) }
        val credits = parseArray(root.optJSONArray("credits")) { jsonToCredit(it) }
        val payments = parseArray(root.optJSONArray("payments")) { jsonToPayment(it) }
        val events = parseArray(root.optJSONArray("events")) { jsonToEvent(it) }
        val adjustments = parseArray(root.optJSONArray("adjustments")) { jsonToAdjustment(it) }
        val auditLogs = parseArray(root.optJSONArray("auditLogs")) { jsonToAudit(it) }
        val reminders = parseArray(root.optJSONArray("reminders")) { jsonToReminder(it) }
        val settings = root.optJSONObject("preferences")?.let { jsonToSettings(it) }

        val existingPersons = db.personDao().getAll().map { it.id }.toSet()
        val existingCredits = db.creditDao().getAll().map { it.id }.toSet()
        val existingPayments = db.paymentDao().getAll().map { it.id }.toSet()
        val existingEvents = db.creditEventDao().getAll().map { it.id }.toSet()

        val importedPersonIds = persons.map { it.id }.toSet() + existingPersons
        val importedCreditIds = credits.map { it.id }.toSet() + existingCredits

        val warnings = mutableListOf<String>()
        if (root.optString("exportScope", "") == ExportScope.TRANSACTIONS_ONLY.name) {
            warnings.add("Transactions-only backup: use Safe Merge (Replace All is blocked to protect profiles & credits).")
        }
        credits.forEach { c ->
            if (c.personId !in importedPersonIds) {
                warnings.add("Credit ${c.creditNumber} references missing person ${c.personId}")
            }
        }
        payments.forEach { p ->
            if (p.personId !in importedPersonIds) {
                warnings.add("Payment ${p.receiptNumber} references missing person ${p.personId}")
            }
            if (p.creditId != null && p.creditId !in importedCreditIds) {
                warnings.add("Payment ${p.receiptNumber} references missing credit ${p.creditId}")
            }
        }

        val bundle = ParsedBackupBundle(
            schemaVersion = schemaVersion,
            exportScope = root.optString("exportScope", ExportScope.ALL_DATA.name),
            exportTimestamp = root.optLong("exportTimestamp", System.currentTimeMillis()),
            persons = persons,
            credits = credits,
            payments = payments,
            events = events,
            adjustments = adjustments,
            auditLogs = auditLogs,
            reminders = reminders,
            settings = settings
        )

        return ImportPreview(
            bundle = bundle,
            duplicatePersonIds = persons.count { it.id in existingPersons },
            duplicateCreditIds = credits.count { it.id in existingCredits },
            duplicatePaymentIds = payments.count { it.id in existingPayments },
            duplicateEventIds = events.count { it.id in existingEvents },
            missingReferenceWarnings = warnings.distinct(),
            sampleNames = persons.take(4).map { it.name }
        )
    }

    suspend fun applyImport(preview: ImportPreview, mode: ImportMode): ImportResultSummary {
        val b = preview.bundle
        val now = System.currentTimeMillis()
        var addedPersons = 0
        var addedCredits = 0
        var addedPayments = 0
        var addedEvents = 0
        var addedReminders = 0

        db.withTransaction {
            if (mode == ImportMode.REPLACE_ALL) {
                db.reminderDao().deleteAll()
                db.auditLogDao().deleteAll()
                db.adjustmentDao().deleteAll()
                db.paymentDao().deleteAll()
                db.creditEventDao().deleteAll()
                db.creditDao().deleteAll()
                db.personDao().deleteAll()

                db.personDao().upsertAll(b.persons.map { it.toEntity() })
                db.creditDao().upsertAll(b.credits.map { it.toEntity() })
                db.paymentDao().upsertAll(b.payments.map { it.toEntity() })
                db.creditEventDao().upsertAll(b.events.map { it.toEntity() })
                db.adjustmentDao().upsertAll(b.adjustments.map { it.toEntity() })
                db.auditLogDao().insertAll(b.auditLogs.map { it.toEntity() })
                db.reminderDao().upsertAll(b.reminders.map { it.toEntity() })
                b.settings?.let { db.settingsDao().upsert(it.toEntity()) }

                addedPersons = b.persons.size
                addedCredits = b.credits.size
                addedPayments = b.payments.size
                addedEvents = b.events.size
                addedReminders = b.reminders.size
            } else {
                // Safe Merge: preserve existing newer records, insert missing IDs
                val existingPersons = db.personDao().getAll().associateBy { it.id }
                val existingCredits = db.creditDao().getAll().associateBy { it.id }
                val existingCreditNums = db.creditDao().getAll().associateBy { it.creditNumber }
                val existingPayments = db.paymentDao().getAll().associateBy { it.id }
                val existingEvents = db.creditEventDao().getAll().associateBy { it.id }
                val existingReminders = db.reminderDao().getAll().associateBy { it.id }

                for (p in b.persons) {
                    val cur = existingPersons[p.id]
                    if (cur == null || p.updatedAt > cur.updatedAt) {
                        db.personDao().upsert(p.toEntity())
                        addedPersons++
                    }
                }
                for (c in b.credits) {
                    val cur = existingCredits[c.id]
                    val conflictNum = existingCreditNums[c.creditNumber]
                    if (cur == null && conflictNum == null) {
                        db.creditDao().upsert(c.toEntity())
                        addedCredits++
                    } else if (cur != null && c.updatedAt > cur.updatedAt) {
                        db.creditDao().upsert(c.toEntity())
                        addedCredits++
                    }
                }
                for (pay in b.payments) {
                    val cur = existingPayments[pay.id]
                    if (cur == null || pay.updatedAt > cur.updatedAt) {
                        db.paymentDao().upsert(pay.toEntity())
                        addedPayments++
                    }
                }
                for (ev in b.events) {
                    val cur = existingEvents[ev.id]
                    if (cur == null || ev.updatedAt > cur.updatedAt) {
                        db.creditEventDao().upsert(ev.toEntity())
                        addedEvents++
                    }
                }
                db.adjustmentDao().upsertAll(b.adjustments.map { it.toEntity() })
                for (rem in b.reminders) {
                    val cur = existingReminders[rem.id]
                    if (cur == null || rem.updatedAt > cur.updatedAt) {
                        db.reminderDao().upsert(rem.toEntity())
                        addedReminders++
                    }
                }
            }

            val allCredits = db.creditDao().getAll().map { it.toDomain() }
            val today = LedgerDate.todayLocal()
            for (credit in allCredits) {
                val events = db.creditEventDao().getByCreditId(credit.id).map { it.toDomain() }
                val startDate = LedgerDate.parseOrNull(credit.startDate) ?: today
                val maxDate = events.mapNotNull { LedgerDate.parseOrNull(it.effectiveDate) }.maxOrNull()
                val asOf = maxOf(today, startDate, maxDate ?: today)
                val snap = InterestEngine.calculateCreditBalanceAsOfDate(credit, events, asOf)
                db.creditDao().upsert(
                    credit.copy(
                        currentPrincipalPaise = snap.currentPrincipalPaise,
                        status = snap.status
                    ).toEntity()
                )
            }

            db.auditLogDao().insert(
                AuditLog(
                    id = UUID.randomUUID().toString(),
                    entityType = "BACKUP",
                    entityId = "json_import",
                    action = mode.name,
                    oldData = "",
                    newData = "persons=$addedPersons, credits=$addedCredits, payments=$addedPayments, events=$addedEvents",
                    timestamp = now
                ).toEntity()
            )
        }

        return ImportResultSummary(
            importedPersons = addedPersons,
            importedCredits = addedCredits,
            importedPayments = addedPayments,
            importedEvents = addedEvents,
            importedReminders = addedReminders,
            warnings = preview.missingReferenceWarnings
        )
    }

    private fun <T> parseArray(arr: JSONArray?, mapper: (JSONObject) -> T): List<T> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { mapper(arr.getJSONObject(it)) }
    }

    private fun personToJson(p: Person) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("phone", p.phone)
        put("address", p.address)
        put("photoRef", p.photoRef)
        put("note", p.note)
        put("createdAt", p.createdAt)
        put("updatedAt", p.updatedAt)
        if (p.archivedAt != null) put("archivedAt", p.archivedAt) else put("archivedAt", JSONObject.NULL)
    }

    private fun jsonToPerson(j: JSONObject) = Person(
        id = j.getString("id"),
        name = j.getString("name"),
        phone = j.optString("phone", ""),
        address = j.optString("address", ""),
        photoRef = j.optString("photoRef", ""),
        note = j.optString("note", ""),
        createdAt = j.getLong("createdAt"),
        updatedAt = j.getLong("updatedAt"),
        archivedAt = if (j.isNull("archivedAt")) null else j.optLong("archivedAt")
    )

    private fun creditToJson(c: Credit) = JSONObject().apply {
        put("id", c.id)
        put("personId", c.personId)
        put("creditNumber", c.creditNumber)
        put("originalPrincipalPaise", c.originalPrincipalPaise)
        put("currentPrincipalPaise", c.currentPrincipalPaise)
        put("interestRate", c.interestRate)
        put("ratePeriod", c.ratePeriod.name)
        put("interestMethod", c.interestMethod.name)
        put("startDate", c.startDate)
        if (c.dueDate != null) put("dueDate", c.dueDate) else put("dueDate", JSONObject.NULL)
        put("status", c.status.name)
        put("note", c.note)
        put("createdAt", c.createdAt)
        put("updatedAt", c.updatedAt)
        if (c.archivedAt != null) put("archivedAt", c.archivedAt) else put("archivedAt", JSONObject.NULL)
    }

    private fun jsonToCredit(j: JSONObject) = Credit(
        id = j.getString("id"),
        personId = j.getString("personId"),
        creditNumber = j.getString("creditNumber"),
        originalPrincipalPaise = j.getLong("originalPrincipalPaise"),
        currentPrincipalPaise = j.getLong("currentPrincipalPaise"),
        interestRate = j.getString("interestRate"),
        ratePeriod = RatePeriod.valueOf(j.optString("ratePeriod", RatePeriod.MONTHLY.name)),
        interestMethod = InterestMethod.valueOf(j.optString("interestMethod", InterestMethod.SIMPLE.name)),
        startDate = j.getString("startDate"),
        dueDate = if (j.isNull("dueDate")) null else j.optString("dueDate"),
        status = CreditStatus.valueOf(j.optString("status", CreditStatus.ACTIVE.name)),
        note = j.optString("note", ""),
        createdAt = j.getLong("createdAt"),
        updatedAt = j.getLong("updatedAt"),
        archivedAt = if (j.isNull("archivedAt")) null else j.optLong("archivedAt")
    )

    private fun paymentToJson(p: Payment) = JSONObject().apply {
        put("id", p.id)
        put("personId", p.personId)
        if (p.creditId != null) put("creditId", p.creditId) else put("creditId", JSONObject.NULL)
        put("paymentType", p.paymentType.name)
        put("amountPaise", p.amountPaise)
        put("paymentDate", p.paymentDate)
        put("paymentMethod", p.paymentMethod.name)
        put("receiptNumber", p.receiptNumber)
        if (p.allocationMode != null) put("allocationMode", p.allocationMode.name) else put("allocationMode", JSONObject.NULL)
        put("systemNote", p.systemNote)
        put("note", p.note)
        put("createdAt", p.createdAt)
        put("updatedAt", p.updatedAt)
        put("status", p.status)
        if (p.reversesPaymentId != null) put("reversesPaymentId", p.reversesPaymentId) else put("reversesPaymentId", JSONObject.NULL)
    }

    private fun jsonToPayment(j: JSONObject) = Payment(
        id = j.getString("id"),
        personId = j.getString("personId"),
        creditId = if (j.isNull("creditId")) null else j.optString("creditId"),
        paymentType = PaymentType.valueOf(j.optString("paymentType", PaymentType.CREDIT.name)),
        amountPaise = j.getLong("amountPaise"),
        paymentDate = j.getString("paymentDate"),
        paymentMethod = PaymentMethod.valueOf(j.optString("paymentMethod", PaymentMethod.CASH.name)),
        receiptNumber = j.getString("receiptNumber"),
        allocationMode = if (j.isNull("allocationMode")) null else AllocationMode.valueOf(j.getString("allocationMode")),
        systemNote = j.optString("systemNote", ""),
        note = j.optString("note", ""),
        createdAt = j.getLong("createdAt"),
        updatedAt = j.getLong("updatedAt"),
        status = j.optString("status", "COMPLETED"),
        reversesPaymentId = if (j.isNull("reversesPaymentId")) null else j.optString("reversesPaymentId")
    )

    private fun eventToJson(e: CreditEvent) = JSONObject().apply {
        put("id", e.id)
        put("personId", e.personId)
        put("creditId", e.creditId)
        put("eventType", e.eventType.name)
        put("effectiveDate", e.effectiveDate)
        put("createdAt", e.createdAt)
        put("updatedAt", e.updatedAt)
        put("principalBeforePaise", e.principalBeforePaise)
        put("interestBeforePaise", e.interestBeforePaise)
        put("misradhanBeforePaise", e.misradhanBeforePaise)
        put("amountPaise", e.amountPaise)
        put("principalAfterPaise", e.principalAfterPaise)
        put("interestAfterPaise", e.interestAfterPaise)
        put("rateAfter", e.rateAfter)
        put("ratePeriodAfter", e.ratePeriodAfter.name)
        if (e.allocationMode != null) put("allocationMode", e.allocationMode.name) else put("allocationMode", JSONObject.NULL)
        if (e.paymentMethod != null) put("paymentMethod", e.paymentMethod.name) else put("paymentMethod", JSONObject.NULL)
        if (e.receiptNumber != null) put("receiptNumber", e.receiptNumber) else put("receiptNumber", JSONObject.NULL)
        put("advancePaise", e.advancePaise)
        put("systemNote", e.systemNote)
        put("note", e.note)
        if (e.reversesEventId != null) put("reversesEventId", e.reversesEventId) else put("reversesEventId", JSONObject.NULL)
        put("isReversed", e.isReversed)
    }

    private fun jsonToEvent(j: JSONObject) = CreditEvent(
        id = j.getString("id"),
        personId = j.getString("personId"),
        creditId = j.getString("creditId"),
        eventType = CreditEventType.valueOf(j.getString("eventType")),
        effectiveDate = j.getString("effectiveDate"),
        createdAt = j.getLong("createdAt"),
        updatedAt = j.getLong("updatedAt"),
        principalBeforePaise = j.getLong("principalBeforePaise"),
        interestBeforePaise = j.getLong("interestBeforePaise"),
        misradhanBeforePaise = j.getLong("misradhanBeforePaise"),
        amountPaise = j.getLong("amountPaise"),
        principalAfterPaise = j.getLong("principalAfterPaise"),
        interestAfterPaise = j.optLong("interestAfterPaise", 0L),
        rateAfter = j.optString("rateAfter", "0"),
        ratePeriodAfter = RatePeriod.valueOf(j.optString("ratePeriodAfter", RatePeriod.MONTHLY.name)),
        allocationMode = if (j.isNull("allocationMode")) null else AllocationMode.valueOf(j.getString("allocationMode")),
        paymentMethod = if (j.isNull("paymentMethod")) null else PaymentMethod.valueOf(j.getString("paymentMethod")),
        receiptNumber = if (j.isNull("receiptNumber")) null else j.optString("receiptNumber"),
        advancePaise = j.optLong("advancePaise", 0L),
        systemNote = j.optString("systemNote", ""),
        note = j.optString("note", ""),
        reversesEventId = if (j.isNull("reversesEventId")) null else j.optString("reversesEventId"),
        isReversed = j.optBoolean("isReversed", false)
    )

    private fun adjustmentToJson(a: Adjustment) = JSONObject().apply {
        put("id", a.id)
        put("creditId", a.creditId)
        put("personId", a.personId)
        put("effectiveDate", a.effectiveDate)
        put("oldPrincipalPaise", a.oldPrincipalPaise)
        put("newPrincipalPaise", a.newPrincipalPaise)
        put("oldRate", a.oldRate)
        put("newRate", a.newRate)
        put("oldRatePeriod", a.oldRatePeriod.name)
        put("newRatePeriod", a.newRatePeriod.name)
        put("note", a.note)
        put("createdAt", a.createdAt)
        put("updatedAt", a.updatedAt)
    }

    private fun jsonToAdjustment(j: JSONObject) = Adjustment(
        id = j.getString("id"),
        creditId = j.getString("creditId"),
        personId = j.getString("personId"),
        effectiveDate = j.getString("effectiveDate"),
        oldPrincipalPaise = j.getLong("oldPrincipalPaise"),
        newPrincipalPaise = j.getLong("newPrincipalPaise"),
        oldRate = j.getString("oldRate"),
        newRate = j.getString("newRate"),
        oldRatePeriod = RatePeriod.valueOf(j.optString("oldRatePeriod", RatePeriod.MONTHLY.name)),
        newRatePeriod = RatePeriod.valueOf(j.optString("newRatePeriod", RatePeriod.MONTHLY.name)),
        note = j.optString("note", ""),
        createdAt = j.getLong("createdAt"),
        updatedAt = j.getLong("updatedAt")
    )

    private fun auditToJson(a: AuditLog) = JSONObject().apply {
        put("id", a.id)
        put("entityType", a.entityType)
        put("entityId", a.entityId)
        put("action", a.action)
        put("oldData", a.oldData)
        put("newData", a.newData)
        put("timestamp", a.timestamp)
    }

    private fun jsonToAudit(j: JSONObject) = AuditLog(
        id = j.getString("id"),
        entityType = j.getString("entityType"),
        entityId = j.getString("entityId"),
        action = j.getString("action"),
        oldData = j.optString("oldData", ""),
        newData = j.optString("newData", ""),
        timestamp = j.getLong("timestamp")
    )

    private fun reminderToJson(r: Reminder) = JSONObject().apply {
        put("id", r.id)
        put("personId", r.personId)
        if (r.creditId != null) put("creditId", r.creditId) else put("creditId", JSONObject.NULL)
        put("reminderDate", r.reminderDate)
        put("reminderTime", r.reminderTime)
        put("note", r.note)
        put("status", r.status.name)
        put("createdAt", r.createdAt)
        put("updatedAt", r.updatedAt)
    }

    private fun jsonToReminder(j: JSONObject) = Reminder(
        id = j.getString("id"),
        personId = j.getString("personId"),
        creditId = if (j.isNull("creditId")) null else j.optString("creditId"),
        reminderDate = j.getString("reminderDate"),
        reminderTime = j.optString("reminderTime", "10:00"),
        note = j.optString("note", ""),
        status = ReminderStatus.valueOf(j.optString("status", ReminderStatus.UPCOMING.name)),
        createdAt = j.getLong("createdAt"),
        updatedAt = j.getLong("updatedAt")
    )

    private fun settingsToJson(s: AppSettings) = JSONObject().apply {
        put("theme", s.theme.name)
        put("language", s.language.name)
        put("defaultRatePeriod", s.defaultRatePeriod.name)
        put("defaultInterestMethod", s.defaultInterestMethod.name)
        put("allowCompoundInterest", s.allowCompoundInterest)
        put("defaultAllocationMode", s.defaultAllocationMode.name)
        put("displayDecimals", s.displayDecimals)
        put("dateFormat", s.dateFormat)
        put("notificationsEnabled", s.notificationsEnabled)
        put("overdueAlertsEnabled", s.overdueAlertsEnabled)
        put("backupReminderEnabled", s.backupReminderEnabled)
    }

    private fun jsonToSettings(j: JSONObject) = AppSettings(
        theme = AppThemeMode.valueOf(j.optString("theme", AppThemeMode.DARK.name)),
        language = AppLanguage.valueOf(j.optString("language", AppLanguage.HINDI.name)),
        defaultRatePeriod = RatePeriod.valueOf(j.optString("defaultRatePeriod", RatePeriod.MONTHLY.name)),
        defaultInterestMethod = InterestMethod.valueOf(j.optString("defaultInterestMethod", InterestMethod.SIMPLE.name)),
        allowCompoundInterest = j.optBoolean("allowCompoundInterest", false),
        defaultAllocationMode = AllocationMode.valueOf(j.optString("defaultAllocationMode", AllocationMode.PRINCIPAL_ADJUSTMENT.name)),
        displayDecimals = j.optInt("displayDecimals", 2),
        dateFormat = j.optString("dateFormat", "DD/MM/YYYY"),
        notificationsEnabled = j.optBoolean("notificationsEnabled", true),
        overdueAlertsEnabled = j.optBoolean("overdueAlertsEnabled", true),
        backupReminderEnabled = j.optBoolean("backupReminderEnabled", true),
        onboardingCompleted = true,
        updatedAt = System.currentTimeMillis()
    )
}
