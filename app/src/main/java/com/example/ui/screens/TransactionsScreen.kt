package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.domain.model.CreditEvent
import com.example.domain.model.CreditEventType
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.TransactionSortOption
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
fun TransactionsScreen(
    state: MainUiState,
    onShareReceipt: (Payment) -> Unit,
    onEditManualNote: (paymentId: String, newNote: String) -> Unit,
    onReverseEvent: (eventId: String, reason: String) -> Unit
) {
    val strings = LocalAppStrings.current
    val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, state.settings.displayDecimals, trimTrailingZerosWhenWhole = true) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<CreditEventType?>(null) }
    var selectedMethodFilter by remember { mutableStateOf<PaymentMethod?>(null) }
    var selectedPersonFilter by remember { mutableStateOf<String?>(null) }
    var dateFrom by remember { mutableStateOf<LedgerDate?>(null) }
    var dateTo by remember { mutableStateOf<LedgerDate?>(null) }
    var minRupees by remember { mutableStateOf("") }
    var maxRupees by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(TransactionSortOption.NEWEST_FIRST) }

    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedEventForDetail by remember { mutableStateOf<CreditEvent?>(null) }
    var editNotePaymentId by remember { mutableStateOf<String?>(null) }
    var reverseTarget by remember { mutableStateOf<CreditEvent?>(null) }

    val personMap = remember(state.persons) { state.persons.associateBy { it.id } }
    val creditMap = remember(state.credits) { state.credits.associateBy { it.id } }
    val paymentMap = remember(state.payments) { state.payments.associateBy { it.id } }

    val filteredEvents = remember(
        state.events,
        personMap,
        creditMap,
        searchQuery,
        selectedTypeFilter,
        selectedMethodFilter,
        selectedPersonFilter,
        dateFrom,
        dateTo,
        minRupees,
        maxRupees,
        sortOption
    ) {
        val q = searchQuery.trim().lowercase()
        val minPaise = IndianNumberFormatter.rupeesInputToPaiseOrNull(minRupees)
        val maxPaise = IndianNumberFormatter.rupeesInputToPaiseOrNull(maxRupees)

        state.events.filter { ev ->
            val person = personMap[ev.personId]
            val credit = creditMap[ev.creditId]
            val d = LedgerDate.parseOrNull(ev.effectiveDate)

            val matchSearch = q.isEmpty() ||
                ev.id.lowercase().contains(q) ||
                (ev.receiptNumber?.lowercase()?.contains(q) == true) ||
                (person?.name?.lowercase()?.contains(q) == true) ||
                (person?.phone?.lowercase()?.contains(q) == true) ||
                (credit?.creditNumber?.lowercase()?.contains(q) == true)

            val matchType = selectedTypeFilter == null || ev.eventType == selectedTypeFilter
            val matchMethod = selectedMethodFilter == null || ev.paymentMethod == selectedMethodFilter
            val matchPerson = selectedPersonFilter == null || ev.personId == selectedPersonFilter
            val matchFrom = dateFrom == null || (d != null && d >= dateFrom!!)
            val matchTo = dateTo == null || (d != null && d <= dateTo!!)
            val matchMin = minPaise == null || ev.amountPaise >= minPaise
            val matchMax = maxPaise == null || ev.amountPaise <= maxPaise

            matchSearch && matchType && matchMethod && matchPerson && matchFrom && matchTo && matchMin && matchMax
        }.sortedWith { a, b ->
            when (sortOption) {
                TransactionSortOption.NEWEST_FIRST -> {
                    val cmp = b.effectiveDate.compareTo(a.effectiveDate)
                    if (cmp != 0) cmp else b.createdAt.compareTo(a.createdAt)
                }
                TransactionSortOption.OLDEST_FIRST -> {
                    val cmp = a.effectiveDate.compareTo(b.effectiveDate)
                    if (cmp != 0) cmp else a.createdAt.compareTo(b.createdAt)
                }
                TransactionSortOption.AMOUNT_HIGH_LOW -> b.amountPaise.compareTo(a.amountPaise)
                TransactionSortOption.AMOUNT_LOW_HIGH -> a.amountPaise.compareTo(b.amountPaise)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${strings.navTransactions} (${filteredEvents.size})") },
                actions = {
                    IconButton(
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("transactions_filter_btn")
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("transactions_sort_btn")
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("Newest First") }, onClick = { sortOption = TransactionSortOption.NEWEST_FIRST; showSortMenu = false })
                            DropdownMenuItem(text = { Text("Oldest First") }, onClick = { sortOption = TransactionSortOption.OLDEST_FIRST; showSortMenu = false })
                            DropdownMenuItem(text = { Text("Amount High → Low") }, onClick = { sortOption = TransactionSortOption.AMOUNT_HIGH_LOW; showSortMenu = false })
                            DropdownMenuItem(text = { Text("Amount Low → High") }, onClick = { sortOption = TransactionSortOption.AMOUNT_LOW_HIGH; showSortMenu = false })
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(modifier = Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchPlaceholder) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("transactions_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTypeFilter == null,
                        onClick = { selectedTypeFilter = null },
                        label = { Text(strings.filterAllTime) }
                    )
                    listOf(
                        CreditEventType.CREDIT_CREATED to "Credits",
                        CreditEventType.CREDIT_PAYMENT to strings.creditPaymentsLabel,
                        CreditEventType.NORMAL_PAYMENT to strings.normalPaymentsLabel,
                        CreditEventType.PRINCIPAL_ADJUSTMENT to "Adjustments",
                        CreditEventType.CORRECTION_REVERSAL to "Reversals"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = selectedTypeFilter == type,
                            onClick = { selectedTypeFilter = if (selectedTypeFilter == type) null else type },
                            label = { Text(label) }
                        )
                    }
                }

                if (filteredEvents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(strings.noDataYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredEvents, key = { it.id }) { ev ->
                            val personName = personMap[ev.personId]?.name ?: ""
                            val creditNum = creditMap[ev.creditId]?.creditNumber
                            val dDisp = LedgerDate.parseOrNull(ev.effectiveDate)?.toDisplayString() ?: ev.effectiveDate
                            val amountColor = when (ev.eventType) {
                                CreditEventType.CREDIT_PAYMENT -> PaidGreen
                                CreditEventType.NORMAL_PAYMENT -> InterestOrange
                                CreditEventType.CREDIT_CREATED -> PrincipalBlue
                                CreditEventType.CORRECTION_REVERSAL -> OverdueRed
                                else -> MisradhanPurple
                            }

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedEventForDetail = ev }
                                    .testTag("tx_card_${ev.id}")
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$personName ${if (creditNum != null) "($creditNum)" else ""}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = fmt(ev.amountPaise),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = amountColor
                                        )
                                    }
                                    Text(
                                        text = "${ev.eventType.name} • $dDisp ${if (ev.isReversed) "[REVERSED]" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!ev.receiptNumber.isNullOrBlank()) {
                                        Text("${strings.receiptNumber}: ${ev.receiptNumber}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (ev.principalBeforePaise > 0L || ev.principalAfterPaise > 0L) {
                                        Text(
                                            "Principal: ${fmt(ev.principalBeforePaise)} → ${fmt(ev.principalAfterPaise)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }

    if (selectedEventForDetail != null) {
        val ev = selectedEventForDetail!!
        val linkedPayment = paymentMap[ev.id]
        AlertDialog(
            onDismissRequest = { selectedEventForDetail = null },
            title = { Text(ev.eventType.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("ID: ${ev.id.take(12)}")
                    Text("Person: ${personMap[ev.personId]?.name ?: ""}")
                    creditMap[ev.creditId]?.let { Text("Credit: ${it.creditNumber}") }
                    Text("Date: ${LedgerDate.parseOrNull(ev.effectiveDate)?.toDisplayString() ?: ev.effectiveDate}")
                    Text("Amount: ${fmt(ev.amountPaise)}", fontWeight = FontWeight.Bold)
                    ev.paymentMethod?.let { Text("Method: ${StatementTextGenerator.formatPaymentMethod(it, state.settings.language)}") }
                    ev.receiptNumber?.let { Text("Receipt: $it") }
                    if (ev.systemNote.isNotBlank()) {
                        HorizontalDivider()
                        Text(ev.systemNote, style = MaterialTheme.typography.bodySmall)
                    }
                    if (ev.note.isNotBlank()) {
                        Text("Note: ${ev.note}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (linkedPayment != null) {
                        TextButton(onClick = {
                            selectedEventForDetail = null
                            onShareReceipt(linkedPayment)
                        }) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(strings.share)
                        }
                        TextButton(onClick = {
                            selectedEventForDetail = null
                            editNotePaymentId = linkedPayment.id
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(strings.edit)
                        }
                    }
                    if (!ev.isReversed && ev.eventType != CreditEventType.CREDIT_CREATED && ev.eventType != CreditEventType.CORRECTION_REVERSAL) {
                        TextButton(onClick = {
                            selectedEventForDetail = null
                            reverseTarget = ev
                        }) {
                            Icon(Icons.Default.Undo, contentDescription = null, tint = OverdueRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(strings.reverseEvent, color = OverdueRed)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedEventForDetail = null }) { Text(strings.cancel) }
            }
        )
    }

    if (editNotePaymentId != null) {
        val pay = paymentMap[editNotePaymentId!!]
        if (pay != null) {
            var noteText by remember { mutableStateOf(pay.note) }
            AlertDialog(
                onDismissRequest = { editNotePaymentId = null },
                title = { Text("${strings.edit} ${strings.notes}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("System note is preserved automatically.", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            label = { Text(strings.notes) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onEditManualNote(pay.id, noteText)
                        editNotePaymentId = null
                    }) { Text(strings.save) }
                },
                dismissButton = {
                    TextButton(onClick = { editNotePaymentId = null }) { Text(strings.cancel) }
                }
            )
        }
    }

    if (reverseTarget != null) {
        val ev = reverseTarget!!
        var reason by remember { mutableStateOf("") }
        val credit = creditMap[ev.creditId]
        val impactText = remember(ev, credit, state.events) {
            if (credit == null) "Normal payment ${fmt(ev.amountPaise)} will be marked reversed."
            else {
                val cEvents = state.events.filter { it.creditId == credit.id }
                val today = LedgerDate.todayLocal()
                val before = runCatching { InterestEngine.calculateCreditBalanceAsOfDate(credit, cEvents, today) }.getOrNull()
                val after = runCatching { InterestEngine.calculateCreditBalanceAsOfDate(credit, cEvents.filter { it.id != ev.id }, today) }.getOrNull()
                if (before != null && after != null) {
                    "Principal: ${fmt(before.currentPrincipalPaise)} → ${fmt(after.currentPrincipalPaise)}\nMisradhan: ${fmt(before.misradhanPaise)} → ${fmt(after.misradhanPaise)}"
                } else ""
            }
        }
        AlertDialog(
            onDismissRequest = { reverseTarget = null },
            title = { Text("${strings.reverseEvent} — ${strings.impactPreview}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(impactText)
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReverseEvent(ev.id, reason.ifBlank { "Reversed by user" })
                        reverseTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OverdueRed)
                ) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { reverseTarget = null }) { Text(strings.cancel) }
            }
        )
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filters") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LedgerDateField(label = "From Date", date = dateFrom, onDateSelected = { dateFrom = it }, isOptional = true, onCleared = { dateFrom = null })
                    LedgerDateField(label = "To Date", date = dateTo, onDateSelected = { dateTo = it }, isOptional = true, onCleared = { dateTo = null })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minRupees,
                            onValueChange = { minRupees = it },
                            label = { Text("Min ₹") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxRupees,
                            onValueChange = { maxRupees = it },
                            label = { Text("Max ₹") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(strings.paymentMethod, style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = selectedMethodFilter == null, onClick = { selectedMethodFilter = null }, label = { Text("All") })
                        PaymentMethod.entries.forEach { pm ->
                            FilterChip(selected = selectedMethodFilter == pm, onClick = { selectedMethodFilter = pm }, label = { Text(pm.name) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFilterDialog = false }) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedTypeFilter = null
                    selectedMethodFilter = null
                    selectedPersonFilter = null
                    dateFrom = null
                    dateTo = null
                    minRupees = ""
                    maxRupees = ""
                    showFilterDialog = false
                }) { Text("Reset") }
            }
        )
    }
}
