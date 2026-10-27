package com.steady.accountability.blocking

import com.steady.accountability.data.TaskEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingRulesTest {
    private val date = LocalDate.of(2026, 10, 27)
    private val now = date.atTime(10, 0)
    private val task = TaskEntity("t1", "Write", null, date.toString(), null, "PLANNED", "", "")
    private val selectedApp = "com.example.social"
    private val base = BlockingConfig(
        enabled = true,
        blockedPackages = setOf(selectedApp),
        selectedTaskIds = setOf("t1"),
        ruleDate = date.toString(),
    )

    @Test fun onlySelectedAppsAreBlockedAndExceptionsWin() {
        assertEquals(BlockingDecision.BLOCK, decision(base, selectedApp, listOf(task)))
        assertEquals(BlockingDecision.ALLOW_NOT_SELECTED, decision(base, "com.example.other", listOf(task)))
        assertEquals(BlockingDecision.ALLOW_EXCEPTION, decision(base.copy(exceptionPackages = setOf(selectedApp)), selectedApp, listOf(task)))
        assertEquals(BlockingDecision.ALLOW_ESSENTIAL, decision(base, "com.android.settings", listOf(task)))
        assertEquals(BlockingDecision.ALLOW_ESSENTIAL, decision(base, "com.steady.accountability", listOf(task)))
    }

    @Test fun completedOrSkippedSelectedTasksReleaseTheBlock() {
        assertEquals(BlockingDecision.ALLOW_RULE_FINISHED, decision(base, selectedApp, listOf(task.copy(status = "COMPLETED"))))
        assertEquals(BlockingDecision.ALLOW_RULE_FINISHED, decision(base, selectedApp, listOf(task.copy(status = "SKIPPED"))))
        assertEquals(BlockingDecision.ALLOW_RULE_FINISHED, decision(base, selectedApp, emptyList()))
        assertEquals(BlockingDecision.BLOCK, decision(base, selectedApp, listOf(task)))
    }

    @Test fun pauseAndEmergencyBypassExpireAtTheirDeadlines() {
        val zone = ZoneId.systemDefault()
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        assertEquals(BlockingDecision.ALLOW_PAUSED, decision(base.copy(pauseUntilMillis = BlockingRules.pauseFor(nowMillis, 15)), selectedApp, listOf(task)))
        assertEquals(BlockingDecision.BLOCK, decision(base.copy(pauseUntilMillis = nowMillis - 1), selectedApp, listOf(task)))
        assertEquals(BlockingDecision.ALLOW_BYPASS, decision(base.copy(bypassUntilMillis = BlockingRules.bypassUntilTomorrow(now, zone)), selectedApp, listOf(task)))
        assertEquals(BlockingDecision.BLOCK, decision(base.copy(bypassUntilMillis = nowMillis - 1), selectedApp, listOf(task)))
        assertEquals(nowMillis + 15 * 60_000L, BlockingRules.pauseFor(nowMillis, 15))
    }

    @Test fun breakTimeReleasesOnlyAfterTheConfiguredTime() {
        val config = base.copy(ruleType = BlockingRuleType.BREAK_TIME, breakTime = "12:00")
        assertEquals(BlockingDecision.BLOCK, decision(config, selectedApp, emptyList()))
        assertEquals(BlockingDecision.ALLOW_RULE_FINISHED, BlockingRules.evaluate(config, selectedApp, emptyList(), date.atTime(12, 0), "com.steady.accountability"))
    }

    @Test fun oldDayRulesAndDisabledServiceFailOpen() {
        assertEquals(BlockingDecision.ALLOW_RULE_FINISHED, decision(base.copy(ruleDate = date.minusDays(1).toString()), selectedApp, listOf(task)))
        assertEquals(BlockingDecision.ALLOW_DISABLED, decision(base.copy(enabled = false), selectedApp, listOf(task)))
    }

    @Test fun recoveryStateExplainsEachAccessCondition() {
        assertEquals(BlockingRecoveryState.OFF, BlockingRules.recoveryState(false, false, false))
        assertEquals(BlockingRecoveryState.NEEDS_ACCESS, BlockingRules.recoveryState(true, false, false))
        assertEquals(BlockingRecoveryState.SERVICE_NOT_CONNECTED, BlockingRules.recoveryState(true, true, false))
        assertEquals(BlockingRecoveryState.READY, BlockingRules.recoveryState(true, true, true))
    }

    @Test fun essentialPackagesCannotBeSelectedForBlocking() {
        assertTrue(BlockingRules.isAlwaysAllowed("com.android.settings", "com.steady.accountability", setOf("com.android.settings")))
        assertFalse(BlockingRules.isAlwaysAllowed(selectedApp, "com.steady.accountability", setOf("com.android.settings")))
    }

    private fun decision(config: BlockingConfig, packageName: String, tasks: List<TaskEntity>) =
        BlockingRules.evaluate(config, packageName, tasks, now, "com.steady.accountability")
}
