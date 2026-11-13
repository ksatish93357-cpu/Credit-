package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
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

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val photoRef: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long?
)

@Entity(
    tableName = "credits",
    indices = [
        Index(value = ["personId"]),
        Index(value = ["creditNumber"], unique = true)
    ]
)
data class CreditEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val creditNumber: String,
    val originalPrincipalPaise: Long,
    val currentPrincipalPaise: Long,
    val interestRate: String,
    val ratePeriod: String,
    val interestMethod: String,
    val startDate: String,
    val dueDate: String?,
    val status: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long?
)

@Entity(
    tableName = "credit_events",
    indices = [
        Index(value = ["personId"]),
        Index(value = ["creditId"]),
        Index(value = ["effectiveDate"])
    ]
)
data class CreditEventEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val creditId: String,
    val eventType: String,
    val effectiveDate: String,
    val createdAt: Long,
    val updatedAt: Long,
    val principalBeforePaise: Long,
    val interestBeforePaise: Long,
    val misradhanBeforePaise: Long,
    val amountPaise: Long,
    val principalAfterPaise: Long,
    val interestAfterPaise: Long,
    val rateAfter: String,
    val ratePeriodAfter: String,
    val allocationMode: String?,
    val paymentMethod: String?,
    val receiptNumber: String?,
    val advancePaise: Long,
    val systemNote: String,
    val note: String,
    val reversesEventId: String?,
    val isReversed: Boolean
)

@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["personId"]),
        Index(value = ["creditId"]),
        Index(value = ["receiptNumber"], unique = true)
    ]
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val creditId: String?,
    val paymentType: String,
    val amountPaise: Long,
    val paymentDate: String,
    val paymentMethod: String,
    val receiptNumber: String,
    val allocationMode: String?,
    val systemNote: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val reversesPaymentId: String?
)

@Entity(
    tableName = "adjustments",
    indices = [Index(value = ["creditId"]), Index(value = ["personId"])]
)
data class AdjustmentEntity(
    @PrimaryKey val id: String,
    val creditId: String,
    val personId: String,
    val effectiveDate: String,
    val oldPrincipalPaise: Long,
    val newPrincipalPaise: Long,
    val oldRate: String,
    val newRate: String,
    val oldRatePeriod: String,
    val newRatePeriod: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val oldData: String,
    val newData: String,
    val timestamp: Long
)

@Entity(
    tableName = "reminders",
    indices = [Index(value = ["personId"]), Index(value = ["creditId"])]
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val creditId: String?,
    val reminderDate: String,
    val reminderTime: String,
    val note: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: String,
    val theme: String,
    val language: String,
    val defaultRatePeriod: String,
    val defaultInterestMethod: String,
    val allowCompoundInterest: Boolean,
    val defaultAllocationMode: String,
    val displayDecimals: Int,
    val dateFormat: String,
    val notificationsEnabled: Boolean,
    val overdueAlertsEnabled: Boolean,
    val backupReminderEnabled: Boolean,
    val lastBackupTimestamp: Long,
    val onboardingCompleted: Boolean,
    val updatedAt: Long
)

// Mappers
fun PersonEntity.toDomain() = Person(
    id = id,
    name = name,
    phone = phone,
    address = address,
    photoRef = photoRef,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt
)

fun Person.toEntity() = PersonEntity(
    id = id,
    name = name,
    phone = phone,
    address = address,
    photoRef = photoRef,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt
)

fun CreditEntity.toDomain() = Credit(
    id = id,
    personId = personId,
    creditNumber = creditNumber,
    originalPrincipalPaise = originalPrincipalPaise,
    currentPrincipalPaise = currentPrincipalPaise,
    interestRate = interestRate,
    ratePeriod = runCatching { RatePeriod.valueOf(ratePeriod) }.getOrDefault(RatePeriod.MONTHLY),
    interestMethod = runCatching { InterestMethod.valueOf(interestMethod) }.getOrDefault(InterestMethod.SIMPLE),
    startDate = startDate,
    dueDate = dueDate,
    status = runCatching { CreditStatus.valueOf(status) }.getOrDefault(CreditStatus.ACTIVE),
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt
)

