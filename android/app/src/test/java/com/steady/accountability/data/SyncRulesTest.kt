package com.steady.accountability.data

import org.junit.Assert.*
import org.junit.Test

class SyncRulesTest {
    @Test fun newerTimestampWinsAndRemoteWinsTies() {
        assertTrue(SyncRules.shouldQueueUpload("2026-10-25T10:00:00Z", null))
        assertTrue(SyncRules.shouldQueueUpload("2026-10-25T10:00:01Z", "2026-10-25T10:00:00+00:00"))
        assertFalse(SyncRules.shouldQueueUpload("2026-10-25T10:00:00Z", "2026-10-25T10:00:00+00:00"))
    }

    @Test fun pendingLocalEditsStayQueuedWhenNetworkHasNotReturnedRemoteState() {
        assertTrue(SyncRules.shouldQueueUpload("2026-10-25T10:00:00Z", null))
    }

    @Test fun tombstonesAreRetainedForTheRemoteWrite() {
        val tombstone = "2026-10-25T10:00:00Z"
        assertEquals(tombstone, SyncRules.deletedAtForSync(tombstone))
    }
}
