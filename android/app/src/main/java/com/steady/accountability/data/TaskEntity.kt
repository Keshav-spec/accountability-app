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
    @ColumnInfo(defaultValue = "'local'") val ownerId: String = LOCAL_OWNER_ID,
    @ColumnInfo(defaultValue = "'General'") val category: String = "General",
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "'none'") val recurrenceRule: String = "none",
    val seriesId: String? = null,
)

const val LOCAL_OWNER_ID = "local"
