package com.steady.accountability.usage

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageRulesTest {
    @Test fun dayWindowUsesLocalMidnightAndNextMidnight() {
        val date = LocalDate.of(2026, 10, 27)
        val zone = ZoneId.of("Asia/Kolkata")
        val window = UsageRules.dayWindow(date, zone)
        assertEquals(date.atStartOfDay(zone).toInstant().toEpochMilli(), window.startMillis)
        assertEquals(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), window.endMillis)
    }

    @Test fun dayWindowHonorsDaylightSavingDateLength() {
        val date = LocalDate.of(2026, 3, 8)
        val window = UsageRules.dayWindow(date, ZoneId.of("America/New_York"))
        assertEquals(23L * 60 * 60 * 1000, window.endMillis - window.startMillis)
    }

    @Test fun usageAccessStatesArePresentedSeparately() {
        assertEquals(UsageDashboardState.DISABLED, UsageRules.dashboardState(false, UsagePermissionState.DENIED, 0, false))
        assertEquals(UsageDashboardState.NEEDS_ACCESS, UsageRules.dashboardState(true, UsagePermissionState.DENIED, 0, false))
        assertEquals(UsageDashboardState.RESTRICTED, UsageRules.dashboardState(true, UsagePermissionState.RESTRICTED, 0, false))
        assertEquals(UsageDashboardState.UNAVAILABLE, UsageRules.dashboardState(true, UsagePermissionState.UNAVAILABLE, 0, false))
        assertEquals(UsageDashboardState.EMPTY_SELECTION, UsageRules.dashboardState(true, UsagePermissionState.GRANTED, 0, false))
        assertEquals(UsageDashboardState.EMPTY, UsageRules.dashboardState(true, UsagePermissionState.GRANTED, 1, false))
        assertEquals(UsageDashboardState.READY, UsageRules.dashboardState(true, UsagePermissionState.GRANTED, 1, true))
    }

    @Test fun warningAppearsAtOrAboveDailyThresholdOnly() {
        assertFalse(UsageRules.thresholdReached(29 * 60_000L, 30))
        assertTrue(UsageRules.thresholdReached(30 * 60_000L, 30))
        assertTrue(UsageRules.thresholdReached(45 * 60_000L, 30))
        assertFalse(UsageRules.thresholdReached(60 * 60_000L, null))
    }

    @Test fun disablingTrackingRequestsAggregateDeletion() {
        assertTrue(UsageRules.shouldDeleteAggregatesWhenDisabled(false))
        assertFalse(UsageRules.shouldDeleteAggregatesWhenDisabled(true))
    }

    @Test fun invalidNegativeUsageDurationIsClampedToZero() {
        assertEquals(0L, UsageRules.safeDurationMillis(-10))
        assertEquals(25L, UsageRules.safeDurationMillis(25))
    }

    @Test fun dailyTotalsIgnoreAdjacentDayBucketsAndAvoidDuplicateOvercounting() {
        val zone = ZoneId.of("Asia/Kolkata")
        val day = UsageRules.dayWindow(LocalDate.of(2026, 10, 27), zone)
        val totals = UsageRules.totalsForDay(
            listOf(
                UsageBucket("app.a", day.startMillis - 1, 90_000L),
                UsageBucket("app.a", day.startMillis, 12 * 60_000L),
                UsageBucket("app.a", day.startMillis, 12 * 60_000L),
                UsageBucket("app.b", day.startMillis, 30 * 60_000L),
                UsageBucket("app.c", day.startMillis, 60 * 60_000L),
            ),
            setOf("app.a", "app.b"),
            day,
        )
        assertEquals(12 * 60_000L, totals["app.a"])
        assertEquals(30 * 60_000L, totals["app.b"])
        assertEquals(2, totals.size)
    }
}
