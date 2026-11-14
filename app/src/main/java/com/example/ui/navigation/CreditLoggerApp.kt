package com.example.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.export.ExportScope
import com.example.data.export.ImportPreview
import com.example.data.export.PdfAndShareManager
import com.example.domain.engine.LedgerDate
import com.example.domain.model.Person
import com.example.ui.components.QuickAddBottomSheet
import com.example.ui.components.ShareStatementSheet
import com.example.ui.screens.AddCreditPaymentScreen
import com.example.ui.screens.AddCreditScreen
import com.example.ui.screens.AddEditPersonDialog
import com.example.ui.screens.AddNormalPaymentScreen
import com.example.ui.screens.CalculatorScreen
import com.example.ui.screens.CreditDetailScreen
import com.example.ui.screens.FirebaseCloudSettingsScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.MainCreditUpdateScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PinLockScreen
import com.example.ui.screens.ProfileDetailScreen
import com.example.ui.screens.ProfilesListScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.NavyDeep
import com.example.ui.viewmodel.CreditLoggerViewModel
import kotlinx.coroutines.launch
import java.io.File

object Routes {
    const val HOME = "home"
    const val PROFILES = "profiles"
    const val TRANSACTIONS = "transactions"
    const val CALCULATOR = "calculator"
    const val SETTINGS = "settings"

    const val PROFILE_DETAIL = "profile_detail/{personId}"
    const val ADD_CREDIT = "add_credit?personId={personId}"
    const val CREDIT_DETAIL = "credit_detail/{creditId}"
    const val ADD_CREDIT_PAYMENT = "add_credit_payment?personId={personId}&creditId={creditId}"
    const val ADD_NORMAL_PAYMENT = "add_normal_payment?personId={personId}"
    const val MAIN_CREDIT_UPDATE = "main_credit_update/{creditId}"
    const val REPORTS = "reports"
    const val REMINDERS = "reminders"
    const val CLOUD_SETTINGS = "cloud_settings"
}

private data class TopNavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private data class PendingShareData(
    val title: String,
    val fileName: String,
    val text: String
)

