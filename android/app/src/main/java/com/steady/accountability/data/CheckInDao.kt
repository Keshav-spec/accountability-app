package com.steady.accountability.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Query("SELECT * FROM daily_check_ins WHERE date = :date AND ownerId = :ownerId")
    fun checkInForDate(date: String, ownerId: String): Flow<DailyCheckInEntity?>
    @Query("SELECT * FROM daily_check_ins WHERE date = :date AND ownerId = :ownerId")
    suspend fun getCheckIn(date: String, ownerId: String): DailyCheckInEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCheckIn(checkIn: DailyCheckInEntity)

    @Query("SELECT * FROM daily_check_ins WHERE ownerId = :ownerId")
    suspend fun all(ownerId: String): List<DailyCheckInEntity>

    @Query("SELECT * FROM daily_check_ins WHERE ownerId = :ownerId") suspend fun allForSync(ownerId: String): List<DailyCheckInEntity>
    @Query("UPDATE daily_check_ins SET syncPending = 0 WHERE date = :date AND ownerId = :ownerId") suspend fun markSynced(date: String, ownerId: String)
    @Query("UPDATE daily_check_ins SET ownerId = :toOwner WHERE ownerId = :fromOwner") suspend fun moveOwner(fromOwner: String, toOwner: String)
}
