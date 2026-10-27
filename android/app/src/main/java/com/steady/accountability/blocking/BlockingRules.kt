package com.steady.accountability.blocking

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.content.pm.PackageManager
import com.steady.accountability.data.TaskEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class BlockingRuleType { PRIORITY_TASKS, BREAK_TIME }
enum class BlockingDecision { ALLOW_DISABLED, ALLOW_ESSENTIAL, ALLOW_NOT_SELECTED, ALLOW_EXCEPTION, ALLOW_PAUSED, ALLOW_BYPASS, ALLOW_RULE_FINISHED, BLOCK }
enum class BlockingRecoveryState { OFF, NEEDS_ACCESS, SERVICE_NOT_CONNECTED, READY }

data class BlockingConfig(
    val enabled: Boolean = false,
    val blockedPackages: Set<String> = emptySet(),
    val exceptionPackages: Set<String> = emptySet(),
    val essentialPackages: Set<String> = DEFAULT_ESSENTIAL_PACKAGES,
    val selectedTaskIds: Set<String> = emptySet(),
    val ruleType: BlockingRuleType = BlockingRuleType.PRIORITY_TASKS,
    val ruleDate: String = LocalDate.now().toString(),
    val breakTime: String = "18:00",
    val breakDurationMinutes: Int = 30,
    val pauseUntilMillis: Long = 0L,
    val bypassUntilMillis: Long = 0L,
)

private val DEFAULT_ESSENTIAL_PACKAGES = setOf(
    "com.android.systemui", "com.android.settings", "com.android.phone",
    "com.google.android.permissioncontroller", "com.android.permissioncontroller",
    "com.android.packageinstaller",
)

object BlockingRules {
    fun evaluate(
        config: BlockingConfig,
        packageName: String,
        tasks: List<TaskEntity>,
        now: LocalDateTime,
        ownPackage: String,
    ): BlockingDecision {
        if (!config.enabled) return BlockingDecision.ALLOW_DISABLED
        if (packageName == ownPackage || packageName in config.essentialPackages || packageName in DEFAULT_ESSENTIAL_PACKAGES) return BlockingDecision.ALLOW_ESSENTIAL
        if (packageName !in config.blockedPackages) return BlockingDecision.ALLOW_NOT_SELECTED
        if (packageName in config.exceptionPackages) return BlockingDecision.ALLOW_EXCEPTION
        val nowMillis = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (nowMillis < config.pauseUntilMillis) return BlockingDecision.ALLOW_PAUSED
        if (nowMillis < config.bypassUntilMillis) return BlockingDecision.ALLOW_BYPASS
        if (config.ruleDate != now.toLocalDate().toString()) return BlockingDecision.ALLOW_RULE_FINISHED
        return when (config.ruleType) {
            BlockingRuleType.PRIORITY_TASKS -> {
                if (config.selectedTaskIds.isEmpty()) return BlockingDecision.ALLOW_RULE_FINISHED
                val unfinished = tasks.any { it.id in config.selectedTaskIds && it.scheduledDate == config.ruleDate && it.status == "PLANNED" && it.deletedAt == null }
                if (unfinished) BlockingDecision.BLOCK else BlockingDecision.ALLOW_RULE_FINISHED
            }
            BlockingRuleType.BREAK_TIME -> {
                val release = runCatching { LocalTime.parse(config.breakTime) }.getOrDefault(LocalTime.of(18, 0))
                if (now.toLocalTime().isBefore(release)) BlockingDecision.BLOCK else BlockingDecision.ALLOW_RULE_FINISHED
            }
        }
    }

    fun isAlwaysAllowed(packageName: String, ownPackage: String, essentialPackages: Set<String>): Boolean =
        packageName == ownPackage || packageName in essentialPackages

    fun recoveryState(enabled: Boolean, accessibilityGranted: Boolean, serviceConnected: Boolean): BlockingRecoveryState = when {
        !enabled -> BlockingRecoveryState.OFF
        !accessibilityGranted -> BlockingRecoveryState.NEEDS_ACCESS
        !serviceConnected -> BlockingRecoveryState.SERVICE_NOT_CONNECTED
        else -> BlockingRecoveryState.READY
    }

    fun pauseFor(nowMillis: Long, minutes: Int): Long = nowMillis + minutes.coerceIn(1, 180) * 60_000L

    fun bypassUntilTomorrow(now: LocalDateTime, zone: java.time.ZoneId): Long =
        now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

object BlockingPreferences {
    private const val PREFS = "steady-blocking"

    fun read(context: Context): BlockingConfig = read(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    fun read(prefs: SharedPreferences): BlockingConfig = BlockingConfig(
        enabled = prefs.getBoolean("enabled", false),
        blockedPackages = prefs.getStringSet("blocked_packages", emptySet())?.toSet().orEmpty(),
        exceptionPackages = prefs.getStringSet("exception_packages", emptySet())?.toSet().orEmpty(),
        essentialPackages = prefs.getStringSet("essential_packages", DEFAULT_ESSENTIAL_PACKAGES)?.toSet().orEmpty() + DEFAULT_ESSENTIAL_PACKAGES,
        selectedTaskIds = prefs.getStringSet("task_ids", emptySet())?.toSet().orEmpty(),
        ruleType = runCatching { BlockingRuleType.valueOf(prefs.getString("rule_type", BlockingRuleType.PRIORITY_TASKS.name).orEmpty()) }.getOrDefault(BlockingRuleType.PRIORITY_TASKS),
        ruleDate = prefs.getString("rule_date", LocalDate.now().toString()).orEmpty(),
        breakTime = prefs.getString("break_time", "18:00").orEmpty(),
        breakDurationMinutes = prefs.getInt("break_duration_minutes", 30).coerceIn(5, 120),
        pauseUntilMillis = prefs.getLong("pause_until_millis", 0L),
        bypassUntilMillis = prefs.getLong("bypass_until_millis", 0L),
    )

    fun save(context: Context, value: BlockingConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", value.enabled)
            .putStringSet("blocked_packages", value.blockedPackages.toMutableSet())
            .putStringSet("exception_packages", value.exceptionPackages.toMutableSet())
            .putStringSet("essential_packages", value.essentialPackages.toMutableSet())
            .putStringSet("task_ids", value.selectedTaskIds.toMutableSet())
            .putString("rule_type", value.ruleType.name)
            .putString("rule_date", value.ruleDate)
            .putString("break_time", value.breakTime)
            .putInt("break_duration_minutes", value.breakDurationMinutes.coerceIn(5, 120))
            .putLong("pause_until_millis", value.pauseUntilMillis)
            .putLong("bypass_until_millis", value.bypassUntilMillis)
            .apply()
    }

    fun essentialPackages(context: Context): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val home = runCatching { context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }.getOrNull()
        return DEFAULT_ESSENTIAL_PACKAGES + listOfNotNull(home)
    }

}
