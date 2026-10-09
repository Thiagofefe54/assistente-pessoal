package com.thiago.assistentepessoal.tools

import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class GoogleLinksTest{
    @Test fun linksStayOnFixedGoogleServices(){
        listOf("drive" to "drive.google.com","mail" to "mail.google.com","calendar" to "calendar.google.com","task_items" to "tasks.google.com").forEach{(service,host)->
            val url=googleItemUrl(service,"fixture@example.com","abcd")!!
            assertEquals("https",URI(url).scheme);assertEquals(host,URI(url).host)
        }
    }
    @Test fun arbitraryIdsCannotBecomeLinks(){
        for(id in listOf("https://evil.example","../other","abc?key=secret","a#fragment","")){
            assertNull(googleItemUrl("drive","fixture@example.com",id));assertNull(googleItemUrl("mail","fixture@example.com",id))
        }
    }
    @Test fun accountSelectorIsEncoded(){
        val url=googleItemUrl("mail","fixture+test@example.com&evil=true","abcd")!!
        assertFalse(url.contains("&evil=true"));assertTrue(url.contains("%26evil%3Dtrue"))
        assertEquals("mail.google.com",URI(url).host)
    }
    @Test fun unsupportedServicesHaveNoInventedItemLink(){assertNull(googleItemUrl("tasks","fixture@example.com","abcd"))}
}
