package com.thiago.assistentepessoal.tools

import android.app.role.RoleManager
import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.thiago.assistentepessoal.*

@Composable fun AssistantRolePanel(){
    val context=LocalContext.current
    var info by remember{mutableStateOf<String?>(null)}
    val request=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){
        info=if(Build.VERSION.SDK_INT>=29 && context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT))"Koi selecionada como assistente 💜" else "A escolha fica com você e com as opções deste Android."
    }
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple){
        Text("Koi como assistente do Android",fontSize=20.sp)
        Text("Chame a Koi pelo gesto do Android: um painel compacto aparece sobre a tela atual. Você pode falar, escrever ou continuar no Chat completo.",fontSize=12.sp,color=KoiColors.Muted)
        TextButton(onClick={context.startActivity(Intent(context,com.thiago.assistentepessoal.KoiAssistActivity::class.java))}){Text("Experimentar painel da Koi")}
        KoiAction("Escolher assistente padrão",{
            if(Build.VERSION.SDK_INT>=29){
                val roles=context.getSystemService(RoleManager::class.java)
                if(roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT))request.launch(roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                else openIntent(context,Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            }else openIntent(context,Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        })
        info?.let{Text(it,color=KoiColors.Blue,fontSize=12.sp)}
    }
}
