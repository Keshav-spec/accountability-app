package com.steady.accountability.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.util.UUID

enum class TaskStatus { PLANNED, COMPLETED, SKIPPED }

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String? = null,
    val scheduledDate: LocalDate = LocalDate.now(),
    val targetTime: LocalTime? = null,
    val status: TaskStatus = TaskStatus.PLANNED,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
)

data class DailyCheckIn(
    val date: LocalDate,
    val priorities: List<String> = emptyList(),
    val intention: String? = null,
    val progressNote: String? = null,
    val eveningReflection: String? = null,
    val dayOutcome: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
)

object TaskValidation {
    fun titleError(title: String): String? = when {
        title.isBlank() -> "Add a task title to continue."
        title.length > 120 -> "Keep the title to 120 characters or fewer."
        else -> null
    }
}
