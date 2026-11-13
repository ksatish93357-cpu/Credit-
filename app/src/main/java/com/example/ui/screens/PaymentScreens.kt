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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.engine.IndianNumberFormatter
import com.example.domain.engine.InterestEngine
import com.example.domain.engine.LedgerDate
import com.example.domain.engine.StatementTextGenerator
import com.example.domain.model.AllocationMode
import com.example.domain.model.CreditStatus
import com.example.domain.model.PaymentMethod
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
                ExposedDropdownMenuBox(
                    expanded = creditDropdownOpen,
                    onExpandedChange = { creditDropdownOpen = it }
                ) {
                    val labelText = selectedCredit?.let { c ->
                        val pName = state.persons.firstOrNull { it.id == c.personId }?.name ?: ""
                        "${c.creditNumber} ($pName)"
                    } ?: strings.selectCredit
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = {},
                        readOnly = true,
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

                ExposedDropdownMenuBox(
                    expanded = personExpanded,
                    onExpandedChange = { personExpanded = it }
                ) {
                    val personName = activePersons.firstOrNull { it.id == selectedPersonId }?.name ?: strings.selectPerson
                    OutlinedTextField(
                        value = personName,
                        onValueChange = {},
                        readOnly = true,
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
