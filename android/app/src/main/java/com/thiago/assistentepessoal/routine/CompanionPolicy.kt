package com.thiago.assistentepessoal.routine

/** Pure delivery policy; Android work may run later than the requested hour. */
data class CompanionPolicy(val limit:Int=2,val morning:Int=540,val evening:Int=1200,
    val morningEnabled:Boolean=true,val reviewEnabled:Boolean=true,val alertsEnabled:Boolean=true,val affectionEnabled:Boolean=false)

fun companionNotice(policy:CompanionPolicy,minute:Int,sent:Int,hoursSinceLast:Double,
    already:Set<String>,hasAlerts:Boolean):String?{
    if(sent>=policy.limit.coerceIn(1,3) || hoursSinceLast<4)return null
    if(policy.reviewEnabled && minute>=policy.evening && "review" !in already)return "review"
    if(minute<policy.morning || minute>=policy.evening)return null
    if(policy.alertsEnabled && hasAlerts && "alerts" !in already)return "alerts"
    if(policy.morningEnabled && "morning" !in already)return "morning"
    if(policy.affectionEnabled && "affection" !in already && hoursSinceLast>=6)return "affection"
    return null
}
