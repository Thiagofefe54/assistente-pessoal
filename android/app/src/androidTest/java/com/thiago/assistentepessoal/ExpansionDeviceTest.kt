package com.thiago.assistentepessoal

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ExpansionDeviceTest {
    @Test fun privateSnapshotsAreIsolatedAndNeverReplayWrites(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val cache=AssistantReadCache(context,UUID.randomUUID().toString())
        val other=AssistantReadCache(context,UUID.randomUUID().toString())
        val request=JSONObject().put("timezone","America/Sao_Paulo")
        try{
            cache.save("review",request,JSONObject().put("value","Teste fictício"))
            val read=requireNotNull(cache.read("review",request))
            assertTrue(read.getBoolean("_offline"));assertEquals("Teste fictício",read.getString("value"))
            assertNull(other.read("review",request))
            assertNull(cache.read("review",JSONObject().put("timezone","UTC")))
            cache.save("checkin",request,JSONObject().put("done",true))
            assertNull(cache.read("checkin",request))
            cache.clear();assertNull(cache.read("review",request))
        }finally{cache.clear();other.clear()}
    }
    @Test fun assistantActionIsDeclaredAndUsesExistingActivity(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val intent=Intent(Intent.ACTION_ASSIST).setPackage(context.packageName)
        val target=context.packageManager.resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY)
        assertNotNull(target);assertEquals(MainActivity::class.java.name,target!!.activityInfo.name)
    }
}
