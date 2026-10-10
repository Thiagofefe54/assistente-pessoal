package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.cloud.*
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.*
import org.junit.Test

class ReadRecoveryTest {
    @Test fun readsRecoverFromWakeupAndNetworkButStopAfterThreeRetries(){
        for(error in listOf(SocketTimeoutException(),UnknownHostException(),HttpFailure(503,"Acordando"),CloudException(429))){
            for(attempt in 0..2)assertTrue(retryReadFailure(error,attempt))
            assertFalse(retryReadFailure(error,3))
            assertFalse(retryReadFailure(error,-1))
        }
    }
    @Test fun expiredConsentConflictsTlsAndBadRequestsNeedIntervention(){
        for(code in listOf(400,401,403,404,409,422))assertFalse(retryReadFailure(HttpFailure(code,"Erro"),0))
        assertFalse(retryReadFailure(SSLException("Certificado"),0))
        assertFalse(retryReadFailure(IllegalStateException(),0))
    }
}
