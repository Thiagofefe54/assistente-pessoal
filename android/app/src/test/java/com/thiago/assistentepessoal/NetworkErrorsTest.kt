package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.cloud.connectionMessage
import com.thiago.assistentepessoal.cloud.savedConnectionMessage
import java.io.IOException
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import org.junit.Assert.*
import org.junit.Test

class NetworkErrorsTest {
    @Test fun oldSavedDnsFailureIsReadableWithoutDeletingHistory() {
        assertEquals(connectionMessage(UnknownHostException()),savedConnectionMessage("Unable to resolve host koiwai-backend.onrender.com: No address associated with hostname"))
        assertEquals("Entre novamente.",savedConnectionMessage("Entre novamente."))
    }
    @Test fun dnsFailureExplainsNetworkWithoutClaimingServerOrPcIsDown() {
        val value=connectionMessage(UnknownHostException("Unable to resolve host koiwai-backend.onrender.com"))
        assertTrue(value.contains("VPN"));assertTrue(value.contains("outra rede"))
        assertFalse(value.contains("Unable"));assertFalse(value.contains("onrender.com"))
    }
    @Test fun timeoutAndTlsAreDistinctAndAccountErrorsRemainSpecific() {
        assertTrue(connectionMessage(SocketTimeoutException()).contains("pode estar iniciando"))
        assertTrue(connectionMessage(SSLException("private diagnostic")).contains("conexão segura"))
        assertEquals("Entre novamente na sua conta.",connectionMessage(IOException("Entre novamente na sua conta.")))
    }
}
