package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.engine.IndianNumberFormatter
import com.example.domain.engine.InterestEngine
import com.example.domain.engine.LedgerDate
import com.example.domain.engine.StatementTextGenerator
import com.example.domain.model.AllocationMode
import com.example.domain.model.CreditEvent
import com.example.domain.model.CreditEventType
import com.example.domain.model.CreditStatus
import com.example.domain.model.InterestMethod
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.RatePeriod
import com.example.ui.components.LedgerDateField
import com.example.ui.theme.InterestOrange
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MisradhanPurple
import com.example.ui.theme.OverdueRed
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PrincipalBlue
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCreditScreen(
    preselectedPersonId: String?,
    state: MainUiState,
    generateCreditNumber: suspend () -> String,
    checkUniqueCreditNumber: suspend (String) -> Boolean,
    onBack: () -> Unit,
    onSaveCredit: (
        personId: String,
        creditNumber: String,
        principalPaise: Long,
        interestRate: String,
        ratePeriod: RatePeriod,
        interestMethod: InterestMethod,
        startDate: LedgerDate,
        dueDate: LedgerDate?,
        note: String
    ) -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current
    val activePersons = state.persons.filter { !it.isArchived }

    var selectedPersonId by remember(preselectedPersonId, activePersons) {
        mutableStateOf(preselectedPersonId ?: activePersons.firstOrNull()?.id ?: "")
    }
    var personExpanded by remember { mutableStateOf(false) }

    var creditNumber by remember { mutableStateOf("") }
    var isCreditNumberUnique by remember { mutableStateOf(true) }
    var principalText by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf("") }
    var ratePeriod by remember { mutableStateOf(state.settings.defaultRatePeriod) }
    var interestMethod by remember { mutableStateOf(state.settings.defaultInterestMethod) }
    var startDate by remember { mutableStateOf(LedgerDate.todayLocal()) }
    var dueDate by remember { mutableStateOf<LedgerDate?>(null) }
    var note by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        creditNumber = generateCreditNumber()
    }
    LaunchedEffect(creditNumber) {
        if (creditNumber.isNotBlank()) {
            isCreditNumberUnique = checkUniqueCreditNumber(creditNumber)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.addCredit) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (activePersons.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No active profiles found. Please add a profile before creating a credit.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = personExpanded,
                    onExpandedChange = { if (activePersons.isNotEmpty()) personExpanded = it }
                ) {
                    val selectedPersonName = activePersons.firstOrNull { it.id == selectedPersonId }?.name ?: strings.selectPerson
                    OutlinedTextField(
                        value = selectedPersonName,
                        onValueChange = {},
                        readOnly = true,
                        enabled = activePersons.isNotEmpty(),
                        label = { Text(strings.selectPerson) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = personExpanded,
                        onDismissRequest = { personExpanded = false }
                    ) {
                        activePersons.forEach { person ->
                            DropdownMenuItem(
                                text = { Text("${person.name} ${if (person.phone.isNotBlank()) "(${person.phone})" else ""}") },
                                onClick = {
                                    selectedPersonId = person.id
                                    personExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = creditNumber,
                    onValueChange = { creditNumber = it },
                    label = { Text(strings.creditNumber) },
                    isError = !isCreditNumberUnique || creditNumber.isBlank(),
                    supportingText = if (!isCreditNumberUnique) {
                        { Text("Credit number already exists") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_credit_number_input")
                )

                OutlinedTextField(
                    value = principalText,
                    onValueChange = { principalText = it },
                    label = { Text(strings.principalAmount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_credit_principal_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text(strings.interestRate) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("add_credit_rate_input")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = ratePeriod == RatePeriod.MONTHLY,
                        onClick = { ratePeriod = RatePeriod.MONTHLY },
                        label = { Text(strings.monthly) },
                        modifier = Modifier.testTag("rate_period_monthly")
                    )
                    FilterChip(
                        selected = ratePeriod == RatePeriod.YEARLY,
                        onClick = { ratePeriod = RatePeriod.YEARLY },
                        label = { Text(strings.yearly) },
                        modifier = Modifier.testTag("rate_period_yearly")
                    )
                }

                if (state.settings.allowCompoundInterest) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.interestMethod, style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = interestMethod == InterestMethod.SIMPLE,
                                onClick = { interestMethod = InterestMethod.SIMPLE },
                                label = { Text(strings.simpleInterest) }
                            )
                            FilterChip(
                                selected = interestMethod == InterestMethod.COMPOUND,
                                onClick = { interestMethod = InterestMethod.COMPOUND },
                                label = { Text(strings.compoundInterest) }
                            )
                        }
                    }
                }

                LedgerDateField(
                    label = strings.startDate,
                    date = startDate,
                    onDateSelected = { startDate = it },
                    testTag = "add_credit_start_date"
                )

                LedgerDateField(
                    label = strings.dueDateOptional,
                    date = dueDate,
                    onDateSelected = { dueDate = it },
                    isOptional = true,
                    onCleared = { dueDate = null },
                    testTag = "add_credit_due_date"
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(strings.optionalNote) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("add_credit_note_input")
                )

                if (validationError != null) {
                    Text(validationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        val paise = IndianNumberFormatter.rupeesInputToPaiseOrNull(principalText)
                        val rateBd = runCatching { java.math.BigDecimal(rateText.trim()) }.getOrNull()
                        when {
                            selectedPersonId.isBlank() -> validationError = strings.selectPerson
                            creditNumber.isBlank() || !isCreditNumberUnique -> validationError = "Unique credit number required"
                            paise == null || paise <= 0L -> validationError = "Enter positive principal amount"
                            rateBd == null || rateBd < java.math.BigDecimal.ZERO -> validationError = "Enter non-negative interest rate"
                            dueDate != null && dueDate!! < startDate -> validationError = "Due date cannot be before start date"
                            else -> {
                                validationError = null
                                onSaveCredit(
                                    selectedPersonId,
                                    creditNumber.trim(),
                                    paise,
                                    rateText.trim(),
                                    ratePeriod,
                                    interestMethod,
                                    startDate,
                                    dueDate,
                                    note
                                )
                            }
                        }
                    },
                    enabled = activePersons.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_credit_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(strings.saveCredit)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditDetailScreen(
    creditId: String,
    state: MainUiState,
    checkUniqueCreditNumber: suspend (creditNumber: String, excludeCreditId: String) -> Boolean,
    onBack: () -> Unit,
    onAddPayment: (String) -> Unit,
    onMainUpdate: (String) -> Unit,
    onUpdateFullDetails: (
        creditId: String,
        creditNumber: String,
        originalPrincipalPaise: Long,
        interestRate: String,
        ratePeriod: RatePeriod,
        interestMethod: InterestMethod,
        startDate: LedgerDate,
        dueDate: LedgerDate?,
        note: String
    ) -> Unit,
    onDeleteCredit: (creditId: String) -> Unit,
    onCloseOrReopen: (creditId: String, close: Boolean, date: LedgerDate) -> Unit,
    onArchiveRestore: (creditId: String, archived: Boolean) -> Unit,
    onEditPaymentFull: (
        paymentId: String,
        amountPaise: Long,
        paymentDate: LedgerDate,
        method: PaymentMethod,
        allocationMode: AllocationMode?,
        note: String
    ) -> Unit,
    onEditAdjustmentEvent: (
        eventId: String,
        newPrincipalPaise: Long,
        newRate: String,
        newRatePeriod: RatePeriod,
        effectiveDate: LedgerDate,
        note: String
    ) -> Unit,
    onDeleteEventOrPayment: (eventId: String) -> Unit,
    onReverseEvent: (eventId: String, reason: String) -> Unit,
    onShareStatement: (String, LedgerDate) -> Unit,
    onExportJson: (String) -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current
    val credit = state.credits.firstOrNull { it.id == creditId }
    if (credit == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(strings.noDataYet) }
        return
    }
    val person = state.persons.firstOrNull { it.id == credit.personId }
    val creditEvents = state.events.filter { it.creditId == creditId }
    val paymentMap = remember(state.payments) { state.payments.associateBy { it.id } }
    val creditStart = InterestEngine.parseDateOnly(credit.startDate)

    var calcDate by remember(creditStart) { mutableStateOf(maxOf(LedgerDate.todayLocal(), creditStart)) }
    var dateError by remember { mutableStateOf<String?>(null) }

    val snapshot = remember(credit, creditEvents, calcDate) {
        runCatching {
            InterestEngine.calculateCreditBalanceAsOfDate(credit, creditEvents, calcDate)
        }.getOrNull()
    }

    val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, state.settings.displayDecimals, trimTrailingZerosWhenWhole = true) }

    var showEditDetailsDialog by remember { mutableStateOf(false) }
    var showDeleteCreditDialog by remember { mutableStateOf(false) }
    var showCloseReopenDialog by remember { mutableStateOf(false) }
    var paymentToEdit by remember { mutableStateOf<Payment?>(null) }
    var adjustmentToEdit by remember { mutableStateOf<CreditEvent?>(null) }
    var eventToDelete by remember { mutableStateOf<CreditEvent?>(null) }
    var eventToReverse by remember { mutableStateOf<CreditEvent?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${credit.creditNumber} • ${person?.name ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDetailsDialog = true }, modifier = Modifier.testTag("credit_top_edit_btn")) {
                        Icon(Icons.Default.Edit, contentDescription = strings.editCreditTitle)
                    }
                    IconButton(onClick = { onShareStatement(creditId, calcDate) }, modifier = Modifier.testTag("credit_share_btn")) {
                        Icon(Icons.Default.Share, contentDescription = strings.shareCreditStatement)
                    }
                    IconButton(onClick = { onExportJson(creditId) }) {
                        Icon(Icons.Default.Code, contentDescription = strings.exportCreditJson)
                    }
                    IconButton(onClick = { showDeleteCreditDialog = true }, modifier = Modifier.testTag("credit_top_delete_btn")) {
                        Icon(Icons.Default.DeleteForever, contentDescription = strings.deleteCredit, tint = OverdueRed)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    LedgerDateField(
                        label = strings.calculationDate,
                        date = calcDate,
                        onDateSelected = { selected ->
                            if (selected < creditStart) {
                                dateError = "Cannot be before ${creditStart.toDisplayString()}"
                            } else {
                                dateError = null
                                calcDate = selected
                            }
                        },
                        testTag = "credit_calc_date_input"
                    )
                    if (dateError != null) {
                        Text(dateError!!, color = OverdueRed, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (snapshot != null) {
                    item {
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${credit.creditNumber} — ${snapshot.status.name}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Surface(
                                        color = if (snapshot.status == CreditStatus.ACTIVE) PaidGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = when (snapshot.status) {
                                                CreditStatus.ACTIVE -> strings.statusActive
                                                CreditStatus.CLOSED -> strings.statusClosed
                                                CreditStatus.ARCHIVED -> strings.statusArchived
                                            },
                                            color = if (snapshot.status == CreditStatus.ACTIVE) PaidGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                HorizontalDivider()
                                DetailRow(strings.originalPrincipal, fmt(snapshot.originalPrincipalPaise))
                                DetailRow(strings.currentPrincipal, fmt(snapshot.currentPrincipalPaise), PrincipalBlue)
                                DetailRow(strings.interestRate, "${snapshot.activeRate}% ${StatementTextGenerator.formatRatePeriod(snapshot.activeRatePeriod, state.settings.language)}")
                                DetailRow(strings.interestMethod, if (snapshot.interestMethod == InterestMethod.SIMPLE) strings.simpleInterest else strings.compoundInterest)
                                DetailRow(strings.startDate, "${creditStart.toDisplayString()} (Eff: ${snapshot.effectiveStartDate.toDisplayString()})")
                                credit.dueDate?.let { d ->
                                    LedgerDate.parseOrNull(d)?.let { DetailRow(strings.dueDateOptional, it.toDisplayString()) }
                                }
                                HorizontalDivider()
                                DetailRow(strings.completedMonths, "${snapshot.durationSinceEffectiveStart.completedMonths}")
                                DetailRow(strings.remainingDays, "${snapshot.durationSinceEffectiveStart.remainingDays}")
                                DetailRow(strings.totalDays, "${snapshot.durationSinceEffectiveStart.totalElapsedDays}")
                                DetailRow(strings.accruedInterest, fmt(snapshot.accruedUnpaidInterestPaise), InterestOrange)
                                DetailRow(strings.misradhan, fmt(snapshot.misradhanPaise), MisradhanPurple)
                                DetailRow(strings.totalPaymentsOnCredit, fmt(snapshot.totalPaidOnCreditPaise), PaidGreen)
                                if (snapshot.advancePaise > 0L) {
                                    DetailRow(strings.advanceBalance, fmt(snapshot.advancePaise), PaidGreen)
                                }
                                if (snapshot.nextMonthInterestEstimatePaise > 0L) {
                                    DetailRow(strings.nextPeriodEstimate, fmt(snapshot.nextMonthInterestEstimatePaise))
                                }
                                if (credit.note.isNotBlank()) {
                                    HorizontalDivider()
                                    Text("${strings.notes}: ${credit.note}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = strings.calendarRuleHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Actions Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onAddPayment(creditId) },
                                colors = ButtonDefaults.buttonColors(containerColor = PaidGreen),
                                modifier = Modifier.weight(1f).testTag("credit_add_payment_btn")
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(strings.addCreditPayment)
                            }
                            OutlinedButton(
                                onClick = { onMainUpdate(creditId) },
                                modifier = Modifier.weight(1f).testTag("credit_main_update_btn")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(strings.mainCreditUpdate)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showEditDetailsDialog = true },
                                modifier = Modifier.weight(1f).testTag("credit_edit_details_btn")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(strings.editDetails)
                            }
                            OutlinedButton(onClick = { showCloseReopenDialog = true }, modifier = Modifier.weight(1f)) {
                                val isClosed = credit.status == CreditStatus.CLOSED
                                Icon(if (isClosed) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(if (isClosed) strings.reopenCredit else strings.closeCredit)
                            }
                            OutlinedButton(
                                onClick = { onArchiveRestore(creditId, credit.status != CreditStatus.ARCHIVED) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(if (credit.status == CreditStatus.ARCHIVED) Icons.Default.Restore else Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(if (credit.status == CreditStatus.ARCHIVED) strings.restoreProfile else strings.archiveProfile, maxLines = 1)
                            }
                        }
                        OutlinedButton(
                            onClick = { showDeleteCreditDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OverdueRed),
                            modifier = Modifier.fillMaxWidth().testTag("delete_credit_btn")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(strings.deleteCredit)
                        }
                    }
                }

                item {
                    Text(
                        text = "${strings.historyTab} (${creditEvents.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                items(creditEvents.sortedByDescending { it.effectiveDate }, key = { it.id }) { ev ->
                    val dStr = LedgerDate.parseOrNull(ev.effectiveDate)?.toDisplayString() ?: ev.effectiveDate
                    val linkedPayment = paymentMap[ev.id]
                    val isEditableAdjustment = !ev.isReversed &&
                        (ev.eventType == CreditEventType.PRINCIPAL_ADJUSTMENT || ev.eventType == CreditEventType.INTEREST_RATE_UPDATE)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ev.isReversed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${ev.eventType.name} • $dStr ${if (ev.isReversed) "[REVERSED]" else ""}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(fmt(ev.amountPaise), fontWeight = FontWeight.Bold)
                            }
                            if (ev.principalBeforePaise > 0L || ev.principalAfterPaise > 0L) {
                                Text(
                                    "Principal: ${fmt(ev.principalBeforePaise)} -> ${fmt(ev.principalAfterPaise)} | Interest: ${fmt(ev.interestBeforePaise)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (ev.systemNote.isNotBlank()) Text(ev.systemNote, style = MaterialTheme.typography.bodySmall)
                            if (ev.note.isNotBlank()) Text("Note: ${ev.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (ev.eventType != CreditEventType.CREDIT_CREATED) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
                                ) {
                                    if (linkedPayment != null && !ev.isReversed) {
                                        TextButton(
                                            onClick = { paymentToEdit = linkedPayment },
                                            modifier = Modifier.testTag("credit_hist_edit_${ev.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text(strings.edit)
                                        }
                                    } else if (isEditableAdjustment) {
                                        TextButton(
                                            onClick = { adjustmentToEdit = ev },
                                            modifier = Modifier.testTag("credit_hist_edit_adj_${ev.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text(strings.edit)
                                        }
                                    }
                                    if (!ev.isReversed && ev.eventType != CreditEventType.CORRECTION_REVERSAL) {
                                        TextButton(onClick = { eventToReverse = ev }) {
                                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text(strings.reverseEvent)
                                        }
                                    }
                                    TextButton(
                                        onClick = { eventToDelete = ev },
                                        modifier = Modifier.testTag("credit_hist_delete_${ev.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = OverdueRed, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text(strings.delete, color = OverdueRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDetailsDialog) {
        val reversedIds = remember(creditEvents) { creditEvents.mapNotNull { it.reversesEventId }.toSet() }
        val earliestNonCreateDate = remember(creditEvents, reversedIds) {
            creditEvents
                .filter {
                    !it.isReversed &&
                        it.id !in reversedIds &&
                        it.eventType != CreditEventType.CREDIT_CREATED &&
                        it.eventType != CreditEventType.CORRECTION_REVERSAL
                }
                .mapNotNull { LedgerDate.parseOrNull(it.effectiveDate) }
                .minOrNull()
        }
        var newNumber by remember(credit.id) { mutableStateOf(credit.creditNumber) }
        var isNumberUnique by remember(credit.id) { mutableStateOf(true) }
        var newPrincipalText by remember(credit.id) {
            mutableStateOf(IndianNumberFormatter.paiseToPlainRupeesString(credit.originalPrincipalPaise))
        }
        var newRateText by remember(credit.id) { mutableStateOf(credit.interestRate) }
        var newRatePeriod by remember(credit.id) { mutableStateOf(credit.ratePeriod) }
        var newMethod by remember(credit.id) { mutableStateOf(credit.interestMethod) }
        var newStartDate by remember(credit.id) { mutableStateOf(creditStart) }
        var newDue by remember(credit.id) { mutableStateOf(credit.dueDate?.let { LedgerDate.parseOrNull(it) }) }
        var newNote by remember(credit.id) { mutableStateOf(credit.note) }
        var editError by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(newNumber) {
            if (newNumber.isNotBlank()) {
                isNumberUnique = checkUniqueCreditNumber(newNumber.trim(), creditId)
            }
        }

        AlertDialog(
            onDismissRequest = { showEditDetailsDialog = false },
            title = { Text(strings.editCreditTitle) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newNumber,
                        onValueChange = { newNumber = it; editError = null },
                        label = { Text(strings.creditNumber) },
                        isError = !isNumberUnique || newNumber.isBlank(),
                        supportingText = if (!isNumberUnique) {
                            { Text("Credit number already exists") }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_credit_number_input")
                    )
                    OutlinedTextField(
                        value = newPrincipalText,
                        onValueChange = { newPrincipalText = it; editError = null },
                        label = { Text(strings.originalPrincipal) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_credit_principal_input")
                    )
                    OutlinedTextField(
                        value = newRateText,
                        onValueChange = { newRateText = it; editError = null },
                        label = { Text(strings.interestRate) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_credit_rate_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = newRatePeriod == RatePeriod.MONTHLY,
                            onClick = { newRatePeriod = RatePeriod.MONTHLY },
                            label = { Text(strings.monthly) }
                        )
                        FilterChip(
                            selected = newRatePeriod == RatePeriod.YEARLY,
                            onClick = { newRatePeriod = RatePeriod.YEARLY },
                            label = { Text(strings.yearly) }
                        )
                    }
                    if (state.settings.allowCompoundInterest || credit.interestMethod == InterestMethod.COMPOUND) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = newMethod == InterestMethod.SIMPLE,
                                onClick = { newMethod = InterestMethod.SIMPLE },
                                label = { Text(strings.simpleInterest) }
                            )
                            FilterChip(
                                selected = newMethod == InterestMethod.COMPOUND,
                                onClick = { newMethod = InterestMethod.COMPOUND },
                                label = { Text(strings.compoundInterest) }
                            )
                        }
                    }
                    LedgerDateField(
                        label = strings.startDate,
                        date = newStartDate,
                        onDateSelected = { d ->
                            newStartDate = d
                            editError = if (earliestNonCreateDate != null && d > earliestNonCreateDate) {
                                "Start date cannot be after event on ${earliestNonCreateDate.toDisplayString()}"
                            } else null
                        },
                        testTag = "edit_credit_start_date"
                    )
                    LedgerDateField(
                        label = strings.dueDateOptional,
                        date = newDue,
                        onDateSelected = { newDue = it; editError = null },
                        isOptional = true,
                        onCleared = { newDue = null },
                        testTag = "edit_credit_due_date"
                    )
                    OutlinedTextField(
                        value = newNote,
                        onValueChange = { newNote = it },
                        label = { Text(strings.notes) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("edit_credit_note_input")
                    )
                    if (editError != null) {
                        Text(editError!!, color = OverdueRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paise = IndianNumberFormatter.rupeesInputToPaiseOrNull(newPrincipalText)
                        val rateBd = runCatching { java.math.BigDecimal(newRateText.trim()) }.getOrNull()
                        when {
                            newNumber.isBlank() || !isNumberUnique -> editError = "Unique credit number required"
                            paise == null || paise <= 0L -> editError = "Enter positive principal amount"
                            rateBd == null || rateBd < java.math.BigDecimal.ZERO -> editError = "Enter non-negative interest rate"
                            earliestNonCreateDate != null && newStartDate > earliestNonCreateDate ->
                                editError = "Start date cannot be after event on ${earliestNonCreateDate.toDisplayString()}"
                            newDue != null && newDue!! < newStartDate -> editError = "Due date cannot be before start date"
                            else -> {
                                onUpdateFullDetails(
                                    creditId,
                                    newNumber.trim(),
                                    paise,
                                    newRateText.trim(),
                                    newRatePeriod,
                                    newMethod,
                                    newStartDate,
                                    newDue,
                                    newNote
                                )
                                showEditDetailsDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_edit_credit_btn")
                ) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDetailsDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showDeleteCreditDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCreditDialog = false },
            title = { Text(strings.deleteCreditConfirmTitle) },
            text = { Text("${credit.creditNumber} — ${strings.deleteCreditConfirmBody}") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteCreditDialog = false
                        onDeleteCredit(creditId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OverdueRed),
                    modifier = Modifier.testTag("confirm_delete_credit_btn")
                ) { Text(strings.delete) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCreditDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (paymentToEdit != null) {
        EditPaymentDialog(
            payment = paymentToEdit!!,
            state = state,
            onDismiss = { paymentToEdit = null },
            onSave = { pid, amountPaise, date, method, mode, note ->
                onEditPaymentFull(pid, amountPaise, date, method, mode, note)
                paymentToEdit = null
            }
        )
    }

    if (adjustmentToEdit != null) {
        EditAdjustmentDialog(
            event = adjustmentToEdit!!,
            state = state,
            onDismiss = { adjustmentToEdit = null },
            onSave = { evId, newPrincipalPaise, newRate, newPeriod, effDate, note ->
                onEditAdjustmentEvent(evId, newPrincipalPaise, newRate, newPeriod, effDate, note)
                adjustmentToEdit = null
            }
        )
    }

    if (eventToDelete != null) {
        val target = eventToDelete!!
        DeleteEventConfirmDialog(
            event = target,
            state = state,
            onDismiss = { eventToDelete = null },
            onConfirmDelete = {
                onDeleteEventOrPayment(target.id)
                eventToDelete = null
            }
        )
    }

    if (showCloseReopenDialog) {
        val isClosed = credit.status == CreditStatus.CLOSED
        AlertDialog(
            onDismissRequest = { showCloseReopenDialog = false },
            title = { Text(if (isClosed) strings.reopenCredit else strings.closeCredit) },
            text = {
                Text(
                    if (isClosed) "Reopen ${credit.creditNumber} from ${calcDate.toDisplayString()}?"
                    else "Mark ${credit.creditNumber} closed as of ${calcDate.toDisplayString()}? Current Misradhan: ${fmt(snapshot?.misradhanPaise ?: 0L)}"
                )
            },
            confirmButton = {
                Button(onClick = {
                    onCloseOrReopen(creditId, !isClosed, calcDate)
                    showCloseReopenDialog = false
                }) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showCloseReopenDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (eventToReverse != null) {
        val ev = eventToReverse!!
        var reason by remember { mutableStateOf("") }
        val simulatedWithoutEvent = remember(credit, creditEvents, ev, calcDate) {
            runCatching {
                InterestEngine.calculateCreditBalanceAsOfDate(
                    credit,
                    creditEvents.filter { it.id != ev.id },
                    calcDate
                )
            }.getOrNull()
        }
        AlertDialog(
            onDismissRequest = { eventToReverse = null },
            title = { Text("${strings.reverseEvent} — ${strings.impactPreview}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Event: ${ev.eventType.name} (${fmt(ev.amountPaise)}) on ${LedgerDate.parseOrNull(ev.effectiveDate)?.toDisplayString() ?: ev.effectiveDate}")
                    if (snapshot != null && simulatedWithoutEvent != null) {
                        Text("Principal: ${fmt(snapshot.currentPrincipalPaise)} -> ${fmt(simulatedWithoutEvent.currentPrincipalPaise)}")
                        Text("Misradhan: ${fmt(snapshot.misradhanPaise)} -> ${fmt(simulatedWithoutEvent.misradhanPaise)}")
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason / Note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReverseEvent(ev.id, reason.ifBlank { "Reversed by user" })
                        eventToReverse = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OverdueRed)
                ) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { eventToReverse = null }) { Text(strings.cancel) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCreditUpdateScreen(
    creditId: String,
    state: MainUiState,
    onBack: () -> Unit,
    onConfirmUpdate: (newPrincipalPaise: Long, newRate: String, newRatePeriod: RatePeriod, effectiveDate: LedgerDate, note: String) -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current
    val credit = state.credits.firstOrNull { it.id == creditId }
    if (credit == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(strings.mainCreditUpdate) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(strings.noDataYet)
            }
        }
        return
    }
    val creditEvents = state.events.filter { it.creditId == creditId }
    val creditStart = InterestEngine.parseDateOnly(credit.startDate)
    val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, state.settings.displayDecimals, trimTrailingZerosWhenWhole = true) }

    var effectiveDate by remember { mutableStateOf(maxOf(LedgerDate.todayLocal(), creditStart)) }
    val previewSnapshot = remember(credit, creditEvents, effectiveDate) {
        runCatching {
            InterestEngine.calculateCreditBalanceAsOfDate(credit, creditEvents, effectiveDate)
        }.getOrNull()
    }

    var principalEdited by remember { mutableStateOf(false) }
    var rateEdited by remember { mutableStateOf(false) }
    var newPrincipalText by remember {
        mutableStateOf(
            IndianNumberFormatter.paiseToPlainRupeesString(previewSnapshot?.currentPrincipalPaise ?: credit.currentPrincipalPaise)
        )
    }
    var newRateText by remember { mutableStateOf(previewSnapshot?.activeRate ?: credit.interestRate) }
    var newRatePeriod by remember { mutableStateOf(previewSnapshot?.activeRatePeriod ?: credit.ratePeriod) }
    LaunchedEffect(previewSnapshot) {
        if (previewSnapshot != null) {
            if (!principalEdited) {
                newPrincipalText = IndianNumberFormatter.paiseToPlainRupeesString(previewSnapshot.currentPrincipalPaise)
            }
            if (!rateEdited) {
                newRateText = previewSnapshot.activeRate
                newRatePeriod = previewSnapshot.activeRatePeriod
            }
        }
    }
    var note by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${strings.mainCreditUpdate} — ${credit.creditNumber}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LedgerDateField(
                    label = "Effective Date",
                    date = effectiveDate,
                    onDateSelected = { d ->
                        if (d < creditStart) errorText = "Cannot precede credit start date"
                        else {
                            errorText = null
                            effectiveDate = d
                        }
                    },
                    testTag = "main_update_effective_date"
                )

                OutlinedTextField(
                    value = newPrincipalText,
                    onValueChange = {
                        principalEdited = true
                        newPrincipalText = it
                    },
                    label = { Text("New Principal (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("main_update_principal_input")
                )

                OutlinedTextField(
                    value = newRateText,
                    onValueChange = {
                        rateEdited = true
                        newRateText = it
                    },
                    label = { Text("New Interest Rate (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("main_update_rate_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = newRatePeriod == RatePeriod.MONTHLY,
                        onClick = {
                            rateEdited = true
                            newRatePeriod = RatePeriod.MONTHLY
                        },
                        label = { Text(strings.monthly) }
                    )
                    FilterChip(
                        selected = newRatePeriod == RatePeriod.YEARLY,
                        onClick = {
                            rateEdited = true
                            newRatePeriod = RatePeriod.YEARLY
                        },
                        label = { Text(strings.yearly) }
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(strings.optionalNote) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (previewSnapshot != null) {
                    val newPaise = IndianNumberFormatter.rupeesInputToPaiseOrNull(newPrincipalText) ?: 0L
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Preview (Old periods keep old rate!)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            DetailRow("Old Principal (${effectiveDate.toDisplayString()})", fmt(previewSnapshot.currentPrincipalPaise))
                            DetailRow("Accrued Interest Preserved", fmt(previewSnapshot.accruedUnpaidInterestPaise), InterestOrange)
                            DetailRow("Old Rate", "${previewSnapshot.activeRate}% ${previewSnapshot.activeRatePeriod.name}")
                            HorizontalDivider()
                            DetailRow("New Principal (from ${effectiveDate.toDisplayString()})", fmt(newPaise), PaidGreen)
                            DetailRow("New Rate (from ${effectiveDate.toDisplayString()})", "$newRateText% ${newRatePeriod.name}", PrincipalBlue)
                        }
                    }
                }

                if (errorText != null) {
                    Text(errorText!!, color = OverdueRed, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        val paise = IndianNumberFormatter.rupeesInputToPaiseOrNull(newPrincipalText)
                        val rateBd = runCatching { java.math.BigDecimal(newRateText.trim()) }.getOrNull()
                        when {
                            effectiveDate < creditStart -> errorText = "Effective date precedes start date"
                            paise == null || paise < 0L -> errorText = "Enter valid non-negative principal"
                            rateBd == null || rateBd < java.math.BigDecimal.ZERO -> errorText = "Enter valid non-negative rate"
                            else -> onConfirmUpdate(paise, newRateText.trim(), newRatePeriod, effectiveDate, note)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_main_update_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(strings.save)
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = valueColor)
    }
}
