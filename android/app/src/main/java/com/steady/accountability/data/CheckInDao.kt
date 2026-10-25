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
}
