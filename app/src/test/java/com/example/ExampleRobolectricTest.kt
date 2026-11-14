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
import org.junit.Assert.assertFalse
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
        // Initial credit rate stays "2" so historical Jan->Feb period always calculates at 2%
        assertEquals("2", updated.interestRate)
        val futureSnap = InterestEngine.calculateCreditBalanceAsOfDate(
            updated,
            repo.eventsFlow.first().filter { it.creditId == futureCredit.id },
            LedgerDate(2099, 3, 1)
        )
        assertEquals("3", futureSnap.activeRate)
        // 1 mo at 2% on 1000 (=20) + 1 mo at 3% on 1500 (=45) = 65 (6500 paise)
        assertEquals(6_500L, futureSnap.accruedUnpaidInterestPaise)

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

    @Test
    fun `full credit edit, payment edit, adjustment edit, and permanent deletes recalculate exact ledger`() = runTest {
        val person = repo.savePerson(
            name = "सुरेश शर्मा",
            phone = "9876500000",
            address = "Delhi",
            photoRef = "",
            note = ""
        )
        val credit = repo.createCredit(
            personId = person.id,
            creditNumber = "CL-101",
            principalPaise = 100_000L, // ₹1,000
            interestRate = "2",
            ratePeriod = RatePeriod.MONTHLY,
            interestMethod = InterestMethod.SIMPLE,
            startDate = LedgerDate(2026, 1, 1),
            dueDate = LedgerDate(2026, 12, 31),
            note = "Initial"
        )
        val pay = repo.recordCreditPayment(
            creditId = credit.id,
            amountPaise = 24_000L, // ₹240 on 2026-03-01 (before edit: 2 mo @ 2% = ₹40 -> Misradhan ₹1040 -> New Principal ₹800)
            paymentDate = LedgerDate(2026, 3, 1),
            paymentMethod = PaymentMethod.CASH,
            allocationMode = AllocationMode.PRINCIPAL_ADJUSTMENT,
            manualNote = "First installment",
            lang = AppLanguage.ENGLISH
        )
        assertEquals(80_000L, repo.creditsFlow.first().first { it.id == credit.id }.currentPrincipalPaise)

        // 1. Full credit edit: change original principal from ₹1,000 to ₹2,000, rate 2% -> 5%, number CL-101A
        repo.updateCreditFullDetails(
            creditId = credit.id,
            creditNumber = "CL-101A",
            originalPrincipalPaise = 200_000L,
            interestRate = "5",
            ratePeriod = RatePeriod.MONTHLY,
            interestMethod = InterestMethod.SIMPLE,
            startDate = LedgerDate(2026, 1, 1),
            dueDate = LedgerDate(2027, 1, 15),
            note = "Edited credit"
        )
        // Recalculation: 2 mo @ 5% on ₹2,000 = ₹200 interest -> Misradhan ₹2,200 - ₹240 = ₹1,960 (196,000 paise)
        val afterCreditEdit = repo.creditsFlow.first().first { it.id == credit.id }
        assertEquals("CL-101A", afterCreditEdit.creditNumber)
        assertEquals(200_000L, afterCreditEdit.originalPrincipalPaise)
        assertEquals("5", afterCreditEdit.interestRate)
        assertEquals(196_000L, afterCreditEdit.currentPrincipalPaise)
        assertEquals("Edited credit", afterCreditEdit.note)

        // 2. Edit payment: pay off full ₹2,200 in INTEREST_FIRST on 2026-03-01 -> credit closes (₹0)
        repo.updatePaymentFull(
            paymentId = pay.id,
            amountPaise = 220_000L,
            paymentDate = LedgerDate(2026, 3, 1),
            paymentMethod = PaymentMethod.UPI,
            allocationMode = AllocationMode.INTEREST_FIRST,
            note = "Full settlement"
        )
        val afterFullPayEdit = repo.creditsFlow.first().first { it.id == credit.id }
        assertEquals(0L, afterFullPayEdit.currentPrincipalPaise)
        assertEquals(CreditStatus.CLOSED, afterFullPayEdit.status)

        // 3. Edit payment again to partial ₹100 (Interest First -> pays ₹100 of ₹200 interest, principal stays ₹2,000, status returns to ACTIVE!)
        repo.updatePaymentFull(
            paymentId = pay.id,
            amountPaise = 10_000L,
            paymentDate = LedgerDate(2026, 3, 1),
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            allocationMode = AllocationMode.INTEREST_FIRST,
            note = "Partial interest payment"
        )
        val afterPartialPayEdit = repo.creditsFlow.first().first { it.id == credit.id }
        assertEquals(200_000L, afterPartialPayEdit.currentPrincipalPaise)
        assertEquals(CreditStatus.ACTIVE, afterPartialPayEdit.status)
        val snapMar = InterestEngine.calculateCreditBalanceAsOfDate(
            afterPartialPayEdit,
            repo.eventsFlow.first().filter { it.creditId == credit.id },
            LedgerDate(2026, 3, 1)
        )
        assertEquals(10_000L, snapMar.accruedUnpaidInterestPaise)
        assertEquals(210_000L, snapMar.misradhanPaise)

        // 4. Add Main Credit Update on 2026-04-01 (rate 5% -> 3%), then edit adjustment to 4%
        repo.applyMainCreditUpdate(
            creditId = credit.id,
            newPrincipalPaise = 250_000L,
            newRate = "3",
            newRatePeriod = RatePeriod.MONTHLY,
            effectiveDate = LedgerDate(2026, 4, 1),
            note = "Rate change"
        )
        val adjEvent = repo.eventsFlow.first().first { it.creditId == credit.id && it.principalAfterPaise == 250_000L }
        repo.updateAdjustmentEvent(
            eventId = adjEvent.id,
            newPrincipalPaise = 250_000L,
            newRate = "4",
            newRatePeriod = RatePeriod.MONTHLY,
            effectiveDate = LedgerDate(2026, 4, 1),
            note = "Edited rate change"
        )
        // Verify rate before 2026-04-01 remained 5% and from 2026-04-01 is 4%
        val snapMay = InterestEngine.calculateCreditBalanceAsOfDate(
            repo.creditsFlow.first().first { it.id == credit.id },
            repo.eventsFlow.first().filter { it.creditId == credit.id },
            LedgerDate(2026, 5, 1)
        )
        assertEquals("4", snapMay.activeRate)
        // Unpaid interest on Apr 1: ₹100 carried from Mar 1 + 1 mo @ 5% on ₹2,000 (=₹100) => ₹200
        // May 1 adds 1 mo @ 4% on ₹2,500 (=₹100) => total accrued ₹300 (30,000 paise)
        assertEquals(30_000L, snapMay.accruedUnpaidInterestPaise)

        // 5. Permanently delete adjustment and verify payment still intact, then permanently delete payment
        repo.deleteEventOrPaymentPermanently(adjEvent.id)
        assertFalse(repo.eventsFlow.first().any { it.id == adjEvent.id })
        assertEquals(200_000L, repo.creditsFlow.first().first { it.id == credit.id }.currentPrincipalPaise)

        repo.deleteEventOrPaymentPermanently(pay.id)
        assertFalse(repo.paymentsFlow.first().any { it.id == pay.id })
        assertFalse(repo.eventsFlow.first().any { it.id == pay.id })

        // 6. Permanently delete credit + linked reminder and verify clean cascade
        val rem = repo.saveReminder(
            personId = person.id,
            creditId = credit.id,
            reminderDate = LedgerDate(2026, 6, 1),
            reminderTime = "10:00",
            note = "Due"
        )
        val cancelledRemIds = repo.deleteCreditPermanently(credit.id)
        assertEquals(listOf(rem.id), cancelledRemIds)
        assertTrue(repo.creditsFlow.first().none { it.id == credit.id })
        assertTrue(repo.eventsFlow.first().none { it.creditId == credit.id })
        assertTrue(repo.remindersFlow.first().none { it.id == rem.id })
    }
}
