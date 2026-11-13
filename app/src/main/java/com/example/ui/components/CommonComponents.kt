package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.engine.LedgerDate
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldSoft
import com.example.ui.theme.InterestOrange
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.MisradhanPurple
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.PaidGreen
import com.example.ui.theme.PrincipalBlue
import java.io.File
import java.util.Calendar
import java.util.TimeZone

@Composable
fun CreditLoggerLogoBadge(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    colors = listOf(NavyCard, NavyDeep)
                )
            )
            .border(1.5.dp, GoldAccent.copy(alpha = 0.85f), RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.18f)) {
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(
                color = GoldAccent,
                topLeft = Offset(0f, 0f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w * 0.16f, w * 0.16f)
            )
            drawRoundRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(w * 0.12f, h * 0.12f),
                size = Size(w * 0.76f, h * 0.76f),
                cornerRadius = CornerRadius(w * 0.1f, w * 0.1f)
            )
        }
        Text(
            text = "₹",
            color = GoldSoft,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
        )
    }
}

@Composable
fun SemanticKpiCard(
    title: String,
    amountText: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            accentColor,
            accentColor.copy(alpha = 0.84f)
        )
    )
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = Color.White,
                            modifier = Modifier
                                .padding(5.dp)
                                .size(16.dp)
                        )
                    }
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = amountText,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerDateField(
    label: String,
    date: LedgerDate?,
    onDateSelected: (LedgerDate) -> Unit,
    modifier: Modifier = Modifier,
    isOptional: Boolean = false,
    onCleared: (() -> Unit)? = null,
    testTag: String = "date_input"
) {
    var textValue by remember(date) { mutableStateOf(date?.toDisplayString() ?: "") }
    var isInvalid by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = textValue,
        onValueChange = { raw ->
            textValue = raw
            if (raw.isBlank() && isOptional) {
                isInvalid = false
                onCleared?.invoke()
            } else {
                val parsed = LedgerDate.parseOrNull(raw)
                if (parsed != null) {
                    isInvalid = false
                    onDateSelected(parsed)
                } else {
                    isInvalid = raw.length >= 8
                }
            }
        },
        label = { Text(label) },
        placeholder = { Text("DD/MM/YYYY") },
        isError = isInvalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = if (isInvalid) {
            { Text("DD/MM/YYYY") }
        } else null,
        trailingIcon = {
            IconButton(
                onClick = { showPicker = true },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = label
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )

    if (showPicker) {
        val initialMillis = remember(date) {
            val d = date ?: LedgerDate.todayLocal()
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(d.year, d.month - 1, d.day)
            }.timeInMillis
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = millis
                            }
                            val picked = LedgerDate(
                                year = cal.get(Calendar.YEAR),
                                month = cal.get(Calendar.MONTH) + 1,
                                day = cal.get(Calendar.DAY_OF_MONTH)
                            )
                            textValue = picked.toDisplayString()
                            isInvalid = false
                            onDateSelected(picked)
                        }
                        showPicker = false
                    }
                ) {
                    Text(LocalAppStrings.current.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(LocalAppStrings.current.cancel)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
fun PersonAvatar(
    name: String,
    photoRef: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val colors = listOf(PrincipalBlue, MisradhanPurple, PaidGreen, InterestOrange)
    val bgColor = colors[kotlin.math.abs(name.hashCode()) % colors.size]
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = 0.18f))
            .border(1.5.dp, bgColor.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (photoRef.isNotBlank()) {
            val model = if (photoRef.startsWith("/")) File(photoRef) else Uri.parse(photoRef)
            AsyncImage(
                model = model,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initial,
                color = bgColor,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    onDismiss: () -> Unit,
    onAddPerson: () -> Unit,
    onAddCredit: () -> Unit,
    onAddCreditPayment: () -> Unit,
    onAddNormalPayment: () -> Unit
) {
    val strings = LocalAppStrings.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = strings.quickAddTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            QuickAddActionRow(
                icon = Icons.Default.PersonAdd,
                title = strings.addLoanTaker,
                color = PrincipalBlue,
                tag = "quick_add_person",
                onClick = { onDismiss(); onAddPerson() }
            )
            QuickAddActionRow(
                icon = Icons.Default.AccountBalanceWallet,
                title = strings.addCredit,
                color = MisradhanPurple,
                tag = "quick_add_credit",
                onClick = { onDismiss(); onAddCredit() }
            )
            QuickAddActionRow(
                icon = Icons.Default.Payment,
                title = strings.addCreditPayment,
                color = PaidGreen,
                tag = "quick_add_credit_payment",
                onClick = { onDismiss(); onAddCreditPayment() }
            )
            QuickAddActionRow(
                icon = Icons.Default.ReceiptLong,
                title = strings.addNormalPayment,
                color = InterestOrange,
                tag = "quick_add_normal_payment",
                onClick = { onDismiss(); onAddNormalPayment() }
            )
        }
    }
}

@Composable
private fun QuickAddActionRow(
    icon: ImageVector,
    title: String,
    color: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareStatementSheet(
    title: String,
    previewText: String,
    onDismiss: () -> Unit,
    onCopyText: () -> Unit,
    onShareText: () -> Unit,
    onSharePdf: () -> Unit,
    onDownloadPdf: () -> Unit,
    onPrintPdf: () -> Unit
) {
    val strings = LocalAppStrings.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = previewText.lines().take(6).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ListItem(
                headlineContent = { Text(strings.copyText) },
                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = strings.copyText, tint = PrincipalBlue) },
                modifier = Modifier.clickable { onDismiss(); onCopyText() }.testTag("share_copy_text")
            )
            ListItem(
                headlineContent = { Text(strings.shareAsText) },
                leadingContent = { Icon(Icons.Default.Share, contentDescription = strings.shareAsText, tint = PaidGreen) },
                modifier = Modifier.clickable { onDismiss(); onShareText() }.testTag("share_as_text")
            )
            ListItem(
                headlineContent = { Text(strings.generateAndSharePdf) },
                leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = strings.generateAndSharePdf, tint = MisradhanPurple) },
                modifier = Modifier.clickable { onDismiss(); onSharePdf() }.testTag("share_pdf")
            )
            ListItem(
                headlineContent = { Text(strings.downloadPdf) },
                leadingContent = { Icon(Icons.Default.Download, contentDescription = strings.downloadPdf, tint = InterestOrange) },
                modifier = Modifier.clickable { onDismiss(); onDownloadPdf() }.testTag("download_pdf")
            )
            ListItem(
                headlineContent = { Text(strings.printStatement) },
                leadingContent = { Icon(Icons.Default.Print, contentDescription = strings.printStatement, tint = GoldAccent) },
                modifier = Modifier.clickable { onDismiss(); onPrintPdf() }.testTag("print_pdf")
            )
        }
    }
}
