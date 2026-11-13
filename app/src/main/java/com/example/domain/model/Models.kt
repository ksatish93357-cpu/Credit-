package com.example.domain.model

enum class RatePeriod {
    MONTHLY,
    YEARLY
}

enum class InterestMethod {
    SIMPLE,
    COMPOUND
}

enum class CreditStatus {
    ACTIVE,
    CLOSED,
    ARCHIVED
}

enum class AllocationMode {
    INTEREST_FIRST,
    PRINCIPAL_ADJUSTMENT
}

enum class PaymentType {
    CREDIT,
    NORMAL
}

enum class PaymentMethod {
    CASH,
    UPI,
    BANK_TRANSFER,
    OTHER
}

enum class CreditEventType {
    CREDIT_CREATED,
    CREDIT_PAYMENT,
    NORMAL_PAYMENT,
    PRINCIPAL_ADJUSTMENT,
    INTEREST_RATE_UPDATE,
    CREDIT_CLOSED,
    CREDIT_REOPENED,
    CORRECTION_REVERSAL
}

enum class ReminderStatus {
    UPCOMING,
    COMPLETED,
    DISMISSED
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppLanguage {
    HINDI,
    ENGLISH
}

enum class DashboardFilter {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    THIS_YEAR,
    CUSTOM,
    ALL_TIME
}

enum class ProfileSortOption {
    HIGHEST_OUTSTANDING,
    LOWEST_OUTSTANDING,
    NAME_ASC,
    RECENT
}

enum class ProfileFilterOption {
    ALL,
    ACTIVE,
    SETTLED,
    ARCHIVED
}

enum class TransactionSortOption {
    NEWEST_FIRST,
    OLDEST_FIRST,
    AMOUNT_HIGH_LOW,
    AMOUNT_LOW_HIGH
}

data class Person(
    val id: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val photoRef: String = "",
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long? = null
) {
    val isArchived: Boolean get() = archivedAt != null
}

data class Credit(
    val id: String,
    val personId: String,
    val creditNumber: String,
    val originalPrincipalPaise: Long,
    val currentPrincipalPaise: Long,
    val interestRate: String,
    val ratePeriod: RatePeriod,
    val interestMethod: InterestMethod = InterestMethod.SIMPLE,
    val startDate: String, // YYYY-MM-DD
    val dueDate: String? = null, // Optional manual due date, separate from interest start date
    val status: CreditStatus = CreditStatus.ACTIVE,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long? = null
)

data class CreditEvent(
    val id: String,
    val personId: String,
    val creditId: String,
    val eventType: CreditEventType,
    val effectiveDate: String, // YYYY-MM-DD
    val createdAt: Long,
    val updatedAt: Long,
    val principalBeforePaise: Long,
    val interestBeforePaise: Long,
    val misradhanBeforePaise: Long,
    val amountPaise: Long,
    val principalAfterPaise: Long,
    val interestAfterPaise: Long = 0L,
    val rateAfter: String,
    val ratePeriodAfter: RatePeriod,
    val allocationMode: AllocationMode? = null,
    val paymentMethod: PaymentMethod? = null,
    val receiptNumber: String? = null,
    val advancePaise: Long = 0L,
    val systemNote: String = "",
    val note: String = "",
    val reversesEventId: String? = null,
    val isReversed: Boolean = false
)

data class Payment(
    val id: String,
    val personId: String,
    val creditId: String? = null, // null for Normal Payment
    val paymentType: PaymentType,
    val amountPaise: Long,
    val paymentDate: String, // YYYY-MM-DD
    val paymentMethod: PaymentMethod,
    val receiptNumber: String,
    val allocationMode: AllocationMode? = null,
    val systemNote: String = "",
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val status: String = "COMPLETED", // COMPLETED or REVERSED
    val reversesPaymentId: String? = null
)

data class Adjustment(
    val id: String,
    val creditId: String,
    val personId: String,
    val effectiveDate: String,
    val oldPrincipalPaise: Long,
    val newPrincipalPaise: Long,
    val oldRate: String,
    val newRate: String,
    val oldRatePeriod: RatePeriod,
    val newRatePeriod: RatePeriod,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

data class AuditLog(
    val id: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val oldData: String,
    val newData: String,
    val timestamp: Long
)

data class Reminder(
    val id: String,
    val personId: String,
    val creditId: String? = null,
    val reminderDate: String, // YYYY-MM-DD
    val reminderTime: String, // HH:mm
    val note: String,
    val status: ReminderStatus = ReminderStatus.UPCOMING,
    val createdAt: Long,
    val updatedAt: Long
)

data class AppSettings(
    val id: String = "default",
    val theme: AppThemeMode = AppThemeMode.DARK,
    val language: AppLanguage = AppLanguage.HINDI,
    val defaultRatePeriod: RatePeriod = RatePeriod.MONTHLY,
    val defaultInterestMethod: InterestMethod = InterestMethod.SIMPLE,
    val allowCompoundInterest: Boolean = false,
    val defaultAllocationMode: AllocationMode = AllocationMode.PRINCIPAL_ADJUSTMENT,
    val displayDecimals: Int = 2,
    val dateFormat: String = "DD/MM/YYYY",
    val notificationsEnabled: Boolean = true,
    val overdueAlertsEnabled: Boolean = true,
    val backupReminderEnabled: Boolean = true,
    val lastBackupTimestamp: Long = 0L,
    val onboardingCompleted: Boolean = false,
    val updatedAt: Long = 0L
)

data class FirebaseCloudConfig(
    val apiKey: String = "",
    val authDomain: String = "",
    val projectId: String = "",
    val storageBucket: String = "",
    val messagingSenderId: String = "",
    val appId: String = "",
    val userEmail: String = "",
    val isConnected: Boolean = false,
    val lastSyncTime: Long = 0L,
    val lastSyncStatus: String = "",
    val lastError: String = ""
)
