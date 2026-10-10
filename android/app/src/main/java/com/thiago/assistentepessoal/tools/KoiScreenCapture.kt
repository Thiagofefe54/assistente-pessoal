package com.thiago.assistentepessoal.tools

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import com.thiago.assistentepessoal.KoiwaiApplication
import com.thiago.assistentepessoal.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class SharedScreen(val id:String,val owner:String,val jpeg:String?,val error:String?)
object KoiSharedScreen {
    private val mutable=MutableStateFlow<SharedScreen?>(null)
    val pending=mutable.asStateFlow()
    fun publish(value:SharedScreen){mutable.value=value}
    fun clear(id:String){if(mutable.value?.id==id)mutable.value=null}
}

/** Each capture starts with a new system consent; there is no reusable recording token. */
class KoiScreenCaptureActivity:ComponentActivity(){
    private val consent=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==Activity.RESULT_OK && result.data!=null){
            runCatching{startForegroundService(Intent(this,KoiScreenCaptureService::class.java)
                .putExtra("consent",result.data).putExtra("result",result.resultCode))}
            moveTaskToBack(true)
        }
        finish()
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        if((application as KoiwaiApplication).auth.account.value==null){finish();return}
        if(savedInstanceState==null)consent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
}

class KoiScreenCaptureService:Service(){
    private val handler=Handler(Looper.getMainLooper())
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private var finished=false
    private var owner:String?=null
    private val channel="koi-screen-share"
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="cancel"){finishCapture("Captura cancelada.");return START_NOT_STICKY}
        if(projection!=null || finished)return START_NOT_STICKY
        owner=(application as KoiwaiApplication).auth.account.value?.id
        val data=if(Build.VERSION.SDK_INT>=33)intent?.getParcelableExtra("consent",Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra("consent")
        if(owner==null || data==null || intent?.getIntExtra("result",0)!=Activity.RESULT_OK){stopSelf();return START_NOT_STICKY}
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"Compartilhar tela com a Koi",NotificationManager.IMPORTANCE_LOW))
            val cancel=PendingIntent.getService(this,2,Intent(this,KoiScreenCaptureService::class.java).setAction("cancel"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val note=NotificationCompat.Builder(this,channel).setSmallIcon(R.drawable.ic_koi_foreground)
                .setContentTitle("Abra a tela que quer mostrar à Koi")
                .setContentText("Captura única em 7 segundos. Depois volte ao Chat para revisar.")
                .setOngoing(true).addAction(0,"Cancelar",cancel).build()
            if(Build.VERSION.SDK_INT>=29)startForeground(742,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(742,note)
            projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK,data)
            projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){finishCapture("Compartilhamento encerrado pelo Android.")}},handler)
            handler.postDelayed({if(!finished)capture()},7000)
            handler.postDelayed({if(!finished)finishCapture("Não consegui capturar. Tente novamente ou escolha uma captura de tela em Imagem.")},22000)
        } catch(_:Exception){finishCapture("Não consegui iniciar a captura. Autorize novamente pelo Android.")}
        return START_NOT_STICKY
    }
    private fun capture(){
        try {
            val manager=getSystemService(WindowManager::class.java)
            val bounds=if(Build.VERSION.SDK_INT>=30)manager.maximumWindowMetrics.bounds else {
                val metrics=android.util.DisplayMetrics();@Suppress("DEPRECATION") manager.defaultDisplay.getRealMetrics(metrics)
                android.graphics.Rect(0,0,metrics.widthPixels,metrics.heightPixels)
            }
            val width=bounds.width();val height=bounds.height()
            require(width>0 && height>0 && width.toLong()*height<=24_000_000)
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
            reader?.setOnImageAvailableListener({source->
                val image=source.acquireLatestImage() ?: return@setOnImageAvailableListener
                image.use {
                    if(finished)return@setOnImageAvailableListener
                    try {
                        val plane=image.planes[0]
                        val rowWidth=plane.rowStride/plane.pixelStride
                        val padded=Bitmap.createBitmap(rowWidth,height,Bitmap.Config.ARGB_8888)
                        try {
                            padded.copyPixelsFromBuffer(plane.buffer)
                            val cropped=Bitmap.createBitmap(padded,0,0,width,height)
                            try {
                                val app=application as KoiwaiApplication
                                if(app.auth.account.value?.id!=owner){finishCapture(null);return@setOnImageAvailableListener}
                                KoiSharedScreen.publish(SharedScreen(UUID.randomUUID().toString(),owner!!,jpegForKoi(cropped),null))
                            } finally {if(cropped!==padded)cropped.recycle()}
                        } finally {padded.recycle()}
                        finishCapture(null)
                    } catch(_:Exception){finishCapture("Não consegui preparar a captura. Telas protegidas podem aparecer vazias.")}
                }
            },handler)
            display=projection?.createVirtualDisplay("Koi captura única",width,height,resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
        }catch(_:Exception){finishCapture("Não consegui capturar esta tela. Tente escolher uma imagem pelo Chat.")}
    }
    private fun finishCapture(error:String?){
        if(finished)return
        finished=true
        if(error!=null && owner!=null && (application as KoiwaiApplication).auth.account.value?.id==owner)
            KoiSharedScreen.publish(SharedScreen(UUID.randomUUID().toString(),owner!!,null,error))
        handler.removeCallbacksAndMessages(null)
        display?.release();display=null
        reader?.close();reader=null
        projection?.stop();projection=null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy(){finishCapture(null);super.onDestroy()}
}
