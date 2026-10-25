package com.steady.accountability.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reminder_settings")
data class ReminderSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = false,
    val timesText: String = "09:00,13:00,18:00",
    val quietStart: String = "22:00",
    val quietEnd: String = "08:00",
    val dailyLimit: Int = 3,
    val snoozeMinutes: Int = 10,
    val stoppedDate: String? = null,
    val snoozeUntilMillis: Long? = null,
    val deliveredDate: String? = null,
    val deliveredCount: Int = 0,
    val deliveredSlots: String = "",
)

@Dao
interface ReminderSettingsDao {
    @Query("SELECT * FROM reminder_settings WHERE id = 1")
    fun observe(): Flow<ReminderSettingsEntity?>

    @Query("SELECT * FROM reminder_settings WHERE id = 1")
    suspend fun get(): ReminderSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: ReminderSettingsEntity)
}
