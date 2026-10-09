package com.thiago.assistentepessoal.routine

import org.junit.Test
import org.junit.Assert.*

class CompanionPolicyTest{
    @Test fun dailyLimitAndMinimumInterval(){
        assertNull(companionNotice(CompanionPolicy(),1200,2,20.0,emptySet(),true))
        assertNull(companionNotice(CompanionPolicy(),1200,1,3.9,emptySet(),true))
    }
    @Test fun reviewWinsInEveningAndNeverRepeats(){
        assertEquals("review",companionNotice(CompanionPolicy(),1200,1,4.0,emptySet(),true))
        assertNull(companionNotice(CompanionPolicy(),1260,1,8.0,setOf("review"),true))
    }
    @Test fun alertsHavePriorityAndDisabledKindsDoNotSend(){
        assertEquals("alerts",companionNotice(CompanionPolicy(),540,0,24.0,emptySet(),true))
        assertEquals("morning",companionNotice(CompanionPolicy(alertsEnabled=false),540,0,24.0,emptySet(),true))
        assertNull(companionNotice(CompanionPolicy(morningEnabled=false,reviewEnabled=false,alertsEnabled=false),600,0,24.0,emptySet(),true))
    }
    @Test fun affectionIsOptInAndHasLongerGap(){
        assertNull(companionNotice(CompanionPolicy(),900,1,8.0,setOf("morning"),false))
        assertNull(companionNotice(CompanionPolicy(affectionEnabled=true),900,1,5.0,setOf("morning"),false))
        assertEquals("affection",companionNotice(CompanionPolicy(affectionEnabled=true),900,1,6.0,setOf("morning"),false))
    }
    @Test fun nothingBeforeMorning(){assertNull(companionNotice(CompanionPolicy(),400,0,24.0,emptySet(),true))}
}
