package com.steady.accountability.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Query("SELECT * FROM daily_check_ins WHERE date = :date")
    fun checkInForDate(date: String): Flow<DailyCheckInEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCheckIn(checkIn: DailyCheckInEntity)

    @Query("SELECT * FROM daily_check_ins")
    suspend fun all(): List<DailyCheckInEntity>

    @Query("SELECT * FROM daily_check_ins") suspend fun allForSync(): List<DailyCheckInEntity>
    @Query("UPDATE daily_check_ins SET syncPending = 0 WHERE date = :date") suspend fun markSynced(date: String)
}
