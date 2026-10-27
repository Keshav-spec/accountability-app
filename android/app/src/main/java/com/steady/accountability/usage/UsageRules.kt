package com.steady.accountability.usage

import java.time.LocalDate
import java.time.ZoneId

enum class UsagePermissionState { GRANTED, DENIED, RESTRICTED, UNAVAILABLE }

enum class UsageDashboardState { DISABLED, NEEDS_ACCESS, RESTRICTED, UNAVAILABLE, EMPTY_SELECTION, EMPTY, READY }

data class UsageDayWindow(val startMillis: Long, val endMillis: Long)
data class UsageBucket(val packageName: String, val bucketStartMillis: Long, val totalMillis: Long)

object UsageRules {
    fun dayWindow(date: LocalDate, zone: ZoneId): UsageDayWindow = UsageDayWindow(
        startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    fun dashboardState(enabled: Boolean, permission: UsagePermissionState, selectedCount: Int, hasData: Boolean): UsageDashboardState {
        if (!enabled) return UsageDashboardState.DISABLED
        return when (permission) {
            UsagePermissionState.DENIED -> UsageDashboardState.NEEDS_ACCESS
            UsagePermissionState.RESTRICTED -> UsageDashboardState.RESTRICTED
            UsagePermissionState.UNAVAILABLE -> UsageDashboardState.UNAVAILABLE
            UsagePermissionState.GRANTED -> when {
                selectedCount <= 0 -> UsageDashboardState.EMPTY_SELECTION
                hasData -> UsageDashboardState.READY
                else -> UsageDashboardState.EMPTY
            }
        }
    }

    fun thresholdReached(usedMillis: Long, limitMinutes: Int?): Boolean =
        limitMinutes != null && limitMinutes > 0 && usedMillis >= limitMinutes * 60_000L

    fun shouldDeleteAggregatesWhenDisabled(enabled: Boolean): Boolean = !enabled

    fun safeDurationMillis(value: Long): Long = value.coerceAtLeast(0L)

    /** Daily queries may expand to calendar buckets. Keep today's bucket and avoid overlapping duplicates. */
    fun totalsForDay(buckets: Iterable<UsageBucket>, selectedPackages: Set<String>, day: UsageDayWindow): Map<String, Long> =
        buckets.asSequence()
            .filter { it.packageName in selectedPackages && it.bucketStartMillis >= day.startMillis && it.bucketStartMillis < day.endMillis }
            .groupBy { it.packageName }
            .mapValues { (_, records) -> records.maxOf { safeDurationMillis(it.totalMillis) } }
}
