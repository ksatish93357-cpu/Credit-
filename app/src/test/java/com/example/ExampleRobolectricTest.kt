package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.export.ExportScope
import com.example.data.export.ImportMode
import com.example.data.export.JsonBackupManager
import com.example.data.export.PdfAndShareManager
import com.example.data.local.CreditLoggerDatabase
import com.example.data.repository.LedgerRepository
import com.example.domain.engine.InterestEngine
import com.example.domain.engine.LedgerDate
import com.example.domain.engine.StatementTextGenerator
import com.example.domain.model.AllocationMode
import com.example.domain.model.AppLanguage
import com.example.domain.model.CreditStatus
import com.example.domain.model.InterestMethod
import com.example.domain.model.PaymentMethod
import com.example.domain.model.RatePeriod
import com.example.domain.model.ReminderStatus
import com.example.notifications.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: CreditLoggerDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var backupManager: JsonBackupManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CreditLoggerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db, context)
        backupManager = JsonBackupManager(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app_name string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("CREDIT LOGGER", appName)
    }

    @Test
    fun `tests 9 to 12 persistence backup statements pdf and event reversal`() = runTest {
        // Create person + credit
        val person = repo.savePerson(
            name = "राहुल कुमार",
            phone = "9876543210",
            address = "Patna",
            photoRef = "",
            note = "Regular"
        )
        val credit = repo.createCredit(
            personId = person.id,
            creditNumber = "CL-001",
            principalPaise = 100_000L, // ₹1,000
            interestRate = "8.3333",
            ratePeriod = RatePeriod.MONTHLY,
            interestMethod = InterestMethod.SIMPLE,
            startDate = LedgerDate(2026, 1, 13),
            dueDate = null,
            note = "Test 1"
        )

        // Test 2: record ₹1,000 payment on 13/07/2026 via Principal Adjustment
        val payment = repo.recordCreditPayment(
            creditId = credit.id,
            amountPaise = 100_000L,
            paymentDate = LedgerDate(2026, 7, 13),
            paymentMethod = PaymentMethod.CASH,
            allocationMode = AllocationMode.PRINCIPAL_ADJUSTMENT,
            manualNote = "आंशिक भुगतान",
            lang = AppLanguage.HINDI
        )

        // Test 3: Normal payment ₹500
        repo.recordNormalPayment(
            personId = person.id,
            amountPaise = 50_000L,
            paymentDate = LedgerDate(2026, 7, 14),
            paymentMethod = PaymentMethod.UPI,
            note = "Separate"
        )

        val creditsAfterPay = repo.creditsFlow.first()
        assertEquals(50_000L, creditsAfterPay.first().currentPrincipalPaise)
        assertEquals(CreditStatus.ACTIVE, creditsAfterPay.first().status)

        // Test 10: Export JSON -> Clear/Import into clean DB -> Verify identical balances
        val exportedJson = backupManager.exportJson(ExportScope.ALL_DATA)
        val cleanDb = Room.inMemoryDatabaseBuilder(context, CreditLoggerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val cleanBackup = JsonBackupManager(cleanDb)
        val preview = cleanBackup.parseAndPreviewImport(exportedJson)
        assertEquals(1, preview.bundle.persons.size)
        assertEquals(1, preview.bundle.credits.size)
        assertEquals(2, preview.bundle.payments.size)

        val importSummary = cleanBackup.applyImport(preview, ImportMode.REPLACE_ALL)
        assertEquals(1, importSummary.importedPersons)
        assertEquals(50_000L, cleanDb.creditDao().getAll().first().currentPrincipalPaise)
        cleanDb.close()

        // Test 11: Statement & Receipt generation + PDF generation
        val events = repo.eventsFlow.first()
        val snap = InterestEngine.calculateCreditBalanceAsOfDate(
            creditsAfterPay.first(),
            events,
            LedgerDate(2026, 7, 13)
        )
        val statementText = StatementTextGenerator.generateCreditStatementText(
            person = person,
            credit = creditsAfterPay.first(),
            snapshot = snap,
            payments = repo.paymentsFlow.first(),
            lang = AppLanguage.ENGLISH
        )
        assertTrue(statementText.contains("CL-001"))
        assertTrue(statementText.contains("₹500"))

        val pdfFile = PdfAndShareManager.generateStatementPdfFile(
            context = context,
            fileName = "test_statement.pdf",
            title = "CREDIT LOGGER",
            subtitle = "CL-001",
            bodyText = statementText
        )
        assertTrue(pdfFile.exists() && pdfFile.length() > 100)

        // Test 12: Reverse credit payment and verify principal restores to ₹1,000 + Audit Log
        repo.reverseEvent(payment.id, "Entered by mistake")
        val creditsAfterReversal = repo.creditsFlow.first()
        assertEquals(100_000L, creditsAfterReversal.first().currentPrincipalPaise)
        val audits = repo.auditLogsFlow.first()
        assertTrue(audits.any { it.action == "REVERSE" })
    }

    @Test
    fun `transactions only export warns on preview and reminders plus future credits work cleanly`() = runTest {
        val person = repo.savePerson(
            name = "अमित",
            phone = "9999988888",
            address = "",
            photoRef = "",
            note = ""
        )
        val futureStart = LedgerDate(2099, 1, 1)
        val futureCredit = repo.createCredit(
            personId = person.id,
            creditNumber = "CL-FUTURE",
            principalPaise = 100_000L,
            interestRate = "2",
            ratePeriod = RatePeriod.MONTHLY,
            interestMethod = InterestMethod.SIMPLE,
            startDate = futureStart,
            dueDate = null,
            note = "Future"
        )
        repo.applyMainCreditUpdate(
            creditId = futureCredit.id,
            newPrincipalPaise = 150_000L,
            newRate = "3",
            newRatePeriod = RatePeriod.MONTHLY,
            effectiveDate = LedgerDate(2099, 2, 1),
            note = "Top up"
        )
        val updated = repo.creditsFlow.first().first { it.id == futureCredit.id }
        assertEquals(150_000L, updated.currentPrincipalPaise)
        assertEquals("3", updated.interestRate)

        val txJson = backupManager.exportJson(ExportScope.TRANSACTIONS_ONLY)
        val preview = backupManager.parseAndPreviewImport(txJson)
        assertTrue(preview.missingReferenceWarnings.isNotEmpty())

        val reminder = repo.saveReminder(
            personId = person.id,
            creditId = futureCredit.id,
            reminderDate = futureStart,
            reminderTime = "25:99",
            note = "Follow up"
        )
        ReminderScheduler.scheduleReminder(context, reminder, person.name)
        repo.updateReminderStatus(reminder.id, ReminderStatus.COMPLETED)
        ReminderScheduler.cancelReminder(context, reminder.id)
        repo.deleteReminder(reminder.id)
        assertTrue(repo.remindersFlow.first().isEmpty())
    }
}
