package com.steady.accountability.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ReminderPolicyTest {
    private val date = LocalDate.of(2026, 10, 25)
    private fun prefs(
        times: List<LocalTime> = listOf(LocalTime.of(9, 0)), limit: Int = 3,
        deliveredCount: Int = 0, deliveredSlots: Set<String> = emptySet(), stopped: LocalDate? = null,
    ) = ReminderPreferences(true, times, LocalTime.of(22, 0), LocalTime.of(8, 0), limit, 10, stopped, null, date, deliveredCount, deliveredSlots)

    @Test fun quietHoursMoveOvernightReminderToMorning() {
        val next = ReminderPolicy.next(date.atTime(21, 0), prefs(listOf(LocalTime.of(23, 0))), true)
        assertEquals(date.plusDays(1).atTime(8, 0), next?.at)
    }

    @Test fun earlyMorningQuietReminderMovesToQuietEndToday() {
        val next = ReminderPolicy.next(date.atTime(5, 0), prefs(listOf(LocalTime.of(6, 0))), true)
        assertEquals(date.atTime(8, 0), next?.at)
    }

    @Test fun completedOrSkippedTasksDoNotReceiveReminders() {
        assertNull(ReminderPolicy.next(date.atTime(7, 0), prefs(), false))
    }

    @Test fun dailyLimitAndStopForTodayDeferUntilTomorrow() {
        assertEquals(date.plusDays(1).atTime(9, 0), ReminderPolicy.next(date.atTime(7, 0), prefs(limit = 2, deliveredCount = 2), true)?.at)
        assertEquals(date.plusDays(1).atTime(9, 0), ReminderPolicy.next(date.atTime(7, 0), prefs(stopped = date), true)?.at)
    }

    @Test fun deliveredSlotCannotBeRepeated() {
        val key = "${date}_09:00"
        assertFalse(ReminderPolicy.mayDeliver(date.atTime(9, 0), prefs(deliveredSlots = setOf(key)), key, true))
    }

    @Test fun snoozeIsSelectedWhenItIsTheNextDueReminder() {
        val snooze = date.atTime(10, 0)
        val value = prefs().copy(snoozeUntil = snooze)
        assertEquals("snooze:$snooze", ReminderPolicy.next(date.atTime(9, 30), value, true)?.slotKey)
    }
}
