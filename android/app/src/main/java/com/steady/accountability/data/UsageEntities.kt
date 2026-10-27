package com.steady.accountability.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "usage_tracking")
data class UsageTrackingEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = false,
)

@Entity(tableName = "usage_app_settings")
data class UsageAppSettingEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val included: Boolean = false,
    val dailyLimitMinutes: Int? = null,
)

@Entity(tableName = "usage_daily_aggregates", primaryKeys = ["usageDate", "packageName"], indices = [Index(value = ["usageDate"])])
data class UsageDailyAggregateEntity(
    val usageDate: String,
    val packageName: String,
    val totalMillis: Long,
)

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_tracking WHERE id = 1")
    fun observeTracking(): Flow<UsageTrackingEntity?>

    @Query("SELECT * FROM usage_tracking WHERE id = 1")
    suspend fun getTracking(): UsageTrackingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTracking(value: UsageTrackingEntity)

    @Query("SELECT * FROM usage_app_settings ORDER BY displayName COLLATE NOCASE")
    fun observeApps(): Flow<List<UsageAppSettingEntity>>

    @Query("SELECT * FROM usage_app_settings")
    suspend fun getApps(): List<UsageAppSettingEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppsIfMissing(values: List<UsageAppSettingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveApp(value: UsageAppSettingEntity)

    @Query("DELETE FROM usage_daily_aggregates WHERE usageDate = :date")
    suspend fun deleteAggregatesForDate(date: String)

    @Query("DELETE FROM usage_daily_aggregates")
    suspend fun deleteAllAggregates()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAggregates(values: List<UsageDailyAggregateEntity>)

    @Query("SELECT * FROM usage_daily_aggregates WHERE usageDate = :date")
    suspend fun getAggregatesForDate(date: String): List<UsageDailyAggregateEntity>
}
