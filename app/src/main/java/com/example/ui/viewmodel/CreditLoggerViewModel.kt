package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.export.ExportScope
import com.example.data.export.ImportMode
import com.example.data.export.ImportPreview
import com.example.data.export.ImportResultSummary
import com.example.data.export.JsonBackupManager
import com.example.data.export.PdfAndShareManager
import com.example.data.local.CreditLoggerDatabase
import com.example.data.remote.FirebaseSyncManager
import com.example.data.repository.LedgerRepository
import com.example.domain.engine.DashboardFinancialSummary
import com.example.domain.engine.InterestEngine
import com.example.domain.engine.LedgerDate
import com.example.domain.engine.PersonFinancialSummary
import com.example.domain.engine.StatementTextGenerator
import com.example.domain.model.Adjustment
import com.example.domain.model.AllocationMode
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppSettings
import com.example.domain.model.AppThemeMode
import com.example.domain.model.AuditLog
import com.example.domain.model.Credit
import com.example.domain.model.CreditEvent
import com.example.domain.model.DashboardFilter
import com.example.domain.model.FirebaseCloudConfig
import com.example.domain.model.InterestMethod
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Person
import com.example.domain.model.RatePeriod
import com.example.domain.model.Reminder
import com.example.domain.model.ReminderStatus
import com.example.notifications.ReminderScheduler
import com.example.security.SecurityPreferencesStore
import com.example.security.SecurityState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class MainUiState(
    val isLoading: Boolean = true,
    val persons: List<Person> = emptyList(),
    val credits: List<Credit> = emptyList(),
    val events: List<CreditEvent> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val adjustments: List<Adjustment> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val securityState: SecurityState = SecurityState(),
    val isAppLocked: Boolean = false,
    val firebaseConfig: FirebaseCloudConfig = FirebaseCloudConfig(),
    val calculatorHistory: List<String> = emptyList(),
    val dashboardFilter: DashboardFilter = DashboardFilter.THIS_MONTH,
    val customStartDate: LedgerDate = LedgerDate.todayLocal().plusDays(-30),
    val customEndDate: LedgerDate = LedgerDate.todayLocal(),
    val asOfDate: LedgerDate = LedgerDate.todayLocal(),
    val dashboardSummary: DashboardFinancialSummary = DashboardFinancialSummary(
        totalPrincipalPaise = 0L,
        totalAccruedUnpaidInterestPaise = 0L,
        totalMisradhanPaise = 0L,
        totalPaidAmountPaise = 0L,
        profilesCount = 0,
        activeCreditsCount = 0,
        closedCreditsCount = 0,
        totalCreditRecordsCount = 0,
        todaysInterestAccrualPaise = 0L,
        totalOriginallyAdvancedPaise = 0L,
        periodCreditPaymentsPaise = 0L,
        periodNormalPaymentsPaise = 0L,
        periodOriginallyAdvancedPaise = 0L,
        overdueCredits = emptyList()
    ),
    val personSummaries: Map<String, PersonFinancialSummary> = emptyMap(),
    val dbSizeBytes: Long = 0L,
    val isBusy: Boolean = false,
    val toastMessage: String? = null,
    val errorMessage: String? = null
)

class CreditLoggerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val db = CreditLoggerDatabase.getInstance(appContext)
    val repository = LedgerRepository(db, appContext)
    private val securityStore = SecurityPreferencesStore(appContext)
    private val backupManager = JsonBackupManager(db)
    private val firebaseSyncManager = FirebaseSyncManager(appContext, db, securityStore)

    private val dashboardFilterFlow = MutableStateFlow(DashboardFilter.THIS_MONTH)
    private val customRangeFlow = MutableStateFlow(
        LedgerDate.todayLocal().plusDays(-30) to LedgerDate.todayLocal()
    )
    private val asOfDateFlow = MutableStateFlow(LedgerDate.todayLocal())
    private val isLockedFlow = MutableStateFlow(false)
    private val hasCheckedInitialLock = MutableStateFlow(false)
    private val isBusyFlow = MutableStateFlow(false)
    private var activeTasksCount = 0
    private val toastMessageFlow = MutableStateFlow<String?>(null)
    private val errorMessageFlow = MutableStateFlow<String?>(null)
    private val dbSizeFlow = MutableStateFlow(0L)

    init {
        refreshDbSize()
    }

    private val coreDataFlow = combine(
        repository.personsFlow,
        repository.creditsFlow,
        repository.eventsFlow,
        repository.paymentsFlow,
        repository.adjustmentsFlow
    ) { persons, credits, events, payments, adjustments ->
        CoreData(persons, credits, events, payments, adjustments)
    }

    private val metaDataFlow = combine(
        repository.auditLogsFlow,
        repository.remindersFlow,
        repository.settingsFlow,
        securityStore.securityState,
        securityStore.firebaseConfig
    ) { audits, reminders, settings, security, fbConfig ->
        if (!hasCheckedInitialLock.value) {
            hasCheckedInitialLock.value = true
            if (security.hasPin) isLockedFlow.value = true
        }
        MetaData(audits, reminders, settings, security, fbConfig)
    }

    private val filterDataFlow = combine(
        dashboardFilterFlow,
        customRangeFlow,
        asOfDateFlow,
        isLockedFlow,
        securityStore.calculatorHistory
    ) { filter, customRange, asOf, locked, calcHistory ->
        FilterData(filter, customRange.first, customRange.second, asOf, locked, calcHistory)
    }

    private val statusFlow = combine(
        isBusyFlow,
        toastMessageFlow,
        errorMessageFlow,
        dbSizeFlow
    ) { busy, toast, err, dbSize ->
        StatusData(busy, toast, err, dbSize)
    }

    val uiState: StateFlow<MainUiState> = combine(
        coreDataFlow,
        metaDataFlow,
        filterDataFlow,
        statusFlow
    ) { core, meta, filter, status ->
        val (startFilter, endFilter) = resolveFilterRange(
            filter.dashboardFilter,
            filter.asOfDate,
            filter.customStart,
            filter.customEnd
        )
        val personSummaries = core.persons.associate { person ->
            person.id to InterestEngine.calculatePersonSummary(
                person = person,
                credits = core.credits,
                allEvents = core.events,
                allPayments = core.payments,
                asOfDate = filter.asOfDate
            )
        }
        val dashSummary = InterestEngine.calculateDashboardSummary(
            persons = core.persons,
            credits = core.credits,
            allEvents = core.events,
            allPayments = core.payments,
            asOfDate = filter.asOfDate,
            filterStartDate = startFilter,
            filterEndDate = endFilter
        )

        MainUiState(
            isLoading = false,
            persons = core.persons,
            credits = core.credits,
            events = core.events,
            payments = core.payments,
            adjustments = core.adjustments,
            auditLogs = meta.audits,
            reminders = meta.reminders,
            settings = meta.settings,
            securityState = meta.security,
            isAppLocked = meta.security.hasPin && filter.isLocked,
            firebaseConfig = meta.fbConfig,
            calculatorHistory = filter.calcHistory,
            dashboardFilter = filter.dashboardFilter,
            customStartDate = filter.customStart,
            customEndDate = filter.customEnd,
            asOfDate = filter.asOfDate,
            dashboardSummary = dashSummary,
            personSummaries = personSummaries,
            dbSizeBytes = status.dbSizeBytes,
            isBusy = status.isBusy,
            toastMessage = status.toastMessage,
            errorMessage = status.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun clearToast() {
        toastMessageFlow.value = null
    }

    fun clearError() {
        errorMessageFlow.value = null
    }

    fun showToast(msg: String) {
        toastMessageFlow.value = msg
    }

    fun setDashboardFilter(filter: DashboardFilter) {
        dashboardFilterFlow.value = filter
    }

    fun setCustomDateRange(start: LedgerDate, end: LedgerDate) {
        customRangeFlow.value = start to end
        dashboardFilterFlow.value = DashboardFilter.CUSTOM
    }

    fun setAsOfDate(date: LedgerDate) {
        asOfDateFlow.value = date
    }

    fun completeOnboarding(language: AppLanguage) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(language = language, onboardingCompleted = true)
            }
        }
    }

    fun toggleThemeQuick() {
        viewModelScope.launch {
            repository.updateSettings {
                val next = when (it.theme) {
                    AppThemeMode.DARK -> AppThemeMode.LIGHT
                    AppThemeMode.LIGHT -> AppThemeMode.DARK
                    AppThemeMode.SYSTEM -> AppThemeMode.DARK
                }
                it.copy(theme = next)
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
        }
    }

    // --- PIN & Security ---
    fun unlockWithPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = securityStore.verifyPin(pin)
            if (ok) isLockedFlow.value = false
            onResult(ok)
        }
    }

    fun unlockWithBiometric() {
        isLockedFlow.value = false
    }

    fun lockNow() {
        if (uiState.value.securityState.hasPin) {
            isLockedFlow.value = true
        }
    }

    fun setPin(pin: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            securityStore.setPin(pin)
            onDone()
        }
    }

    fun disablePin(currentPin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = securityStore.verifyPin(currentPin)
            if (ok) {
                securityStore.clearPin()
                isLockedFlow.value = false
            }
            onResult(ok)
        }
    }

    fun setAutoLockSeconds(seconds: Long) {
        viewModelScope.launch { securityStore.setAutoLockSeconds(seconds) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { securityStore.setBiometricEnabled(enabled) }
    }

    fun onAppBackgrounded() {
        viewModelScope.launch {
            securityStore.markBackgrounded(System.currentTimeMillis())
        }
    }

    fun onAppForegrounded() {
        val sec = uiState.value.securityState
        if (!sec.hasPin || sec.autoLockSeconds < 0) return
        if (sec.autoLockSeconds == 0L) {
            isLockedFlow.value = true
        } else if (sec.lastBackgroundTimestamp > 0L) {
            val elapsedSec = (System.currentTimeMillis() - sec.lastBackgroundTimestamp) / 1000L
            if (elapsedSec >= sec.autoLockSeconds) {
                isLockedFlow.value = true
            }
        }
    }

    // --- Person CRUD ---
    fun savePerson(
        existingId: String? = null,
        name: String,
        phone: String,
        address: String,
        photoUriString: String,
        note: String,
        onSaved: (Person) -> Unit = {}
    ) {
        viewModelScope.launch {
            runSafe {
                val persistedPhoto = persistPhotoIfNeeded(photoUriString)
                val person = repository.savePerson(
                    existingId = existingId,
                    name = name,
                    phone = phone,
                    address = address,
                    photoRef = persistedPhoto,
                    note = note
                )
                refreshDbSize()
                onSaved(person)
            }
        }
    }

    fun setPersonArchived(personId: String, archived: Boolean) {
        viewModelScope.launch {
            runSafe {
                repository.setPersonArchived(personId, archived)
            }
        }
    }

    fun deletePersonPermanently(personId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            runSafe {
                repository.permanentlyDeletePerson(personId)
                refreshDbSize()
                onDeleted()
            }
        }
    }

    // --- Credit CRUD & Updates ---
    suspend fun generateNextCreditNumber(): String = repository.generateNextCreditNumber()

    suspend fun isCreditNumberUnique(num: String, excludeId: String? = null): Boolean =
        repository.isCreditNumberUnique(num, excludeId)

    fun createCredit(
        personId: String,
        creditNumber: String,
        principalPaise: Long,
        interestRate: String,
        ratePeriod: RatePeriod,
        interestMethod: InterestMethod,
        startDate: LedgerDate,
        dueDate: LedgerDate?,
        note: String,
        onCreated: (Credit) -> Unit = {}
    ) {
        viewModelScope.launch {
            runSafe {
                val credit = repository.createCredit(
                    personId = personId,
                    creditNumber = creditNumber,
                    principalPaise = principalPaise,
                    interestRate = interestRate,
                    ratePeriod = ratePeriod,
                    interestMethod = interestMethod,
                    startDate = startDate,
                    dueDate = dueDate,
                    note = note
                )
                refreshDbSize()
                onCreated(credit)
            }
        }
    }

    fun updateCreditDetails(creditId: String, dueDate: LedgerDate?, note: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runSafe {
                repository.updateCreditDetails(creditId, dueDate, note)
                onDone()
            }
        }
    }

    fun updateCreditFullDetails(
        creditId: String,
        creditNumber: String,
        originalPrincipalPaise: Long,
        interestRate: String,
        ratePeriod: RatePeriod,
        interestMethod: InterestMethod,
        startDate: LedgerDate,
        dueDate: LedgerDate?,
        note: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            runSafe {
                repository.updateCreditFullDetails(
                    creditId = creditId,
                    creditNumber = creditNumber,
                    originalPrincipalPaise = originalPrincipalPaise,
                    interestRate = interestRate,
                    ratePeriod = ratePeriod,
                    interestMethod = interestMethod,
                    startDate = startDate,
                    dueDate = dueDate,
                    note = note
                )
                refreshDbSize()
                onDone()
            }
        }
    }

    fun deleteCreditPermanently(creditId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            runSafe {
                val reminderIds = repository.deleteCreditPermanently(creditId)
                reminderIds.forEach { ReminderScheduler.cancelReminder(appContext, it) }
                refreshDbSize()
                onDeleted()
            }
        }
    }

    fun closeOrReopenCredit(creditId: String, close: Boolean, date: LedgerDate, note: String = "") {
        viewModelScope.launch {
            runSafe {
                repository.closeOrReopenCredit(creditId, close, date, note)
            }
        }
    }

    fun setCreditArchived(creditId: String, archived: Boolean) {
        viewModelScope.launch {
            runSafe {
                repository.setCreditArchived(creditId, archived)
            }
        }
    }

    fun applyMainCreditUpdate(
        creditId: String,
        newPrincipalPaise: Long,
        newRate: String,
        newRatePeriod: RatePeriod,
        effectiveDate: LedgerDate,
        note: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            runSafe {
                repository.applyMainCreditUpdate(
                    creditId = creditId,
                    newPrincipalPaise = newPrincipalPaise,
                    newRate = newRate,
                    newRatePeriod = newRatePeriod,
                    effectiveDate = effectiveDate,
                    note = note
                )
                refreshDbSize()
                onDone()
            }
        }
    }

    // --- Payments & Reversals ---
    fun recordCreditPayment(
        creditId: String,
        amountPaise: Long,
        paymentDate: LedgerDate,
        paymentMethod: PaymentMethod,
        allocationMode: AllocationMode,
        manualNote: String,
        onSaved: (Payment) -> Unit = {}
    ) {
        viewModelScope.launch {
            runSafe {
                val payment = repository.recordCreditPayment(
                    creditId = creditId,
                    amountPaise = amountPaise,
                    paymentDate = paymentDate,
                    paymentMethod = paymentMethod,
                    allocationMode = allocationMode,
                    manualNote = manualNote,
                    lang = uiState.value.settings.language
                )
                refreshDbSize()
                onSaved(payment)
            }
        }
    }

    fun recordNormalPayment(
        personId: String,
        amountPaise: Long,
        paymentDate: LedgerDate,
        paymentMethod: PaymentMethod,
        note: String,
        onSaved: (Payment) -> Unit = {}
    ) {
        viewModelScope.launch {
            runSafe {
                val payment = repository.recordNormalPayment(
                    personId = personId,
                    amountPaise = amountPaise,
                    paymentDate = paymentDate,
                    paymentMethod = paymentMethod,
                    note = note
                )
                refreshDbSize()
                onSaved(payment)
            }
        }
    }

    fun updatePaymentManualNote(paymentId: String, newManualNote: String) {
        viewModelScope.launch {
            runSafe {
                repository.updatePaymentManualNote(paymentId, newManualNote)
            }
        }
    }

    fun updatePaymentFull(
        paymentId: String,
        amountPaise: Long,
        paymentDate: LedgerDate,
        paymentMethod: PaymentMethod,
        allocationMode: AllocationMode?,
        note: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            runSafe {
                repository.updatePaymentFull(
                    paymentId = paymentId,
                    amountPaise = amountPaise,
                    paymentDate = paymentDate,
                    paymentMethod = paymentMethod,
                    allocationMode = allocationMode,
                    note = note
                )
                refreshDbSize()
                onDone()
            }
        }
    }

    fun updateAdjustmentEvent(
        eventId: String,
        newPrincipalPaise: Long,
        newRate: String,
        newRatePeriod: RatePeriod,
        effectiveDate: LedgerDate,
        note: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            runSafe {
                repository.updateAdjustmentEvent(
                    eventId = eventId,
                    newPrincipalPaise = newPrincipalPaise,
                    newRate = newRate,
                    newRatePeriod = newRatePeriod,
                    effectiveDate = effectiveDate,
                    note = note
                )
                refreshDbSize()
                onDone()
            }
        }
    }

    fun deleteEventOrPaymentPermanently(eventId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            runSafe {
                repository.deleteEventOrPaymentPermanently(eventId)
                refreshDbSize()
                onDeleted()
            }
        }
    }

    fun reverseEvent(eventId: String, reason: String) {
        viewModelScope.launch {
            runSafe {
                repository.reverseEvent(eventId, reason)
                refreshDbSize()
            }
        }
    }

    // --- Reminders ---
    fun saveReminder(
        personId: String,
        creditId: String?,
        reminderDate: LedgerDate,
        reminderTime: String,
        note: String
    ) {
        viewModelScope.launch {
            runSafe {
                val reminder = repository.saveReminder(
                    personId = personId,
                    creditId = creditId,
                    reminderDate = reminderDate,
                    reminderTime = reminderTime,
                    note = note
                )
                val personName = uiState.value.persons.firstOrNull { it.id == personId }?.name ?: "Person"
                if (uiState.value.settings.notificationsEnabled) {
                    ReminderScheduler.scheduleReminder(appContext, reminder, personName)
                }
            }
        }
    }

    fun updateReminderStatus(reminderId: String, status: ReminderStatus) {
        viewModelScope.launch {
            runSafe {
                repository.updateReminderStatus(reminderId, status)
                if (status == ReminderStatus.COMPLETED) {
                    ReminderScheduler.cancelReminder(appContext, reminderId)
                }
            }
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            runSafe {
                ReminderScheduler.cancelReminder(appContext, reminderId)
                repository.deleteReminder(reminderId)
            }
        }
    }

    // --- Calculator History ---
    fun addCalculatorHistory(entry: String) {
        viewModelScope.launch { securityStore.appendCalculatorHistory(entry) }
    }

    fun clearCalculatorHistory() {
        viewModelScope.launch { securityStore.clearCalculatorHistory() }
    }

    // --- JSON Backup / Import / Export ---
    suspend fun generateBackupJson(
        scope: ExportScope,
        personId: String? = null,
        creditId: String? = null
    ): String = withContext(Dispatchers.IO) {
        backupManager.exportJson(scope, personId, creditId).also {
            if (scope == ExportScope.ALL_DATA) {
                repository.updateSettings { s -> s.copy(lastBackupTimestamp = System.currentTimeMillis()) }
            }
        }
    }

    fun exportJsonToUri(
        scope: ExportScope,
        targetUri: Uri,
        personId: String? = null,
        creditId: String? = null
    ) {
        viewModelScope.launch {
            runSafe {
                val json = generateBackupJson(scope, personId, creditId)
                withContext(Dispatchers.IO) {
                    PdfAndShareManager.writeStringToUri(appContext, json, targetUri)
                }
                showToast(
                    if (uiState.value.settings.language == AppLanguage.HINDI)
                        "JSON बैकअप सफलतापूर्वक सहेजा गया"
                    else "JSON backup saved successfully"
                )
            }
        }
    }

    suspend fun parseImportFromUri(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        val content = PdfAndShareManager.readStringFromUri(appContext, uri)
        backupManager.parseAndPreviewImport(content)
    }

    fun applyImport(preview: ImportPreview, mode: ImportMode, onDone: (ImportResultSummary) -> Unit) {
        viewModelScope.launch {
            runSafe {
                val summary = withContext(Dispatchers.IO) {
                    backupManager.applyImport(preview, mode)
                }
                refreshDbSize()
                onDone(summary)
            }
        }
    }

    // --- PDF & Statement Sharing ---
    fun buildPaymentReceiptText(payment: Payment): String {
        val state = uiState.value
        val person = state.persons.firstOrNull { it.id == payment.personId }
            ?: Person(payment.personId, "Unknown", "", "", "", "", 0L, 0L)
        val credit = payment.creditId?.let { cid -> state.credits.firstOrNull { it.id == cid } }
        val event = state.events.firstOrNull { it.id == payment.id }
        return StatementTextGenerator.generatePaymentReceiptText(
            person = person,
            payment = payment,
            creditNumber = credit?.creditNumber,
            previousBalancePaise = event?.misradhanBeforePaise,
            newBalancePaise = event?.principalAfterPaise,
            lang = state.settings.language
        )
    }

    fun buildCreditStatementText(creditId: String, asOfDate: LedgerDate): String {
        val state = uiState.value
        val credit = state.credits.firstOrNull { it.id == creditId } ?: return ""
        val person = state.persons.firstOrNull { it.id == credit.personId }
            ?: Person(credit.personId, "Unknown", "", "", "", "", 0L, 0L)
        val snapshot = InterestEngine.calculateCreditBalanceAsOfDate(
            credit,
            state.events.filter { it.creditId == credit.id },
            asOfDate
        )
        return StatementTextGenerator.generateCreditStatementText(
            person = person,
            credit = credit,
            snapshot = snapshot,
            payments = state.payments,
            lang = state.settings.language
        )
    }

    fun buildProfileStatementText(personId: String, asOfDate: LedgerDate): String {
        val state = uiState.value
        val summary = state.personSummaries[personId] ?: return ""
        return StatementTextGenerator.generateProfileStatementText(
            summary = summary,
            credits = state.credits.filter { it.personId == personId },
            payments = state.payments,
            asOfDate = asOfDate,
            lang = state.settings.language
        )
    }

    fun generatePdfFile(fileName: String, title: String, subtitle: String, body: String): File {
        return PdfAndShareManager.generateStatementPdfFile(
            context = appContext,
            fileName = fileName,
            title = title,
            subtitle = subtitle,
            bodyText = body
        )
    }

    fun savePdfToUri(sourceFile: File, targetUri: Uri) {
        viewModelScope.launch {
            runSafe {
                withContext(Dispatchers.IO) {
                    PdfAndShareManager.writeFileToUri(appContext, sourceFile, targetUri)
                }
                showToast(
                    if (uiState.value.settings.language == AppLanguage.HINDI)
                        "PDF फ़ाइल सहेजी गई"
                    else "PDF saved successfully"
                )
            }
        }
    }

    // --- Optional Firebase Cloud Sync ---
    fun connectFirebase(
        config: FirebaseCloudConfig,
        email: String,
        password: String,
        createIfMissing: Boolean
    ) {
        viewModelScope.launch {
            isBusyFlow.value = true
            val res = withContext(Dispatchers.IO) {
                firebaseSyncManager.connectAndSignIn(config, email, password, createIfMissing)
            }
            isBusyFlow.value = false
            res.onSuccess {
                showToast("Firebase connected: ${it.userEmail}")
            }.onFailure {
                errorMessageFlow.value = it.message ?: "Firebase connection failed"
            }
        }
    }

    fun testFirebaseConnection(config: FirebaseCloudConfig) {
        viewModelScope.launch {
            isBusyFlow.value = true
            val res = withContext(Dispatchers.IO) { firebaseSyncManager.testConnection(config) }
            isBusyFlow.value = false
            res.onSuccess { showToast(it) }.onFailure { errorMessageFlow.value = it.message }
        }
    }

    fun syncFirebaseNow(config: FirebaseCloudConfig) {
        viewModelScope.launch {
            isBusyFlow.value = true
            val res = withContext(Dispatchers.IO) { firebaseSyncManager.syncNow(config) }
            isBusyFlow.value = false
            res.onSuccess {
                refreshDbSize()
                showToast(it.lastSyncStatus)
            }.onFailure {
                errorMessageFlow.value = it.message ?: "Sync failed"
            }
        }
    }

    fun disconnectFirebase() {
        viewModelScope.launch {
            firebaseSyncManager.disconnect(uiState.value.firebaseConfig)
            showToast("Disconnected from Firebase. Local data remains intact.")
        }
    }

    private fun refreshDbSize() {
        viewModelScope.launch(Dispatchers.IO) {
            dbSizeFlow.value = repository.getDatabaseSizeBytes()
        }
    }

    private suspend fun persistPhotoIfNeeded(uriString: String): String {
        if (uriString.isBlank()) return ""
        if (uriString.startsWith("/")) return uriString
        return withContext(Dispatchers.IO) {
            runCatching {
                val uri = uriString.toUri()
                val photosDir = File(appContext.filesDir, "photos").apply { mkdirs() }
                val dest = File(photosDir, "${UUID.randomUUID()}.jpg")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                dest.absolutePath
            }.getOrDefault(uriString)
        }
    }

    private suspend fun runSafe(block: suspend () -> Unit) {
        activeTasksCount++
        isBusyFlow.value = true
        try {
            block()
        } catch (e: Exception) {
            errorMessageFlow.value = e.message ?: "Unexpected error"
        } finally {
            activeTasksCount = (activeTasksCount - 1).coerceAtLeast(0)
            isBusyFlow.value = activeTasksCount > 0
        }
    }

    private fun resolveFilterRange(
        filter: DashboardFilter,
        today: LedgerDate,
        customStart: LedgerDate,
        customEnd: LedgerDate
    ): Pair<LedgerDate?, LedgerDate?> {
        return when (filter) {
            DashboardFilter.TODAY -> today to today
            DashboardFilter.THIS_WEEK -> today.plusDays(-6) to today
            DashboardFilter.THIS_MONTH -> LedgerDate(today.year, today.month, 1) to today
            DashboardFilter.THIS_YEAR -> LedgerDate(today.year, 1, 1) to today
            DashboardFilter.CUSTOM -> customStart to customEnd
            DashboardFilter.ALL_TIME -> null to null
        }
    }

    private data class CoreData(
        val persons: List<Person>,
        val credits: List<Credit>,
        val events: List<CreditEvent>,
        val payments: List<Payment>,
        val adjustments: List<Adjustment>
    )

    private data class MetaData(
        val audits: List<AuditLog>,
        val reminders: List<Reminder>,
        val settings: AppSettings,
        val security: SecurityState,
        val fbConfig: FirebaseCloudConfig
    )

    private data class FilterData(
        val dashboardFilter: DashboardFilter,
        val customStart: LedgerDate,
        val customEnd: LedgerDate,
        val asOfDate: LedgerDate,
        val isLocked: Boolean,
        val calcHistory: List<String>
    )

    private data class StatusData(
        val isBusy: Boolean,
        val toastMessage: String?,
        val errorMessage: String?,
        val dbSizeBytes: Long
    )
}
