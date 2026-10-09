package com.thiago.assistentepessoal.tools
import org.junit.Assert.*
import org.junit.Test
class KoiVoiceProfileTest{
    @Test fun spokenTextDoesNotReadEmojiOrRawLinks(){
        val text=speechText("Oi 💜 **Mestre**! https://example.com/path `teste`")
        assertFalse(text.contains("💜"));assertFalse(text.contains("https://"));assertFalse(text.contains("**"))
        assertTrue(text.contains("Mestre"));assertTrue(text.contains("teste"))
    }
    @Test fun spokenTextPreservesPortugueseAndAmounts(){assertEquals("Olá! R$ 12,50 às 19:00.",speechText("Olá! R$ 12,50 às 19:00."))}
}
