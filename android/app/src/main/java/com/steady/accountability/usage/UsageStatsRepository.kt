package com.steady.accountability.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import com.steady.accountability.data.UsageAppSettingEntity
import com.steady.accountability.data.UsageDailyAggregateEntity
import com.steady.accountability.data.UsageDao
import java.time.LocalDate
import java.time.ZoneId

data class UsageAppRow(
    val setting: UsageAppSettingEntity,
    val totalMillis: Long,
)

data class UsageReadResult(
    val permission: UsagePermissionState,
    val date: String,
    val rows: List<UsageAppRow> = emptyList(),
)

class UsageStatsRepository(private val context: Context, private val dao: UsageDao) {
    fun permissionState(): UsagePermissionState {
        val manager = context.getSystemService(AppOpsManager::class.java) ?: return UsagePermissionState.UNAVAILABLE
        return try {
            when (manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)) {
                AppOpsManager.MODE_ALLOWED -> UsagePermissionState.GRANTED
                AppOpsManager.MODE_ERRORED -> UsagePermissionState.RESTRICTED
                AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_DEFAULT -> UsagePermissionState.DENIED
                else -> UsagePermissionState.RESTRICTED
            }
        } catch (_: RuntimeException) {
            UsagePermissionState.RESTRICTED
        }
    }

    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    suspend fun refreshToday(): UsageReadResult {
        val date = LocalDate.now().toString()
        val permission = permissionState()
        if (permission != UsagePermissionState.GRANTED) return UsageReadResult(permission, date)
        val statsManager = context.getSystemService(UsageStatsManager::class.java)
            ?: return UsageReadResult(UsagePermissionState.UNAVAILABLE, date)
        return try {
            val zone = ZoneId.systemDefault()
            val window = UsageRules.dayWindow(LocalDate.now(zone), zone)
            val start = window.startMillis
            val end = System.currentTimeMillis().coerceAtMost(window.endMillis)
            val appCatalog = launcherApps()
            dao.insertAppsIfMissing(appCatalog.map { UsageAppSettingEntity(it.first, it.second) })
            val settings = dao.getApps().filter { it.included }
            val selectedPackages = settings.mapTo(mutableSetOf()) { it.packageName }
            val stats = if (end > start) statsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end).orEmpty() else emptyList()
            val totals = UsageRules.totalsForDay(
                stats.map { UsageBucket(it.packageName, it.firstTimeStamp, it.totalTimeInForeground) },
                selectedPackages,
                window,
            )
            dao.deleteAggregatesForDate(date)
            val stored = settings.mapNotNull { setting ->
                totals[setting.packageName]?.let { UsageDailyAggregateEntity(date, setting.packageName, it) }
            }
            if (stored.isNotEmpty()) dao.saveAggregates(stored)
            val rows = settings.map { setting -> UsageAppRow(setting, totals[setting.packageName] ?: 0L) }
                .sortedByDescending { it.totalMillis }
            UsageReadResult(permission, date, rows)
        } catch (_: SecurityException) {
            UsageReadResult(UsagePermissionState.RESTRICTED, date)
        } catch (_: RuntimeException) {
            UsageReadResult(UsagePermissionState.UNAVAILABLE, date)
        }
    }

    private fun launcherApps(): List<Pair<String, String>> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(launcherIntent, 0)
        }
        return matches.mapNotNull { info ->
            val app = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (app.packageName == context.packageName) return@mapNotNull null
            val label = runCatching { context.packageManager.getApplicationLabel(app).toString() }.getOrNull().orEmpty()
            if (label.isBlank()) null else app.packageName to label
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
}
