package com.steady.accountability.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TaskEntity::class, DailyCheckInEntity::class, ReminderSettingsEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun checkInDao(): CheckInDao
    abstract fun reminderSettingsDao(): ReminderSettingsDao

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
    }
}
