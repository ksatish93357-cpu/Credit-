package com.example.data.remote

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.CreditLoggerDatabase
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.engine.InterestEngine
import com.example.domain.engine.LedgerDate
import com.example.domain.model.AllocationMode
import com.example.domain.model.Credit
import com.example.domain.model.CreditEvent
import com.example.domain.model.CreditEventType
import com.example.domain.model.CreditStatus
import com.example.domain.model.FirebaseCloudConfig
import com.example.domain.model.InterestMethod
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentType
import com.example.domain.model.Person
import com.example.domain.model.RatePeriod
import com.example.domain.model.Reminder
import com.example.domain.model.ReminderStatus
import com.example.security.SecurityPreferencesStore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

data class FirebaseConfigKey(
    val apiKey: String,
    val projectId: String,
    val appId: String,
    val authDomain: String,
    val storageBucket: String,
    val messagingSenderId: String
)

fun FirebaseCloudConfig.toConfigKey(): FirebaseConfigKey = FirebaseConfigKey(
    apiKey = apiKey.trim(),
    projectId = projectId.trim(),
    appId = appId.trim(),
    authDomain = authDomain.trim(),
    storageBucket = storageBucket.trim(),
    messagingSenderId = messagingSenderId.trim()
)

data class CloudAuthUser(
    val uid: String,
    val email: String?
)

data class RemoteDocumentData(
    val id: String,
    val data: Map<String, Any?>
)

data class RemoteWriteOperation(
    val collection: String,
    val documentId: String,
    val data: Map<String, Any?>
)

interface CloudSessionHandle {
    val key: FirebaseConfigKey
}

interface FirebaseCloudBackend {
    suspend fun ensureConfiguredSession(config: FirebaseCloudConfig): CloudSessionHandle
    suspend fun signInOrCreateUser(
        session: CloudSessionHandle,
        email: String,
        password: String,
        createAccountIfMissing: Boolean
    ): CloudAuthUser
    suspend fun getCurrentUser(session: CloudSessionHandle): CloudAuthUser?
    suspend fun signOut()
    suspend fun writeUserMetadata(session: CloudSessionHandle, uid: String, email: String, timestamp: Long)
    suspend fun verifyUserDocument(session: CloudSessionHandle, uid: String)
    suspend fun pullCollection(session: CloudSessionHandle, uid: String, collection: String): List<RemoteDocumentData>
    suspend fun batchUpsert(session: CloudSessionHandle, uid: String, operations: List<RemoteWriteOperation>)
}

/**
 * Process-wide singleton holder for the secondary FirebaseApp, FirebaseAuth, and
 * single configured FirebaseFirestore instance so settings are applied strictly ONCE
 * before any auth or Firestore operation, and never re-applied on reconnect or retry.
 */
internal object FirebaseCloudSessionRegistry {
    const val SECONDARY_APP_NAME = "credit_logger_user_cloud"

    val sessionMutex = Mutex()
    private var activeSession: ActiveSession? = null
    private val configuredAppIdentities = mutableSetOf<Int>()

    data class ActiveSession(
        val key: FirebaseConfigKey,
        val app: FirebaseApp,
        val auth: FirebaseAuth,
        val firestore: FirebaseFirestore,
        val handle: AndroidCloudSessionHandle
    )

    class AndroidCloudSessionHandle(
        override val key: FirebaseConfigKey,
        val app: FirebaseApp,
        val auth: FirebaseAuth,
        val firestore: FirebaseFirestore
    ) : CloudSessionHandle

