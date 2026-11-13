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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.IndianNumberFormatter
import com.example.domain.engine.LedgerDate
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppThemeMode
import com.example.domain.model.CreditEventType
import com.example.domain.model.DashboardFilter
import com.example.domain.model.PaymentType
import com.example.ui.components.CreditLoggerLogoBadge
import com.example.ui.components.LedgerDateField
import com.example.ui.components.PersonAvatar
import com.example.ui.components.SemanticKpiCard
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.InterestOrange
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MisradhanPurple
import com.example.ui.theme.OverdueRed
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PrincipalBlue
import com.example.ui.viewmodel.MainUiState

@Composable
fun HomeDashboardScreen(
    state: MainUiState,
    onToggleTheme: () -> Unit,
    onSelectFilter: (DashboardFilter) -> Unit,
    onSetCustomRange: (LedgerDate, LedgerDate) -> Unit,
    onOpenReports: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenPersonDetail: (String) -> Unit,
    onOpenCreditDetail: (String) -> Unit,
    onOpenTransactions: () -> Unit,
    onQuickAddPerson: () -> Unit,
    onQuickAddCredit: () -> Unit,
    onQuickAddCreditPayment: () -> Unit,
    onQuickAddNormalPayment: () -> Unit
) {
    val strings = LocalAppStrings.current
    val decimals = state.settings.displayDecimals
    val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, decimals = decimals, trimTrailingZerosWhenWhole = true) }
    val summary = state.dashboardSummary

    var showCustomRangeDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 920.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CreditLoggerLogoBadge(size = 40.dp)
                        Column {
                            Text(
                                text = strings.appName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = GoldAccent
                            )
                            Text(
                                text = "${strings.helloGreeting} ${strings.financialSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenReports,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("home_reports_btn")
                        ) {
                            Icon(Icons.Default.Analytics, contentDescription = strings.reportsAndAnalytics)
                        }
                        IconButton(
                            onClick = onOpenReminders,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("home_reminders_btn")
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = strings.remindersTitle)
                        }
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("home_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (state.settings.theme == AppThemeMode.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle theme"
                            )
                        }
                    }
                }
            }

            // Filter Chips Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        DashboardFilter.TODAY to strings.filterToday,
                        DashboardFilter.THIS_WEEK to strings.filterWeek,
                        DashboardFilter.THIS_MONTH to strings.filterMonth,
                        DashboardFilter.THIS_YEAR to strings.filterYear,
                        DashboardFilter.CUSTOM to strings.filterCustom,
                        DashboardFilter.ALL_TIME to strings.filterAllTime
                    )
                    filters.forEach { (f, label) ->
                        FilterChip(
                            selected = state.dashboardFilter == f,
                            onClick = {
                                if (f == DashboardFilter.CUSTOM) showCustomRangeDialog = true
                                else onSelectFilter(f)
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("filter_${f.name.lowercase()}")
                        )
                    }
                }
            }

            // 4 Primary Semantic Summary Cards (2x2 Grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SemanticKpiCard(
                            title = strings.totalPrincipal,
                            amountText = fmt(summary.totalPrincipalPaise),
                            icon = Icons.Default.AccountBalanceWallet,
                            accentColor = PrincipalBlue,
                            modifier = Modifier.weight(1f).testTag("kpi_principal")
                        )
                        SemanticKpiCard(
                            title = strings.totalInterest,
                            amountText = fmt(summary.totalAccruedUnpaidInterestPaise),
                            icon = Icons.Default.Percent,
                            accentColor = InterestOrange,
                            modifier = Modifier.weight(1f).testTag("kpi_interest")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SemanticKpiCard(
                            title = strings.totalMisradhan,
                            amountText = fmt(summary.totalMisradhanPaise),
                            icon = Icons.Default.Savings,
                            accentColor = MisradhanPurple,
                            modifier = Modifier.weight(1f).testTag("kpi_misradhan")
                        )
                        SemanticKpiCard(
                            title = strings.totalPaid,
                            amountText = fmt(summary.totalPaidAmountPaise),
                            icon = Icons.Default.CheckCircle,
                            accentColor = PaidGreen,
                            modifier = Modifier.weight(1f).testTag("kpi_paid")
                        )
                    }
                }
            }

            // 4 Stat Counters Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DashboardMiniCounter(strings.profilesCount, summary.profilesCount.toString(), Modifier.weight(1f))
                        DashboardMiniCounter(strings.activeCredits, summary.activeCreditsCount.toString(), Modifier.weight(1f))
                        DashboardMiniCounter(strings.closedCredits, summary.closedCreditsCount.toString(), Modifier.weight(1f))
                        DashboardMiniCounter(strings.totalRecords, summary.totalCreditRecordsCount.toString(), Modifier.weight(1f))
                    }
                }
            }

            // Today's Interest + Period Summary
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = InterestOrange, modifier = Modifier.size(16.dp))
                                Text(strings.todaysInterestAccrual, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(fmt(summary.todaysInterestAccrualPaise), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(strings.originallyAdvanced, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fmt(summary.totalOriginallyAdvancedPaise), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // Period Breakdown Card (Credit Payments vs Normal Payments vs Period Advanced)
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.thisMonthSummary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings.creditPaymentsLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fmt(summary.periodCreditPaymentsPaise), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = PaidGreen)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings.normalPaymentsLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fmt(summary.periodNormalPaymentsPaise), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = InterestOrange)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings.originallyAdvanced, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fmt(summary.periodOriginallyAdvancedPaise), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = PrincipalBlue)
                        }
                    }
                }
            }

            // Quick Actions
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onQuickAddPerson, modifier = Modifier.testTag("home_btn_add_person")) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.addLoanTaker)
                    }
                    OutlinedButton(onClick = onQuickAddCredit, modifier = Modifier.testTag("home_btn_add_credit")) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.addCredit)
                    }
                    OutlinedButton(onClick = onQuickAddCreditPayment, modifier = Modifier.testTag("home_btn_add_payment")) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.addCreditPayment)
                    }
                    OutlinedButton(onClick = onQuickAddNormalPayment, modifier = Modifier.testTag("home_btn_add_normal_payment")) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.addNormalPayment)
                    }
                }
            }

            // Overdue Due-Date Alerts (if any)
            if (summary.overdueCredits.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = OverdueRed.copy(alpha = 0.14f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = OverdueRed)
                                Text(
                                    text = "${strings.overdueAlerts} (${summary.overdueCredits.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = OverdueRed
                                )
                            }
                            summary.overdueCredits.take(3).forEach { (credit, snap) ->
                                val personName = state.persons.firstOrNull { it.id == credit.personId }?.name ?: ""
                                val dueStr = LedgerDate.parseOrNull(credit.dueDate)?.toDisplayString() ?: ""
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onOpenCreditDetail(credit.id) }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("$personName • ${credit.creditNumber} ($dueStr)", style = MaterialTheme.typography.bodyMedium)
                                    Text(fmt(snap.misradhanPaise), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = OverdueRed)
                                }
                            }
                        }
                    }
                }
            }

            // Recent Profiles
            item {
                SectionHeader(title = strings.recentProfiles, actionText = strings.seeAll, onAction = onOpenProfiles)
            }
            val recentPersons = state.persons.filter { !it.isArchived }.take(4)
            if (recentPersons.isEmpty()) {
                item {
                    EmptyHintCard(strings.noDataYet, strings.addLoanTaker, onQuickAddPerson)
                }
            } else {
                items(recentPersons, key = { "rp_${it.id}" }) { person ->
                    val pSummary = state.personSummaries[person.id]
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPersonDetail(person.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PersonAvatar(name = person.name, photoRef = person.photoRef, size = 44.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(person.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = buildString {
                                    if (person.phone.isNotBlank()) append("${person.phone} • ")
                                    append("${pSummary?.activeCreditsCount ?: 0} ${strings.activeCredits}")
                                }
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = fmt(pSummary?.totalMisradhanPaise ?: 0L),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MisradhanPurple
                            )
                        }
                    }
                }
            }

            // Recent Transactions
            item {
                SectionHeader(title = strings.recentTransactions, actionText = strings.seeAll, onAction = onOpenTransactions)
            }
            val recentPayments = state.payments.filter { it.status == "COMPLETED" }.take(5)
            if (recentPayments.isEmpty()) {
                item {
                    EmptyHintCard(strings.noDataYet, strings.addCredit, onQuickAddCredit)
                }
            } else {
                items(recentPayments, key = { "pay_${it.id}" }) { payment ->
                    val personName = state.persons.firstOrNull { it.id == payment.personId }?.name ?: ""
                    val creditNumber = payment.creditId?.let { cid -> state.credits.firstOrNull { it.id == cid }?.creditNumber }
                    val dStr = LedgerDate.parseOrNull(payment.paymentDate)?.toDisplayString() ?: payment.paymentDate
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (payment.creditId != null) onOpenCreditDetail(payment.creditId)
                                else onOpenPersonDetail(payment.personId)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(personName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                val label = if (payment.paymentType == PaymentType.NORMAL) {
                                    "${strings.normalPaymentsLabel} • $dStr"
                                } else {
                                    "${creditNumber ?: ""} • $dStr"
                                }
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = "+${fmt(payment.amountPaise)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (payment.paymentType == PaymentType.NORMAL) InterestOrange else PaidGreen
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showCustomRangeDialog) {
        var start by remember { mutableStateOf(state.customStartDate) }
        var end by remember { mutableStateOf(state.customEndDate) }
        AlertDialog(
            onDismissRequest = { showCustomRangeDialog = false },
            title = { Text(strings.filterCustom) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LedgerDateField(label = strings.startDate, date = start, onDateSelected = { start = it })
                    LedgerDateField(label = strings.calculationDate, date = end, onDateSelected = { end = it })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val orderedStart = minOf(start, end)
                    val orderedEnd = maxOf(start, end)
                    onSetCustomRange(orderedStart, orderedEnd)
                    showCustomRangeDialog = false
                }) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRangeDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
private fun DashboardMiniCounter(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionHeader(title: String, actionText: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        TextButton(onClick = onAction) {
            Text(actionText)
        }
    }
}

@Composable
private fun EmptyHintCard(hint: String, ctaText: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClick) { Text(ctaText) }
        }
    }
}
