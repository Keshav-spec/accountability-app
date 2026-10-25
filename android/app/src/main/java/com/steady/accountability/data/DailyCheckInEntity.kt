package com.steady.accountability.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_check_ins")
data class DailyCheckInEntity(
    @PrimaryKey val date: String,
    val morningPlan: String?,
    val eveningReflection: String?,
    val createdAt: String,
    val updatedAt: String,
)
