package com.steady.accountability.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TaskEntity::class, DailyCheckInEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun checkInDao(): CheckInDao
}
