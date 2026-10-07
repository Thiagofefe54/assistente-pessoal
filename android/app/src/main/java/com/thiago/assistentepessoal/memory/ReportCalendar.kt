package com.thiago.assistentepessoal.memory

import java.time.LocalDate

fun reportStart(kind:String,anchor:String):String {
    val day=LocalDate.parse(anchor)
    return when(kind) {
        "day"->day
        "week"->day.minusDays((day.dayOfWeek.value-1).toLong())
        "month"->day.withDayOfMonth(1)
        "halfyear"->LocalDate.of(day.year,if(day.monthValue<=6)1 else 7,1)
        "year"->LocalDate.of(day.year,1,1)
        else->throw IllegalArgumentException("Período inválido")
    }.toString()
}
