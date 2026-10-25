package com.steady.accountability.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val scheduledDate: String,
    val targetTime: String?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    @ColumnInfo(defaultValue = "0") val syncPending: Boolean = false,
)
