package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.tools.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class BankPlanningTest {
    private val now=Instant.parse("2026-10-09T15:00:00Z")
    private fun plan(balance:Long,amounts:List<Long>,count:Int=amounts.size,partial:Boolean=false,
        update:Instant=now)=compareBalanceWithBills(balance,amounts,count,partial,update,now)
    @Test fun comparisonPreservesCentsAndNegativeBalance(){
        val p=plan(2535,listOf(1000,1700));assertEquals(BalancePlanState.SHORTFALL,p.state)
        assertEquals(2700,p.billsCents);assertEquals(-165,p.differenceCents)
        assertEquals(-3700,plan(-1000,listOf(2700)).differenceCents)
        assertEquals(BalancePlanState.COVERED,plan(2700,listOf(2700)).state)
    }
    @Test fun missingRowsOrPartialBankCannotClaimCoverage(){
        assertEquals(BalancePlanState.INCOMPLETE,plan(100000,listOf(1000),count=9).state)
        assertEquals(BalancePlanState.INCOMPLETE,plan(100000,listOf(1000),partial=true).state)
    }
    @Test fun oldOrFutureSnapshotsCannotClaimCoverage(){
        assertEquals(BalancePlanState.OLD_BALANCE,plan(100000,listOf(1000),update=now.minusSeconds(86401)).state)
        assertEquals(BalancePlanState.OLD_BALANCE,plan(100000,listOf(1000),update=now.plusSeconds(301)).state)
        assertEquals(BalancePlanState.NO_BILLS,plan(100000,emptyList()).state)
    }
    @Test fun rejectsInvalidAmountsAndOverflow(){
        assertThrows(IllegalArgumentException::class.java){plan(100,listOf(-1))}
        assertThrows(IllegalArgumentException::class.java){plan(100,listOf(1),count=0)}
        assertThrows(ArithmeticException::class.java){plan(100,listOf(Long.MAX_VALUE,1))}
        assertThrows(ArithmeticException::class.java){plan(Long.MIN_VALUE,listOf(1))}
    }
}