fun Credit.toEntity() = CreditEntity(
    id = id,
    personId = personId,
    creditNumber = creditNumber,
    originalPrincipalPaise = originalPrincipalPaise,
    currentPrincipalPaise = currentPrincipalPaise,
    interestRate = interestRate,
    ratePeriod = ratePeriod.name,
    interestMethod = interestMethod.name,
    startDate = startDate,
    dueDate = dueDate,
    status = status.name,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt
)

fun CreditEventEntity.toDomain() = CreditEvent(
    id = id,
    personId = personId,
    creditId = creditId,
    eventType = runCatching { CreditEventType.valueOf(eventType) }.getOrDefault(CreditEventType.CREDIT_CREATED),
    effectiveDate = effectiveDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    principalBeforePaise = principalBeforePaise,
    interestBeforePaise = interestBeforePaise,
    misradhanBeforePaise = misradhanBeforePaise,
    amountPaise = amountPaise,
    principalAfterPaise = principalAfterPaise,
    interestAfterPaise = interestAfterPaise,
    rateAfter = rateAfter,
    ratePeriodAfter = runCatching { RatePeriod.valueOf(ratePeriodAfter) }.getOrDefault(RatePeriod.MONTHLY),
    allocationMode = allocationMode?.let { runCatching { AllocationMode.valueOf(it) }.getOrNull() },
    paymentMethod = paymentMethod?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() },
    receiptNumber = receiptNumber,
    advancePaise = advancePaise,
    systemNote = systemNote,
    note = note,
    reversesEventId = reversesEventId,
    isReversed = isReversed
)

fun CreditEvent.toEntity() = CreditEventEntity(
    id = id,
    personId = personId,
    creditId = creditId,
    eventType = eventType.name,
    effectiveDate = effectiveDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    principalBeforePaise = principalBeforePaise,
    interestBeforePaise = interestBeforePaise,
    misradhanBeforePaise = misradhanBeforePaise,
    amountPaise = amountPaise,
    principalAfterPaise = principalAfterPaise,
    interestAfterPaise = interestAfterPaise,
    rateAfter = rateAfter,
    ratePeriodAfter = ratePeriodAfter.name,
    allocationMode = allocationMode?.name,
    paymentMethod = paymentMethod?.name,
    receiptNumber = receiptNumber,
    advancePaise = advancePaise,
    systemNote = systemNote,
    note = note,
    reversesEventId = reversesEventId,
    isReversed = isReversed
)

fun PaymentEntity.toDomain() = Payment(
    id = id,
    personId = personId,
    creditId = creditId,
    paymentType = runCatching { PaymentType.valueOf(paymentType) }.getOrDefault(PaymentType.CREDIT),
    amountPaise = amountPaise,
    paymentDate = paymentDate,
    paymentMethod = runCatching { PaymentMethod.valueOf(paymentMethod) }.getOrDefault(PaymentMethod.CASH),
    receiptNumber = receiptNumber,
    allocationMode = allocationMode?.let { runCatching { AllocationMode.valueOf(it) }.getOrNull() },
    systemNote = systemNote,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    status = status,
    reversesPaymentId = reversesPaymentId
)

fun Payment.toEntity() = PaymentEntity(
    id = id,
    personId = personId,
    creditId = creditId,
    paymentType = paymentType.name,
    amountPaise = amountPaise,
    paymentDate = paymentDate,
    paymentMethod = paymentMethod.name,
    receiptNumber = receiptNumber,
    allocationMode = allocationMode?.name,
    systemNote = systemNote,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    status = status,
    reversesPaymentId = reversesPaymentId
)

