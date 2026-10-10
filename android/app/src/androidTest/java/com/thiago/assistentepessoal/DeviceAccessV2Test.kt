package com.thiago.assistentepessoal

import android.app.Notification
import android.content.ComponentName
import androidx.test.platform.app.InstrumentationRegistry
import com.thiago.assistentepessoal.tools.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class DeviceAccessV2Test {
    @Test fun listenerIsSystemProtectedAndCaptureIsPrivate(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val listener=context.packageManager.getServiceInfo(ComponentName(context,KoiNotificationListener::class.java),0)
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",listener.permission)
        assertFalse(context.packageManager.getServiceInfo(ComponentName(context,KoiScreenCaptureService::class.java),0).exported)
        assertFalse(context.packageManager.getActivityInfo(ComponentName(context,KoiScreenCaptureActivity::class.java),0).exported)
    }
    @Test fun notificationViewDropsRemovedMessagesAndBoundsMemory(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(!PhoneMessages.enabled(context))
        try{
            repeat(50){PhoneMessages.put(PhoneMessage("fixture-$it","Fictício","Título","Texto",it.toLong(),null))}
            assertEquals(40,PhoneMessages.items.value.size)
            assertEquals("fixture-49",PhoneMessages.items.value.first().key)
            PhoneMessages.remove("fixture-49");assertEquals(39,PhoneMessages.items.value.size)
            assertTrue(PhoneMessages.visible(Notification()))
            assertFalse(PhoneMessages.visible(Notification().apply{flags=Notification.FLAG_GROUP_SUMMARY}))
            assertFalse(PhoneMessages.visible(Notification().apply{flags=Notification.FLAG_ONGOING_EVENT}))
        }finally{PhoneMessages.clear()}
        assertTrue(PhoneMessages.items.value.isEmpty())
    }
    @Test fun authorizedFreshStartPreservesLoginAndConnectionsWithoutHistory()=runBlocking{
        val expected=InstrumentationRegistry.getArguments().getString("verifyOwner")
        assumeTrue(expected!=null)
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        assertEquals(expected,app.auth.account.value?.id)
        val messages=withTimeout(15000){app.repositories.value.messages.first{it!=null}}
        assertEquals(0,messages!!.size)
        assertFalse(app.getSharedPreferences("koiwai-preferences",0).getBoolean("capture-reports",false))
        assertFalse(PhoneMessages.enabled(app))
        val google=assistantRequest(app,expected!!,"google-status",allowCached=false)
        assertEquals(3,google.getJSONArray("accounts").length())
    }
}
