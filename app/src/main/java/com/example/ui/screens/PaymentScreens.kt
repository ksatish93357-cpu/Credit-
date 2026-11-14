package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.engine.IndianNumberFormatter
import com.example.domain.engine.InterestEngine
import com.example.domain.engine.LedgerDate
import com.example.domain.engine.StatementTextGenerator
import com.example.domain.model.AllocationMode
import com.example.domain.model.CreditEvent
import com.example.domain.model.CreditStatus
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentType
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
fun AddCreditPaymentScreen(
    preselectedPersonId: String?,
    preselectedCreditId: String?,
    state: MainUiState,
    onBack: () -> Unit,
    onSavePayment: (
        creditId: String,
        amountPaise: Long,
        paymentDate: LedgerDate,
        method: PaymentMethod,
        allocationMode: AllocationMode,
        manualNote: String
    ) -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current
    val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, state.settings.displayDecimals, trimTrailingZerosWhenWhole = true) }

    val availableCredits = remember(state.credits, preselectedPersonId) {
        state.credits.filter { c ->
            c.status != CreditStatus.ARCHIVED && (preselectedPersonId == null || c.personId == preselectedPersonId)
        }
    }

    var selectedCreditId by remember(preselectedCreditId, availableCredits) {
        mutableStateOf(preselectedCreditId ?: availableCredits.firstOrNull()?.id ?: "")
    }
    var creditDropdownOpen by remember { mutableStateOf(false) }

    val selectedCredit = state.credits.firstOrNull { it.id == selectedCreditId }
    val creditStart = selectedCredit?.let { InterestEngine.parseDateOnly(it.startDate) } ?: LedgerDate.todayLocal()

    var amountText by remember { mutableStateOf("") }
    var paymentDate by remember { mutableStateOf(maxOf(LedgerDate.todayLocal(), creditStart)) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var allocationMode by remember { mutableStateOf(state.settings.defaultAllocationMode) }
    var manualNote by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val preSnapshot = remember(selectedCredit, state.events, paymentDate) {
        if (selectedCredit == null || paymentDate < creditStart) null
        else runCatching {
            InterestEngine.calculateCreditBalanceAsOfDate(
                selectedCredit,
                state.events.filter { it.creditId == selectedCredit.id },
                paymentDate
            )
        }.getOrNull()
    }

    val enteredPaise = IndianNumberFormatter.rupeesInputToPaiseOrNull(amountText) ?: 0L
    val allocationPreview = remember(preSnapshot, enteredPaise, allocationMode) {
        if (preSnapshot == null || enteredPaise <= 0L) null
        else if (allocationMode == AllocationMode.INTEREST_FIRST) {
            InterestEngine.applyInterestFirstPayment(
                preSnapshot.currentPrincipalPaise,
                preSnapshot.accruedUnpaidInterestPaise,
                enteredPaise
            )
        } else {
            InterestEngine.applyMisradhanLessPayment(
                preSnapshot.currentPrincipalPaise,
                preSnapshot.accruedUnpaidInterestPaise,
                enteredPaise
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.addCreditPayment) },
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
                if (availableCredits.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No active credits available. Please create a credit record first.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = creditDropdownOpen,
                    onExpandedChange = { if (availableCredits.isNotEmpty()) creditDropdownOpen = it }
                ) {
                    val labelText = selectedCredit?.let { c ->
                        val pName = state.persons.firstOrNull { it.id == c.personId }?.name ?: ""
                        "${c.creditNumber} ($pName)"
                    } ?: strings.selectCredit
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = {},
                        readOnly = true,
                        enabled = availableCredits.isNotEmpty(),
                        label = { Text(strings.creditNumber) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = creditDropdownOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = creditDropdownOpen,
                        onDismissRequest = { creditDropdownOpen = false }
                    ) {
                        availableCredits.forEach { c ->
                            val pName = state.persons.firstOrNull { it.id == c.personId }?.name ?: ""
                            DropdownMenuItem(
                                text = { Text("${c.creditNumber} — $pName") },
                                onClick = {
                                    selectedCreditId = c.id
                                    val newStart = InterestEngine.parseDateOnly(c.startDate)
                                    if (paymentDate < newStart) {
                                        paymentDate = newStart
                                    }
                                    validationError = null
                                    creditDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(strings.paymentAmount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                )

                LedgerDateField(
                    label = strings.paymentDate,
                    date = paymentDate,
                    onDateSelected = { d ->
                        paymentDate = d
                        validationError = if (selectedCredit != null && d < creditStart) {
                            "Payment date cannot be before credit start date (${creditStart.toDisplayString()})"
                        } else null
                    },
                    testTag = "payment_date_input"
                )

                Text(strings.paymentMethod, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PaymentMethod.entries.forEach { pm ->
                        FilterChip(
                            selected = method == pm,
                            onClick = { method = pm },
                            label = {
                                Text(
                                    when (pm) {
                                        PaymentMethod.CASH -> strings.methodCash
                                        PaymentMethod.UPI -> strings.methodUpi
                                        PaymentMethod.BANK_TRANSFER -> strings.methodBank
                                        PaymentMethod.OTHER -> strings.methodOther
                                    },
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                Text(strings.allocationMode, style = MaterialTheme.typography.labelLarge)
                AllocationModeOptionCard(
                    selected = allocationMode == AllocationMode.PRINCIPAL_ADJUSTMENT,
                    title = strings.modePrincipalAdjustment,
                    description = strings.modePrincipalAdjustmentDesc,
                    tag = "alloc_mode_principal_adjustment",
                    onClick = { allocationMode = AllocationMode.PRINCIPAL_ADJUSTMENT }
                )
                AllocationModeOptionCard(
                    selected = allocationMode == AllocationMode.INTEREST_FIRST,
                    title = strings.modeInterestFirst,
                    description = strings.modeInterestFirstDesc,
                    tag = "alloc_mode_interest_first",
                    onClick = { allocationMode = AllocationMode.INTEREST_FIRST }
                )

                OutlinedTextField(
                    value = manualNote,
                    onValueChange = { manualNote = it },
                    label = { Text(strings.optionalNote) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("payment_note_input")
                )

                // Live Preview Card
                if (preSnapshot != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(strings.paymentPreview, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            DetailRow(strings.beforePayment + " • " + strings.currentPrincipal, fmt(preSnapshot.currentPrincipalPaise), PrincipalBlue)
                            DetailRow(strings.accruedInterest, fmt(preSnapshot.accruedUnpaidInterestPaise), InterestOrange)
                            DetailRow(strings.misradhan, fmt(preSnapshot.misradhanPaise), MisradhanPurple)
                            if (allocationPreview != null) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DetailRow(strings.paymentAmount, "-${fmt(allocationPreview.paymentAmountPaise)}", PaidGreen)
                                DetailRow(strings.afterPayment + " • " + strings.currentPrincipal, fmt(allocationPreview.principalAfterPaise), PaidGreen)
                                if (allocationPreview.unpaidInterestAfterPaise > 0L) {
                                    DetailRow("Remaining Unpaid Interest", fmt(allocationPreview.unpaidInterestAfterPaise), InterestOrange)
                                }
                                if (allocationPreview.advancePaise > 0L) {
                                    DetailRow(strings.advanceBalance, fmt(allocationPreview.advancePaise), PaidGreen)
                                }
                                if (allocationMode == AllocationMode.PRINCIPAL_ADJUSTMENT) {
                                    Text(
                                        text = "Next interest period starts ${paymentDate.toDisplayString()} on ${fmt(allocationPreview.principalAfterPaise)} (unpaid interest capitalized).",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Text(validationError!!, color = OverdueRed, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        when {
                            selectedCredit == null -> validationError = strings.selectCredit
                            enteredPaise <= 0L -> validationError = "Enter positive payment amount"
                            paymentDate < creditStart -> validationError = "Payment date precedes credit start date"
                            else -> {
                                validationError = null
                                showConfirmDialog = true
                            }
                        }
                    },
                    enabled = availableCredits.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("review_credit_payment_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(strings.confirmAndSavePayment)
                }
            }
        }
    }

    if (showConfirmDialog && allocationPreview != null && selectedCredit != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(strings.paymentPreview) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${selectedCredit.creditNumber} • ${paymentDate.toDisplayString()}")
                    Text("${strings.misradhan}: ${fmt(allocationPreview.misradhanBeforePaise)}")
                    Text("${strings.paymentAmount}: ${fmt(allocationPreview.paymentAmountPaise)}")
                    Text("New Principal: ${fmt(allocationPreview.principalAfterPaise)}", fontWeight = FontWeight.Bold)
                    if (allocationPreview.advancePaise > 0L) {
                        Text("${strings.advanceBalance}: ${fmt(allocationPreview.advancePaise)}", color = PaidGreen)
                    }
                    if (allocationPreview.isSettled) {
                        Text("This payment settles the credit balance to ₹0 (Closed).", color = PaidGreen)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onSavePayment(selectedCredit.id, enteredPaise, paymentDate, method, allocationMode, manualNote)
                    },
                    modifier = Modifier.testTag("confirm_save_credit_payment_btn")
                ) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
private fun AllocationModeOptionCard(
    selected: Boolean,
    title: String,
    description: String,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNormalPaymentScreen(
    preselectedPersonId: String?,
    state: MainUiState,
    onBack: () -> Unit,
    onSaveNormalPayment: (personId: String, amountPaise: Long, date: LedgerDate, method: PaymentMethod, note: String) -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current
    val activePersons = state.persons.filter { !it.isArchived }

    var selectedPersonId by remember(preselectedPersonId, activePersons) {
        mutableStateOf(preselectedPersonId ?: activePersons.firstOrNull()?.id ?: "")
    }
    var personExpanded by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var paymentDate by remember { mutableStateOf(LedgerDate.todayLocal()) }
    var method by remember { mutableStateOf(PaymentMethod.UPI) }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.addNormalPayment) },
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
                Surface(
                    color = InterestOrange.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = InterestOrange)
                        Column {
                            Text(strings.normalPaymentBanner, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = InterestOrange)
                            Text(strings.normalPaymentNotice, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (activePersons.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No active profiles found. Please add a profile first.",
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
                    val personName = activePersons.firstOrNull { it.id == selectedPersonId }?.name ?: strings.selectPerson
                    OutlinedTextField(
                        value = personName,
                        onValueChange = {},
                        readOnly = true,
                        enabled = activePersons.isNotEmpty(),
                        label = { Text(strings.selectPerson) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = personExpanded, onDismissRequest = { personExpanded = false }) {
                        activePersons.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = { selectedPersonId = p.id; personExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(strings.paymentAmount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("normal_payment_amount_input")
                )

                LedgerDateField(
                    label = strings.paymentDate,
                    date = paymentDate,
                    onDateSelected = { paymentDate = it },
                    testTag = "normal_payment_date_input"
                )

                Text(strings.paymentMethod, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethod.entries.forEach { pm ->
                        FilterChip(
                            selected = method == pm,
                            onClick = { method = pm },
                            label = { Text(StatementTextGenerator.formatPaymentMethod(pm, state.settings.language), maxLines = 1) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(strings.optionalNote) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("normal_payment_note_input")
                )

                if (errorMsg != null) {
                    Text(errorMsg!!, color = OverdueRed, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        val paise = IndianNumberFormatter.rupeesInputToPaiseOrNull(amountText)
                        when {
                            selectedPersonId.isBlank() -> errorMsg = strings.selectPerson
                            paise == null || paise <= 0L -> errorMsg = "Enter positive payment amount"
                            else -> onSaveNormalPayment(selectedPersonId, paise, paymentDate, method, note)
                        }
                    },
                    enabled = activePersons.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = InterestOrange),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_normal_payment_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(strings.addNormalPayment)
                }
            }
        }
    }
}

@Composable
fun EditPaymentDialog(
    payment: Payment,
    state: MainUiState,
    onDismiss: () -> Unit,
    onSave: (
        paymentId: String,
        amountPaise: Long,
        paymentDate: LedgerDate,
        method: PaymentMethod,
        allocationMode: AllocationMode?,
        note: String
    ) -> Unit
) {
    val strings = LocalAppStrings.current
    val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, state.settings.displayDecimals, trimTrailingZerosWhenWhole = true) }
    val linkedCredit = payment.creditId?.let { cid -> state.credits.firstOrNull { it.id == cid } }
    val creditStart = linkedCredit?.let { InterestEngine.parseDateOnly(it.startDate) }

    var amountText by remember(payment.id) {
        mutableStateOf(IndianNumberFormatter.paiseToPlainRupeesString(payment.amountPaise))
    }
    var paymentDate by remember(payment.id) {
        mutableStateOf(LedgerDate.parseOrNull(payment.paymentDate) ?: LedgerDate.todayLocal())
    }
    var method by remember(payment.id) { mutableStateOf(payment.paymentMethod) }
    var allocationMode by remember(payment.id) {
        mutableStateOf(payment.allocationMode ?: AllocationMode.PRINCIPAL_ADJUSTMENT)
    }
    var noteText by remember(payment.id) { mutableStateOf(payment.note) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val enteredPaise = IndianNumberFormatter.rupeesInputToPaiseOrNull(amountText) ?: 0L
    val preSnapshot = remember(linkedCredit, state.events, payment.id, paymentDate) {
        if (linkedCredit == null || creditStart == null || paymentDate < creditStart) null
        else runCatching {
            InterestEngine.calculateCreditBalanceAsOfDate(
                linkedCredit,
                state.events.filter { it.creditId == linkedCredit.id && it.id != payment.id },
                paymentDate
            )
        }.getOrNull()
    }
    val allocPreview = remember(preSnapshot, enteredPaise, allocationMode, payment.paymentType) {
        if (payment.paymentType != PaymentType.CREDIT || preSnapshot == null || enteredPaise <= 0L) null
        else if (allocationMode == AllocationMode.INTEREST_FIRST) {
            InterestEngine.applyInterestFirstPayment(
                preSnapshot.currentPrincipalPaise,
                preSnapshot.accruedUnpaidInterestPaise,
                enteredPaise
            )
        } else {
            InterestEngine.applyMisradhanLessPayment(
                preSnapshot.currentPrincipalPaise,
                preSnapshot.accruedUnpaidInterestPaise,
                enteredPaise
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.editPaymentTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (linkedCredit != null) {
                    Text(
                        "${linkedCredit.creditNumber} • ${payment.receiptNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = PrincipalBlue
                    )
                } else {
                    Text(
                        "${strings.normalPaymentsLabel} • ${payment.receiptNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = InterestOrange
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorText = null },
                    label = { Text(strings.paymentAmount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_payment_amount_input")
                )
                LedgerDateField(
                    label = strings.paymentDate,
                    date = paymentDate,
                    onDateSelected = { d ->
                        paymentDate = d
                        errorText = if (creditStart != null && d < creditStart) {
                            "Cannot precede credit start date (${creditStart.toDisplayString()})"
                        } else null
                    },
                    testTag = "edit_payment_date_input"
                )
                Text(strings.paymentMethod, style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaymentMethod.entries.forEach { pm ->
                        FilterChip(
                            selected = method == pm,
                            onClick = { method = pm },
                            label = { Text(StatementTextGenerator.formatPaymentMethod(pm, state.settings.language), maxLines = 1) }
                        )
                    }
                }
                if (payment.paymentType == PaymentType.CREDIT) {
                    Text(strings.allocationMode, style = MaterialTheme.typography.labelMedium)
                    AllocationModeOptionCard(
                        selected = allocationMode == AllocationMode.PRINCIPAL_ADJUSTMENT,
                        title = strings.modePrincipalAdjustment,
                        description = strings.modePrincipalAdjustmentDesc,
                        tag = "edit_alloc_principal_adjustment",
                        onClick = { allocationMode = AllocationMode.PRINCIPAL_ADJUSTMENT }
                    )
                    AllocationModeOptionCard(
                        selected = allocationMode == AllocationMode.INTEREST_FIRST,
                        title = strings.modeInterestFirst,
                        description = strings.modeInterestFirstDesc,
                        tag = "edit_alloc_interest_first",
                        onClick = { allocationMode = AllocationMode.INTEREST_FIRST }
                    )
                    if (allocPreview != null) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(strings.paymentPreview, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "${strings.misradhan}: ${fmt(allocPreview.misradhanBeforePaise)} → New Principal: ${fmt(allocPreview.principalAfterPaise)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (allocPreview.unpaidInterestAfterPaise > 0L) {
                                    Text(
                                        "Remaining Unpaid Interest: ${fmt(allocPreview.unpaidInterestAfterPaise)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InterestOrange
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(strings.notes) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("edit_payment_note_input")
                )
                if (errorText != null) {
                    Text(errorText!!, color = OverdueRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paise = IndianNumberFormatter.rupeesInputToPaiseOrNull(amountText)
                    when {
                        paise == null || paise <= 0L -> errorText = "Enter positive payment amount"
                        payment.paymentType == PaymentType.CREDIT && creditStart != null && paymentDate < creditStart ->
                            errorText = "Cannot precede credit start date (${creditStart.toDisplayString()})"
                        else -> onSave(
                            payment.id,
                            paise,
                            paymentDate,
                            method,
                            if (payment.paymentType == PaymentType.CREDIT) allocationMode else null,
                            noteText
                        )
                    }
                },
                modifier = Modifier.testTag("save_edit_payment_btn")
            ) { Text(strings.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun EditAdjustmentDialog(
    event: CreditEvent,
    state: MainUiState,
    onDismiss: () -> Unit,
    onSave: (
        eventId: String,
        newPrincipalPaise: Long,
        newRate: String,
        newRatePeriod: RatePeriod,
        effectiveDate: LedgerDate,
        note: String
    ) -> Unit
) {
    val strings = LocalAppStrings.current
    val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, state.settings.displayDecimals, trimTrailingZerosWhenWhole = true) }
    val credit = state.credits.firstOrNull { it.id == event.creditId }
    val creditStart = credit?.let { InterestEngine.parseDateOnly(it.startDate) } ?: LedgerDate.todayLocal()

    var effectiveDate by remember(event.id) {
        mutableStateOf(LedgerDate.parseOrNull(event.effectiveDate) ?: LedgerDate.todayLocal())
    }
    var principalText by remember(event.id) {
        mutableStateOf(IndianNumberFormatter.paiseToPlainRupeesString(event.principalAfterPaise))
    }
    var rateText by remember(event.id) { mutableStateOf(event.rateAfter) }
    var ratePeriod by remember(event.id) { mutableStateOf(event.ratePeriodAfter) }
    var noteText by remember(event.id) { mutableStateOf(event.note) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val preSnapshot = remember(credit, state.events, event.id, effectiveDate) {
        if (credit == null || effectiveDate < creditStart) null
        else runCatching {
            InterestEngine.calculateCreditBalanceAsOfDate(
                credit,
                state.events.filter { it.creditId == credit.id && it.id != event.id },
                effectiveDate
            )
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${strings.edit} — ${strings.mainCreditUpdate}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LedgerDateField(
                    label = "Effective Date",
                    date = effectiveDate,
                    onDateSelected = { d ->
                        effectiveDate = d
                        errorText = if (d < creditStart) "Cannot precede credit start date (${creditStart.toDisplayString()})" else null
                    },
                    testTag = "edit_adj_date_input"
                )
                OutlinedTextField(
                    value = principalText,
                    onValueChange = { principalText = it; errorText = null },
                    label = { Text("New Principal (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_adj_principal_input")
                )
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it; errorText = null },
                    label = { Text("New Interest Rate (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_adj_rate_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ratePeriod == RatePeriod.MONTHLY,
                        onClick = { ratePeriod = RatePeriod.MONTHLY },
                        label = { Text(strings.monthly) }
                    )
                    FilterChip(
                        selected = ratePeriod == RatePeriod.YEARLY,
                        onClick = { ratePeriod = RatePeriod.YEARLY },
                        label = { Text(strings.yearly) }
                    )
                }
                if (preSnapshot != null) {
                    Text(
                        "Before adjustment: Principal ${fmt(preSnapshot.currentPrincipalPaise)}, Interest ${fmt(preSnapshot.accruedUnpaidInterestPaise)}, Rate ${preSnapshot.activeRate}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(strings.notes) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(errorText!!, color = OverdueRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paise = IndianNumberFormatter.rupeesInputToPaiseOrNull(principalText)
                    val rateBd = runCatching { java.math.BigDecimal(rateText.trim()) }.getOrNull()
                    when {
                        effectiveDate < creditStart -> errorText = "Cannot precede credit start date"
                        paise == null || paise < 0L -> errorText = "Enter valid non-negative principal"
                        rateBd == null || rateBd < java.math.BigDecimal.ZERO -> errorText = "Enter valid non-negative rate"
                        else -> onSave(event.id, paise, rateText.trim(), ratePeriod, effectiveDate, noteText)
                    }
                },
                modifier = Modifier.testTag("save_edit_adj_btn")
            ) { Text(strings.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun DeleteEventConfirmDialog(
    event: CreditEvent,
    state: MainUiState,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val strings = LocalAppStrings.current
    val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, state.settings.displayDecimals, trimTrailingZerosWhenWhole = true) }
    val credit = state.credits.firstOrNull { it.id == event.creditId }
    val impactText = remember(event.id, credit, state.events) {
        if (credit == null) {
            "${event.eventType.name}: ${fmt(event.amountPaise)}"
        } else {
            val cEvents = state.events.filter { it.creditId == credit.id }
            val today = maxOf(LedgerDate.todayLocal(), InterestEngine.parseDateOnly(credit.startDate))
            val before = runCatching { InterestEngine.calculateCreditBalanceAsOfDate(credit, cEvents, today) }.getOrNull()
            val after = runCatching { InterestEngine.calculateCreditBalanceAsOfDate(credit, cEvents.filter { it.id != event.id }, today) }.getOrNull()
            if (before != null && after != null) {
                "Principal: ${fmt(before.currentPrincipalPaise)} → ${fmt(after.currentPrincipalPaise)}\nMisradhan: ${fmt(before.misradhanPaise)} → ${fmt(after.misradhanPaise)}"
            } else ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.deleteTransactionConfirmTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.deleteTransactionConfirmBody)
                if (impactText.isNotBlank()) {
                    Text(impactText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = OverdueRed),
                modifier = Modifier.testTag("confirm_delete_event_btn")
            ) { Text(strings.delete) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
