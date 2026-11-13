package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PersonEntity::class,
        CreditEntity::class,
        CreditEventEntity::class,
        PaymentEntity::class,
        AdjustmentEntity::class,
        AuditLogEntity::class,
        ReminderEntity::class,
        SettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CreditLoggerDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun creditDao(): CreditDao
    abstract fun creditEventDao(): CreditEventDao
    abstract fun paymentDao(): PaymentDao
    abstract fun adjustmentDao(): AdjustmentDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun reminderDao(): ReminderDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val DATABASE_NAME = "credit_logger_ledger.db"

        @Volatile
        private var INSTANCE: CreditLoggerDatabase? = null

        fun getInstance(context: Context): CreditLoggerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CreditLoggerDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