fun AdjustmentEntity.toDomain() = Adjustment(
    id = id,
    creditId = creditId,
    personId = personId,
    effectiveDate = effectiveDate,
    oldPrincipalPaise = oldPrincipalPaise,
    newPrincipalPaise = newPrincipalPaise,
    oldRate = oldRate,
    newRate = newRate,
    oldRatePeriod = runCatching { RatePeriod.valueOf(oldRatePeriod) }.getOrDefault(RatePeriod.MONTHLY),
    newRatePeriod = runCatching { RatePeriod.valueOf(newRatePeriod) }.getOrDefault(RatePeriod.MONTHLY),
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Adjustment.toEntity() = AdjustmentEntity(
    id = id,
    creditId = creditId,
    personId = personId,
    effectiveDate = effectiveDate,
    oldPrincipalPaise = oldPrincipalPaise,
    newPrincipalPaise = newPrincipalPaise,
    oldRate = oldRate,
    newRate = newRate,
    oldRatePeriod = oldRatePeriod.name,
    newRatePeriod = newRatePeriod.name,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun AuditLogEntity.toDomain() = AuditLog(
    id = id,
    entityType = entityType,
    entityId = entityId,
    action = action,
    oldData = oldData,
    newData = newData,
    timestamp = timestamp
)

fun AuditLog.toEntity() = AuditLogEntity(
    id = id,
    entityType = entityType,
    entityId = entityId,
    action = action,
    oldData = oldData,
    newData = newData,
    timestamp = timestamp
)

fun ReminderEntity.toDomain() = Reminder(
    id = id,
    personId = personId,
    creditId = creditId,
    reminderDate = reminderDate,
    reminderTime = reminderTime,
    note = note,
    status = runCatching { ReminderStatus.valueOf(status) }.getOrDefault(ReminderStatus.UPCOMING),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Reminder.toEntity() = ReminderEntity(
    id = id,
    personId = personId,
    creditId = creditId,
    reminderDate = reminderDate,
    reminderTime = reminderTime,
    note = note,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SettingsEntity.toDomain() = AppSettings(
    id = id,
    theme = runCatching { AppThemeMode.valueOf(theme) }.getOrDefault(AppThemeMode.DARK),
    language = runCatching { AppLanguage.valueOf(language) }.getOrDefault(AppLanguage.HINDI),
    defaultRatePeriod = runCatching { RatePeriod.valueOf(defaultRatePeriod) }.getOrDefault(RatePeriod.MONTHLY),
    defaultInterestMethod = runCatching { InterestMethod.valueOf(defaultInterestMethod) }.getOrDefault(InterestMethod.SIMPLE),
    allowCompoundInterest = allowCompoundInterest,
    defaultAllocationMode = runCatching { AllocationMode.valueOf(defaultAllocationMode) }.getOrDefault(AllocationMode.PRINCIPAL_ADJUSTMENT),
    displayDecimals = displayDecimals,
    dateFormat = dateFormat,
    notificationsEnabled = notificationsEnabled,
    overdueAlertsEnabled = overdueAlertsEnabled,
    backupReminderEnabled = backupReminderEnabled,
    lastBackupTimestamp = lastBackupTimestamp,
    onboardingCompleted = onboardingCompleted,
    updatedAt = updatedAt
)

fun AppSettings.toEntity() = SettingsEntity(
    id = id,
    theme = theme.name,
    language = language.name,
    defaultRatePeriod = defaultRatePeriod.name,
    defaultInterestMethod = defaultInterestMethod.name,
    allowCompoundInterest = allowCompoundInterest,
    defaultAllocationMode = defaultAllocationMode.name,
    displayDecimals = displayDecimals,
    dateFormat = dateFormat,
    notificationsEnabled = notificationsEnabled,
    overdueAlertsEnabled = overdueAlertsEnabled,
    backupReminderEnabled = backupReminderEnabled,
    lastBackupTimestamp = lastBackupTimestamp,
    onboardingCompleted = onboardingCompleted,
    updatedAt = updatedAt
)
