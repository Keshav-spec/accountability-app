package com.steady.accountability.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE scheduledDate = :date AND ownerId = :ownerId AND deletedAt IS NULL ORDER BY sortOrder, createdAt")
    fun tasksForDate(date: String, ownerId: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt, syncPending = 1 WHERE id = :id AND ownerId = :ownerId")
    suspend fun updateStatus(id: String, ownerId: String, status: String, updatedAt: String)
    @Query("UPDATE tasks SET sortOrder = :sortOrder, updatedAt = :updatedAt, syncPending = 1 WHERE id = :id AND ownerId = :ownerId")
    suspend fun updateSortOrder(id: String, ownerId: String, sortOrder: Int, updatedAt: String)

    @Query("SELECT * FROM tasks WHERE ownerId = :ownerId ORDER BY scheduledDate, sortOrder, createdAt")
    fun allTasks(ownerId: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("SELECT COUNT(*) FROM tasks WHERE scheduledDate = :date AND ownerId = :ownerId AND status = 'PLANNED' AND deletedAt IS NULL")
    suspend fun pendingCount(date: String, ownerId: String): Int

    @Query("SELECT * FROM tasks WHERE ownerId = :ownerId") suspend fun allForSync(ownerId: String): List<TaskEntity>
    @Query("UPDATE tasks SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncPending = 1 WHERE id = :id AND ownerId = :ownerId") suspend fun softDelete(id: String, ownerId: String, deletedAt: String, updatedAt: String)
    @Query("UPDATE tasks SET deletedAt = NULL, updatedAt = :updatedAt, syncPending = 1 WHERE id = :id AND ownerId = :ownerId") suspend fun restoreDeleted(id: String, ownerId: String, updatedAt: String)
    @Query("UPDATE tasks SET syncPending = 0 WHERE id = :id AND ownerId = :ownerId") suspend fun markSynced(id: String, ownerId: String)
    @Query("UPDATE tasks SET ownerId = :toOwner WHERE ownerId = :fromOwner") suspend fun moveOwner(fromOwner: String, toOwner: String)
}
