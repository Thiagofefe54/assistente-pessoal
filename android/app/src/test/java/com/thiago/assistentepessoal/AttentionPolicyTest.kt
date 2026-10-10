package com.thiago.assistentepessoal
import org.junit.Assert.*
import org.junit.Test

class AttentionPolicyTest {
    @Test fun respectsAbsenceAndDailyLimit(){
        val start=1_000L;val day=86_400_000L
        assertFalse(attentionDue(start+day-1,start,0,24))
        assertTrue(attentionDue(start+day,start,0,24))
        assertFalse(attentionDue(start+day,start,start+day-1,24))
        assertFalse(attentionDue(start-1,start,0,24))
        assertFalse(attentionDue(start+day,0,0,24))
        assertTrue(attentionDue(start+2*day,start,start+day,24))
    }
}
