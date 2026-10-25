package com.steady.accountability.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskValidationTest {
    @Test fun rejectsBlankTitle() { assertEquals("Add a task title to continue.", TaskValidation.titleError("  ")) }
    @Test fun rejectsLongTitle() { assertEquals("Keep the title to 120 characters or fewer.", TaskValidation.titleError("x".repeat(121))) }
    @Test fun acceptsValidTitle() { assertNull(TaskValidation.titleError("Take a short walk")) }
    @Test fun modelsHaveStableDefaults() {
        val task = Task("id", "Read", scheduledDate = java.time.LocalDate.parse("2026-10-25"))
        assertEquals(TaskStatus.PLANNED, task.status)
        assertEquals(task.createdAt, task.updatedAt)
    }
}
