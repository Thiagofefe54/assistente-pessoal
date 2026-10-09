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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun BankConnectionPanel(queryMode:String?=null,autoRead:Boolean=false){
    val context=LocalContext.current;val app=context.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState();val owner=account?.id
    val scope=rememberCoroutineScope()
    var result by remember(owner){mutableStateOf<JSONObject?>(null)}
    var day by remember(owner){mutableStateOf<JSONObject?>(null)}
    var details by remember(owner){mutableStateOf(false)}
    var busy by remember(owner){mutableStateOf(false)};var info by remember(owner){mutableStateOf<String?>(null)}
    fun read(path:String){if(owner==null || busy)return;busy=true;info=null;result=null;day=null;scope.launch{
        try{val value=assistantRequest(app,owner,if(path=="bank-plan")"bank-summary" else path,allowCached=false)
            if(app.auth.account.value?.id==owner)result=value
            if(path=="bank-plan"){
                val currentDay=assistantRequest(app,owner,"day",allowCached=false)
                if(app.auth.account.value?.id==owner)day=currentDay
            }
        }
        catch(e:CancellationException){throw e}
        catch(e:Exception){if(app.auth.account.value?.id==owner)info=if(path=="bank-plan" && result!=null)"O saldo foi consultado, mas não consegui atualizar as contas cadastradas. Tente novamente." else e.message?:"Não consegui consultar a conexão."}
        finally{if(app.auth.account.value?.id==owner)busy=false}
    }}
    LaunchedEffect(owner,autoRead){if(owner!=null && autoRead)read(if(queryMode=="balance")"bank-summary" else "bank-plan")}
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
        Text(if(queryMode=="balance")"Saldo do Inter" else "Saldo e próximas contas",fontSize=20.sp)
        Text("Inter · saldo e contas cadastradas · sem gastar pontos de IA",fontSize=12.sp,color=KoiColors.Muted)
        Text("Consultar lê a última atualização da Pluggy; não sincroniza o Inter na hora. No acesso pessoal, a conexão atualiza diariamente.",fontSize=12.sp,color=KoiColors.Muted)
        KoiAction(if(busy)"Conferindo…" else "Consultar dados do provedor",{read(if(queryMode=="balance")"bank-summary" else "bank-plan")},enabled=owner!=null && !busy)
        TextButton(onClick={details=!details}){Text(if(details)"Fechar detalhes e conexão ↑" else "Detalhes e conexão ↓")}
        if(details){
        TextButton(onClick={openIntent(context,Intent(Intent.ACTION_VIEW,Uri.parse("https://meu.pluggy.ai/")))}){Text("Abrir Meu Pluggy →")}
        TextButton(onClick={openIntent(context,Intent(Intent.ACTION_VIEW,Uri.parse("https://meu.pluggy.ai/en/api-guide")))}){Text("Como ativar o acesso pessoal →")}
        Text("Você autoriza o banco no serviço escolhido. A integração só será ativada depois de configurar as credenciais privadas no servidor para sua conta Koiwai. Não cole senhas ou chaves no chat.",fontSize=12.sp,color=KoiColors.Muted)
        KoiAction(if(busy)"Conferindo…" else "Conferir conexão da Koi",{read("bank-status")},enabled=owner!=null && !busy)
        KoiAction("Consultar saldos disponíveis",{read("bank-summary")},enabled=owner!=null && !busy)
        }
        if(owner==null)Text("Entre na conta Koiwai para consultar.",fontSize=12.sp)
        info?.let{Text(it,color=KoiColors.Red,fontSize=12.sp)}
        result?.let{value->
            if(value.has("configured"))Text(if(value.getBoolean("configured"))"Servidor configurado. Consulte saldos para verificar o acesso." else "Ainda não configurada para sua conta. Complete os passos de ativação.",fontSize=13.sp)
            value.optJSONArray("accounts")?.let{rows->
                if(rows.length()==0)Text("Nenhuma conta BRL disponível nesta consulta.")
                for(i in 0 until rows.length()){val r=rows.getJSONObject(i)
                    Text("${r.getString("label")} · último saldo informado: ${money(r.getLong("balance_cents"))}")
                    Text("Atualização do provedor: ${bankTime(r.getString("provider_updated_at"))}",fontSize=11.sp,color=KoiColors.Muted)
                }
                if(rows.length()>0)Text("Total na atualização do provedor: ${money(value.getLong("total_cents"))}")
                Text("Não é saldo em tempo real. Se o Inter mostra outro valor, confira por lá antes de planejar gastos.",fontSize=12.sp,color=KoiColors.Red)
                if(value.optBoolean("partial"))Text("Consulta parcial; não representa todas as contas ou moedas.",fontSize=12.sp,color=KoiColors.Red)
                Text("Consultado em ${bankTime(value.getString("checked_at"))}",fontSize=11.sp,color=KoiColors.Muted)
            }
            day?.let{current-> BankBalanceGuidance(value,current)}
            if(details)Text(value.optString("note"),fontSize=11.sp,color=KoiColors.Muted)
        }
        Text("Saldo pode estar desatualizado. Somente leitura; nenhuma conta é paga ou importada. Dados não enviados à IA.",fontSize=12.sp,color=KoiColors.Muted)
    }
}

