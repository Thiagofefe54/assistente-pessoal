package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.routine.TaskDraft
import org.junit.Assert.*
import org.junit.Test

class TaskDraftTest {
    @Test fun invalidDatesAndUndatedRepeatsCannotBeSaved() {
        assertNotNull(TaskDraft("Missão","","2026-02-30","","none").error())
        assertNotNull(TaskDraft("Missão","","","09:00","none").error())
        assertNotNull(TaskDraft("Missão","","","","daily").error())
        assertNotNull(TaskDraft("Missão","","2026-10-07","24:00","daily").error())
        assertNull(TaskDraft("Missão","","2028-02-29","23:59","monthly").error())
        assertNull(TaskDraft("Missão","","","","none").error())
    }
}
