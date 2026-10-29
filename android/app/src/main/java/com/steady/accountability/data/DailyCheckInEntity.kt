package com.steady.accountability.data

import androidx.room.Entity
import androidx.room.ColumnInfo

@Entity(tableName = "daily_check_ins", primaryKeys = ["date", "ownerId"])
data class DailyCheckInEntity(
    val date: String,
    val morningPlan: String?,
    val eveningReflection: String?,
    @ColumnInfo(defaultValue = "''") val prioritiesText: String = "",
    val progressNote: String? = null,
    val dayOutcome: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    @ColumnInfo(defaultValue = "0") val syncPending: Boolean = false,
    @ColumnInfo(defaultValue = "'local'") val ownerId: String = LOCAL_OWNER_ID,
    val weeklyReflection: String? = null,
)
