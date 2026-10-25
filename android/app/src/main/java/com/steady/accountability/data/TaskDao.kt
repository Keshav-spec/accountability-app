package com.steady.accountability.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE scheduledDate = :date AND deletedAt IS NULL ORDER BY createdAt")
    fun tasksForDate(date: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt, syncPending = 1 WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: String)

    @Query("SELECT * FROM tasks ORDER BY scheduledDate, createdAt")
    fun allTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("SELECT COUNT(*) FROM tasks WHERE scheduledDate = :date AND status = 'PLANNED' AND deletedAt IS NULL")
    suspend fun pendingCount(date: String): Int

    @Query("SELECT * FROM tasks") suspend fun allForSync(): List<TaskEntity>
    @Query("UPDATE tasks SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncPending = 1 WHERE id = :id") suspend fun softDelete(id: String, deletedAt: String, updatedAt: String)
    @Query("UPDATE tasks SET syncPending = 0 WHERE id = :id") suspend fun markSynced(id: String)
}
