package com.steady.accountability.data

object TaskRules {
    fun setStatus(requested: TaskStatus): TaskStatus = requested

    fun toggleComplete(current: String): TaskStatus =
        if (current == TaskStatus.COMPLETED.name) TaskStatus.PLANNED else TaskStatus.COMPLETED
}
