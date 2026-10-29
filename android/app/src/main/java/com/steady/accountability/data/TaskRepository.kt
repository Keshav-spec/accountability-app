package com.steady.accountability.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TaskRepository(private val dao: TaskDao) {
    private val owner = MutableStateFlow("__locked__")
    fun setOwner(ownerId: String) { owner.value = ownerId }
    fun tasksForToday(): Flow<List<TaskEntity>> = owner.flatMapLatest { dao.tasksForDate(LocalDate.now().toString(), it) }
    fun allTasks(): Flow<List<TaskEntity>> = owner.flatMapLatest { dao.allTasks(it) }
    suspend fun allForSync(): List<TaskEntity> = dao.allForSync(owner.value)

    suspend fun add(title: String): Boolean {
        if (TaskValidation.titleError(title) != null) return false
        val now = Instant.now().toString()
        dao.saveTask(TaskEntity(java.util.UUID.randomUUID().toString(), title.trim(), null, LocalDate.now().toString(), null, TaskStatus.PLANNED.name, now, now, syncPending = true, ownerId = owner.value))
        return true
    }

    suspend fun save(task: TaskEntity): Boolean {
        if (TaskValidation.titleError(task.title) != null) return false
        dao.upsert(task.copy(title = task.title.trim(), updatedAt = Instant.now().toString(), syncPending = true, ownerId = owner.value))
        return true
    }

    suspend fun setStatus(task: TaskEntity, status: TaskStatus) {
        val now = Instant.now().toString()
        dao.updateStatus(task.id, owner.value, TaskRules.setStatus(status).name, now)
        val rule = task.recurrenceRule
        val series = task.seriesId ?: task.id
        val nextDate = nextDate(task.scheduledDate, rule)
        if (status == TaskStatus.PLANNED && task.status != TaskStatus.PLANNED.name && nextDate != null) {
            val occurrenceId = "$series:$nextDate"
            dao.allForSync(owner.value).firstOrNull { it.id == occurrenceId && it.status == TaskStatus.PLANNED.name }?.let { dao.softDelete(it.id, owner.value, now, now) }
        } else if (status != TaskStatus.PLANNED && nextDate != null) {
            val occurrenceId = "$series:$nextDate"
            val priorOccurrence = dao.allForSync(owner.value).firstOrNull { it.id == occurrenceId }
            if (priorOccurrence == null || priorOccurrence.deletedAt != null) dao.upsert(task.copy(id = occurrenceId, seriesId = series, scheduledDate = nextDate, status = TaskStatus.PLANNED.name, createdAt = priorOccurrence?.createdAt ?: now, updatedAt = now, deletedAt = null, syncPending = true, sortOrder = 0, ownerId = owner.value))
        }
    }

    suspend fun toggleComplete(task: TaskEntity) = setStatus(task, TaskRules.toggleComplete(task.status))

    suspend fun delete(task: TaskEntity) { val now = Instant.now().toString(); dao.softDelete(task.id, owner.value, now, now) }
    suspend fun restore(task: TaskEntity) { dao.restoreDeleted(task.id, owner.value, Instant.now().toString()) }
    suspend fun reschedule(task: TaskEntity, date: String) { dao.upsert(task.copy(scheduledDate = date, updatedAt = Instant.now().toString(), syncPending = true, ownerId = owner.value)) }
    suspend fun move(task: TaskEntity, direction: Int) {
        val sameDay = dao.allForSync(owner.value).filter { it.scheduledDate == task.scheduledDate && it.deletedAt == null }.sortedBy { it.sortOrder }
        val from = sameDay.indexOfFirst { it.id == task.id }
        val to = from + direction
        if (from < 0 || to !in sameDay.indices) return
        val now = Instant.now().toString()
        val reordered = sameDay.toMutableList().apply { add(to, removeAt(from)) }
        reordered.forEachIndexed { index, item -> dao.updateSortOrder(item.id, owner.value, index, now) }
    }
    suspend fun claimOwner(fromOwner: String, toOwner: String) { dao.moveOwner(fromOwner, toOwner) }

    private fun nextDate(date: String, rule: String): String? {
        if (rule == "none") return null
        var next = LocalDate.parse(date)
        when (rule) {
            "daily" -> next = next.plusDays(1)
            "weekly" -> next = next.plusWeeks(1)
            "weekdays" -> { do { next = next.plusDays(1) } while (next.dayOfWeek.value >= 6) }
            else -> return null
        }
        return next.toString()
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CheckInRepository(private val dao: CheckInDao) {
    private val owner = MutableStateFlow("__locked__")
    fun setOwner(ownerId: String) { owner.value = ownerId }
    fun forDate(date: String = LocalDate.now().toString()): Flow<DailyCheckInEntity?> = owner.flatMapLatest { dao.checkInForDate(date, it) }
    suspend fun allForSync(): List<DailyCheckInEntity> = dao.allForSync(owner.value)
    suspend fun all(): List<DailyCheckInEntity> = dao.all(owner.value)
    suspend fun markSynced(date: String) = dao.markSynced(date, owner.value)
    suspend fun saveRemote(value: DailyCheckInEntity) = dao.saveCheckIn(value.copy(ownerId = owner.value))
    suspend fun claimOwner(fromOwner: String, toOwner: String) { dao.moveOwner(fromOwner, toOwner) }

    suspend fun save(date: String, morningPlan: String, priorities: List<String>, progressNote: String, eveningReflection: String, outcome: String?, weeklyReflection: String, weekStart: String, existing: DailyCheckInEntity?) {
        val now = Instant.now().toString()
        dao.saveCheckIn(DailyCheckInEntity(date, morningPlan.ifBlank { null }, eveningReflection.ifBlank { null }, priorities.joinToString("\n"), progressNote.ifBlank { null }, outcome, existing?.createdAt ?: now, now, syncPending = true, ownerId = owner.value, weeklyReflection = if (date == weekStart) weeklyReflection.ifBlank { null } else existing?.weeklyReflection))
        if (date != weekStart) {
            val existingWeek = dao.getCheckIn(weekStart, owner.value)
            dao.saveCheckIn(DailyCheckInEntity(weekStart, existingWeek?.morningPlan, existingWeek?.eveningReflection, existingWeek?.prioritiesText.orEmpty(), existingWeek?.progressNote, existingWeek?.dayOutcome, existingWeek?.createdAt ?: now, now, syncPending = true, ownerId = owner.value, weeklyReflection = weeklyReflection.ifBlank { null }))
        }
    }
}

class ReminderSettingsRepository(private val dao: ReminderSettingsDao) {
    fun observe(): Flow<ReminderSettingsEntity?> = dao.observe()
    suspend fun get(): ReminderSettingsEntity = dao.get() ?: ReminderSettingsEntity().also { dao.save(it) }
    suspend fun save(settings: ReminderSettingsEntity) = dao.save(settings)
}