@Composable
fun CreditLoggerApp(viewModel: CreditLoggerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(state.errorMessage ?: "") },
            confirmButton = {
                Button(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }

    // 1. Onboarding Gate
    if (!state.isLoading && !state.settings.onboardingCompleted) {
        OnboardingScreen(
            selectedLanguage = state.settings.language,
            onSelectLanguage = { lang -> viewModel.updateSettings { it.copy(language = lang) } },
            onGetStarted = { viewModel.completeOnboarding(state.settings.language) }
        )
        return
    }

    // 2. PIN Lock Gate
    if (state.isAppLocked) {
        PinLockScreen(
            biometricEnabled = state.securityState.biometricEnabled,
            onVerifyPin = { pin, cb -> viewModel.unlockWithPin(pin, cb) },
            onBiometricSuccess = { viewModel.unlockWithBiometric() }
        )
        return
    }

    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    var showQuickAddSheet by remember { mutableStateOf(false) }
    var showAddEditPersonDialog by remember { mutableStateOf<Person?>(null) }
    var isCreatingPerson by remember { mutableStateOf(false) }
    var pendingShare by remember { mutableStateOf<PendingShareData?>(null) }
    var pendingPdfDownloadFile by remember { mutableStateOf<File?>(null) }
    var pendingExportRequest by remember { mutableStateOf<Triple<ExportScope, String?, String?>?>(null) }
    var pendingImportPreview by remember { mutableStateOf<ImportPreview?>(null) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val file = pendingPdfDownloadFile
        if (uri != null && file != null) {
            viewModel.savePdfToUri(file, uri)
        }
        pendingPdfDownloadFile = null
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val req = pendingExportRequest
        if (uri != null && req != null) {
            viewModel.exportJsonToUri(req.first, uri, req.second, req.third)
        }
        pendingExportRequest = null
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { viewModel.parseImportFromUri(uri) }
                    .onSuccess { pendingImportPreview = it }
                    .onFailure { viewModel.showToast(it.message ?: "Invalid JSON file") }
            }
        }
    }

    val topDestinations = listOf(
        TopNavDestination(Routes.HOME, strings.navHome, Icons.Filled.Home, Icons.Outlined.Home),
        TopNavDestination(Routes.PROFILES, strings.navProfiles, Icons.Filled.People, Icons.Outlined.People),
        TopNavDestination(Routes.TRANSACTIONS, strings.navTransactions, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
        TopNavDestination(Routes.CALCULATOR, strings.navCalculator, Icons.Filled.Calculate, Icons.Outlined.Calculate),
        TopNavDestination(Routes.SETTINGS, strings.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )
    val isTopLevel = currentRoute in topDestinations.map { it.route }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail && isTopLevel) {
                NavigationRail(
                    header = {
                        FloatingActionButton(
                            onClick = { showQuickAddSheet = true },
                            containerColor = GoldAccent,
                            contentColor = NavyDeep,
                            modifier = Modifier.padding(top = 12.dp).testTag("rail_quick_add_fab")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = strings.quickAddTitle)
                        }
                    }
                ) {
                    topDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) dest.selectedIcon else dest.unselectedIcon, contentDescription = dest.label) },
                            label = { Text(dest.label, maxLines = 1) }
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    if (!useRail && currentRoute == Routes.HOME) {
                        FloatingActionButton(
                            onClick = { showQuickAddSheet = true },
                            containerColor = GoldAccent,
                            contentColor = NavyDeep,
                            modifier = Modifier.testTag("home_quick_add_fab")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = strings.quickAddTitle)
                        }
                    }
                },
                bottomBar = {
                    if (!useRail && isTopLevel) {
                        NavigationBar {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(Routes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(if (selected) dest.selectedIcon else dest.unselectedIcon, contentDescription = dest.label) },
                                    label = { Text(dest.label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {
                    composable(Routes.HOME) {
                        HomeDashboardScreen(
                            state = state,
                            onToggleTheme = { viewModel.toggleThemeQuick() },
                            onSelectFilter = { viewModel.setDashboardFilter(it) },
                            onSetCustomRange = { s, e -> viewModel.setCustomDateRange(s, e) },
                            onOpenReports = { navController.navigate(Routes.REPORTS) },
                            onOpenReminders = { navController.navigate(Routes.REMINDERS) },
                            onOpenProfiles = {
                                navController.navigate(Routes.PROFILES) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenPersonDetail = { pid -> navController.navigate("profile_detail/${Uri.encode(pid)}") },
                            onOpenCreditDetail = { cid -> navController.navigate("credit_detail/${Uri.encode(cid)}") },
                            onOpenTransactions = {
                                navController.navigate(Routes.TRANSACTIONS) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onQuickAddPerson = { isCreatingPerson = true },
                            onQuickAddCredit = { navController.navigate("add_credit") },
                            onQuickAddCreditPayment = { navController.navigate("add_credit_payment") },
                            onQuickAddNormalPayment = { navController.navigate("add_normal_payment") }
                        )
                    }

                    composable(Routes.PROFILES) {
                        ProfilesListScreen(
                            state = state,
                            onOpenPersonDetail = { pid -> navController.navigate("profile_detail/${Uri.encode(pid)}") },
                            onAddPerson = { isCreatingPerson = true }
                        )
                    }

                    composable(
                        route = Routes.PROFILE_DETAIL,
                        arguments = listOf(navArgument("personId") { type = NavType.StringType })
                    ) { backStack ->
                        val personId = Uri.decode(backStack.arguments?.getString("personId") ?: "")
                        ProfileDetailScreen(
                            personId = personId,
                            state = state,
                            onBack = { navController.popBackStack() },
                            onEditPerson = { showAddEditPersonDialog = it },
                            onArchiveRestorePerson = { pid, arch -> viewModel.setPersonArchived(pid, arch) },
                            onDeletePerson = { pid ->
                                viewModel.deletePersonPermanently(pid) { navController.popBackStack() }
                            },
                            onAddCredit = { pid -> navController.navigate("add_credit?personId=${Uri.encode(pid)}") },
                            onAddCreditPayment = { pid -> navController.navigate("add_credit_payment?personId=${Uri.encode(pid)}") },
                            onAddNormalPayment = { pid -> navController.navigate("add_normal_payment?personId=${Uri.encode(pid)}") },
                            onOpenCreditDetail = { cid -> navController.navigate("credit_detail/${Uri.encode(cid)}") },
                            onDeleteCredit = { cid -> viewModel.deleteCreditPermanently(cid) },
                            onEditPaymentFull = { payId, amountPaise, date, method, mode, note ->
                                viewModel.updatePaymentFull(payId, amountPaise, date, method, mode, note)
                            },
                            onEditAdjustmentEvent = { evId, newPrincipalPaise, newRate, newPeriod, effDate, note ->
                                viewModel.updateAdjustmentEvent(evId, newPrincipalPaise, newRate, newPeriod, effDate, note)
                            },
                            onDeleteEventOrPayment = { evId -> viewModel.deleteEventOrPaymentPermanently(evId) },
                            onShareProfileStatement = { pid ->
                                val person = state.persons.firstOrNull { it.id == pid }
                                val text = viewModel.buildProfileStatementText(pid, state.asOfDate)
                                pendingShare = PendingShareData(
                                    title = "${person?.name ?: "Profile"} Statement",
                                    fileName = "Profile_${person?.name ?: pid}.pdf",
                                    text = text
                                )
                            },
                            onExportProfileJson = { pid ->
                                pendingExportRequest = Triple(ExportScope.SINGLE_PERSON, pid, null)
                                exportJsonLauncher.launch("credit_logger_profile_${pid.take(6)}.json")
                            }
                        )
                    }

                    composable(
                        route = Routes.ADD_CREDIT,
                        arguments = listOf(navArgument("personId") { type = NavType.StringType; nullable = true })
                    ) { backStack ->
                        val personId = backStack.arguments?.getString("personId")?.let(Uri::decode)
                        AddCreditScreen(
                            preselectedPersonId = personId,
                            state = state,
                            generateCreditNumber = { viewModel.generateNextCreditNumber() },
                            checkUniqueCreditNumber = { num -> viewModel.isCreditNumberUnique(num) },
                            onBack = { navController.popBackStack() },
                            onSaveCredit = { pid, num, principal, rate, period, method, start, due, note ->
                                viewModel.createCredit(pid, num, principal, rate, period, method, start, due, note) {
                                    navController.popBackStack()
                                }
                            }
                        )
                    }

                    composable(
                        route = Routes.CREDIT_DETAIL,
                        arguments = listOf(navArgument("creditId") { type = NavType.StringType })
                    ) { backStack ->
                        val creditId = Uri.decode(backStack.arguments?.getString("creditId") ?: "")
                        CreditDetailScreen(
                            creditId = creditId,
                            state = state,
                            checkUniqueCreditNumber = { num, exclId -> viewModel.isCreditNumberUnique(num, exclId) },
                            onBack = { navController.popBackStack() },
                            onAddPayment = { cid ->
                                val c = state.credits.firstOrNull { it.id == cid }
                                navController.navigate("add_credit_payment?personId=${Uri.encode(c?.personId ?: "")}&creditId=${Uri.encode(cid)}")
                            },
                            onMainUpdate = { cid -> navController.navigate("main_credit_update/${Uri.encode(cid)}") },
                            onUpdateFullDetails = { cid, num, principalPaise, rate, period, method, start, due, note ->
                                viewModel.updateCreditFullDetails(cid, num, principalPaise, rate, period, method, start, due, note)
                            },
                            onDeleteCredit = { cid ->
                                viewModel.deleteCreditPermanently(cid) { navController.popBackStack() }
                            },
                            onCloseOrReopen = { cid, close, date -> viewModel.closeOrReopenCredit(cid, close, date) },
                            onArchiveRestore = { cid, arch -> viewModel.setCreditArchived(cid, arch) },
                            onEditPaymentFull = { payId, amountPaise, date, method, mode, note ->
                                viewModel.updatePaymentFull(payId, amountPaise, date, method, mode, note)
                            },
                            onEditAdjustmentEvent = { evId, newPrincipalPaise, newRate, newPeriod, effDate, note ->
                                viewModel.updateAdjustmentEvent(evId, newPrincipalPaise, newRate, newPeriod, effDate, note)
                            },
                            onDeleteEventOrPayment = { evId -> viewModel.deleteEventOrPaymentPermanently(evId) },
                            onReverseEvent = { evId, reason -> viewModel.reverseEvent(evId, reason) },
                            onShareStatement = { cid, asOf ->
                                val c = state.credits.firstOrNull { it.id == cid }
                                val text = viewModel.buildCreditStatementText(cid, asOf)
                                pendingShare = PendingShareData(
                                    title = "Credit ${c?.creditNumber ?: ""} Statement",
                                    fileName = "Statement_${c?.creditNumber ?: cid}.pdf",
                                    text = text
                                )
                            },
                            onExportJson = { cid ->
                                pendingExportRequest = Triple(ExportScope.SINGLE_CREDIT, null, cid)
                                exportJsonLauncher.launch("credit_logger_credit_${cid.take(6)}.json")
                            }
                        )
                    }

                    composable(
                        route = Routes.MAIN_CREDIT_UPDATE,
                        arguments = listOf(navArgument("creditId") { type = NavType.StringType })
                    ) { backStack ->
                        val creditId = Uri.decode(backStack.arguments?.getString("creditId") ?: "")
                        MainCreditUpdateScreen(
                            creditId = creditId,
                            state = state,
                            onBack = { navController.popBackStack() },
                            onConfirmUpdate = { newP, newR, newPer, effDate, note ->
                                viewModel.applyMainCreditUpdate(creditId, newP, newR, newPer, effDate, note) {
                                    navController.popBackStack()
                                }
                            }
                        )
                    }

                    composable(
                        route = Routes.ADD_CREDIT_PAYMENT,
                        arguments = listOf(
                            navArgument("personId") { type = NavType.StringType; nullable = true },
                            navArgument("creditId") { type = NavType.StringType; nullable = true }
                        )
                    ) { backStack ->
                        val personId = backStack.arguments?.getString("personId")?.takeIf { it.isNotBlank() }?.let(Uri::decode)
                        val creditId = backStack.arguments?.getString("creditId")?.takeIf { it.isNotBlank() }?.let(Uri::decode)
                        AddCreditPaymentScreen(
                            preselectedPersonId = personId,
                            preselectedCreditId = creditId,
                            state = state,
                            onBack = { navController.popBackStack() },
                            onSavePayment = { cid, amount, date, method, mode, note ->
                                viewModel.recordCreditPayment(cid, amount, date, method, mode, note) { savedPayment ->
                                    navController.popBackStack()
                                    val receiptText = viewModel.buildPaymentReceiptText(savedPayment)
                                    pendingShare = PendingShareData(
                                        title = "Payment Receipt ${savedPayment.receiptNumber}",
                                        fileName = "Receipt_${savedPayment.receiptNumber}.pdf",
                                        text = receiptText
                                    )
                                }
                            }
                        )
                    }

                    composable(
                        route = Routes.ADD_NORMAL_PAYMENT,
                        arguments = listOf(navArgument("personId") { type = NavType.StringType; nullable = true })
                    ) { backStack ->
                        val personId = backStack.arguments?.getString("personId")?.takeIf { it.isNotBlank() }?.let(Uri::decode)
                        AddNormalPaymentScreen(
                            preselectedPersonId = personId,
                            state = state,
                            onBack = { navController.popBackStack() },
                            onSaveNormalPayment = { pid, amount, date, method, note ->
                                viewModel.recordNormalPayment(pid, amount, date, method, note) { savedPayment ->
                                    navController.popBackStack()
                                    val receiptText = viewModel.buildPaymentReceiptText(savedPayment)
                                    pendingShare = PendingShareData(
                                        title = "Normal Payment Receipt ${savedPayment.receiptNumber}",
                                        fileName = "Receipt_${savedPayment.receiptNumber}.pdf",
                                        text = receiptText
                                    )
                                }
                            }
                        )
                    }

                    composable(Routes.TRANSACTIONS) {
                        TransactionsScreen(
                            state = state,
                            onShareReceipt = { pay ->
                                val text = viewModel.buildPaymentReceiptText(pay)
                                pendingShare = PendingShareData(
                                    title = "Receipt ${pay.receiptNumber}",
                                    fileName = "Receipt_${pay.receiptNumber}.pdf",
                                    text = text
                                )
                            },
                            onEditPaymentFull = { payId, amountPaise, date, method, mode, note ->
                                viewModel.updatePaymentFull(payId, amountPaise, date, method, mode, note)
                            },
                            onEditAdjustmentEvent = { evId, newPrincipalPaise, newRate, newPeriod, effDate, note ->
                                viewModel.updateAdjustmentEvent(evId, newPrincipalPaise, newRate, newPeriod, effDate, note)
                            },
                            onDeleteEventOrPayment = { evId -> viewModel.deleteEventOrPaymentPermanently(evId) },
                            onReverseEvent = { evId, reason -> viewModel.reverseEvent(evId, reason) }
                        )
                    }

                    composable(Routes.CALCULATOR) {
                        CalculatorScreen(
                            state = state,
                            onAddHistory = { viewModel.addCalculatorHistory(it) },
                            onClearHistory = { viewModel.clearCalculatorHistory() },
                            onShowToast = { viewModel.showToast(it) }
                        )
                    }

                    composable(Routes.REPORTS) {
                        ReportsScreen(
                            state = state,
                            onSelectFilter = { viewModel.setDashboardFilter(it) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.REMINDERS) {
                        RemindersScreen(
                            state = state,
                            onBack = { navController.popBackStack() },
                            onAddReminder = { pid, cid, d, t, n -> viewModel.saveReminder(pid, cid, d, t, n) },
                            onUpdateStatus = { id, status -> viewModel.updateReminderStatus(id, status) },
                            onDeleteReminder = { id -> viewModel.deleteReminder(id) }
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            state = state,
                            onUpdateSettings = { viewModel.updateSettings(it) },
                            onOpenCloudDatabase = { navController.navigate(Routes.CLOUD_SETTINGS) },
                            onOpenReports = { navController.navigate(Routes.REPORTS) },
                            onOpenReminders = { navController.navigate(Routes.REMINDERS) },
                            onSetPin = { viewModel.setPin(it) },
                            onDisablePin = { pin, cb -> viewModel.disablePin(pin, cb) },
                            onSetAutoLockSeconds = { viewModel.setAutoLockSeconds(it) },
                            onSetBiometricEnabled = { viewModel.setBiometricEnabled(it) },
                            onLockNow = { viewModel.lockNow() },
                            onExportJson = { scopeType ->
                                pendingExportRequest = Triple(scopeType, null, null)
                                exportJsonLauncher.launch("credit_logger_backup_${LedgerDate.todayLocal()}.json")
                            },
                            onTriggerImportJson = {
                                importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            pendingImportPreview = pendingImportPreview,
                            onDismissImportPreview = { pendingImportPreview = null },
                            onConfirmImport = { preview, mode ->
                                viewModel.applyImport(preview, mode) { summary ->
                                    pendingImportPreview = null
                                    viewModel.showToast(
                                        "Imported ${summary.importedPersons} profiles, ${summary.importedCredits} credits, ${summary.importedPayments} payments"
                                    )
                                }
                            }
                        )
                    }

                    composable(Routes.CLOUD_SETTINGS) {
                        FirebaseCloudSettingsScreen(
                            config = state.firebaseConfig,
                            isBusy = state.isBusy,
                            onBack = { navController.popBackStack() },
                            onConnect = { cfg, email, pass, create -> viewModel.connectFirebase(cfg, email, pass, create) },
                            onTestConnection = { cfg -> viewModel.testFirebaseConnection(cfg) },
                            onSyncNow = { cfg -> viewModel.syncFirebaseNow(cfg) },
                            onDisconnect = { viewModel.disconnectFirebase() }
                        )
                    }
                }
            }
        }
    }

    if (showQuickAddSheet) {
        QuickAddBottomSheet(
            onDismiss = { showQuickAddSheet = false },
            onAddPerson = { isCreatingPerson = true },
            onAddCredit = { navController.navigate("add_credit") },
            onAddCreditPayment = { navController.navigate("add_credit_payment") },
            onAddNormalPayment = { navController.navigate("add_normal_payment") }
        )
    }

    if (isCreatingPerson || showAddEditPersonDialog != null) {
        val editing = showAddEditPersonDialog
        AddEditPersonDialog(
            initialPerson = editing,
            onDismiss = {
                isCreatingPerson = false
                showAddEditPersonDialog = null
            },
            onSave = { name, phone, address, photoUri, note ->
                viewModel.savePerson(editing?.id, name, phone, address, photoUri, note) {
                    isCreatingPerson = false
                    showAddEditPersonDialog = null
                }
            }
        )
    }

    if (pendingShare != null) {
        val shareData = pendingShare!!
        ShareStatementSheet(
            title = shareData.title,
            previewText = shareData.text,
            onDismiss = { pendingShare = null },
            onCopyText = {
                PdfAndShareManager.copyText(context, shareData.title, shareData.text)
                viewModel.showToast(strings.copiedToClipboard)
            },
            onShareText = {
                runCatching {
                    PdfAndShareManager.shareText(context, shareData.title, shareData.text)
                }.onFailure { viewModel.showToast(it.message ?: "Unable to share") }
            },
            onSharePdf = {
                runCatching {
                    val file = viewModel.generatePdfFile(
                        fileName = shareData.fileName,
                        title = "CREDIT LOGGER",
                        subtitle = shareData.title,
                        body = shareData.text
                    )
                    PdfAndShareManager.sharePdfFile(context, file, shareData.title)
                }.onFailure { viewModel.showToast(it.message ?: "Unable to share PDF") }
            },
            onDownloadPdf = {
                runCatching {
                    val file = viewModel.generatePdfFile(
                        fileName = shareData.fileName,
                        title = "CREDIT LOGGER",
                        subtitle = shareData.title,
                        body = shareData.text
                    )
                    pendingPdfDownloadFile = file
                    savePdfLauncher.launch(shareData.fileName)
                }.onFailure { viewModel.showToast(it.message ?: "Unable to generate PDF") }
            },
            onPrintPdf = {
                runCatching {
                    val file = viewModel.generatePdfFile(
                        fileName = shareData.fileName,
                        title = "CREDIT LOGGER",
                        subtitle = shareData.title,
                        body = shareData.text
                    )
                    PdfAndShareManager.printPdfFile(context, file, shareData.title)
                }.onFailure { viewModel.showToast(it.message ?: "Unable to print PDF") }
            }
        )
    }
}
