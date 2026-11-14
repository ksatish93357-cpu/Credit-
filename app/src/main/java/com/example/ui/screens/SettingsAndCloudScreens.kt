package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.export.ExportScope
import com.example.data.export.ImportMode
import com.example.data.export.ImportPreview
import com.example.domain.model.AllocationMode
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppSettings
import com.example.domain.model.AppThemeMode
import com.example.domain.model.FirebaseCloudConfig
import com.example.domain.model.InterestMethod
import com.example.domain.model.RatePeriod
import com.example.security.BiometricHelper
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.OverdueRed
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PrincipalBlue
import com.example.ui.viewmodel.MainUiState
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onOpenCloudDatabase: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenReminders: () -> Unit,
    onSetPin: (String) -> Unit,
    onDisablePin: (String, (Boolean) -> Unit) -> Unit,
    onSetAutoLockSeconds: (Long) -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onExportJson: (ExportScope) -> Unit,
    onTriggerImportJson: () -> Unit,
    pendingImportPreview: ImportPreview?,
    onDismissImportPreview: () -> Unit,
    onConfirmImport: (ImportPreview, ImportMode) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val settings = state.settings
    val sec = state.securityState
    val biometricSupported = remember(context) { BiometricHelper.isBiometricAvailable(context) }

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }
    var showConventionsDialog by remember { mutableStateOf(false) }
    var showAuditLogDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(strings.navSettings) }) }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // GENERAL
                item {
                    SettingsSectionCard(title = strings.settingsGeneral, icon = Icons.Default.Palette) {
                        Text("Language / भाषा", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.language == AppLanguage.HINDI,
                                onClick = { onUpdateSettings { it.copy(language = AppLanguage.HINDI) } },
                                label = { Text(strings.hindiLabel) },
                                modifier = Modifier.testTag("settings_lang_hindi")
                            )
                            FilterChip(
                                selected = settings.language == AppLanguage.ENGLISH,
                                onClick = { onUpdateSettings { it.copy(language = AppLanguage.ENGLISH) } },
                                label = { Text(strings.englishLabel) },
                                modifier = Modifier.testTag("settings_lang_english")
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Theme / थीम", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.theme == AppThemeMode.SYSTEM,
                                onClick = { onUpdateSettings { it.copy(theme = AppThemeMode.SYSTEM) } },
                                label = { Text(strings.themeSystem) }
                            )
                            FilterChip(
                                selected = settings.theme == AppThemeMode.LIGHT,
                                onClick = { onUpdateSettings { it.copy(theme = AppThemeMode.LIGHT) } },
                                label = { Text(strings.themeLight) }
                            )
                            FilterChip(
                                selected = settings.theme == AppThemeMode.DARK,
                                onClick = { onUpdateSettings { it.copy(theme = AppThemeMode.DARK) } },
                                label = { Text(strings.themeDark) }
                            )
                        }
                        DetailRow("Currency", "INR (₹ — Indian Formatting)")
                        DetailRow("Date Format", settings.dateFormat)
                    }
                }

                // FINANCIAL
                item {
                    SettingsSectionCard(title = strings.settingsFinancial, icon = Icons.Default.Percent) {
                        Text(strings.ratePeriod, style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.defaultRatePeriod == RatePeriod.MONTHLY,
                                onClick = { onUpdateSettings { it.copy(defaultRatePeriod = RatePeriod.MONTHLY) } },
                                label = { Text(strings.monthly) }
                            )
                            FilterChip(
                                selected = settings.defaultRatePeriod == RatePeriod.YEARLY,
                                onClick = { onUpdateSettings { it.copy(defaultRatePeriod = RatePeriod.YEARLY) } },
                                label = { Text(strings.yearly) }
                            )
                        }

                        Text(strings.allocationMode, style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.defaultAllocationMode == AllocationMode.PRINCIPAL_ADJUSTMENT,
                                onClick = { onUpdateSettings { it.copy(defaultAllocationMode = AllocationMode.PRINCIPAL_ADJUSTMENT) } },
                                label = { Text("Principal Adjustment") }
                            )
                            FilterChip(
                                selected = settings.defaultAllocationMode == AllocationMode.INTEREST_FIRST,
                                onClick = { onUpdateSettings { it.copy(defaultAllocationMode = AllocationMode.INTEREST_FIRST) } },
                                label = { Text("Interest First") }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Enable Compound Interest Option", style = MaterialTheme.typography.bodyMedium)
                                Text("Simple interest remains default", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.allowCompoundInterest,
                                onCheckedChange = { checked ->
                                    onUpdateSettings {
                                        it.copy(
                                            allowCompoundInterest = checked,
                                            defaultInterestMethod = if (!checked) InterestMethod.SIMPLE else it.defaultInterestMethod
                                        )
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Display Precision", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = settings.displayDecimals == 2,
                                    onClick = { onUpdateSettings { it.copy(displayDecimals = 2) } },
                                    label = { Text("₹1,000.50") }
                                )
                                FilterChip(
                                    selected = settings.displayDecimals == 0,
                                    onClick = { onUpdateSettings { it.copy(displayDecimals = 0) } },
                                    label = { Text("₹1,001") }
                                )
                            }
                        }

                        TextButton(onClick = { showConventionsDialog = true }) {
                            Text("View Calendar & Calculation Conventions")
                        }
                    }
                }

                // DATABASE & CLOUD
                item {
                    SettingsSectionCard(title = strings.settingsDatabase, icon = Icons.Default.Storage) {
                        val sizeKb = state.dbSizeBytes / 1024
                        DetailRow("Local SQLite (Room) Status", "Active Offline (${sizeKb} KB)")
                        DetailRow("Stored Records", "${state.persons.size} profiles • ${state.credits.size} credits • ${state.payments.size} payments")
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(strings.cloudDatabaseTitle) },
                            supportingContent = {
                                Text(
                                    if (state.firebaseConfig.isConnected) "Connected (${state.firebaseConfig.projectId})"
                                    else "Local-only mode (Tap to configure optional Firebase)"
                                )
                            },
                            leadingContent = {
                                Icon(
                                    if (state.firebaseConfig.isConnected) Icons.Default.CloudDone else Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = if (state.firebaseConfig.isConnected) PaidGreen else PrincipalBlue
                                )
                            },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onOpenCloudDatabase).testTag("settings_cloud_db_btn")
                        )
                    }
                }

                // SECURITY
                item {
                    SettingsSectionCard(title = strings.settingsSecurity, icon = Icons.Default.Security) {
                        DetailRow("4-Digit PIN Lock", if (sec.hasPin) "Enabled" else "Disabled")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { showSetPinDialog = true },
                                modifier = Modifier.weight(1f).testTag("settings_set_pin_btn")
                            ) {
                                Text(if (sec.hasPin) strings.changePin else strings.setPin)
                            }
                            if (sec.hasPin) {
                                OutlinedButton(
                                    onClick = { showDisablePinDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(strings.disablePin)
                                }
                            }
                        }
                        if (sec.hasPin) {
                            OutlinedButton(
                                onClick = onLockNow,
                                modifier = Modifier.fillMaxWidth().testTag("settings_lock_now_btn")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(strings.lockNow)
                            }
                            Text("Auto-lock Inactivity", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0L to "Immediate", 30L to "30s", 60L to "1m", 300L to "5m", -1L to "Off").forEach { (secs, lbl) ->
                                    FilterChip(
                                        selected = sec.autoLockSeconds == secs,
                                        onClick = { onSetAutoLockSeconds(secs) },
                                        label = { Text(lbl) }
                                    )
                                }
                            }
                            if (biometricSupported) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                                        Text(strings.unlockWithBiometric)
                                    }
                                    Switch(checked = sec.biometricEnabled, onCheckedChange = onSetBiometricEnabled)
                                }
                            }
                        }
                    }
                }

                // DATA & BACKUP
                item {
                    SettingsSectionCard(title = strings.settingsData, icon = Icons.Default.Backup) {
                        val lastBackupText = if (settings.lastBackupTimestamp > 0L) {
                            DateFormat.getDateTimeInstance().format(Date(settings.lastBackupTimestamp))
                        } else "Never"
                        DetailRow("Last JSON Backup", lastBackupText)
                        Button(
                            onClick = { onExportJson(ExportScope.ALL_DATA) },
                            modifier = Modifier.fillMaxWidth().testTag("settings_export_all_json")
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(strings.exportAllJson)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onExportJson(ExportScope.TRANSACTIONS_ONLY) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(strings.exportTransactionsJson, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onTriggerImportJson,
                                modifier = Modifier.weight(1f).testTag("settings_import_json")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(strings.importJson, maxLines = 1)
                            }
                        }
                        TextButton(onClick = { showAuditLogDialog = true }) {
                            Text("View Audit Log (${state.auditLogs.size})")
                        }
                    }
                }

                // NOTIFICATIONS & ANALYTICS SHORTCUTS
                item {
                    SettingsSectionCard(title = strings.settingsNotifications, icon = Icons.Default.Notifications) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Reminders Notifications")
                            Switch(
                                checked = settings.notificationsEnabled,
                                onCheckedChange = { c -> onUpdateSettings { it.copy(notificationsEnabled = c) } }
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Overdue Alerts")
                            Switch(
                                checked = settings.overdueAlertsEnabled,
                                onCheckedChange = { c -> onUpdateSettings { it.copy(overdueAlertsEnabled = c) } }
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Manual Backup Reminders")
                            Switch(
                                checked = settings.backupReminderEnabled,
                                onCheckedChange = { c -> onUpdateSettings { it.copy(backupReminderEnabled = c) } }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onOpenReports, modifier = Modifier.weight(1f)) { Text(strings.reportsAndAnalytics) }
                            OutlinedButton(onClick = onOpenReminders, modifier = Modifier.weight(1f)) { Text(strings.remindersTitle) }
                        }
                    }
                }

                // ABOUT
                item {
                    SettingsSectionCard(title = strings.settingsAbout, icon = Icons.Default.Info) {
                        DetailRow("App Name", "CREDIT LOGGER")
                        DetailRow("Version", "1.0.0")
                        Text(
                            "Offline-first personal credit & payment ledger. All data stays on your device unless you configure your own Firebase project.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showAboutDialog = true }) {
                            Text("Privacy & Terms")
                        }
                    }
                }

                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showSetPinDialog) {
        var pin1 by remember { mutableStateOf("") }
        var pin2 by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text(strings.setPin) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin1,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin1 = it },
                        label = { Text("4-Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("set_pin_input")
                    )
                    OutlinedTextField(
                        value = pin2,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin2 = it },
                        label = { Text("Confirm 4-Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("confirm_pin_input")
                    )
                    if (err != null) Text(err!!, color = OverdueRed, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    when {
                        pin1.length != 4 -> err = "PIN must be 4 digits"
                        pin1 != pin2 -> err = "PINs do not match"
                        else -> {
                            onSetPin(pin1)
                            showSetPinDialog = false
                        }
                    }
                }, modifier = Modifier.testTag("save_pin_confirm_btn")) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { showSetPinDialog = false }) { Text(strings.cancel) } }
        )
    }

    if (showDisablePinDialog) {
        var curPin by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showDisablePinDialog = false },
            title = { Text(strings.disablePin) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = curPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) curPin = it },
                        label = { Text("Enter Current PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (err != null) Text(err!!, color = OverdueRed, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onDisablePin(curPin) { ok ->
                        if (ok) showDisablePinDialog = false else err = "Incorrect PIN"
                    }
                }) { Text(strings.confirm) }
            },
            dismissButton = { TextButton(onClick = { showDisablePinDialog = false }) { Text(strings.cancel) } }
        )
    }

    if (pendingImportPreview != null) {
        val p = pendingImportPreview
        AlertDialog(
            onDismissRequest = onDismissImportPreview,
            title = { Text("JSON Import Preview") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Schema Version: v${p.bundle.schemaVersion}")
                    Text("Profiles: ${p.bundle.persons.size} (Duplicates: ${p.duplicatePersonIds})")
                    Text("Credits: ${p.bundle.credits.size} (Duplicates: ${p.duplicateCreditIds})")
                    Text("Payments: ${p.bundle.payments.size} (Duplicates: ${p.duplicatePaymentIds})")
                    Text("Events: ${p.bundle.events.size} (Duplicates: ${p.duplicateEventIds})")
                    if (p.sampleNames.isNotEmpty()) {
                        Text("Sample Profiles: ${p.sampleNames.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (p.missingReferenceWarnings.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Warnings:", color = OverdueRed, fontWeight = FontWeight.Bold)
                        p.missingReferenceWarnings.take(5).forEach { w ->
                            Text("• $w", style = MaterialTheme.typography.labelSmall, color = OverdueRed)
                        }
                    }
                }
            },
            confirmButton = {
                val isTransactionsOnly = p.bundle.exportScope == ExportScope.TRANSACTIONS_ONLY.name
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !isTransactionsOnly,
                        onClick = { onConfirmImport(p, ImportMode.REPLACE_ALL) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OverdueRed)
                    ) { Text(strings.replaceAll) }
                    Button(onClick = { onConfirmImport(p, ImportMode.SAFE_MERGE) }) {
                        Text(strings.safeMerge)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissImportPreview) { Text(strings.cancel) }
            }
        )
    }

    if (showConventionsDialog) {
        AlertDialog(
            onDismissRequest = { showConventionsDialog = false },
            title = { Text("Calculation Conventions") },
            text = {
                Text(
                    "1. Exact integer paise & BigDecimal arithmetic (no floating-point drift).\n\n" +
                        "2. Calendar Anniversary Rule: Completed months run anniversary-to-anniversary. If a month lacks the start day (e.g. Jan 31 -> Feb 28/29), that month's final day is used.\n\n" +
                        "3. Partial Periods: Monthly rate prorates each remaining day by 1 / actual days in that calendar month (28/29/30/31). Yearly rate prorates by 1 / actual days in that calendar year (365/366).\n\n" +
                        "4. Ordered Event Replay: Every payment or adjustment only affects periods from its effective date onward."
                )
            },
            confirmButton = { Button(onClick = { showConventionsDialog = false }) { Text("OK") } }
        )
    }

    if (showAuditLogDialog) {
        AlertDialog(
            onDismissRequest = { showAuditLogDialog = false },
            title = { Text("Audit Log (${state.auditLogs.size})") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.auditLogs.take(40).forEach { log ->
                        Text(
                            "${log.entityType} • ${log.action}\n${log.newData.ifBlank { log.oldData }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { Button(onClick = { showAuditLogDialog = false }) { Text("Close") } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Privacy & Terms") },
            text = {
                Text(
                    "CREDIT LOGGER operates 100% offline using your device's local SQLite database. " +
                        "PIN secrets are hashed with PBKDF2-SHA256 and never exported in JSON backups. " +
                        "Optional Firebase sync connects only to the Firebase project credentials you supply."
                )
            },
            confirmButton = { Button(onClick = { showAboutDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = title, tint = GoldAccent, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseCloudSettingsScreen(
    config: FirebaseCloudConfig,
    isBusy: Boolean,
    onBack: () -> Unit,
    onConnect: (FirebaseCloudConfig, email: String, password: String, createIfMissing: Boolean) -> Unit,
    onTestConnection: (FirebaseCloudConfig) -> Unit,
    onSyncNow: (FirebaseCloudConfig) -> Unit,
    onDisconnect: () -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current

    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var authDomain by remember(config) { mutableStateOf(config.authDomain) }
    var projectId by remember(config) { mutableStateOf(config.projectId) }
    var storageBucket by remember(config) { mutableStateOf(config.storageBucket) }
    var senderId by remember(config) { mutableStateOf(config.messagingSenderId) }
    var appId by remember(config) { mutableStateOf(config.appId) }
    var email by remember(config) { mutableStateOf(config.userEmail) }
    var password by remember { mutableStateOf("") }
    var createIfMissing by remember { mutableStateOf(false) }

    val buildConfig = {
        config.copy(
            apiKey = apiKey.trim(),
            authDomain = authDomain.trim(),
            projectId = projectId.trim(),
            storageBucket = storageBucket.trim(),
            messagingSenderId = senderId.trim(),
            appId = appId.trim(),
            userEmail = email.trim()
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.cloudDatabaseTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloud_status_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (config.isConnected) PaidGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (config.isConnected) "Status: Connected (${config.userEmail})" else "Status: Local-Only Mode",
                            fontWeight = FontWeight.Bold,
                            color = if (config.isConnected) PaidGreen else MaterialTheme.colorScheme.onSurface
                        )
                        if (config.lastSyncTime > 0L) {
                            Text("Last Sync: ${DateFormat.getDateTimeInstance().format(Date(config.lastSyncTime))}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (config.lastSyncStatus.isNotBlank()) {
                            Text("Sync Status: ${config.lastSyncStatus}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (config.lastError.isNotBlank()) {
                            Text("Error: ${config.lastError}", style = MaterialTheme.typography.bodySmall, color = OverdueRed)
                        }
                    }
                }

                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("Firebase API Key *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = authDomain, onValueChange = { authDomain = it }, label = { Text("Auth Domain") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = projectId, onValueChange = { projectId = it }, label = { Text("Project ID *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = storageBucket, onValueChange = { storageBucket = it }, label = { Text("Storage Bucket") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = senderId, onValueChange = { senderId = it }, label = { Text("Messaging Sender ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("App ID *") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Firebase Authentication (Required for Per-User Security)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Create account if new", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = createIfMissing, onCheckedChange = { createIfMissing = it })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !isBusy && apiKey.isNotBlank() && projectId.isNotBlank() && appId.isNotBlank() && email.isNotBlank() && password.length >= 6,
                        onClick = { onConnect(buildConfig(), email, password, createIfMissing) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cloud_connect_btn")
                    ) { Text(strings.connect) }

                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = { onTestConnection(buildConfig()) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cloud_test_btn")
                    ) { Text(strings.testConnection) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !isBusy && config.isConnected,
                        onClick = { onSyncNow(buildConfig()) },
                        colors = ButtonDefaults.buttonColors(containerColor = PaidGreen),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cloud_sync_btn")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(strings.syncNow)
                    }
                    OutlinedButton(
                        enabled = !isBusy && config.isConnected,
                        onClick = onDisconnect,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OverdueRed),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cloud_disconnect_btn")
                    ) { Text(strings.disconnect) }
                }

                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Security Guidance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "• A Firebase API key is not a database password.\n" +
                                "• Enable Email/Password Authentication in your Firebase Console.\n" +
                                "• Configure Firestore Security Rules so only the signed-in owner can read/write:\n" +
                                "  match /users/{userId}/{document=**} {\n" +
                                "    allow read, write: if request.auth != null && request.auth.uid == userId;\n" +
                                "  }\n" +
                                "• Disconnecting keeps all your local Room SQLite data intact.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
