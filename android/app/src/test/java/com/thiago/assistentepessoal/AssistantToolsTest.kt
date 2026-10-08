package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.tools.calculate
import com.thiago.assistentepessoal.tools.calculationReply
import com.thiago.assistentepessoal.routine.parseCents
import org.junit.Assert.*
import org.junit.Test

class AssistantToolsTest{
    @Test fun decimalArithmeticDoesNotRoundMoneyThroughFloatingPoint(){
        assertEquals("0,3",calculate("0,1 + 0,2"))
        assertEquals("40",calculate("(12,50+7,50)*2"))
        assertEquals("0,25",calculate("25%"))
        assertEquals("-6",calculate("-(2+4)"))
    }
    @Test fun parserRejectsCodeAndInvalidOperations(){
        for(value in listOf("Runtime.exec(1)","1/0","2 +","2**3","(".repeat(40)+"1"+")".repeat(40))){assertTrue(runCatching{calculate(value)}.isFailure)}
        assertNull(calculationReply("Crie uma tarefa 2+2"))
        assertEquals("4 💜",calculationReply("Quanto é 2+2?"))
    }
    @Test fun financialInputUsesExactCentsAndRejectsAmbiguousFractions(){
        assertEquals(1250L,parseCents("R$ 12,50"));assertEquals(30L,parseCents("0.30"))
        assertNull(parseCents("12,345"));assertNull(parseCents("-10"));assertNull(parseCents("1e3"))
    }
}
