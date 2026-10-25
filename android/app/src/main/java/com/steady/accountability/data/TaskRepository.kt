package com.steady.accountability.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {
    fun tasksForToday(): Flow<List<TaskEntity>> = dao.tasksForDate(LocalDate.now().toString())
    fun allTasks(): Flow<List<TaskEntity>> = dao.allTasks()

    suspend fun add(title: String): Boolean {
        if (TaskValidation.titleError(title) != null) return false
        val now = Instant.now().toString()
        dao.saveTask(TaskEntity(java.util.UUID.randomUUID().toString(), title.trim(), null, LocalDate.now().toString(), null, TaskStatus.PLANNED.name, now, now, syncPending = true))
        return true
    }

    suspend fun save(task: TaskEntity): Boolean {
        if (TaskValidation.titleError(task.title) != null) return false
        dao.upsert(task.copy(title = task.title.trim(), updatedAt = Instant.now().toString(), syncPending = true))
        return true
    }

    suspend fun setStatus(task: TaskEntity, status: TaskStatus) {
        dao.updateStatus(task.id, TaskRules.setStatus(status).name, Instant.now().toString())
    }

    suspend fun toggleComplete(task: TaskEntity) = setStatus(task, TaskRules.toggleComplete(task.status))

    suspend fun delete(task: TaskEntity) { val now = Instant.now().toString(); dao.softDelete(task.id, now, now) }
}

class CheckInRepository(private val dao: CheckInDao) {
    fun forDate(date: String = LocalDate.now().toString()): Flow<DailyCheckInEntity?> = dao.checkInForDate(date)

    suspend fun save(date: String, morningPlan: String, priorities: List<String>, progressNote: String, eveningReflection: String, outcome: String?, existing: DailyCheckInEntity?) {
        val now = Instant.now().toString()
        dao.saveCheckIn(DailyCheckInEntity(date, morningPlan.ifBlank { null }, eveningReflection.ifBlank { null }, priorities.joinToString("\n"), progressNote.ifBlank { null }, outcome, existing?.createdAt ?: now, now, syncPending = true))
    }
}

class ReminderSettingsRepository(private val dao: ReminderSettingsDao) {
    fun observe(): Flow<ReminderSettingsEntity?> = dao.observe()
    suspend fun get(): ReminderSettingsEntity = dao.get() ?: ReminderSettingsEntity().also { dao.save(it) }
    suspend fun save(settings: ReminderSettingsEntity) = dao.save(settings)
}
