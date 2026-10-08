package com.thiago.assistentepessoal.tools

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import java.io.ByteArrayOutputStream

fun jpegForKoi(bitmap:Bitmap):String {
    val scale=minOf(1f,1280f/maxOf(bitmap.width,bitmap.height))
    val small=if(scale<1f)Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true) else bitmap
    try{
        var quality=85
        var bytes:ByteArray
        do{val output=ByteArrayOutputStream();check(small.compress(Bitmap.CompressFormat.JPEG,quality,output));bytes=output.toByteArray();quality-=15}while(bytes.size>800000 && quality>=25)
        require(bytes.size<=800000){"Escolha uma imagem menor."}
        return Base64.encodeToString(bytes,Base64.NO_WRAP)
    }finally{if(small!==bitmap)small.recycle()}
}
fun imageForKoi(context:Context,uri:Uri):String {
    val bytes=context.contentResolver.openInputStream(uri)?.use{input->
        val output=ByteArrayOutputStream();val buffer=ByteArray(8192);var count=input.read(buffer)
        while(count>=0){require(output.size()+count<=10000000){"Escolha uma imagem de até 10 MB."};output.write(buffer,0,count);count=input.read(buffer)}
        output.toByteArray()
    } ?: error("Não consegui abrir a imagem.")
    val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
    require(options.outWidth>0 && options.outHeight>0){"Formato de imagem não reconhecido."}
    var sample=1;while(maxOf(options.outWidth,options.outHeight)/sample>1280)sample*=2
    options.inJustDecodeBounds=false;options.inSampleSize=sample
    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: error("Não consegui ler a imagem.")
    val orientation=runCatching{android.media.ExifInterface(bytes.inputStream()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
    val matrix=Matrix().apply{when(orientation){2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f);5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(270f);postScale(-1f,1f)};8->setRotate(270f)}}
    val rotated=if(orientation in 2..8)Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true) else bitmap
    try{return jpegForKoi(rotated)}finally{if(rotated!==bitmap)rotated.recycle();bitmap.recycle()}
}
