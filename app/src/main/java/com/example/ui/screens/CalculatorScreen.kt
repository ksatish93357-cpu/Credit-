package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.export.PdfAndShareManager
import com.example.domain.engine.ExpressionEvaluator
import com.example.domain.engine.IndianNumberFormatter
import com.example.domain.engine.InterestEngine
import com.example.domain.engine.LedgerDate
import com.example.domain.engine.StatementTextGenerator
import com.example.domain.model.InterestMethod
import com.example.domain.model.RatePeriod
import com.example.ui.components.LedgerDateField
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.InterestOrange
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MisradhanPurple
import com.example.ui.theme.OverdueRed
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PrincipalBlue
import com.example.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    state: MainUiState,
    onAddHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedMode by remember { mutableIntStateOf(0) } // 0 = Interest, 1 = Normal

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(strings.navCalculator) })
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedMode == 0,
                        onClick = { selectedMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("calc_tab_interest")
                    ) { Text(strings.creditCalculator, maxLines = 1) }
                    SegmentedButton(
                        selected = selectedMode == 1,
                        onClick = { selectedMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("calc_tab_normal")
                    ) { Text(strings.normalCalculator, maxLines = 1) }
                }

                if (selectedMode == 0) {
                    InterestCalculatorPane(state = state, onShowToast = onShowToast)
                } else {
                    NormalCalculatorPane(
                        history = state.calculatorHistory,
                        onAddHistory = onAddHistory,
                        onClearHistory = onClearHistory,
                        onShowToast = onShowToast
                    )
                }
            }
        }
    }
}

