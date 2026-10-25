package com.steady.accountability.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.steady.accountability.data.*
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val WORK_NAME = "steady-next-local-reminder"

    fun asPreferences(settings: ReminderSettingsEntity): ReminderPreferences = ReminderPreferences(
        enabled = settings.enabled,
        times = settings.timesText.split(',').mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }.distinct().sorted(),
        quietStart = runCatching { LocalTime.parse(settings.quietStart) }.getOrDefault(LocalTime.of(22, 0)),
        quietEnd = runCatching { LocalTime.parse(settings.quietEnd) }.getOrDefault(LocalTime.of(8, 0)),
        dailyLimit = settings.dailyLimit.coerceIn(1, 12),
        snoozeMinutes = settings.snoozeMinutes.coerceIn(5, 120),
        stoppedDate = settings.stoppedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        snoozeUntil = settings.snoozeUntilMillis?.let { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault()) },
        deliveredDate = settings.deliveredDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        deliveredCount = settings.deliveredCount,
        deliveredSlots = settings.deliveredSlots.split('|').filter(String::isNotBlank).toSet(),
    )

    suspend fun refresh(context: Context, appendAfterCurrent: Boolean = false) {
        val db = DatabaseProvider.get(context)
        val repo = ReminderSettingsRepository(db.reminderSettingsDao())
        val settings = repo.get()
        val pending = db.taskDao().pendingCount(LocalDate.now().toString()) > 0
        val now = LocalDateTime.now()
        val next = ReminderPolicy.next(now, asPreferences(settings), pending)
        val manager = WorkManager.getInstance(context)
        if (next == null) {
            if (!appendAfterCurrent) manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = Duration.between(now, next.at).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(ReminderWorker.KEY_SLOT, next.slotKey).build())
            .build()
        manager.enqueueUniqueWork(WORK_NAME, if (appendAfterCurrent) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
    }
}