    suspend fun getOrCreateConfiguredSession(
        context: Context,
        config: FirebaseCloudConfig
    ): AndroidCloudSessionHandle = sessionMutex.withLock {
        val targetKey = config.toConfigKey()
        require(targetKey.apiKey.isNotBlank()) { "Firebase API Key is required." }
        require(targetKey.projectId.isNotBlank()) { "Firebase Project ID is required." }
        require(targetKey.appId.isNotBlank()) { "Firebase App ID is required." }

        val current = activeSession
        if (current != null && current.key == targetKey && !isAppDeleted(context, current.app)) {
            return@withLock current.handle
        }

        if (current != null) {
            tearDownSessionLocked(current)
            activeSession = null
        }

        val existingApp = FirebaseApp.getApps(context).firstOrNull { it.name == SECONDARY_APP_NAME }
        val app = if (existingApp != null) {
            val opts = existingApp.options
            val matches = opts.apiKey == targetKey.apiKey &&
                opts.projectId == targetKey.projectId &&
                opts.applicationId == targetKey.appId &&
                (opts.storageBucket ?: "") == targetKey.storageBucket &&
                (opts.gcmSenderId ?: "") == targetKey.messagingSenderId
            if (matches) {
                existingApp
            } else {
                runCatching { FirebaseFirestore.getInstance(existingApp).terminate().await() }
                configuredAppIdentities.remove(System.identityHashCode(existingApp))
                runCatching { existingApp.delete() }
                initializeSecondaryApp(context, targetKey)
            }
        } else {
            initializeSecondaryApp(context, targetKey)
        }

        // Configure Firestore settings strictly BEFORE obtaining/using Auth or calling any Firestore query/write
        val firestore = configureFirestoreOnceLocked(app)
        val auth = FirebaseAuth.getInstance(app)
        val handle = AndroidCloudSessionHandle(targetKey, app, auth, firestore)
        activeSession = ActiveSession(targetKey, app, auth, firestore, handle)
        handle
    }

    suspend fun signOutActiveSession(context: Context) = sessionMutex.withLock {
        val current = activeSession
        if (current != null) {
            runCatching { current.auth.signOut() }
        } else {
            val existingApp = FirebaseApp.getApps(context).firstOrNull { it.name == SECONDARY_APP_NAME }
            if (existingApp != null) {
                runCatching { FirebaseAuth.getInstance(existingApp).signOut() }
            }
        }
    }

    /**
     * Exposed for testing / cleanup when switching configs or resetting process state.
     */
    suspend fun resetForTesting(context: Context) = sessionMutex.withLock {
        val current = activeSession
        if (current != null) {
            tearDownSessionLocked(current)
            activeSession = null
        }
        FirebaseApp.getApps(context)
            .filter { it.name == SECONDARY_APP_NAME }
            .forEach { app ->
                runCatching { FirebaseFirestore.getInstance(app).terminate().await() }
                configuredAppIdentities.remove(System.identityHashCode(app))
                runCatching { app.delete() }
            }
    }

    private fun isAppDeleted(context: Context, app: FirebaseApp): Boolean {
        return FirebaseApp.getApps(context).none { it === app }
    }

    private suspend fun tearDownSessionLocked(session: ActiveSession) {
        runCatching { session.auth.signOut() }
        runCatching { session.firestore.terminate().await() }
        configuredAppIdentities.remove(System.identityHashCode(session.app))
        runCatching { session.app.delete() }
    }

    private fun initializeSecondaryApp(context: Context, key: FirebaseConfigKey): FirebaseApp {
        val options = FirebaseOptions.Builder()
            .setApiKey(key.apiKey)
            .setApplicationId(key.appId)
            .setProjectId(key.projectId)
            .apply {
                if (key.storageBucket.isNotBlank()) setStorageBucket(key.storageBucket)
                if (key.messagingSenderId.isNotBlank()) setGcmSenderId(key.messagingSenderId)
            }
            .build()
        return FirebaseApp.initializeApp(context, options, SECONDARY_APP_NAME)
    }

    private fun configureFirestoreOnceLocked(app: FirebaseApp): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance(app)
        val appIdentity = System.identityHashCode(app)
        if (configuredAppIdentities.add(appIdentity)) {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
            try {
                firestore.firestoreSettings = settings
            } catch (e: IllegalStateException) {
                // Keep app alive if Firestore was already started outside this registry
                if (e.message?.contains("already been started", ignoreCase = true) != true) {
                    configuredAppIdentities.remove(appIdentity)
                    throw e
                }
            }
        }
        return firestore
    }
}

class AndroidFirebaseCloudBackend(private val context: Context) : FirebaseCloudBackend {

    private fun CloudSessionHandle.asAndroid(): FirebaseCloudSessionRegistry.AndroidCloudSessionHandle {
        return this as FirebaseCloudSessionRegistry.AndroidCloudSessionHandle
    }

