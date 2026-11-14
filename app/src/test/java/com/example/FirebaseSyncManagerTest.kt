package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CreditLoggerDatabase
import com.example.data.remote.CloudAuthUser
import com.example.data.remote.CloudSessionHandle
import com.example.data.remote.FirebaseCloudBackend
import com.example.data.remote.FirebaseCloudSessionRegistry
import com.example.data.remote.FirebaseConfigKey
import com.example.data.remote.FirebaseSyncManager
import com.example.data.remote.RemoteDocumentData
import com.example.data.remote.RemoteWriteOperation
import com.example.data.remote.toConfigKey
import com.example.data.repository.LedgerRepository
import com.example.domain.engine.InterestEngine
import com.example.domain.engine.LedgerDate
import com.example.domain.model.AllocationMode
import com.example.domain.model.AppLanguage
import com.example.domain.model.FirebaseCloudConfig
import com.example.domain.model.InterestMethod
import com.example.domain.model.PaymentMethod
import com.example.domain.model.RatePeriod
import com.example.security.SecurityPreferencesStore
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseSyncManagerTest {

    private lateinit var context: Context
    private lateinit var db: CreditLoggerDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var securityStore: SecurityPreferencesStore

    private val configA = FirebaseCloudConfig(
        apiKey = "AIzaSyDummyKeyA1234567890",
        authDomain = "credit-logger-a.firebaseapp.com",
        projectId = "credit-logger-a",
        storageBucket = "credit-logger-a.appspot.com",
        messagingSenderId = "123456789012",
        appId = "1:123456789012:android:abcdef123456"
    )

    private val configB = FirebaseCloudConfig(
        apiKey = "AIzaSyDummyKeyB1234567890",
        authDomain = "credit-logger-b.firebaseapp.com",
        projectId = "credit-logger-b",
        storageBucket = "credit-logger-b.appspot.com",
        messagingSenderId = "987654321098",
        appId = "1:987654321098:android:654321fedcba"
    )

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        FirebaseCloudSessionRegistry.resetForTesting(context)
        db = Room.inMemoryDatabaseBuilder(context, CreditLoggerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db, context)
        securityStore = SecurityPreferencesStore(context)
        securityStore.clearFirebaseConnection()
    }

    @After
    fun tearDown() = runTest {
        FirebaseCloudSessionRegistry.resetForTesting(context)
        db.close()
    }

    @Test
    fun `real FirebaseCloudSessionRegistry configures single FirebaseFirestore once and survives reconnect and project switch`() = runTest {
        // First connect configures FirebaseApp + single FirebaseFirestore instance once
        val session1 = FirebaseCloudSessionRegistry.getOrCreateConfiguredSession(context, configA)
        // Second call (e.g., sync, testConnection, or repeated connect) reuses the exact same instance
        val session2 = FirebaseCloudSessionRegistry.getOrCreateConfiguredSession(context, configA)
        assertSame(session1.app, session2.app)
        assertSame(session1.firestore, session2.firestore)

        // Disconnect signs out auth cleanly, and reconnecting to same project reuses same configured Firestore
        FirebaseCloudSessionRegistry.signOutActiveSession(context)
        val session3 = FirebaseCloudSessionRegistry.getOrCreateConfiguredSession(context, configA)
        assertSame(session1.app, session3.app)
        assertSame(session1.firestore, session3.firestore)

        // Switching to a different Firebase project cleanly tears down old app/Firestore and initializes a fresh one
        val sessionB1 = FirebaseCloudSessionRegistry.getOrCreateConfiguredSession(context, configB)
        assertNotSame(session1.app, sessionB1.app)
        assertNotSame(session1.firestore, sessionB1.firestore)

        val sessionB2 = FirebaseCloudSessionRegistry.getOrCreateConfiguredSession(context, configB)
        assertSame(sessionB1.app, sessionB2.app)
        assertSame(sessionB1.firestore, sessionB2.firestore)
    }

    @Test
    fun `complete connect test sync disconnect reconnect flow configures once before auth or io and preserves Room data`() = runTest {
        // Pre-populate Room SQLite data to confirm zero local data loss across connect/sync/disconnect/errors
        val person = repo.savePerson(
            name = "राहुल कुमार",
            phone = "9876543210",
            address = "Patna",
            photoRef = "",
            note = "VIP"
        )
        val credit = repo.createCredit(
            personId = person.id,
            creditNumber = "CL-501",
            principalPaise = 100_000L, // ₹1,000
            interestRate = "2",
            ratePeriod = RatePeriod.MONTHLY,
            interestMethod = InterestMethod.SIMPLE,
            startDate = LedgerDate(2026, 1, 1),
            dueDate = null,
            note = "Initial"
        )
        repo.applyMainCreditUpdate(
            creditId = credit.id,
            newPrincipalPaise = 150_000L,
            newRate = "3",
            newRatePeriod = RatePeriod.MONTHLY,
            effectiveDate = LedgerDate(2026, 2, 1),
            note = "Rate updated"
        )
        val payment = repo.recordCreditPayment(
            creditId = credit.id,
            amountPaise = 20_000L,
            paymentDate = LedgerDate(2026, 3, 1),
            paymentMethod = PaymentMethod.UPI,
            allocationMode = AllocationMode.PRINCIPAL_ADJUSTMENT,
            manualNote = "Installment 1",
            lang = AppLanguage.ENGLISH
        )
        val reminder = repo.saveReminder(
            personId = person.id,
            creditId = credit.id,
            reminderDate = LedgerDate(2026, 4, 1),
            reminderTime = "10:00",
            note = "Follow up"
        )

        val fakeBackend = StrictSingleFirestoreFakeBackend()
        // Seed one remote person from another device so pull + merge is also verified
        fakeBackend.seedRemoteDoc(
            uid = "uid-owner-1",
            collection = "persons",
            docId = "remote-person-1",
            data = mapOf(
                "name" to "मोहन यादव",
                "phone" to "9123456789",
                "address" to "Gaya",
                "photoRef" to "",
                "note" to "Synced from cloud",
                "createdAt" to 1000L,
                "updatedAt" to 2000L,
                "archivedAt" to null
            )
        )

        val syncManager = FirebaseSyncManager(context, db, securityStore, fakeBackend)

        // 1. Connect configures Firestore settings strictly BEFORE signIn and metadata write
        val connectRes = syncManager.connectAndSignIn(
            config = configA,
            email = "owner@example.com",
            password = "secret123",
            createAccountIfMissing = true
        )
        assertTrue(connectRes.isSuccess)
        val connectedConfig = connectRes.getOrThrow()
        assertTrue(connectedConfig.isConnected)
        assertEquals("Connected as owner@example.com", connectedConfig.lastSyncStatus)
        assertEquals("", connectedConfig.lastError)
        assertEquals(1, fakeBackend.settingsConfiguredCount)
        assertEquals(
            listOf("CONFIGURE_SETTINGS", "SIGN_IN", "WRITE_USER_META"),
            fakeBackend.trace.take(3)
        )

        // 2. Test Connection does not reconfigure Firestore settings
        val testRes = syncManager.testConnection(connectedConfig)
        assertTrue(testRes.isSuccess)
        assertTrue(testRes.getOrThrow().contains("OK: Authenticated"))
        assertEquals(1, fakeBackend.settingsConfiguredCount)

        // 3. Re-calling connectAndSignIn on same config reuses single instance without reconfiguring settings
        val secondConnectRes = syncManager.connectAndSignIn(
            config = connectedConfig,
            email = "owner@example.com",
            password = "secret123",
            createAccountIfMissing = false
        )
        assertTrue(secondConnectRes.isSuccess)
        assertEquals(1, fakeBackend.settingsConfiguredCount)

        // 4. First syncNow pulls 1 remote person and pushes local records without clobbering historical credit rate
        val syncRes1 = syncManager.syncNow(connectedConfig)
        assertTrue(syncRes1.isSuccess)
        val syncedConfig1 = syncRes1.getOrThrow()
        assertTrue(syncedConfig1.lastSyncStatus.contains("1 pulled"))
        assertEquals(1, fakeBackend.settingsConfiguredCount)

        // Verify local Room data preserved + merged, and historical rate on credit stayed "2"
        val mergedPersons = repo.personsFlow.first()
        assertEquals(2, mergedPersons.size)
        assertTrue(mergedPersons.any { it.id == person.id })
        assertTrue(mergedPersons.any { it.id == "remote-person-1" })
        val localCreditAfterSync = repo.creditsFlow.first().first { it.id == credit.id }
        assertEquals("2", localCreditAfterSync.interestRate)
        val marSnap = InterestEngine.calculateCreditBalanceAsOfDate(
            localCreditAfterSync,
            repo.eventsFlow.first().filter { it.creditId == credit.id },
            LedgerDate(2026, 3, 1)
        )
        assertEquals("3", marSnap.activeRate)

        // 5. Idempotent second sync uploads 0 duplicate documents
        val syncRes2 = syncManager.syncNow(syncedConfig1)
        assertTrue(syncRes2.isSuccess)
        assertEquals("Synced (0 pulled, 0 uploaded)", syncRes2.getOrThrow().lastSyncStatus)
        assertEquals(1, fakeBackend.settingsConfiguredCount)

        // 6. Disconnect updates connection state safely while preserving ALL Room SQLite data
        syncManager.disconnect(syncedConfig1)
        val afterDisconnectConfig = securityStore.firebaseConfig.first()
        assertFalse(afterDisconnectConfig.isConnected)
        assertEquals("Disconnected (local data intact)", afterDisconnectConfig.lastSyncStatus)
        assertEquals(2, repo.personsFlow.first().size)
        assertEquals(1, repo.creditsFlow.first().size)
        assertTrue(repo.paymentsFlow.first().any { it.id == payment.id })
        assertTrue(repo.remindersFlow.first().any { it.id == reminder.id })

        // TestConnection while disconnected fails cleanly with helpful message
        val disconnectedTest = syncManager.testConnection(afterDisconnectConfig)
        assertTrue(disconnectedTest.isFailure)
        assertTrue(disconnectedTest.exceptionOrNull()?.message?.contains("Not signed in") == true)

        // 7. Reconnect after disconnect reuses the single configured Firestore instance without crashing
        val reconnectRes = syncManager.connectAndSignIn(
            config = afterDisconnectConfig,
            email = "owner@example.com",
            password = "secret123",
            createAccountIfMissing = false
        )
        assertTrue(reconnectRes.isSuccess)
        assertEquals(1, fakeBackend.settingsConfiguredCount)
        assertTrue(syncManager.syncNow(reconnectRes.getOrThrow()).isSuccess)
        assertEquals(1, fakeBackend.settingsConfiguredCount)
    }

    @Test
    fun `firebase errors update status cleanly and never alter local Room SQLite data`() = runTest {
        val person = repo.savePerson(
            name = "अमित वर्मा",
            phone = "9000011111",
            address = "Lucknow",
            photoRef = "",
            note = ""
        )
        val fakeBackend = StrictSingleFirestoreFakeBackend()
        val syncManager = FirebaseSyncManager(context, db, securityStore, fakeBackend)

        // Missing API key
        val invalidConfigRes = syncManager.connectAndSignIn(
            config = configA.copy(apiKey = "   "),
            email = "owner@example.com",
            password = "secret123",
            createAccountIfMissing = false
        )
        assertTrue(invalidConfigRes.isFailure)
        assertTrue(invalidConfigRes.exceptionOrNull()?.message?.contains("API Key") == true)
        val savedErr1 = securityStore.firebaseConfig.first()
        assertFalse(savedErr1.isConnected)
        assertEquals("Connection failed", savedErr1.lastSyncStatus)

        // Connect cleanly first
        val connected = syncManager.connectAndSignIn(configA, "owner@example.com", "secret123", true).getOrThrow()

        // Simulate Firestore PERMISSION_DENIED during sync
        fakeBackend.nextPullFailure = FirebaseFirestoreException(
            "Missing or insufficient permissions",
            FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
        val permFail = syncManager.syncNow(connected)
        assertTrue(permFail.isFailure)
        assertTrue(permFail.exceptionOrNull()?.message?.contains("permission denied", ignoreCase = true) == true)
        val savedErr2 = securityStore.firebaseConfig.first()
        assertEquals("Sync error", savedErr2.lastSyncStatus)
        assertTrue(savedErr2.lastError.contains("Security Rules", ignoreCase = true))

        // Simulate Network failure during connect
        fakeBackend.nextSignInFailure = FirebaseNetworkException("Offline")
        val netFail = syncManager.connectAndSignIn(configA, "owner@example.com", "secret123", false)
        assertTrue(netFail.isFailure)
        assertTrue(netFail.exceptionOrNull()?.message?.contains("Network error", ignoreCase = true) == true)

        // Local Room SQLite data remains intact
        assertEquals(listOf(person.id), repo.personsFlow.first().map { it.id })
    }

    /**
     * Stateful fake backend that enforces Firestore's real lifecycle constraints:
     * - Settings MUST be configured strictly ONCE per Firestore instance lifecycle.
     * - Attempting to configure settings on an already-started Firestore throws the exact
     *   IllegalStateException reported by FirebaseFirestore.
     * - Auth, read, or write operations before settings are applied throw an error.
     */
    private class StrictSingleFirestoreFakeBackend : FirebaseCloudBackend {

        private class FakeSession(override val key: FirebaseConfigKey) : CloudSessionHandle {
            var started = false
        }

        private var activeSession: FakeSession? = null
        private var currentUser: CloudAuthUser? = null
        private val remoteCollections = mutableMapOf<String, MutableMap<String, MutableMap<String, Map<String, Any?>>>>()

        var settingsConfiguredCount = 0
            private set
        val trace = mutableListOf<String>()
        var nextPullFailure: Exception? = null
        var nextSignInFailure: Exception? = null

        fun seedRemoteDoc(uid: String, collection: String, docId: String, data: Map<String, Any?>) {
            val userCols = remoteCollections.getOrPut(uid) { mutableMapOf() }
            val col = userCols.getOrPut(collection) { mutableMapOf() }
            col[docId] = data
        }

        override suspend fun ensureConfiguredSession(config: FirebaseCloudConfig): CloudSessionHandle {
            val key = config.toConfigKey()
            require(key.apiKey.isNotBlank()) { "Firebase API Key is required." }
            require(key.projectId.isNotBlank()) { "Firebase Project ID is required." }
            require(key.appId.isNotBlank()) { "Firebase App ID is required." }

            val existing = activeSession
            if (existing != null && existing.key == key) {
                return existing
            }
            if (existing != null && existing.started && existing.key == key) {
                throw IllegalStateException(
                    "FirebaseFirestore has already been started and its settings can no longer be changed."
                )
            }
            val created = FakeSession(key)
            settingsConfiguredCount++
            trace += "CONFIGURE_SETTINGS"
            activeSession = created
            return created
        }

        private fun requireStartedSession(session: CloudSessionHandle): FakeSession {
            val s = session as FakeSession
            check(activeSession === s) { "Firestore settings must be configured before any operation!" }
            s.started = true
            return s
        }

        override suspend fun signInOrCreateUser(
            session: CloudSessionHandle,
            email: String,
            password: String,
            createAccountIfMissing: Boolean
        ): CloudAuthUser {
            requireStartedSession(session)
            nextSignInFailure?.let { err ->
                nextSignInFailure = null
                throw err
            }
            trace += "SIGN_IN"
            val user = CloudAuthUser(uid = "uid-owner-1", email = email.trim())
            currentUser = user
            return user
        }

        override suspend fun getCurrentUser(session: CloudSessionHandle): CloudAuthUser? {
            requireStartedSession(session)
            return currentUser
        }

        override suspend fun signOut() {
            trace += "SIGN_OUT"
            currentUser = null
        }

        override suspend fun writeUserMetadata(
            session: CloudSessionHandle,
            uid: String,
            email: String,
            timestamp: Long
        ) {
            requireStartedSession(session)
            trace += "WRITE_USER_META"
        }

        override suspend fun verifyUserDocument(session: CloudSessionHandle, uid: String) {
            requireStartedSession(session)
            trace += "VERIFY_USER_DOC"
        }

        override suspend fun pullCollection(
            session: CloudSessionHandle,
            uid: String,
            collection: String
        ): List<RemoteDocumentData> {
            requireStartedSession(session)
            nextPullFailure?.let { err ->
                nextPullFailure = null
                throw err
            }
            trace += "PULL:$collection"
            val col = remoteCollections[uid]?.get(collection).orEmpty()
            return col.entries.map { (id, data) -> RemoteDocumentData(id = id, data = data) }
        }

        override suspend fun batchUpsert(
            session: CloudSessionHandle,
            uid: String,
            operations: List<RemoteWriteOperation>
        ) {
            requireStartedSession(session)
            trace += "BATCH_UPSERT"
            val userCols = remoteCollections.getOrPut(uid) { mutableMapOf() }
            operations.forEach { op ->
                val col = userCols.getOrPut(op.collection) { mutableMapOf() }
                col[op.documentId] = op.data
            }
        }
    }
}
