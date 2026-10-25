package com.steady.accountability.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "daily_check_ins")
data class DailyCheckInEntity(
    @PrimaryKey val date: String,
    val morningPlan: String?,
    val eveningReflection: String?,
    @ColumnInfo(defaultValue = "''") val prioritiesText: String = "",
    val progressNote: String? = null,
    val dayOutcome: String? = null,
    val createdAt: String,
    val updatedAt: String,
)