private fun bankTime(value:String)=runCatching{Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))}.getOrDefault(value)

@Composable private fun BankBalanceGuidance(bank:JSONObject,day:JSONObject){
    val accounts=bank.getJSONArray("accounts");val bills=day.getJSONArray("bills_next_seven_days")
    val today=LocalDate.parse(day.getString("date"))
    val plan=runCatching{
        require(accounts.length()>0)
        val oldest=(0 until accounts.length()).map{Instant.parse(accounts.getJSONObject(it).getString("provider_updated_at"))}.minOrNull()!!
        compareBalanceWithBills(bank.getLong("total_cents"),(0 until bills.length()).map{bills.getJSONObject(it).getLong("amount_cents")},
            day.getInt("bill_count"),bank.optBoolean("partial")||day.optBoolean("records_partial")||today!=LocalDate.now(),oldest,Instant.now())
    }.getOrNull()
    Text("Seu dinheiro e os próximos vencimentos",fontSize=18.sp)
    Text("De ${today.format(DateTimeFormatter.ofPattern("dd/MM"))} a ${today.plusDays(7).format(DateTimeFormatter.ofPattern("dd/MM"))}, inclusive",fontSize=12.sp,color=KoiColors.Muted)
    if(plan==null){Text("Não consegui comparar os dados com segurança. Consulte novamente.",color=KoiColors.Red);return}
    Text("Contas cadastradas nesta janela: ${money(plan.billsCents)}")
    when(plan.state){
        BalancePlanState.INCOMPLETE->Text("A consulta está incompleta. Esse total considera apenas as contas exibidas; confira a lista antes de planejar.",color=KoiColors.Red)
        BalancePlanState.OLD_BALANCE->Text("O saldo não tem uma data recente confiável. Confira no Inter antes de comparar com os vencimentos.",color=KoiColors.Red)
        BalancePlanState.NO_BILLS->Text("Não há contas cadastradas nesta janela. Isso não significa que todo o saldo está livre, tá? 💜")
        BalancePlanState.SHORTFALL->Text("O saldo consultado está ${money(Math.negateExact(plan.differenceCents))} abaixo dessas contas. Confira os vencimentos e planeje essa diferença. 💜",color=KoiColors.Red)
        BalancePlanState.COVERED->Text("Uma ideia, mestre: separar ${money(plan.billsCents)} para essas contas. A diferença calculada é ${money(plan.differenceCents)}, mas pode haver gastos e compromissos ainda não registrados. 💜")
    }
    for(i in 0 until bills.length()){
        val b=bills.getJSONObject(i)
        Text("${b.getString("title")} · ${LocalDate.parse(b.getString("due")).format(DateTimeFormatter.ofPattern("dd/MM"))} · ${money(b.getLong("amount_cents"))}",fontSize=12.sp)
    }
    Text("Contas ainda não marcadas como pagas em ${day.getString("month")}: ${money(day.getLong("unpaid_month_cents"))}. Algumas já estão na lista acima; os totais não são somados.",fontSize=12.sp,color=KoiColors.Muted)
    if(day.optInt("budgets_exceeded")>0)Text("Há limite de orçamento ultrapassado nos seus registros. Confira em Rotina → Orçamento.",color=KoiColors.Red,fontSize=12.sp)
    Text("Comparação com o que você cadastrou na Koi, inclusive dados de teste. Não prevê entradas futuras nem outros gastos.",fontSize=11.sp,color=KoiColors.Muted)
}
