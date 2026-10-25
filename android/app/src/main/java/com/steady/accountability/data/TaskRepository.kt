package com.steady.accountability.data

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepository(private val dao: TaskDao) {
    fun tasksForToday(): Flow<List<TaskEntity>> = dao.tasksForDate(LocalDate.now().toString())

    suspend fun add(title: String): Boolean {
        if (TaskValidation.titleError(title) != null) return false
        val now = Instant.now().toString()
        dao.saveTask(TaskEntity(java.util.UUID.randomUUID().toString(), title.trim(), null, LocalDate.now().toString(), null, TaskStatus.PLANNED.name, now, now))
        return true
    }

    suspend fun toggle(task: TaskEntity) {
        val next = if (task.status == TaskStatus.COMPLETED.name) TaskStatus.PLANNED.name else TaskStatus.COMPLETED.name
        dao.updateStatus(task.id, next, Instant.now().toString())
    }
}

class CheckInRepository(private val dao: CheckInDao) {
    fun forToday(): Flow<DailyCheckInEntity?> = dao.checkInForDate(LocalDate.now().toString())

    suspend fun save(morningPlan: String, eveningReflection: String, existing: DailyCheckInEntity?) {
        val now = Instant.now().toString()
        dao.saveCheckIn(DailyCheckInEntity(LocalDate.now().toString(), morningPlan.ifBlank { null }, eveningReflection.ifBlank { null }, existing?.createdAt ?: now, now))
    }
}