    override suspend fun ensureConfiguredSession(config: FirebaseCloudConfig): CloudSessionHandle {
        return FirebaseCloudSessionRegistry.getOrCreateConfiguredSession(context, config)
    }

    override suspend fun signInOrCreateUser(
        session: CloudSessionHandle,
        email: String,
        password: String,
        createAccountIfMissing: Boolean
    ): CloudAuthUser {
        val androidSession = session.asAndroid()
        val auth = androidSession.auth
        val trimmedEmail = email.trim()
        try {
            auth.signInWithEmailAndPassword(trimmedEmail, password).await()
        } catch (e: Exception) {
            if (createAccountIfMissing && e !is FirebaseNetworkException) {
                auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            } else {
                throw e
            }
        }
        val user = auth.currentUser ?: throw IllegalStateException("Authentication failed: no signed-in user.")
        return CloudAuthUser(uid = user.uid, email = user.email ?: trimmedEmail)
    }

    override suspend fun getCurrentUser(session: CloudSessionHandle): CloudAuthUser? {
        val user = session.asAndroid().auth.currentUser ?: return null
        return CloudAuthUser(uid = user.uid, email = user.email)
    }

    override suspend fun signOut() {
        FirebaseCloudSessionRegistry.signOutActiveSession(context)
    }

    override suspend fun writeUserMetadata(
        session: CloudSessionHandle,
        uid: String,
        email: String,
        timestamp: Long
    ) {
        session.asAndroid().firestore
            .collection("users")
            .document(uid)
            .set(
                mapOf("lastConnectedAt" to timestamp, "email" to email.trim()),
                SetOptions.merge()
            )
            .await()
    }

    override suspend fun verifyUserDocument(session: CloudSessionHandle, uid: String) {
        session.asAndroid().firestore
            .collection("users")
            .document(uid)
            .get()
            .await()
    }

    override suspend fun pullCollection(
        session: CloudSessionHandle,
        uid: String,
        collection: String
    ): List<RemoteDocumentData> {
        val snap = session.asAndroid().firestore
            .collection("users")
            .document(uid)
            .collection(collection)
            .get()
            .await()
        return snap.documents.map { doc ->
            RemoteDocumentData(id = doc.id, data = doc.data ?: emptyMap())
        }
    }

    override suspend fun batchUpsert(
        session: CloudSessionHandle,
        uid: String,
        operations: List<RemoteWriteOperation>
    ) {
        val firestore = session.asAndroid().firestore
        val userRoot = firestore.collection("users").document(uid)
        operations.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { op ->
                val docRef = userRoot.collection(op.collection).document(op.documentId)
                batch.set(docRef, op.data, SetOptions.merge())
            }
            batch.commit().await()
        }
    }
}

