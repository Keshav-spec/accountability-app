package com.steady.accountability.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TaskEntity::class, DailyCheckInEntity::class, ReminderSettingsEntity::class, ReminderHistoryEntity::class, UsageTrackingEntity::class, UsageAppSettingEntity::class, UsageDailyAggregateEntity::class], version = 5, exportSchema = false)
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
    }
}
