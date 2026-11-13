package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.core.net.toUri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.IndianNumberFormatter
import com.example.domain.engine.LedgerDate
import com.example.domain.engine.StatementTextGenerator
import com.example.domain.model.CreditStatus
import com.example.domain.model.PaymentType
import com.example.domain.model.Person
import com.example.domain.model.ProfileFilterOption
import com.example.domain.model.ProfileSortOption
import com.example.ui.components.PersonAvatar
import com.example.ui.components.SemanticKpiCard
import com.example.ui.theme.InterestOrange
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MisradhanPurple
import com.example.ui.theme.OverdueRed
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PrincipalBlue
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesListScreen(
    state: MainUiState,
    onOpenPersonDetail: (String) -> Unit,
    onAddPerson: () -> Unit
) {
    val strings = LocalAppStrings.current
    val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, state.settings.displayDecimals, trimTrailingZerosWhenWhole = true) }

    var searchQuery by remember { mutableStateOf("") }
    var filterOption by remember { mutableStateOf(ProfileFilterOption.ALL) }
    var sortOption by remember { mutableStateOf(ProfileSortOption.HIGHEST_OUTSTANDING) }
    var showSortMenu by remember { mutableStateOf(false) }

    val filtered = remember(state.persons, state.personSummaries, state.credits, searchQuery, filterOption, sortOption) {
        val q = searchQuery.trim().lowercase()
        state.persons
            .filter { person ->
                val summary = state.personSummaries[person.id]
                val matchesFilter = when (filterOption) {
                    ProfileFilterOption.ALL -> !person.isArchived
                    ProfileFilterOption.ACTIVE -> !person.isArchived && (summary?.activeCreditsCount ?: 0) > 0
                    ProfileFilterOption.SETTLED -> !person.isArchived && (summary?.activeCreditsCount ?: 0) == 0
                    ProfileFilterOption.ARCHIVED -> person.isArchived
                }
                val personCredits = state.credits.filter { it.personId == person.id }
                val matchesSearch = q.isEmpty() ||
                    person.name.lowercase().contains(q) ||
                    person.phone.lowercase().contains(q) ||
                    personCredits.any { it.creditNumber.lowercase().contains(q) }
                matchesFilter && matchesSearch
            }
            .sortedWith { a, b ->
                val sa = state.personSummaries[a.id]
                val sb = state.personSummaries[b.id]
                when (sortOption) {
                    ProfileSortOption.HIGHEST_OUTSTANDING ->
                        (sb?.totalMisradhanPaise ?: 0L).compareTo(sa?.totalMisradhanPaise ?: 0L)
                    ProfileSortOption.LOWEST_OUTSTANDING ->
                        (sa?.totalMisradhanPaise ?: 0L).compareTo(sb?.totalMisradhanPaise ?: 0L)
                    ProfileSortOption.NAME_ASC -> a.name.compareTo(b.name, ignoreCase = true)
                    ProfileSortOption.RECENT -> b.updatedAt.compareTo(a.updatedAt)
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${strings.profilesTitle} (${filtered.size})") },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("profiles_sort_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("Highest Outstanding") }, onClick = { sortOption = ProfileSortOption.HIGHEST_OUTSTANDING; showSortMenu = false })
                            DropdownMenuItem(text = { Text("Lowest Outstanding") }, onClick = { sortOption = ProfileSortOption.LOWEST_OUTSTANDING; showSortMenu = false })
                            DropdownMenuItem(text = { Text("Name (A-Z)") }, onClick = { sortOption = ProfileSortOption.NAME_ASC; showSortMenu = false })
                            DropdownMenuItem(text = { Text("Recent") }, onClick = { sortOption = ProfileSortOption.RECENT; showSortMenu = false })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddPerson,
                modifier = Modifier.testTag("profiles_add_fab")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = strings.addLoanTaker)
            }
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
                    .widthIn(max = 900.dp)
                    .fillMaxSize()
            ) {
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
                        .testTag("profiles_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileFilterOption.entries.forEach { opt ->
                        val label = when (opt) {
                            ProfileFilterOption.ALL -> strings.filterAllTime
                            ProfileFilterOption.ACTIVE -> strings.statusActive
                            ProfileFilterOption.SETTLED -> strings.statusClosed
                            ProfileFilterOption.ARCHIVED -> strings.statusArchived
                        }
                        FilterChip(
                            selected = filterOption == opt,
                            onClick = { filterOption = opt },
                            label = { Text(label) }
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(strings.noDataYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onAddPerson) { Text(strings.addLoanTaker) }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { person ->
                            val summary = state.personSummaries[person.id]
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenPersonDetail(person.id) }
                                    .testTag("profile_card_${person.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PersonAvatar(name = person.name, photoRef = person.photoRef, size = 50.dp)
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = person.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (person.phone.isNotBlank()) {
                                            Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(
                                            text = "${strings.activeCredits}: ${summary?.activeCreditsCount ?: 0} • ${strings.closedCredits}: ${summary?.closedCreditsCount ?: 0}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = fmt(summary?.totalMisradhanPaise ?: 0L),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if ((summary?.totalMisradhanPaise ?: 0L) > 0L) MisradhanPurple else PaidGreen
                                        )
                                        Text(
                                            text = strings.misradhan,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    personId: String,
    state: MainUiState,
    onBack: () -> Unit,
    onEditPerson: (Person) -> Unit,
    onArchiveRestorePerson: (String, Boolean) -> Unit,
    onDeletePerson: (String) -> Unit,
    onAddCredit: (String) -> Unit,
    onAddCreditPayment: (String) -> Unit,
    onAddNormalPayment: (String) -> Unit,
    onOpenCreditDetail: (String) -> Unit,
    onShareProfileStatement: (String) -> Unit,
    onExportProfileJson: (String) -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val summary = state.personSummaries[personId]
    val person = summary?.person ?: state.persons.firstOrNull { it.id == personId }

    if (person == null || summary == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.noDataYet)
        }
        return
    }

    val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, state.settings.displayDecimals, trimTrailingZerosWhenWhole = true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val personCredits = state.credits.filter { it.personId == personId }
    val creditPayments = state.payments.filter { it.personId == personId && it.paymentType == PaymentType.CREDIT }
    val normalPayments = state.payments.filter { it.personId == personId && it.paymentType == PaymentType.NORMAL }
    val personEvents = state.events.filter { it.personId == personId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onShareProfileStatement(personId) }, modifier = Modifier.testTag("profile_share_btn")) {
                        Icon(Icons.Default.Share, contentDescription = strings.shareProfileStatement)
                    }
                    IconButton(onClick = { onExportProfileJson(personId) }, modifier = Modifier.testTag("profile_export_json_btn")) {
                        Icon(Icons.Default.Code, contentDescription = strings.exportProfileJson)
                    }
                    IconButton(onClick = { onEditPerson(person) }, modifier = Modifier.testTag("profile_edit_btn")) {
                        Icon(Icons.Default.Edit, contentDescription = strings.editProfile)
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
                    .widthIn(max = 900.dp)
                    .fillMaxSize()
            ) {
                // Header card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PersonAvatar(name = person.name, photoRef = person.photoRef, size = 56.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(person.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            if (person.phone.isNotBlank()) {
                                Text(person.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (person.address.isNotBlank()) {
                                Text(person.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (person.phone.isNotBlank()) {
                            IconButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${person.phone}".toUri()))
                                }
                            }) {
                                Icon(Icons.Default.Call, contentDescription = "Call", tint = PaidGreen)
                            }
                            IconButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, "sms:${person.phone}".toUri()))
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "SMS", tint = PrincipalBlue)
                            }
                        }
                    }
                }

                val tabs = listOf(
                    strings.overviewTab,
                    "${strings.creditsTab} (${personCredits.size})",
                    "${strings.creditPaymentsTab} (${creditPayments.size})",
                    "${strings.normalPaymentsTab} (${normalPayments.size})",
                    strings.historyTab,
                    strings.notesTab
                )
                PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(label, maxLines = 1) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SemanticKpiCard(
                                            title = strings.totalPrincipal,
                                            amountText = fmt(summary.totalPrincipalPaise),
                                            icon = Icons.Default.AccountBalanceWallet,
                                            accentColor = PrincipalBlue,
                                            modifier = Modifier.weight(1f)
                                        )
                                        SemanticKpiCard(
                                            title = strings.totalInterest,
                                            amountText = fmt(summary.totalAccruedUnpaidInterestPaise),
                                            icon = Icons.Default.Percent,
                                            accentColor = InterestOrange,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SemanticKpiCard(
                                            title = strings.totalMisradhan,
                                            amountText = fmt(summary.totalMisradhanPaise),
                                            icon = Icons.Default.Savings,
                                            accentColor = MisradhanPurple,
                                            modifier = Modifier.weight(1f)
                                        )
                                        SemanticKpiCard(
                                            title = strings.totalPaid,
                                            amountText = fmt(summary.totalPaidAmountPaise),
                                            icon = Icons.Default.CheckCircle,
                                            accentColor = PaidGreen,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            item {
                                Card(shape = RoundedCornerShape(14.dp)) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(strings.activeCredits)
                                            Text("${summary.activeCreditsCount}", fontWeight = FontWeight.Bold)
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(strings.closedCredits)
                                            Text("${summary.closedCreditsCount}", fontWeight = FontWeight.Bold)
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(strings.creditPaymentsLabel)
                                            Text(fmt(summary.totalCreditPaymentsPaise), color = PaidGreen, fontWeight = FontWeight.SemiBold)
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(strings.normalPaymentsLabel)
                                            Text(fmt(summary.totalNormalPaymentsPaise), color = InterestOrange, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { onArchiveRestorePerson(personId, !person.isArchived) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(if (person.isArchived) Icons.Default.Restore else Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(if (person.isArchived) strings.restoreProfile else strings.archiveProfile)
                                    }
                                    OutlinedButton(
                                        onClick = { showDeleteConfirm = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OverdueRed),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(strings.deleteProfile)
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (summary.creditSnapshots.isEmpty()) {
                                item { Text(strings.noDataYet, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            } else {
                                items(summary.creditSnapshots, key = { it.creditId }) { snap ->
                                    val credit = personCredits.firstOrNull { it.id == snap.creditId }
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenCreditDetail(snap.creditId) }
                                            .testTag("credit_card_${snap.creditNumber}")
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(
                                                    text = "${snap.creditNumber} (${snap.status.name})",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = fmt(snap.misradhanPaise),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MisradhanPurple
                                                )
                                            }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${strings.currentPrincipal}: ${fmt(snap.currentPrincipalPaise)}", style = MaterialTheme.typography.bodySmall)
                                                Text("${strings.accruedInterest}: ${fmt(snap.accruedUnpaidInterestPaise)}", style = MaterialTheme.typography.bodySmall, color = InterestOrange)
                                            }
                                            val startDisp = credit?.let { LedgerDate.parseOrNull(it.startDate)?.toDisplayString() } ?: ""
                                            Text(
                                                "${snap.activeRate}% ${StatementTextGenerator.formatRatePeriod(snap.activeRatePeriod, state.settings.language)} • $startDisp",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            if (creditPayments.isEmpty()) {
                                item { Text(strings.noDataYet, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            } else {
                                items(creditPayments, key = { it.id }) { pay ->
                                    val cNum = personCredits.firstOrNull { it.id == pay.creditId }?.creditNumber ?: ""
                                    val dDisp = LedgerDate.parseOrNull(pay.paymentDate)?.toDisplayString() ?: pay.paymentDate
                                    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("$cNum • $dDisp", style = MaterialTheme.typography.titleSmall)
                                                Text("+${fmt(pay.amountPaise)}", color = PaidGreen, fontWeight = FontWeight.Bold)
                                            }
                                            Text("${pay.receiptNumber} • ${pay.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (pay.systemNote.isNotBlank()) {
                                                Text(pay.systemNote, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            if (normalPayments.isEmpty()) {
                                item { Text(strings.noDataYet, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            } else {
                                items(normalPayments, key = { it.id }) { pay ->
                                    val dDisp = LedgerDate.parseOrNull(pay.paymentDate)?.toDisplayString() ?: pay.paymentDate
                                    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${strings.normalPaymentsLabel} • $dDisp", style = MaterialTheme.typography.titleSmall)
                                                Text("+${fmt(pay.amountPaise)}", color = InterestOrange, fontWeight = FontWeight.Bold)
                                            }
                                            Text(strings.normalPaymentBanner, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (pay.note.isNotBlank()) Text(pay.note, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                        4 -> {
                            if (personEvents.isEmpty()) {
                                item { Text(strings.noDataYet, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            } else {
                                items(personEvents, key = { it.id }) { ev ->
                                    val dDisp = LedgerDate.parseOrNull(ev.effectiveDate)?.toDisplayString() ?: ev.effectiveDate
                                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${ev.eventType.name} • $dDisp", style = MaterialTheme.typography.labelLarge)
                                                Text(fmt(ev.amountPaise), fontWeight = FontWeight.Bold)
                                            }
                                            if (ev.systemNote.isNotBlank()) Text(ev.systemNote, style = MaterialTheme.typography.bodySmall)
                                            if (ev.note.isNotBlank()) Text(ev.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        5 -> {
                            item {
                                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(strings.notes, style = MaterialTheme.typography.titleSmall)
                                        Text(person.note.ifBlank { strings.noDataYet }, style = MaterialTheme.typography.bodyMedium)
                                        HorizontalDivider()
                                        OutlinedButton(onClick = { onEditPerson(person) }) {
                                            Text(strings.editProfile)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Bar
                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onAddCredit(personId) },
                            modifier = Modifier.weight(1f).testTag("profile_add_credit_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(strings.addCredit, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = { onAddCreditPayment(personId) },
                            colors = ButtonDefaults.buttonColors(containerColor = PaidGreen),
                            modifier = Modifier.weight(1f).testTag("profile_add_credit_payment_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(strings.addCreditPayment, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { onAddNormalPayment(personId) },
                            modifier = Modifier.weight(1f).testTag("profile_add_normal_payment_btn"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(strings.normalPaymentsLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(strings.deleteProfileConfirmTitle) },
            text = { Text(strings.deleteProfileConfirmBody) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePerson(personId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OverdueRed)
                ) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
fun AddEditPersonDialog(
    initialPerson: Person? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String, photoUri: String, note: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var name by remember { mutableStateOf(initialPerson?.name ?: "") }
    var phone by remember { mutableStateOf(initialPerson?.phone ?: "") }
    var address by remember { mutableStateOf(initialPerson?.address ?: "") }
    var photoUri by remember { mutableStateOf(initialPerson?.photoRef ?: "") }
    var note by remember { mutableStateOf(initialPerson?.note ?: "") }
    var nameError by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) photoUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialPerson == null) strings.addLoanTaker else strings.editProfile) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PersonAvatar(name = name.ifBlank { "?" }, photoRef = photoUri, size = 52.dp)
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.selectPhoto)
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text(strings.fullName) },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("person_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(strings.phoneNumber) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("person_phone_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(strings.address) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("person_address_input")
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(strings.optionalNote) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("person_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        onSave(name, phone, address, photoUri, note)
                    }
                },
                modifier = Modifier.testTag("save_person_btn")
            ) {
                Text(strings.saveProfile)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
