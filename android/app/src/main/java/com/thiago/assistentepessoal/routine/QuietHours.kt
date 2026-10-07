package com.thiago.assistentepessoal.routine

import java.time.ZonedDateTime
import java.time.Instant

/** Only an occurrence registered while enabled may deliver late. */
fun reminderEligible(due:Instant,now:Instant,previouslyScheduled:Boolean)=!due.isBefore(now) || previouslyScheduled

/** End is exclusive; equal endpoints mean silence disabled, not a 24h loop. */
fun quietRelease(now:ZonedDateTime,startMinute:Int,endMinute:Int):ZonedDateTime {
    require(startMinute in 0..1439 && endMinute in 0..1439)
    if(startMinute==endMinute) return now
    val current=now.hour*60+now.minute
    val inside=if(startMinute<endMinute) current in startMinute until endMinute
        else current>=startMinute || current<endMinute
    if(!inside) return now
    val nextDay=startMinute>endMinute && current>=startMinute
    return (if(nextDay)now.plusDays(1) else now).withHour(endMinute/60).withMinute(endMinute%60).withSecond(0).withNano(0)
}
