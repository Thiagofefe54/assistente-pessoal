package com.thiago.assistentepessoal
import org.junit.Test
import org.junit.Assert.*
class AssistantCachePolicyTest {
    @Test fun cachedSnapshotsExpireAndRejectClockRollback(){
        val start=100000L;val week=7L*24*60*60*1000
        assertTrue(snapshotIsUsable(start,start+week));assertFalse(snapshotIsUsable(start,start+week+1))
        assertFalse(snapshotIsUsable(start,start-1));assertFalse(snapshotIsUsable(0,start))
    }
    @Test fun writesAndPaidBalanceNeverUseSnapshots(){
        listOf("checkin","task-action","undo","demo","usage").forEach{assertFalse(it in cachedAssistantPaths)}
        listOf("day","review","search","plan").forEach{assertTrue(it in cachedAssistantPaths)}
    }
}
