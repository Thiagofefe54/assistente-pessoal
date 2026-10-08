package com.thiago.assistentepessoal.routine

import java.time.*
import org.json.JSONObject

data class BillPayment(val billId:String,val date:String,val expenseId:String)
data class BillOccurrence(val record:PersonalRecord,val date:LocalDate,val paid:Boolean)
val diaryCategories=linkedMapOf("work" to "Trabalho","gym" to "Academia","sleep" to "Sono","home" to "Casa","study" to "Estudo","other" to "Outros")
val billRepeats=linkedMapOf("none" to "Uma vez","weekly" to "Semanal","monthly" to "Mensal")
fun billOccurrence(anchor:LocalDate,repeat:String,index:Long):LocalDate?=when(repeat){"weekly"->anchor.plusWeeks(index);"monthly"->anchor.plusMonths(index);else->if(index==0L)anchor else null}
fun billDays(anchor:LocalDate,repeat:String,start:LocalDate,end:LocalDate):List<LocalDate>{
    val index=when(repeat){"monthly"->((start.year-anchor.year)*12L+start.monthValue-anchor.monthValue-1).coerceAtLeast(0);"weekly"->(java.time.temporal.ChronoUnit.DAYS.between(anchor,start)/7-1).coerceAtLeast(0);else->0L}
    val days=mutableListOf<LocalDate>()
    for(i in index until index+400){val day=billOccurrence(anchor,repeat,i) ?: break;if(!day.isBefore(end))break;if(!day.isBefore(start))days.add(day)}
    return days
}
fun billOccurrences(records:List<PersonalRecord>,payments:List<BillPayment>,start:LocalDate,end:LocalDate):List<BillOccurrence> = records.filter{it.kind=="bill" && !it.archived && it.date!=null}.flatMap{r->
    billDays(LocalDate.parse(r.date),JSONObject(r.details).optString("repeat","none"),start,end).map{day->BillOccurrence(r,day,payments.any{it.billId==r.id && it.date==day.toString()})}
}.sortedBy{it.date}
fun sleepDuration(start:String?,end:String?):Long?=runCatching{
    if(start.isNullOrBlank() || end.isNullOrBlank())return null
    val minutes=Duration.between(OffsetDateTime.parse(start),OffsetDateTime.parse(end)).toMinutes()
    require(minutes in 0..2880);minutes
}.getOrNull()
fun monthlySpent(records:List<PersonalRecord>,today:LocalDate)=records.filter{it.kind=="expense" && !it.archived && it.date!=null && it.date>=today.withDayOfMonth(1).toString() && it.date<=today.toString()}.sumOf{it.cents ?: 0}
