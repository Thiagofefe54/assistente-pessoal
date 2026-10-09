package com.thiago.assistentepessoal.tools

import java.time.Duration
import java.time.Instant

internal enum class BalancePlanState { INCOMPLETE, OLD_BALANCE, NO_BILLS, SHORTFALL, COVERED }
internal data class BalancePlan(val state:BalancePlanState,val billsCents:Long,val differenceCents:Long)

/** Compares snapshots only. A positive difference is never called disposable money. */
internal fun compareBalanceWithBills(balance:Long, amounts:List<Long>, expectedCount:Int,
    partial:Boolean, oldestUpdate:Instant, now:Instant):BalancePlan {
    require(amounts.all{it>=0} && expectedCount>=amounts.size)
    val total=amounts.fold(0L){sum,value->Math.addExact(sum,value)}
    val difference=Math.subtractExact(balance,total)
    if(difference==Long.MIN_VALUE)throw ArithmeticException("Difference out of range")
    val age=Duration.between(oldestUpdate,now)
    val state=when {
        partial || expectedCount!=amounts.size -> BalancePlanState.INCOMPLETE
        age>Duration.ofHours(24) || age<Duration.ofMinutes(-5) -> BalancePlanState.OLD_BALANCE
        amounts.isEmpty() -> BalancePlanState.NO_BILLS
        difference<0 -> BalancePlanState.SHORTFALL
        else -> BalancePlanState.COVERED
    }
    return BalancePlan(state,total,difference)
}
