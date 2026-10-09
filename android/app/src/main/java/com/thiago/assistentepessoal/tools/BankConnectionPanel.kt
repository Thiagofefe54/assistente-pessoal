package com.thiago.assistentepessoal.tools

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.thiago.assistentepessoal.*
import com.thiago.assistentepessoal.routine.money
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable fun BankConnectionPanel(){
    val context=LocalContext.current;val app=context.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState();val owner=account?.id
    val scope=rememberCoroutineScope()
    var result by remember(owner){mutableStateOf<JSONObject?>(null)}
    var busy by remember(owner){mutableStateOf(false)};var info by remember(owner){mutableStateOf<String?>(null)}
    fun read(path:String){if(owner==null || busy)return;busy=true;info=null;result=null;scope.launch{
        try{val value=assistantRequest(app,owner,path,allowCached=false);if(app.auth.account.value?.id==owner)result=value}
        catch(e:CancellationException){throw e}
        catch(e:Exception){if(app.auth.account.value?.id==owner)info=e.message?:"Não consegui consultar a conexão."}
        finally{if(app.auth.account.value?.id==owner)busy=false}
    }}
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
        Text("Bancos · consulta pessoal",fontSize=20.sp)
        Text("Meu Pluggy oferece acesso pessoal por API. Inter divulgado pelo provedor; nextJoy ainda precisa de confirmação. Criar conta ou abrir o site não conecta a Koi.",fontSize=12.sp,color=KoiColors.Muted)
        TextButton(onClick={openIntent(context,Intent(Intent.ACTION_VIEW,Uri.parse("https://meu.pluggy.ai/")))}){Text("Abrir Meu Pluggy →")}
        TextButton(onClick={openIntent(context,Intent(Intent.ACTION_VIEW,Uri.parse("https://meu.pluggy.ai/en/api-guide")))}){Text("Como ativar o acesso pessoal →")}
        Text("Você autoriza o banco no serviço escolhido. A integração só será ativada depois de configurar as credenciais privadas no servidor para sua conta Koiwai. Não cole senhas ou chaves no chat.",fontSize=12.sp,color=KoiColors.Muted)
        KoiAction(if(busy)"Conferindo…" else "Conferir conexão da Koi",{read("bank-status")},enabled=owner!=null && !busy)
        KoiAction("Consultar saldos disponíveis",{read("bank-summary")},enabled=owner!=null && !busy)
        if(owner==null)Text("Entre na conta Koiwai para consultar.",fontSize=12.sp)
        info?.let{Text(it,color=KoiColors.Red,fontSize=12.sp)}
        result?.let{value->
            if(value.has("configured"))Text(if(value.getBoolean("configured"))"Servidor configurado. Consulte saldos para verificar o acesso." else "Ainda não configurada para sua conta. Complete os passos de ativação.",fontSize=13.sp)
            value.optJSONArray("accounts")?.let{rows->
                if(rows.length()==0)Text("Nenhuma conta BRL disponível nesta consulta.")
                for(i in 0 until rows.length()){val r=rows.getJSONObject(i)
                    Text("${r.getString("label")} · ${money(r.getLong("balance_cents"))}")
                    Text("Dados do provedor: ${r.getString("provider_updated_at")}",fontSize=11.sp,color=KoiColors.Muted)
                }
                if(rows.length()>0)Text("Total consultado: ${money(value.getLong("total_cents"))}")
                if(value.optBoolean("partial"))Text("Consulta parcial; não representa todas as contas ou moedas.",fontSize=12.sp,color=KoiColors.Red)
                Text("Consultado em ${value.getString("checked_at")}",fontSize=11.sp,color=KoiColors.Muted)
            }
            Text(value.optString("note"),fontSize=11.sp,color=KoiColors.Muted)
        }
        Text("Somente saldos disponíveis: sem extrato, pagamentos, importação automática ou avisos de dinheiro recebido. Não é atualização em tempo real e não envia dados à IA.",fontSize=12.sp,color=KoiColors.Muted)
    }
}
