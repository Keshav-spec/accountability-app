package com.steady.accountability.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.steady.accountability.MainActivity
import com.steady.accountability.data.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val slot = inputData.getString(KEY_SLOT) ?: return@withContext Result.success()
        val db = DatabaseProvider.get(applicationContext)
        val settingsRepo = ReminderSettingsRepository(db.reminderSettingsDao())
        val settings = settingsRepo.get()
        val now = LocalDateTime.now()
        val activeOwner = applicationContext.getSharedPreferences("steady-sync", Context.MODE_PRIVATE).getString("active_owner", LOCAL_OWNER_ID) ?: LOCAL_OWNER_ID
        val pending = db.taskDao().pendingCount(LocalDate.now().toString(), activeOwner) > 0
        val date = LocalDate.now().toString()
        val plannedTasks = db.taskDao().allForSync(activeOwner).filter { it.scheduledDate == date && it.status == TaskStatus.PLANNED.name && it.deletedAt == null }
        val priorities = db.checkInDao().all(activeOwner).firstOrNull { it.date == date }?.prioritiesText
            ?.lineSequence()?.map(String::trim)?.filter(String::isNotBlank)?.toSet().orEmpty()
        val plannedPriorityCount = plannedTasks.count { task -> priorities.any { it.equals(task.title.trim(), ignoreCase = true) } }
        val reason = if (plannedPriorityCount > 0) {
            "${plannedPriorityCount} selected ${if (plannedPriorityCount == 1) "priority is" else "priorities are"} still planned for today."
        } else {
            "${plannedTasks.size} planned ${if (plannedTasks.size == 1) "task remains" else "tasks remain"} for today."
        }
        val prefs = ReminderScheduler.asPreferences(settings)
        if (!ReminderPolicy.mayDeliver(now, prefs, slot, pending)) {
            ReminderScheduler.refresh(applicationContext, appendAfterCurrent = true)
            return@withContext Result.success()
        }
        val mayPost = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (mayPost && NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            val countToday = if (prefs.deliveredDate == now.toLocalDate()) prefs.deliveredCount else 0
            val slotsToday = if (prefs.deliveredDate == now.toLocalDate()) prefs.deliveredSlots else emptySet()
            // Persist the idempotency key before posting so a retry cannot produce a duplicate.
            settingsRepo.save(settings.copy(deliveredDate = now.toLocalDate().toString(), deliveredCount = countToday + 1, deliveredSlots = (slotsToday + slot).joinToString("|"), snoozeUntilMillis = if (slot.startsWith("snooze:")) null else settings.snoozeUntilMillis))
            postNotification(applicationContext, settings.snoozeMinutes, reason, settings.reminderContext)
            db.reminderHistoryDao().add(ReminderHistoryEntity(UUID.randomUUID().toString(), System.currentTimeMillis(), reason))
            db.reminderHistoryDao().trim()
        }
        ReminderScheduler.refresh(applicationContext, appendAfterCurrent = true)
        Result.success()
    }

    private fun postNotification(context: Context, snoozeMinutes: Int, reason: String, reminderContext: String) {
        val channelId = "steady_reminders"
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channelId, "Steady reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 11, android.content.Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun actionIntent(action: String, code: Int) = PendingIntent.getBroadcast(context, code, android.content.Intent(context, ReminderActionReceiver::class.java).setAction(action).putExtra(ReminderActionReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("A gentle check-in")
            .setContentText("$reason ${reminderContext.ifBlank { "One small step is enough." }}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze", actionIntent(ReminderActionReceiver.ACTION_SNOOZE, 12))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop today", actionIntent(ReminderActionReceiver.ACTION_STOP_TODAY, 13))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_SLOT = "reminder_slot"
        private const val NOTIFICATION_ID = 412
    }
}
