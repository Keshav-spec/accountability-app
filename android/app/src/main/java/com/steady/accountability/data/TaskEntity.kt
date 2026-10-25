package com.steady.accountability.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
)
