package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.routine.TaskDraft
import com.thiago.assistentepessoal.routine.KoiTask
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TaskDraftTest {
    @Test fun overdueRespectsTaskTimezoneAndOptionalTimeAndCompletion() {
        val task=KoiTask("fixture",1,"Missão","","2026-10-07","09:00","America/Sao_Paulo","none",null,0,"")
        assertFalse(task.overdue(Instant.parse("2026-10-07T11:59:00Z")))
        assertTrue(task.overdue(Instant.parse("2026-10-07T12:00:00Z")))
        assertFalse(task.copy(time=null).overdue(Instant.parse("2026-10-07T23:00:00Z")))
        assertTrue(task.copy(time=null).overdue(Instant.parse("2026-10-08T03:00:00Z")))
        assertFalse(task.copy(completedAt="done").overdue(Instant.parse("2026-10-08T12:00:00Z")))
    }
    @Test fun invalidDatesAndUndatedRepeatsCannotBeSaved() {
        assertNotNull(TaskDraft("Missão","","2026-02-30","","none").error())
        assertNotNull(TaskDraft("Missão","","","09:00","none").error())
        assertNotNull(TaskDraft("Missão","","","","daily").error())
        assertNotNull(TaskDraft("Missão","","2026-10-07","24:00","daily").error())
        assertNull(TaskDraft("Missão","","2028-02-29","23:59","monthly").error())
        assertNull(TaskDraft("Missão","","","","none").error())
    }
}