class FirebaseSyncManager(
    private val context: Context,
    private val db: CreditLoggerDatabase,
    private val securityStore: SecurityPreferencesStore,
    private val cloudBackend: FirebaseCloudBackend = AndroidFirebaseCloudBackend(context.applicationContext)
) {
    private val operationMutex = Mutex()

    suspend fun connectAndSignIn(
        config: FirebaseCloudConfig,
        email: String,
        password: String,
        createAccountIfMissing: Boolean
    ): Result<FirebaseCloudConfig> = operationMutex.withLock {
        val cleanEmail = email.trim()
        val normalizedConfig = config.copy(
            apiKey = config.apiKey.trim(),
            authDomain = config.authDomain.trim(),
            projectId = config.projectId.trim(),
            storageBucket = config.storageBucket.trim(),
            messagingSenderId = config.messagingSenderId.trim(),
            appId = config.appId.trim(),
            userEmail = cleanEmail
        )
        return@withLock try {
            require(cleanEmail.isNotBlank() && cleanEmail.contains("@")) {
                "Please enter a valid email address."
            }
            require(password.length >= 6) {
                "Password must be at least 6 characters."
            }

            // Step 1: Ensure single Firestore instance is created and settings applied BEFORE auth or any Firestore I/O
            val session = cloudBackend.ensureConfiguredSession(normalizedConfig)

            // Step 2: Authenticate with Firebase Auth
            val authUser = cloudBackend.signInOrCreateUser(
                session = session,
                email = cleanEmail,
                password = password,
                createAccountIfMissing = createAccountIfMissing
            )

            // Step 3: Verify Firestore read/write access on user profile document
            cloudBackend.writeUserMetadata(
                session = session,
                uid = authUser.uid,
                email = authUser.email ?: cleanEmail,
                timestamp = System.currentTimeMillis()
            )

            val connectedEmail = authUser.email ?: cleanEmail
            val updated = normalizedConfig.copy(
                userEmail = connectedEmail,
                isConnected = true,
                lastSyncStatus = "Connected as $connectedEmail",
                lastError = ""
            )
            securityStore.saveFirebaseConfig(updated)
            Result.success(updated)
        } catch (err: Exception) {
            val friendly = formatFirebaseError(err)
            securityStore.saveFirebaseConfig(
                normalizedConfig.copy(
                    isConnected = false,
                    lastSyncStatus = "Connection failed",
                    lastError = friendly
                )
            )
            Result.failure(IllegalStateException(friendly, err))
        }
    }

    suspend fun testConnection(config: FirebaseCloudConfig): Result<String> = operationMutex.withLock {
        val normalizedConfig = config.copy(
            apiKey = config.apiKey.trim(),
            authDomain = config.authDomain.trim(),
            projectId = config.projectId.trim(),
            storageBucket = config.storageBucket.trim(),
            messagingSenderId = config.messagingSenderId.trim(),
            appId = config.appId.trim()
        )
        return@withLock try {
            // Ensure Firestore settings are applied BEFORE auth check & Firestore read
            val session = cloudBackend.ensureConfiguredSession(normalizedConfig)
            val user = cloudBackend.getCurrentUser(session)
                ?: throw IllegalStateException("Not signed in. Connect with email & password first.")
            cloudBackend.verifyUserDocument(session, user.uid)
            val statusMsg = "OK: Authenticated (${user.email ?: user.uid}) & Firestore reachable"
            securityStore.saveFirebaseConfig(
                normalizedConfig.copy(
                    userEmail = user.email ?: normalizedConfig.userEmail,
                    isConnected = true,
                    lastSyncStatus = statusMsg,
                    lastError = ""
                )
            )
            Result.success(statusMsg)
        } catch (err: Exception) {
            val friendly = formatFirebaseError(err)
            val cause = err.cause ?: err
            val authMissingOrExpired = cause is FirebaseAuthException ||
                (cause is FirebaseFirestoreException && cause.code == FirebaseFirestoreException.Code.UNAUTHENTICATED) ||
                err.message?.contains("Not signed in", ignoreCase = true) == true
            securityStore.saveFirebaseConfig(
                normalizedConfig.copy(
                    isConnected = if (authMissingOrExpired) false else normalizedConfig.isConnected,
                    lastSyncStatus = "Connection test failed",
                    lastError = friendly
                )
            )
            Result.failure(IllegalStateException(friendly, err))
        }
    }

    /**
     * Idempotent bi-directional sync keyed by stable record IDs and updatedAt timestamps.
     * Newer updatedAt wins; payments are never duplicated on retry.
     * Local Room SQLite data is always preserved cleanly.
     */
    suspend fun syncNow(config: FirebaseCloudConfig): Result<FirebaseCloudConfig> = operationMutex.withLock {
        val normalizedConfig = config.copy(
            apiKey = config.apiKey.trim(),
            authDomain = config.authDomain.trim(),
            projectId = config.projectId.trim(),
            storageBucket = config.storageBucket.trim(),
            messagingSenderId = config.messagingSenderId.trim(),
            appId = config.appId.trim()
        )
        return@withLock try {
            // Step 1: Ensure Firestore settings are configured BEFORE auth check, reads, or writes
            val session = cloudBackend.ensureConfiguredSession(normalizedConfig)
            val user = cloudBackend.getCurrentUser(session)
                ?: throw IllegalStateException("Please sign in via Connect first.")
            val uid = user.uid

            // Step 2: Pull remote collections
            val remotePersons = cloudBackend.pullCollection(session, uid, "persons").mapNotNull { doc ->
                runCatching {
                    Person(
                        id = doc.id,
                        name = doc.str("name") ?: return@mapNotNull null,
                        phone = doc.str("phone") ?: "",
                        address = doc.str("address") ?: "",
                        photoRef = doc.str("photoRef") ?: "",
                        note = doc.str("note") ?: "",
                        createdAt = doc.lng("createdAt") ?: 0L,
                        updatedAt = doc.lng("updatedAt") ?: 0L,
                        archivedAt = doc.lng("archivedAt")
                    )
                }.getOrNull()
            }

            val remoteCredits = cloudBackend.pullCollection(session, uid, "credits").mapNotNull { doc ->
                runCatching {
                    Credit(
                        id = doc.id,
                        personId = doc.str("personId") ?: return@mapNotNull null,
                        creditNumber = doc.str("creditNumber") ?: return@mapNotNull null,
                        originalPrincipalPaise = doc.lng("originalPrincipalPaise") ?: 0L,
                        currentPrincipalPaise = doc.lng("currentPrincipalPaise") ?: 0L,
                        interestRate = doc.str("interestRate") ?: "0",
                        ratePeriod = RatePeriod.valueOf(doc.str("ratePeriod") ?: RatePeriod.MONTHLY.name),
                        interestMethod = InterestMethod.valueOf(doc.str("interestMethod") ?: InterestMethod.SIMPLE.name),
                        startDate = doc.str("startDate") ?: return@mapNotNull null,
                        dueDate = doc.str("dueDate"),
                        status = CreditStatus.valueOf(doc.str("status") ?: CreditStatus.ACTIVE.name),
                        note = doc.str("note") ?: "",
                        createdAt = doc.lng("createdAt") ?: 0L,
                        updatedAt = doc.lng("updatedAt") ?: 0L,
                        archivedAt = doc.lng("archivedAt")
                    )
                }.getOrNull()
            }

            val remotePayments = cloudBackend.pullCollection(session, uid, "payments").mapNotNull { doc ->
                runCatching {
                    Payment(
                        id = doc.id,
                        personId = doc.str("personId") ?: return@mapNotNull null,
                        creditId = doc.str("creditId"),
                        paymentType = PaymentType.valueOf(doc.str("paymentType") ?: PaymentType.CREDIT.name),
                        amountPaise = doc.lng("amountPaise") ?: 0L,
                        paymentDate = doc.str("paymentDate") ?: return@mapNotNull null,
                        paymentMethod = PaymentMethod.valueOf(doc.str("paymentMethod") ?: PaymentMethod.CASH.name),
                        receiptNumber = doc.str("receiptNumber") ?: doc.id,
                        allocationMode = doc.str("allocationMode")?.let { AllocationMode.valueOf(it) },
                        systemNote = doc.str("systemNote") ?: "",
                        note = doc.str("note") ?: "",
                        createdAt = doc.lng("createdAt") ?: 0L,
                        updatedAt = doc.lng("updatedAt") ?: 0L,
                        status = doc.str("status") ?: "COMPLETED",
                        reversesPaymentId = doc.str("reversesPaymentId")
                    )
                }.getOrNull()
            }

            val remoteEvents = cloudBackend.pullCollection(session, uid, "events").mapNotNull { doc ->
                runCatching {
                    CreditEvent(
                        id = doc.id,
                        personId = doc.str("personId") ?: return@mapNotNull null,
                        creditId = doc.str("creditId") ?: return@mapNotNull null,
                        eventType = CreditEventType.valueOf(doc.str("eventType") ?: CreditEventType.CREDIT_CREATED.name),
                        effectiveDate = doc.str("effectiveDate") ?: return@mapNotNull null,
                        createdAt = doc.lng("createdAt") ?: 0L,
                        updatedAt = doc.lng("updatedAt") ?: 0L,
                        principalBeforePaise = doc.lng("principalBeforePaise") ?: 0L,
                        interestBeforePaise = doc.lng("interestBeforePaise") ?: 0L,
                        misradhanBeforePaise = doc.lng("misradhanBeforePaise") ?: 0L,
                        amountPaise = doc.lng("amountPaise") ?: 0L,
                        principalAfterPaise = doc.lng("principalAfterPaise") ?: 0L,
                        interestAfterPaise = doc.lng("interestAfterPaise") ?: 0L,
                        rateAfter = doc.str("rateAfter") ?: "0",
                        ratePeriodAfter = RatePeriod.valueOf(doc.str("ratePeriodAfter") ?: RatePeriod.MONTHLY.name),
                        allocationMode = doc.str("allocationMode")?.let { AllocationMode.valueOf(it) },
                        paymentMethod = doc.str("paymentMethod")?.let { PaymentMethod.valueOf(it) },
                        receiptNumber = doc.str("receiptNumber"),
                        advancePaise = doc.lng("advancePaise") ?: 0L,
                        systemNote = doc.str("systemNote") ?: "",
                        note = doc.str("note") ?: "",
                        reversesEventId = doc.str("reversesEventId"),
                        isReversed = doc.bool("isReversed") ?: false
                    )
                }.getOrNull()
            }

            val remoteReminders = cloudBackend.pullCollection(session, uid, "reminders").mapNotNull { doc ->
                runCatching {
                    Reminder(
                        id = doc.id,
                        personId = doc.str("personId") ?: return@mapNotNull null,
                        creditId = doc.str("creditId"),
                        reminderDate = doc.str("reminderDate") ?: return@mapNotNull null,
                        reminderTime = doc.str("reminderTime") ?: "10:00",
                        note = doc.str("note") ?: "",
                        status = ReminderStatus.valueOf(doc.str("status") ?: ReminderStatus.UPCOMING.name),
                        createdAt = doc.lng("createdAt") ?: 0L,
                        updatedAt = doc.lng("updatedAt") ?: 0L
                    )
                }.getOrNull()
            }

            // Step 3: Merge remote newer records into local Room DB (never deletes existing local data)
            val localPersons = db.personDao().getAll().map { it.toDomain() }.associateBy { it.id }
            val localCredits = db.creditDao().getAll().map { it.toDomain() }.associateBy { it.id }
            val localPayments = db.paymentDao().getAll().map { it.toDomain() }.associateBy { it.id }
            val localEvents = db.creditEventDao().getAll().map { it.toDomain() }.associateBy { it.id }
            val localReminders = db.reminderDao().getAll().map { it.toDomain() }.associateBy { it.id }

            var pulledCount = 0
            db.withTransaction {
                remotePersons.forEach { rp ->
                    val lp = localPersons[rp.id]
                    if (lp == null || rp.updatedAt > lp.updatedAt) {
                        db.personDao().upsert(rp.toEntity())
                        pulledCount++
                    }
                }
                remoteCredits.forEach { rc ->
                    val lc = localCredits[rc.id]
                    if (lc == null || rc.updatedAt > lc.updatedAt) {
                        db.creditDao().upsert(rc.toEntity())
                        pulledCount++
                    }
                }
                remotePayments.forEach { rpay ->
                    val lpay = localPayments[rpay.id]
                    if (lpay == null || rpay.updatedAt > lpay.updatedAt) {
                        db.paymentDao().upsert(rpay.toEntity())
                        pulledCount++
                    }
                }
                remoteEvents.forEach { rev ->
                    val lev = localEvents[rev.id]
                    if (lev == null || rev.updatedAt > lev.updatedAt) {
                        db.creditEventDao().upsert(rev.toEntity())
                        pulledCount++
                    }
                }
                remoteReminders.forEach { rr ->
                    val lr = localReminders[rr.id]
                    if (lr == null || rr.updatedAt > lr.updatedAt) {
                        db.reminderDao().upsert(rr.toEntity())
                        pulledCount++
                    }
                }

                // Recompute balances from merged events while preserving initial credit rate & period
                val today = LedgerDate.todayLocal()
                for (c in db.creditDao().getAll().map { it.toDomain() }) {
                    val evs = db.creditEventDao().getByCreditId(c.id).map { it.toDomain() }
                    val startD = LedgerDate.parseOrNull(c.startDate) ?: today
                    val maxD = evs.mapNotNull { LedgerDate.parseOrNull(it.effectiveDate) }.maxOrNull()
                    val asOf = maxOf(today, startD, maxD ?: today)
                    val snap = InterestEngine.calculateCreditBalanceAsOfDate(c, evs, asOf)
                    db.creditDao().upsert(
                        c.copy(
                            currentPrincipalPaise = snap.currentPrincipalPaise,
                            status = snap.status
                        ).toEntity()
                    )
                }
            }

            // Step 4: Push local records that are newer or missing remotely (idempotent upsert by stable ID)
            val mergedPersons = db.personDao().getAll().map { it.toDomain() }
            val mergedCredits = db.creditDao().getAll().map { it.toDomain() }
            val mergedPayments = db.paymentDao().getAll().map { it.toDomain() }
            val mergedEvents = db.creditEventDao().getAll().map { it.toDomain() }
            val mergedReminders = db.reminderDao().getAll().map { it.toDomain() }

            val remotePersonMap = remotePersons.associateBy { it.id }
            val remoteCreditMap = remoteCredits.associateBy { it.id }
            val remotePaymentMap = remotePayments.associateBy { it.id }
            val remoteEventMap = remoteEvents.associateBy { it.id }
            val remoteReminderMap = remoteReminders.associateBy { it.id }

            val writeOps = mutableListOf<RemoteWriteOperation>()

            mergedPersons.forEach { p ->
                val rp = remotePersonMap[p.id]
                if (rp == null || p.updatedAt > rp.updatedAt) {
                    writeOps += RemoteWriteOperation(
                        collection = "persons",
                        documentId = p.id,
                        data = mapOf(
                            "name" to p.name, "phone" to p.phone, "address" to p.address,
                            "photoRef" to p.photoRef, "note" to p.note, "createdAt" to p.createdAt,
                            "updatedAt" to p.updatedAt, "archivedAt" to p.archivedAt
                        )
                    )
                }
            }
            mergedCredits.forEach { c ->
                val rc = remoteCreditMap[c.id]
                if (rc == null || c.updatedAt > rc.updatedAt) {
                    writeOps += RemoteWriteOperation(
                        collection = "credits",
                        documentId = c.id,
                        data = mapOf(
                            "personId" to c.personId, "creditNumber" to c.creditNumber,
                            "originalPrincipalPaise" to c.originalPrincipalPaise,
                            "currentPrincipalPaise" to c.currentPrincipalPaise,
                            "interestRate" to c.interestRate, "ratePeriod" to c.ratePeriod.name,
                            "interestMethod" to c.interestMethod.name, "startDate" to c.startDate,
                            "dueDate" to c.dueDate, "status" to c.status.name, "note" to c.note,
                            "createdAt" to c.createdAt, "updatedAt" to c.updatedAt, "archivedAt" to c.archivedAt
                        )
                    )
                }
            }
            mergedPayments.forEach { p ->
                val rpay = remotePaymentMap[p.id]
                if (rpay == null || p.updatedAt > rpay.updatedAt) {
                    writeOps += RemoteWriteOperation(
                        collection = "payments",
                        documentId = p.id,
                        data = mapOf(
                            "personId" to p.personId, "creditId" to p.creditId,
                            "paymentType" to p.paymentType.name, "amountPaise" to p.amountPaise,
                            "paymentDate" to p.paymentDate, "paymentMethod" to p.paymentMethod.name,
                            "receiptNumber" to p.receiptNumber, "allocationMode" to p.allocationMode?.name,
                            "systemNote" to p.systemNote, "note" to p.note, "createdAt" to p.createdAt,
                            "updatedAt" to p.updatedAt, "status" to p.status, "reversesPaymentId" to p.reversesPaymentId
                        )
                    )
                }
            }
            mergedEvents.forEach { e ->
                val rev = remoteEventMap[e.id]
                if (rev == null || e.updatedAt > rev.updatedAt) {
                    writeOps += RemoteWriteOperation(
                        collection = "events",
                        documentId = e.id,
                        data = mapOf(
                            "personId" to e.personId, "creditId" to e.creditId,
                            "eventType" to e.eventType.name, "effectiveDate" to e.effectiveDate,
                            "createdAt" to e.createdAt, "updatedAt" to e.updatedAt,
                            "principalBeforePaise" to e.principalBeforePaise,
                            "interestBeforePaise" to e.interestBeforePaise,
                            "misradhanBeforePaise" to e.misradhanBeforePaise,
                            "amountPaise" to e.amountPaise, "principalAfterPaise" to e.principalAfterPaise,
                            "interestAfterPaise" to e.interestAfterPaise, "rateAfter" to e.rateAfter,
                            "ratePeriodAfter" to e.ratePeriodAfter.name,
                            "allocationMode" to e.allocationMode?.name, "paymentMethod" to e.paymentMethod?.name,
                            "receiptNumber" to e.receiptNumber, "advancePaise" to e.advancePaise,
                            "systemNote" to e.systemNote, "note" to e.note,
                            "reversesEventId" to e.reversesEventId, "isReversed" to e.isReversed
                        )
                    )
                }
            }
            mergedReminders.forEach { r ->
                val rr = remoteReminderMap[r.id]
                if (rr == null || r.updatedAt > rr.updatedAt) {
                    writeOps += RemoteWriteOperation(
                        collection = "reminders",
                        documentId = r.id,
                        data = mapOf(
                            "personId" to r.personId, "creditId" to r.creditId,
                            "reminderDate" to r.reminderDate, "reminderTime" to r.reminderTime,
                            "note" to r.note, "status" to r.status.name,
                            "createdAt" to r.createdAt, "updatedAt" to r.updatedAt
                        )
                    )
                }
            }

            val pushedCount = writeOps.size
            if (writeOps.isNotEmpty()) {
                cloudBackend.batchUpsert(session, uid, writeOps)
            }

            val now = System.currentTimeMillis()
            val updated = normalizedConfig.copy(
                userEmail = user.email ?: normalizedConfig.userEmail,
                isConnected = true,
                lastSyncTime = now,
                lastSyncStatus = "Synced ($pulledCount pulled, $pushedCount uploaded)",
                lastError = ""
            )
            securityStore.saveFirebaseConfig(updated)
            Result.success(updated)
        } catch (err: Exception) {
            val friendly = formatFirebaseError(err)
            securityStore.saveFirebaseConfig(
                normalizedConfig.copy(
                    lastSyncStatus = "Sync error",
                    lastError = friendly
                )
            )
            Result.failure(IllegalStateException(friendly, err))
        }
    }

    suspend fun disconnect(@Suppress("UNUSED_PARAMETER") config: FirebaseCloudConfig) = operationMutex.withLock {
        runCatching { cloudBackend.signOut() }
        securityStore.clearFirebaseConnection()
    }

    private fun formatFirebaseError(err: Throwable): String {
        val cause = err.cause ?: err
        return when (cause) {
            is FirebaseAuthInvalidUserException ->
                "Account not found. Check your email or enable 'Create account if new'."
            is FirebaseAuthInvalidCredentialsException ->
                "Invalid email or password."
            is FirebaseAuthUserCollisionException ->
                "An account with this email already exists with a different password."
            is FirebaseAuthException ->
                "Authentication failed (${cause.errorCode}): ${cause.localizedMessage ?: "Unknown auth error"}"
            is FirebaseFirestoreException -> when (cause.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Firestore permission denied. Verify your Firestore Security Rules allow access for this signed-in user."
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "Firestore is currently unavailable. Check your internet connection and retry."
                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    "Authentication session expired. Please reconnect."
                else ->
                    "Firestore error (${cause.code}): ${cause.localizedMessage ?: "Unknown database error"}"
            }
            is FirebaseNetworkException ->
                "Network error connecting to Firebase. Check your internet connection."
            else ->
                err.message?.takeIf { it.isNotBlank() } ?: "Unexpected Firebase error"
        }
    }

    private fun RemoteDocumentData.str(key: String): String? = data[key] as? String
    private fun RemoteDocumentData.lng(key: String): Long? = (data[key] as? Number)?.toLong()
    private fun RemoteDocumentData.bool(key: String): Boolean? = data[key] as? Boolean
}
