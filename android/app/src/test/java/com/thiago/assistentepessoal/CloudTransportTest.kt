package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.cloud.cloudEndpoint
import com.thiago.assistentepessoal.cloud.readBoundedText
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.StringReader

class CloudTransportTest {
    @Test fun authenticatedTransportCannotChangeDestination() {
        assertEquals("project.supabase.co",cloudEndpoint("https://project.supabase.co","/rest/v1/koi_tasks?select=id").host)
        for((base,path) in listOf("http://project.supabase.co" to "/rest/v1/x",
            "https://user:password@project.supabase.co" to "/rest/v1/x",
            "https://project.supabase.co/path" to "/rest/v1/x",
            "https://project.supabase.co" to "@evil.example/x",
            "https://project.supabase.co" to "//evil.example/x",
            "https://project.supabase.co" to "https://evil.example")) {
            try {cloudEndpoint(base,path);fail("Invalid target accepted")} catch(_:IOException) {}
        }
    }
    @Test fun oversizedResponseFailsInsteadOfReturningTruncatedData() {
        assertEquals("abc",StringReader("abc").readBoundedText(3))
        try {StringReader("abcd").readBoundedText(3);fail("Partial data was accepted")} catch(_:IOException) {}
    }
}
