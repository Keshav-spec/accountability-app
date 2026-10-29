package com.steady.accountability.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TaskEntity::class, DailyCheckInEntity::class, ReminderSettingsEntity::class, ReminderHistoryEntity::class, UsageTrackingEntity::class, UsageAppSettingEntity::class, UsageDailyAggregateEntity::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun checkInDao(): CheckInDao
    abstract fun reminderSettingsDao(): ReminderSettingsDao
    abstract fun usageDao(): UsageDao
    abstract fun reminderHistoryDao(): ReminderHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_check_ins ADD COLUMN prioritiesText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE daily_check_ins ADD COLUMN progressNote TEXT")
                db.execSQL("ALTER TABLE daily_check_ins ADD COLUMN dayOutcome TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder_settings (id INTEGER NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, timesText TEXT NOT NULL, quietStart TEXT NOT NULL, quietEnd TEXT NOT NULL, dailyLimit INTEGER NOT NULL, snoozeMinutes INTEGER NOT NULL, stoppedDate TEXT, snoozeUntilMillis INTEGER, deliveredDate TEXT, deliveredCount INTEGER NOT NULL, deliveredSlots TEXT NOT NULL)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN syncPending INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_check_ins ADD COLUMN deletedAt TEXT")
                db.execSQL("ALTER TABLE daily_check_ins ADD COLUMN syncPending INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS usage_tracking (id INTEGER NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS usage_app_settings (packageName TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, included INTEGER NOT NULL, dailyLimitMinutes INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS usage_daily_aggregates (usageDate TEXT NOT NULL, packageName TEXT NOT NULL, totalMillis INTEGER NOT NULL, PRIMARY KEY(usageDate, packageName))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_daily_aggregates_usageDate ON usage_daily_aggregates(usageDate)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder_history (id TEXT NOT NULL PRIMARY KEY, occurredAtMillis INTEGER NOT NULL, reason TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_history_occurredAtMillis ON reminder_history(occurredAtMillis)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN ownerId TEXT NOT NULL DEFAULT 'local'")
                db.execSQL("CREATE TABLE daily_check_ins_new (date TEXT NOT NULL, morningPlan TEXT, eveningReflection TEXT, prioritiesText TEXT NOT NULL DEFAULT '', progressNote TEXT, dayOutcome TEXT, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, deletedAt TEXT, syncPending INTEGER NOT NULL DEFAULT 0, ownerId TEXT NOT NULL DEFAULT 'local', PRIMARY KEY(date, ownerId))")
                db.execSQL("INSERT INTO daily_check_ins_new (date, morningPlan, eveningReflection, prioritiesText, progressNote, dayOutcome, createdAt, updatedAt, deletedAt, syncPending, ownerId) SELECT date, morningPlan, eveningReflection, prioritiesText, progressNote, dayOutcome, createdAt, updatedAt, deletedAt, syncPending, 'local' FROM daily_check_ins")
                db.execSQL("DROP TABLE daily_check_ins")
                db.execSQL("ALTER TABLE daily_check_ins_new RENAME TO daily_check_ins")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT 'General'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceRule TEXT NOT NULL DEFAULT 'none'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN seriesId TEXT")
                db.execSQL("ALTER TABLE daily_check_ins ADD COLUMN weeklyReflection TEXT")
                db.execSQL("ALTER TABLE reminder_settings ADD COLUMN reminderContext TEXT NOT NULL DEFAULT 'One small step is enough.'")
            }
        }
    }
}
