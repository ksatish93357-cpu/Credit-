package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons ORDER BY updatedAt DESC")
    suspend fun getAll(): List<PersonEntity>

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getById(id: String): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(persons: List<PersonEntity>)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM persons")
    suspend fun deleteAll()
}

@Dao
interface CreditDao {
    @Query("SELECT * FROM credits ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CreditEntity>>

    @Query("SELECT * FROM credits ORDER BY createdAt DESC")
    suspend fun getAll(): List<CreditEntity>

    @Query("SELECT * FROM credits WHERE id = :id")
    suspend fun getById(id: String): CreditEntity?

    @Query("SELECT * FROM credits WHERE creditNumber = :creditNumber LIMIT 1")
    suspend fun getByCreditNumber(creditNumber: String): CreditEntity?

    @Query("SELECT * FROM credits WHERE personId = :personId ORDER BY createdAt DESC")
    suspend fun getByPersonId(personId: String): List<CreditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(credit: CreditEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(credits: List<CreditEntity>)

    @Query("DELETE FROM credits WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM credits WHERE personId = :personId")
    suspend fun deleteByPersonId(personId: String)

    @Query("DELETE FROM credits")
    suspend fun deleteAll()
}

@Dao
interface CreditEventDao {
    @Query("SELECT * FROM credit_events ORDER BY effectiveDate DESC, createdAt DESC")
    fun observeAll(): Flow<List<CreditEventEntity>>

    @Query("SELECT * FROM credit_events ORDER BY effectiveDate ASC, createdAt ASC")
    suspend fun getAll(): List<CreditEventEntity>

    @Query("SELECT * FROM credit_events WHERE creditId = :creditId ORDER BY effectiveDate ASC, createdAt ASC")
    suspend fun getByCreditId(creditId: String): List<CreditEventEntity>

    @Query("SELECT * FROM credit_events WHERE id = :id")
    suspend fun getById(id: String): CreditEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: CreditEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<CreditEventEntity>)

    @Query("DELETE FROM credit_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM credit_events WHERE creditId = :creditId")
    suspend fun deleteByCreditId(creditId: String)

    @Query("DELETE FROM credit_events WHERE reversesEventId = :eventId")
    suspend fun deleteReversalsForEvent(eventId: String)

    @Query("DELETE FROM credit_events WHERE personId = :personId")
    suspend fun deleteByPersonId(personId: String)

    @Query("DELETE FROM credit_events")
    suspend fun deleteAll()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY paymentDate DESC, createdAt DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY paymentDate DESC, createdAt DESC")
    suspend fun getAll(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE receiptNumber = :receiptNumber LIMIT 1")
    suspend fun getByReceiptNumber(receiptNumber: String): PaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payment: PaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(payments: List<PaymentEntity>)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM payments WHERE creditId = :creditId")
    suspend fun deleteByCreditId(creditId: String)

    @Query("DELETE FROM payments WHERE personId = :personId")
    suspend fun deleteByPersonId(personId: String)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}

@Dao
interface AdjustmentDao {
    @Query("SELECT * FROM adjustments ORDER BY effectiveDate DESC, createdAt DESC")
    fun observeAll(): Flow<List<AdjustmentEntity>>

    @Query("SELECT * FROM adjustments ORDER BY effectiveDate DESC, createdAt DESC")
    suspend fun getAll(): List<AdjustmentEntity>

    @Query("SELECT * FROM adjustments WHERE id = :id")
    suspend fun getById(id: String): AdjustmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(adjustment: AdjustmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(adjustments: List<AdjustmentEntity>)

    @Query("DELETE FROM adjustments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM adjustments WHERE creditId = :creditId")
    suspend fun deleteByCreditId(creditId: String)

    @Query("DELETE FROM adjustments WHERE personId = :personId")
    suspend fun deleteByPersonId(personId: String)

    @Query("DELETE FROM adjustments")
    suspend fun deleteAll()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    suspend fun getAll(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AuditLogEntity>)

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY reminderDate ASC, reminderTime ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY reminderDate ASC, reminderTime ASC")
    suspend fun getAll(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reminders WHERE creditId = :creditId")
    suspend fun deleteByCreditId(creditId: String)

    @Query("DELETE FROM reminders WHERE personId = :personId")
    suspend fun deleteByPersonId(personId: String)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 'default' LIMIT 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 'default' LIMIT 1")
    suspend fun getSettings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}
