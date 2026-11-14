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
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirebaseSyncManager(
    private val context: Context,
    private val db: CreditLoggerDatabase,
    private val securityStore: SecurityPreferencesStore
) {
    companion object {
        private const val SECONDARY_APP_NAME = "credit_logger_user_cloud"
    }

    private fun getOrInitApp(config: FirebaseCloudConfig): FirebaseApp {
        require(config.apiKey.isNotBlank()) { "Firebase API Key is required" }
        require(config.projectId.isNotBlank()) { "Firebase Project ID is required" }
        require(config.appId.isNotBlank()) { "Firebase App ID is required" }

        val existing = FirebaseApp.getApps(context).firstOrNull { it.name == SECONDARY_APP_NAME }
        if (existing != null) {
            val opts = existing.options
            if (opts.apiKey == config.apiKey && opts.projectId == config.projectId && opts.applicationId == config.appId) {
                return existing
            }
            existing.delete()
        }

        val options = FirebaseOptions.Builder()
            .setApiKey(config.apiKey.trim())
            .setApplicationId(config.appId.trim())
            .setProjectId(config.projectId.trim())
            .apply {
                if (config.storageBucket.isNotBlank()) setStorageBucket(config.storageBucket.trim())
                if (config.messagingSenderId.isNotBlank()) setGcmSenderId(config.messagingSenderId.trim())
            }
            .build()

        return FirebaseApp.initializeApp(context, options, SECONDARY_APP_NAME)
    }

    suspend fun connectAndSignIn(
        config: FirebaseCloudConfig,
        email: String,
        password: String,
        createAccountIfMissing: Boolean
    ): Result<FirebaseCloudConfig> = runCatching {
        val app = getOrInitApp(config)
        val auth = FirebaseAuth.getInstance(app)
        val firestore = FirebaseFirestore.getInstance(app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
        }

        try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
        } catch (e: Exception) {
            if (createAccountIfMissing) {
                auth.createUserWithEmailAndPassword(email.trim(), password).await()
            } else {
                throw e
            }
        }

        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Authentication failed: no UID")
        // Verify Firestore access under per-user document
        firestore.collection("users").document(uid).set(
            mapOf("lastConnectedAt" to System.currentTimeMillis(), "email" to email.trim()),
            SetOptions.merge()
        ).await()

        val updated = config.copy(
            userEmail = email.trim(),
            isConnected = true,
            lastSyncStatus = "Connected as ${email.trim()}",
            lastError = ""
        )
        securityStore.saveFirebaseConfig(updated)
        updated
    }.onFailure { err ->
        securityStore.saveFirebaseConfig(
            config.copy(
                isConnected = false,
                lastSyncStatus = "Connection failed",
                lastError = err.message ?: "Unknown error"
            )
        )
    }

    suspend fun testConnection(config: FirebaseCloudConfig): Result<String> = runCatching {
        val app = getOrInitApp(config)
        val auth = FirebaseAuth.getInstance(app)
        val user = auth.currentUser ?: throw IllegalStateException("Not signed in. Connect with email & password first.")
        val firestore = FirebaseFirestore.getInstance(app)
        firestore.collection("users").document(user.uid).get().await()
        "OK: Authenticated (${user.email ?: user.uid}) & Firestore reachable"
    }

    /**
     * Idempotent bi-directional sync keyed by stable record IDs and updatedAt timestamps.
     * Newer updatedAt wins; payments are never duplicated on retry.
     */
    suspend fun syncNow(config: FirebaseCloudConfig): Result<FirebaseCloudConfig> = runCatching {
        val app = getOrInitApp(config)
        val auth = FirebaseAuth.getInstance(app)
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Please sign in via Connect first.")
        val firestore = FirebaseFirestore.getInstance(app)
        val userRoot = firestore.collection("users").document(uid)

        // 1. Pull remote collections
        val remotePersons = userRoot.collection("persons").get().await().documents.mapNotNull { doc ->
            runCatching {
                Person(
                    id = doc.id,
                    name = doc.getString("name") ?: return@mapNotNull null,
                    phone = doc.getString("phone") ?: "",
                    address = doc.getString("address") ?: "",
                    photoRef = doc.getString("photoRef") ?: "",
                    note = doc.getString("note") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0L,
                    updatedAt = doc.getLong("updatedAt") ?: 0L,
                    archivedAt = doc.getLong("archivedAt")
                )
            }.getOrNull()
        }

        val remoteCredits = userRoot.collection("credits").get().await().documents.mapNotNull { doc ->
            runCatching {
                Credit(
                    id = doc.id,
                    personId = doc.getString("personId") ?: return@mapNotNull null,
                    creditNumber = doc.getString("creditNumber") ?: return@mapNotNull null,
                    originalPrincipalPaise = doc.getLong("originalPrincipalPaise") ?: 0L,
                    currentPrincipalPaise = doc.getLong("currentPrincipalPaise") ?: 0L,
                    interestRate = doc.getString("interestRate") ?: "0",
                    ratePeriod = RatePeriod.valueOf(doc.getString("ratePeriod") ?: RatePeriod.MONTHLY.name),
                    interestMethod = InterestMethod.valueOf(doc.getString("interestMethod") ?: InterestMethod.SIMPLE.name),
                    startDate = doc.getString("startDate") ?: return@mapNotNull null,
                    dueDate = doc.getString("dueDate"),
                    status = CreditStatus.valueOf(doc.getString("status") ?: CreditStatus.ACTIVE.name),
                    note = doc.getString("note") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0L,
                    updatedAt = doc.getLong("updatedAt") ?: 0L,
                    archivedAt = doc.getLong("archivedAt")
                )
            }.getOrNull()
        }

        val remotePayments = userRoot.collection("payments").get().await().documents.mapNotNull { doc ->
            runCatching {
                Payment(
                    id = doc.id,
                    personId = doc.getString("personId") ?: return@mapNotNull null,
                    creditId = doc.getString("creditId"),
                    paymentType = PaymentType.valueOf(doc.getString("paymentType") ?: PaymentType.CREDIT.name),
                    amountPaise = doc.getLong("amountPaise") ?: 0L,
                    paymentDate = doc.getString("paymentDate") ?: return@mapNotNull null,
                    paymentMethod = PaymentMethod.valueOf(doc.getString("paymentMethod") ?: PaymentMethod.CASH.name),
                    receiptNumber = doc.getString("receiptNumber") ?: doc.id,
                    allocationMode = doc.getString("allocationMode")?.let { AllocationMode.valueOf(it) },
                    systemNote = doc.getString("systemNote") ?: "",
                    note = doc.getString("note") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0L,
                    updatedAt = doc.getLong("updatedAt") ?: 0L,
                    status = doc.getString("status") ?: "COMPLETED",
                    reversesPaymentId = doc.getString("reversesPaymentId")
                )
            }.getOrNull()
        }

        val remoteEvents = userRoot.collection("events").get().await().documents.mapNotNull { doc ->
            runCatching {
                CreditEvent(
                    id = doc.id,
                    personId = doc.getString("personId") ?: return@mapNotNull null,
                    creditId = doc.getString("creditId") ?: return@mapNotNull null,
                    eventType = CreditEventType.valueOf(doc.getString("eventType") ?: CreditEventType.CREDIT_CREATED.name),
                    effectiveDate = doc.getString("effectiveDate") ?: return@mapNotNull null,
                    createdAt = doc.getLong("createdAt") ?: 0L,
                    updatedAt = doc.getLong("updatedAt") ?: 0L,
                    principalBeforePaise = doc.getLong("principalBeforePaise") ?: 0L,
                    interestBeforePaise = doc.getLong("interestBeforePaise") ?: 0L,
                    misradhanBeforePaise = doc.getLong("misradhanBeforePaise") ?: 0L,
                    amountPaise = doc.getLong("amountPaise") ?: 0L,
                    principalAfterPaise = doc.getLong("principalAfterPaise") ?: 0L,
                    interestAfterPaise = doc.getLong("interestAfterPaise") ?: 0L,
                    rateAfter = doc.getString("rateAfter") ?: "0",
                    ratePeriodAfter = RatePeriod.valueOf(doc.getString("ratePeriodAfter") ?: RatePeriod.MONTHLY.name),
                    allocationMode = doc.getString("allocationMode")?.let { AllocationMode.valueOf(it) },
                    paymentMethod = doc.getString("paymentMethod")?.let { PaymentMethod.valueOf(it) },
                    receiptNumber = doc.getString("receiptNumber"),
                    advancePaise = doc.getLong("advancePaise") ?: 0L,
                    systemNote = doc.getString("systemNote") ?: "",
                    note = doc.getString("note") ?: "",
                    reversesEventId = doc.getString("reversesEventId"),
                    isReversed = doc.getBoolean("isReversed") ?: false
                )
            }.getOrNull()
        }

        val remoteReminders = userRoot.collection("reminders").get().await().documents.mapNotNull { doc ->
            runCatching {
                Reminder(
                    id = doc.id,
                    personId = doc.getString("personId") ?: return@mapNotNull null,
                    creditId = doc.getString("creditId"),
                    reminderDate = doc.getString("reminderDate") ?: return@mapNotNull null,
                    reminderTime = doc.getString("reminderTime") ?: "10:00",
                    note = doc.getString("note") ?: "",
                    status = ReminderStatus.valueOf(doc.getString("status") ?: ReminderStatus.UPCOMING.name),
                    createdAt = doc.getLong("createdAt") ?: 0L,
                    updatedAt = doc.getLong("updatedAt") ?: 0L
                )
            }.getOrNull()
        }

        // 2. Merge remote newer records into local Room DB
        val localPersons = db.personDao().getAll().map { it.toDomain() }.associateBy { it.id }
        val localCredits = db.creditDao().getAll().map { it.toDomain() }.associateBy { it.id }
        val localPayments = db.paymentDao().getAll().map { it.toDomain() }.associateBy { it.id }
        val localEvents = db.creditEventDao().getAll().map { it.toDomain() }.associateBy { it.id }
        val localReminders = db.reminderDao().getAll().map { it.toDomain() }.associateBy { it.id }

        db.withTransaction {
            remotePersons.forEach { rp ->
                val lp = localPersons[rp.id]
                if (lp == null || rp.updatedAt > lp.updatedAt) db.personDao().upsert(rp.toEntity())
            }
            remoteCredits.forEach { rc ->
                val lc = localCredits[rc.id]
                if (lc == null || rc.updatedAt > lc.updatedAt) db.creditDao().upsert(rc.toEntity())
            }
            remotePayments.forEach { rpay ->
                val lpay = localPayments[rpay.id]
                if (lpay == null || rpay.updatedAt > lpay.updatedAt) db.paymentDao().upsert(rpay.toEntity())
            }
            remoteEvents.forEach { rev ->
                val lev = localEvents[rev.id]
                if (lev == null || rev.updatedAt > lev.updatedAt) db.creditEventDao().upsert(rev.toEntity())
            }
            remoteReminders.forEach { rr ->
                val lr = localReminders[rr.id]
                if (lr == null || rr.updatedAt > lr.updatedAt) db.reminderDao().upsert(rr.toEntity())
            }

            // Recompute balances from merged events
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

        // 3. Push local records that are newer or missing remotely (idempotent set by stable ID)
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

        val writeOps = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any?>>>()

        mergedPersons.forEach { p ->
            val rp = remotePersonMap[p.id]
            if (rp == null || p.updatedAt > rp.updatedAt) {
                writeOps += userRoot.collection("persons").document(p.id) to mapOf(
                    "name" to p.name, "phone" to p.phone, "address" to p.address,
                    "photoRef" to p.photoRef, "note" to p.note, "createdAt" to p.createdAt,
                    "updatedAt" to p.updatedAt, "archivedAt" to p.archivedAt
                )
            }
        }
        mergedCredits.forEach { c ->
            val rc = remoteCreditMap[c.id]
            if (rc == null || c.updatedAt > rc.updatedAt) {
                writeOps += userRoot.collection("credits").document(c.id) to mapOf(
                    "personId" to c.personId, "creditNumber" to c.creditNumber,
                    "originalPrincipalPaise" to c.originalPrincipalPaise,
                    "currentPrincipalPaise" to c.currentPrincipalPaise,
                    "interestRate" to c.interestRate, "ratePeriod" to c.ratePeriod.name,
                    "interestMethod" to c.interestMethod.name, "startDate" to c.startDate,
                    "dueDate" to c.dueDate, "status" to c.status.name, "note" to c.note,
                    "createdAt" to c.createdAt, "updatedAt" to c.updatedAt, "archivedAt" to c.archivedAt
                )
            }
        }
        mergedPayments.forEach { p ->
            val rpay = remotePaymentMap[p.id]
            if (rpay == null || p.updatedAt > rpay.updatedAt) {
                writeOps += userRoot.collection("payments").document(p.id) to mapOf(
                    "personId" to p.personId, "creditId" to p.creditId,
                    "paymentType" to p.paymentType.name, "amountPaise" to p.amountPaise,
                    "paymentDate" to p.paymentDate, "paymentMethod" to p.paymentMethod.name,
                    "receiptNumber" to p.receiptNumber, "allocationMode" to p.allocationMode?.name,
                    "systemNote" to p.systemNote, "note" to p.note, "createdAt" to p.createdAt,
                    "updatedAt" to p.updatedAt, "status" to p.status, "reversesPaymentId" to p.reversesPaymentId
                )
            }
        }
        mergedEvents.forEach { e ->
            val rev = remoteEventMap[e.id]
            if (rev == null || e.updatedAt > rev.updatedAt) {
                writeOps += userRoot.collection("events").document(e.id) to mapOf(
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
            }
        }
        mergedReminders.forEach { r ->
            val rr = remoteReminderMap[r.id]
            if (rr == null || r.updatedAt > rr.updatedAt) {
                writeOps += userRoot.collection("reminders").document(r.id) to mapOf(
                    "personId" to r.personId, "creditId" to r.creditId,
                    "reminderDate" to r.reminderDate, "reminderTime" to r.reminderTime,
                    "note" to r.note, "status" to r.status.name,
                    "createdAt" to r.createdAt, "updatedAt" to r.updatedAt
                )
            }
        }
        val pushedCount = writeOps.size
        writeOps.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (docRef, data) ->
                batch.set(docRef, data, SetOptions.merge())
            }
            batch.commit().await()
        }

        val now = System.currentTimeMillis()
        val updated = config.copy(
            isConnected = true,
            lastSyncTime = now,
            lastSyncStatus = "Synced ($pushedCount uploaded)",
            lastError = ""
        )
        securityStore.saveFirebaseConfig(updated)
        updated
    }.onFailure { err ->
        securityStore.saveFirebaseConfig(
            config.copy(
                lastSyncStatus = "Sync error",
                lastError = err.message ?: "Unknown sync error"
            )
        )
    }

    suspend fun disconnect(config: FirebaseCloudConfig) {
        runCatching {
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == SECONDARY_APP_NAME }
            if (app != null) {
                FirebaseAuth.getInstance(app).signOut()
                app.delete()
            }
        }
        securityStore.clearFirebaseConnection()
    }
}