@Composable
private fun InterestCalculatorPane(
    state: MainUiState,
    onShowToast: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val fmt = { p: Long -> IndianNumberFormatter.formatCurrency(p, state.settings.displayDecimals, trimTrailingZerosWhenWhole = true) }

    var principalText by remember { mutableStateOf("1000") }
    var rateText by remember { mutableStateOf("8.3333") }
    var ratePeriod by remember { mutableStateOf(RatePeriod.MONTHLY) }
    var interestMethod by remember { mutableStateOf(InterestMethod.SIMPLE) }
    var startDate by remember { mutableStateOf(LedgerDate(2026, 1, 13)) }
    var calcDate by remember { mutableStateOf(LedgerDate(2026, 7, 13)) }

    val paise = IndianNumberFormatter.rupeesInputToPaiseOrNull(principalText) ?: 0L
    val calcResult = remember(paise, rateText, ratePeriod, interestMethod, startDate, calcDate) {
        if (paise <= 0L || calcDate < startDate) null
        else runCatching {
            InterestEngine.calculateInterestForPeriod(
                principalPaise = paise,
                ratePercent = rateText,
                ratePeriod = ratePeriod,
                interestMethod = interestMethod,
                startDate = startDate,
                endDate = calcDate
            )
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = principalText,
            onValueChange = { principalText = it },
            label = { Text(strings.principalAmount) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("calc_principal_input")
        )

        OutlinedTextField(
            value = rateText,
            onValueChange = { rateText = it },
            label = { Text(strings.interestRate) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("calc_rate_input")
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
            if (state.settings.allowCompoundInterest) {
                FilterChip(
                    selected = interestMethod == InterestMethod.COMPOUND,
                    onClick = {
                        interestMethod = if (interestMethod == InterestMethod.SIMPLE) InterestMethod.COMPOUND else InterestMethod.SIMPLE
                    },
                    label = { Text(strings.compoundInterest) }
                )
            }
        }

        LedgerDateField(label = strings.startDate, date = startDate, onDateSelected = { startDate = it }, testTag = "calc_start_date")
        LedgerDateField(label = strings.calculationDate, date = calcDate, onDateSelected = { calcDate = it }, testTag = "calc_end_date")

        if (calcDate < startDate) {
            Text(
                "Calculation date cannot be earlier than start date",
                color = OverdueRed,
                style = MaterialTheme.typography.bodySmall
            )
        } else if (calcResult != null) {
            val misradhan = InterestEngine.calculateMisradhan(paise, calcResult.interestPaise)
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(strings.completedMonths, "${calcResult.duration.completedMonths}")
                    DetailRow(strings.remainingDays, "${calcResult.duration.remainingDays}")
                    DetailRow(strings.totalDays, "${calcResult.duration.totalElapsedDays}")
                    HorizontalDivider()
                    DetailRow(strings.totalPrincipal, fmt(paise), PrincipalBlue)
                    DetailRow(strings.accruedInterest, fmt(calcResult.interestPaise), InterestOrange)
                    DetailRow(strings.misradhan, fmt(misradhan), MisradhanPurple)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val shortText = "${strings.accruedInterest}: ${fmt(calcResult.interestPaise)} | ${strings.misradhan}: ${fmt(misradhan)}"
                        PdfAndShareManager.copyText(context, "Interest Result", shortText)
                        onShowToast(strings.copiedToClipboard)
                    },
                    modifier = Modifier.weight(1f).testTag("calc_copy_result")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.copyResult, maxLines = 1)
                }
                Button(
                    onClick = {
                        val full = StatementTextGenerator.generateInterestCalculatorBreakdownText(
                            principalPaise = paise,
                            ratePercent = rateText,
                            ratePeriod = ratePeriod,
                            startDate = startDate,
                            endDate = calcDate,
                            result = calcResult,
                            lang = state.settings.language
                        )
                        PdfAndShareManager.copyText(context, "Interest Breakdown", full)
                        onShowToast(strings.copiedToClipboard)
                    },
                    modifier = Modifier.weight(1f).testTag("calc_copy_breakdown")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(strings.copyFullBreakdown, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun NormalCalculatorPane(
    history: List<String>,
    onAddHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    var expression by remember { mutableStateOf("") }
    var lastEvaluatedFull by remember { mutableStateOf("") }
    var currentResult by remember { mutableStateOf("0") }
    var evalError by remember { mutableStateOf(false) }

    val appendToken: (String) -> Unit = { token ->
        evalError = false
        expression += token
        val live = ExpressionEvaluator.evaluate(expression)
        if (live.isSuccess) {
            currentResult = live.getOrDefault("0")
        }
    }

    val evaluateNow: () -> Unit = {
        if (expression.isNotBlank()) {
            val res = ExpressionEvaluator.evaluate(expression)
            res.onSuccess { ans ->
                currentResult = ans
                val formatted = "$expression = $ans"
                lastEvaluatedFull = formatted
                onAddHistory(formatted)
                evalError = false
            }.onFailure {
                evalError = true
                currentResult = "Error"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = expression.ifBlank { "0" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().testTag("normal_calc_expression")
                )
                Text(
                    text = currentResult,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (evalError) OverdueRed else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().testTag("normal_calc_result")
                )
            }
        }

        // Copy full expression + result button (e.g. "2 + 2 + 2 + 2 + 2 = 10")
        OutlinedButton(
            onClick = {
                val textToCopy = when {
                    lastEvaluatedFull.isNotBlank() && expression.isNotBlank() && lastEvaluatedFull.startsWith(expression) -> lastEvaluatedFull
                    expression.isNotBlank() -> {
                        val ans = ExpressionEvaluator.evaluate(expression).getOrDefault(currentResult)
                        "$expression = $ans"
                    }
                    else -> currentResult
                }
                PdfAndShareManager.copyText(context, "Calculation", textToCopy)
                onShowToast("${strings.copiedToClipboard}: $textToCopy")
            },
            modifier = Modifier.fillMaxWidth().testTag("normal_calc_copy_full")
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(8.dp))
            Text(strings.copyExpressionAndResult)
        }

        // Keypad
        val rows = listOf(
            listOf("AC", "(", ")", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("%", "0", ".", "=")
        )
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val isOp = key in listOf("÷", "×", "-", "+", "=", "%")
                    val isClear = key == "AC"
                    val container = when {
                        key == "=" -> MisradhanPurple
                        isClear -> OverdueRed.copy(alpha = 0.18f)
                        isOp -> GoldAccent.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val contentColor = when {
                        key == "=" -> Color.White
                        isClear -> OverdueRed
                        isOp -> GoldAccent
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Button(
                        onClick = {
                            when (key) {
                                "AC" -> {
                                    expression = ""
                                    currentResult = "0"
                                    lastEvaluatedFull = ""
                                    evalError = false
                                }
                                "=" -> evaluateNow()
                                "+", "-", "×", "÷" -> appendToken(" $key ")
                                else -> appendToken(key)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = contentColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("calc_btn_$key")
                    ) {
                        Text(key, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (expression.isNotEmpty()) {
                        expression = expression.trimEnd().dropLast(1).trimEnd()
                        if (expression.isEmpty()) currentResult = "0"
                        else ExpressionEvaluator.evaluate(expression).onSuccess { currentResult = it }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("calc_btn_backspace")
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                Spacer(Modifier.size(8.dp))
                Text("Backspace")
            }
        }

        if (history.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(strings.calculationHistory, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                IconButton(onClick = onClearHistory) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear history")
                }
            }
            history.forEach { item ->
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            PdfAndShareManager.copyText(context, "Calculation", item)
                            onShowToast(strings.copiedToClipboard)
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}
