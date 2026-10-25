package com.steady.accountability.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.steady.accountability.data.DatabaseProvider
import com.steady.accountability.data.ReminderSettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ReminderSettingsRepository(DatabaseProvider.get(context).reminderSettingsDao())
                val settings = repo.get()
                val updated = when (intent.action) {
                    ACTION_SNOOZE -> settings.copy(snoozeUntilMillis = System.currentTimeMillis() + intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10) * 60_000L)
                    ACTION_STOP_TODAY -> settings.copy(stoppedDate = LocalDate.now().toString())
                    else -> settings
                }
                repo.save(updated)
                NotificationManagerCompat.from(context).cancel(412)
                ReminderScheduler.refresh(context)
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.steady.accountability.SNOOZE"
        const val ACTION_STOP_TODAY = "com.steady.accountability.STOP_TODAY"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    }
}
