package com.thiago.assistentepessoal.tools

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PhoneMessage(val key:String,val app:String,val title:String,val text:String,val time:Long,val open:PendingIntent?)

/** Only currently visible notifications; message bodies never go to disk or the server. */
object PhoneMessages {
    private val mutable=MutableStateFlow<List<PhoneMessage>>(emptyList())
    val items=mutable.asStateFlow()
    fun enabled(context:Context)=context.getSharedPreferences("koi-device-access",0).getBoolean("messages",false)
    fun allowed(context:Context)=context.getSharedPreferences("koi-device-access",0)
        .getStringSet("message-apps",setOf("com.whatsapp","com.google.android.apps.messaging"))!!.toSet()
    fun clear(){mutable.value=emptyList()}
    @Synchronized fun remove(key:String){mutable.value=mutable.value.filterNot{it.key==key}}
    @Synchronized fun put(message:PhoneMessage){
        mutable.value=(mutable.value.filterNot{it.key==message.key}+message).sortedByDescending{it.time}.take(40)
    }
    fun visible(notification:Notification)=notification.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY)==0
}

class KoiNotificationListener:NotificationListenerService(){
    override fun onListenerConnected(){
        PhoneMessages.clear()
        if(PhoneMessages.enabled(this))runCatching{activeNotifications?.forEach{onNotificationPosted(it)}}
    }
    override fun onNotificationPosted(sbn:StatusBarNotification?){
        if(sbn==null)return
        if(!PhoneMessages.enabled(this)){PhoneMessages.clear();return}
        if(sbn.packageName !in PhoneMessages.allowed(this) || !PhoneMessages.visible(sbn.notification)){
            PhoneMessages.remove(sbn.key);return
        }
        val extras=sbn.notification.extras
        val title=extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(120).orEmpty()
        val text=(extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.take(1500).orEmpty()
        if(title.isBlank() && text.isBlank()){PhoneMessages.remove(sbn.key);return}
        val label=runCatching{packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName,0)).toString()}
            .getOrDefault(sbn.packageName).take(100)
        PhoneMessages.put(PhoneMessage(sbn.key,label,title,text,sbn.postTime,sbn.notification.contentIntent))
    }
    override fun onNotificationRemoved(sbn:StatusBarNotification?){sbn?.let{PhoneMessages.remove(it.key)}}
    override fun onListenerDisconnected(){PhoneMessages.clear()}
    override fun onDestroy(){PhoneMessages.clear();super.onDestroy()}
}
