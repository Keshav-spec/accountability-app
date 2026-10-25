package com.steady.accountability.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ReminderPreferences(
    val enabled: Boolean,
    val times: List<LocalTime>,
    val quietStart: LocalTime,
    val quietEnd: LocalTime,
    val dailyLimit: Int,
    val snoozeMinutes: Int,
    val stoppedDate: LocalDate? = null,
    val snoozeUntil: LocalDateTime? = null,
    val deliveredDate: LocalDate? = null,
    val deliveredCount: Int = 0,
    val deliveredSlots: Set<String> = emptySet(),
)

data class PlannedReminder(val at: LocalDateTime, val slotKey: String)

object ReminderPolicy {
    fun isQuiet(at: LocalDateTime, start: LocalTime, end: LocalTime): Boolean {
        if (start == end) return false
        val time = at.toLocalTime()
        return if (start < end) time >= start && time < end else time >= start || time < end
    }

    fun moveOutOfQuietHours(at: LocalDateTime, start: LocalTime, end: LocalTime): LocalDateTime {
        if (!isQuiet(at, start, end)) return at
        val next = if (start > end && at.toLocalTime() >= start) at.toLocalDate().plusDays(1) else at.toLocalDate()
        return next.atTime(end)
    }

    fun next(now: LocalDateTime, preferences: ReminderPreferences, hasPlannedTasks: Boolean): PlannedReminder? {
        if (!preferences.enabled || !hasPlannedTasks || preferences.dailyLimit <= 0) return null
        val countToday = if (preferences.deliveredDate == now.toLocalDate()) preferences.deliveredCount else 0
        val sentToday = if (preferences.deliveredDate == now.toLocalDate()) preferences.deliveredSlots else emptySet()
        val deferUntilTomorrow = preferences.stoppedDate == now.toLocalDate() || countToday >= preferences.dailyLimit

        val candidates = mutableListOf<PlannedReminder>()
        preferences.snoozeUntil?.takeIf { it > now && !deferUntilTomorrow }?.let {
            candidates += PlannedReminder(moveOutOfQuietHours(it, preferences.quietStart, preferences.quietEnd), "snooze:${it}")
        }
        var quietDeferralAdded = false
        for (dayOffset in -1..2) {
            val date = now.toLocalDate().plusDays(dayOffset.toLong())
            if (deferUntilTomorrow && date <= now.toLocalDate()) continue
            preferences.times.sorted().forEach { time ->
                val origin = date.atTime(time)
                val key = "${date}_$time"
                if (key in sentToday) return@forEach
                val moved = moveOutOfQuietHours(origin, preferences.quietStart, preferences.quietEnd)
                if (origin <= now && moved > now) {
                    if (!quietDeferralAdded) candidates += PlannedReminder(moved, key)
                    quietDeferralAdded = true
                } else if (origin > now) candidates += PlannedReminder(moved, key)
            }
        }
        return candidates.filter { it.at > now }.minByOrNull { it.at }
    }

    fun mayDeliver(now: LocalDateTime, preferences: ReminderPreferences, slotKey: String, hasPlannedTasks: Boolean): Boolean {
        if (!preferences.enabled || !hasPlannedTasks || preferences.stoppedDate == now.toLocalDate()) return false
        if (isQuiet(now, preferences.quietStart, preferences.quietEnd)) return false
        if (preferences.deliveredDate == now.toLocalDate() && preferences.deliveredCount >= preferences.dailyLimit) return false
        if (preferences.deliveredDate == now.toLocalDate() && slotKey in preferences.deliveredSlots) return false
        return true
    }
}
