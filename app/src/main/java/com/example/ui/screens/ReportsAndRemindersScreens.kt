package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.engine.IndianNumberFormatter
import com.example.domain.engine.LedgerDate
import com.example.domain.model.DashboardFilter
import com.example.domain.model.PaymentType
import com.example.domain.model.ReminderStatus
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
fun ReportsScreen(
    state: MainUiState,
    onSelectFilter: (DashboardFilter) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current
    val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, state.settings.displayDecimals, trimTrailingZerosWhenWhole = true) }
    val summary = state.dashboardSummary

    val completedPayments = state.payments.filter { it.status == "COMPLETED" }
    val today = LedgerDate.todayLocal()
    val dailyTotal = completedPayments.filter { LedgerDate.parseOrNull(it.paymentDate) == today }.sumOf { it.amountPaise }
    val monthlyTotal = completedPayments.filter {
        val d = LedgerDate.parseOrNull(it.paymentDate)
        d != null && d.year == today.year && d.month == today.month
    }.sumOf { it.amountPaise }
    val yearlyTotal = completedPayments.filter {
        val d = LedgerDate.parseOrNull(it.paymentDate)
        d != null && d.year == today.year
    }.sumOf { it.amountPaise }

    val totalCreditPays = completedPayments.filter { it.paymentType == PaymentType.CREDIT }.sumOf { it.amountPaise }
    val totalNormalPays = completedPayments.filter { it.paymentType == PaymentType.NORMAL }.sumOf { it.amountPaise }

    // Monthly bar data for last 6 months
    val monthlySeries = remember(completedPayments, today) {
        (5 downTo 0).map { offset ->
            val targetMonthDate = today.plusMonthsClamped(-offset, 1)
            val total = completedPayments.filter { p ->
                val d = LedgerDate.parseOrNull(p.paymentDate)
                d != null && d.year == targetMonthDate.year && d.month == targetMonthDate.month
            }.sumOf { it.amountPaise }
            "${targetMonthDate.month}/${targetMonthDate.year % 100}" to total
        }
    }

    val topPersons = remember(state.personSummaries) {
        state.personSummaries.values
            .filter { !it.person.isArchived && it.totalMisradhanPaise > 0L }
            .sortedByDescending { it.totalMisradhanPaise }
    }

    val activeCreditSnapshots = remember(state.personSummaries) {
        state.personSummaries.values
            .flatMap { it.creditSnapshots }
            .filter { it.misradhanPaise > 0L }
            .sortedByDescending { it.misradhanPaise }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.reportsAndAnalytics) },
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
            LazyColumn(
                modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            DashboardFilter.TODAY to strings.filterToday,
                            DashboardFilter.THIS_MONTH to strings.filterMonth,
                            DashboardFilter.THIS_YEAR to strings.filterYear,
                            DashboardFilter.ALL_TIME to strings.filterAllTime
                        ).forEach { (f, label) ->
                            FilterChip(selected = state.dashboardFilter == f, onClick = { onSelectFilter(f) }, label = { Text(label) })
                        }
                    }
                }

                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Received Payments", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            DetailRow("Daily Received", fmt(dailyTotal), PaidGreen)
                            DetailRow("Monthly Received", fmt(monthlyTotal), PaidGreen)
                            DetailRow("Yearly Received", fmt(yearlyTotal), PaidGreen)
                            DetailRow(strings.creditPaymentsLabel, fmt(totalCreditPays), PrincipalBlue)
                            DetailRow(strings.normalPaymentsLabel, fmt(totalNormalPays), InterestOrange)
                        }
                    }
                }

                // Principal vs Interest Donut Chart
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${strings.totalPrincipal} vs ${strings.totalInterest}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            val principal = summary.totalPrincipalPaise.toFloat().coerceAtLeast(0f)
                            val interest = summary.totalAccruedUnpaidInterestPaise.toFloat().coerceAtLeast(0f)
                            val total = principal + interest

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                Canvas(modifier = Modifier.size(110.dp)) {
                                    val stroke = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Butt)
                                    if (total <= 0f) {
                                        drawArc(
                                            color = PrincipalBlue.copy(alpha = 0.2f),
                                            startAngle = 0f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            style = stroke
                                        )
                                    } else {
                                        val pSweep = (principal / total) * 360f
                                        drawArc(
                                            color = PrincipalBlue,
                                            startAngle = -90f,
                                            sweepAngle = pSweep,
                                            useCenter = false,
                                            style = stroke
                                        )
                                        drawArc(
                                            color = InterestOrange,
                                            startAngle = -90f + pSweep,
                                            sweepAngle = 360f - pSweep,
                                            useCenter = false,
                                            style = stroke
                                        )
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${strings.totalPrincipal}: ${fmt(summary.totalPrincipalPaise)}", color = PrincipalBlue, fontWeight = FontWeight.SemiBold)
                                    Text("${strings.totalInterest}: ${fmt(summary.totalAccruedUnpaidInterestPaise)}", color = InterestOrange, fontWeight = FontWeight.SemiBold)
                                    Text("${strings.totalMisradhan}: ${fmt(summary.totalMisradhanPaise)}", color = MisradhanPurple, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 6-Month Collection Bar Chart
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("6-Month Collection Trend", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            val maxVal = (monthlySeries.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
                            Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                                val count = monthlySeries.size
                                val slotWidth = size.width / count
                                val barWidth = slotWidth * 0.48f
                                monthlySeries.forEachIndexed { idx, (_, amount) ->
                                    val ratio = amount.toFloat() / maxVal.toFloat()
                                    val barHeight = (size.height - 16.dp.toPx()) * ratio
                                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                                    drawRoundRect(
                                        color = PaidGreen,
                                        topLeft = Offset(left, size.height - barHeight),
                                        size = Size(barWidth, barHeight.coerceAtLeast(4.dp.toPx())),
                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                monthlySeries.forEach { (label, _) ->
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Person-wise Outstanding (${topPersons.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                items(topPersons, key = { "p_${it.person.id}" }) { ps ->
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(ps.person.name, fontWeight = FontWeight.SemiBold)
                                Text("Principal: ${fmt(ps.totalPrincipalPaise)} | Interest: ${fmt(ps.totalAccruedUnpaidInterestPaise)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(fmt(ps.totalMisradhanPaise), fontWeight = FontWeight.Bold, color = MisradhanPurple)
                        }
                    }
                }

                item {
                    Text("Credit-wise Outstanding (${activeCreditSnapshots.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                items(activeCreditSnapshots, key = { "c_${it.creditId}" }) { cs ->
                    val pName = state.persons.firstOrNull { it.id == cs.personId }?.name ?: ""
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${cs.creditNumber} ($pName)", fontWeight = FontWeight.SemiBold)
                                Text("Principal: ${fmt(cs.currentPrincipalPaise)} | Interest: ${fmt(cs.accruedUnpaidInterestPaise)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(fmt(cs.misradhanPaise), fontWeight = FontWeight.Bold, color = MisradhanPurple)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onAddReminder: (personId: String, creditId: String?, date: LedgerDate, time: String, note: String) -> Unit,
    onUpdateStatus: (reminderId: String, status: ReminderStatus) -> Unit,
    onDeleteReminder: (reminderId: String) -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val today = LedgerDate.todayLocal()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Upcoming, 1 = Overdue, 2 = Completed
    var showAddDialog by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        showAddDialog = true
    }

    val filtered = remember(state.reminders, selectedTab, today) {
        state.reminders.filter { r ->
            val d = LedgerDate.parseOrNull(r.reminderDate) ?: today
            when (selectedTab) {
                0 -> r.status == ReminderStatus.UPCOMING && d >= today
                1 -> r.status == ReminderStatus.UPCOMING && d < today
                else -> r.status == ReminderStatus.COMPLETED
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.remindersTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        showAddDialog = true
                    }
                },
                modifier = Modifier.testTag("add_reminder_fab")
            ) {
                Icon(Icons.Default.AddAlert, contentDescription = "Add Reminder")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 800.dp).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text("Upcoming") })
                    FilterChip(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text("Overdue") })
                    FilterChip(selected = selectedTab == 2, onClick = { selectedTab = 2 }, label = { Text("Completed") })
                }

                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(strings.noDataYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.id }) { rem ->
                            val personName = state.persons.firstOrNull { it.id == rem.personId }?.name ?: ""
                            val creditNum = rem.creditId?.let { cid -> state.credits.firstOrNull { it.id == cid }?.creditNumber }
                            val dStr = LedgerDate.parseOrNull(rem.reminderDate)?.toDisplayString() ?: rem.reminderDate
                            val isOverdue = selectedTab == 1

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOverdue) OverdueRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("$personName ${if (creditNum != null) "• $creditNum" else ""}", fontWeight = FontWeight.Bold)
                                        Text("$dStr ${rem.reminderTime}", style = MaterialTheme.typography.bodySmall, color = if (isOverdue) OverdueRed else MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (rem.note.isNotBlank()) Text(rem.note, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row {
                                        if (rem.status != ReminderStatus.COMPLETED) {
                                            IconButton(onClick = { onUpdateStatus(rem.id, ReminderStatus.COMPLETED) }) {
                                                Icon(Icons.Default.Check, contentDescription = "Complete", tint = PaidGreen)
                                            }
                                        }
                                        IconButton(onClick = { onDeleteReminder(rem.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = OverdueRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val activePersons = state.persons.filter { !it.isArchived }
        var personId by remember { mutableStateOf(activePersons.firstOrNull()?.id ?: "") }
        var personOpen by remember { mutableStateOf(false) }
        var creditId by remember { mutableStateOf<String?>(null) }
        var creditOpen by remember { mutableStateOf(false) }
        var date by remember { mutableStateOf(today.plusDays(1)) }
        var time by remember { mutableStateOf("10:00") }
        var note by remember { mutableStateOf("") }

        val personCredits = state.credits.filter { it.personId == personId }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(strings.remindersTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExposedDropdownMenuBox(expanded = personOpen, onExpandedChange = { personOpen = it }) {
                        OutlinedTextField(
                            value = activePersons.firstOrNull { it.id == personId }?.name ?: strings.selectPerson,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(strings.selectPerson) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = personOpen, onDismissRequest = { personOpen = false }) {
                            activePersons.forEach { p ->
                                DropdownMenuItem(text = { Text(p.name) }, onClick = { personId = p.id; creditId = null; personOpen = false })
                            }
                        }
                    }

                    ExposedDropdownMenuBox(expanded = creditOpen, onExpandedChange = { creditOpen = it }) {
                        OutlinedTextField(
                            value = personCredits.firstOrNull { it.id == creditId }?.creditNumber ?: "None (General)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("${strings.creditNumber} (Optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = creditOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = creditOpen, onDismissRequest = { creditOpen = false }) {
                            DropdownMenuItem(text = { Text("None (General)") }, onClick = { creditId = null; creditOpen = false })
                            personCredits.forEach { c ->
                                DropdownMenuItem(text = { Text(c.creditNumber) }, onClick = { creditId = c.id; creditOpen = false })
                            }
                        }
                    }

                    LedgerDateField(label = "Date", date = date, onDateSelected = { date = it })
                    OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time (HH:mm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(strings.notes) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (personId.isNotBlank()) {
                        onAddReminder(personId, creditId, date, time, note)
                        showAddDialog = false
                    }
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}
