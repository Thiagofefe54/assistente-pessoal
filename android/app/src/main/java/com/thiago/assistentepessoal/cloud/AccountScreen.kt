package com.thiago.assistentepessoal.cloud

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.*
import kotlinx.coroutines.*

@Composable
fun AccountScreen(onBack:()->Unit) {
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    var email by rememberSaveable {mutableStateOf("")}
    var password by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var info by remember {mutableStateOf<String?>(null)}
    var importConfirm by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun action(block:suspend()->String) {
        if(busy)return
        busy=true;info=null
        scope.launch {
            try {info=block()}
            catch(e:Exception){if(e is CancellationException)throw e;info=if(e is CloudException)e.message else "Não consegui conectar. Confira sua internet e tente novamente."}
            finally{busy=false;password=""}
        }
    }
    BackHandler(enabled=busy) { }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        TextButton(onClick=onBack,enabled=!busy){Text("← Voltar")}
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){Eyebrow("CONEXÃO PESSOAL",KoiColors.Blue);Spacer(Modifier.height(8.dp));Text("Conta Koiwai",fontSize=30.sp,fontWeight=FontWeight.Bold)}
            OrbitEmblem("spark",KoiColors.Blue,Modifier.size(76.dp))
        }
        Text("Sua história, sempre com você.",color=KoiColors.Muted,fontSize=15.sp)
        KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue) {
            if(account==null) {
                Text("Bem-vindo ao seu universo",fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                Text("Entre para guardar suas conversas na nuvem.",color=KoiColors.Muted,fontSize=13.sp)
                OutlinedTextField(email,{email=it},label={Text("E-mail")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),enabled=!busy)
                OutlinedTextField(password,{password=it},label={Text("Senha da sua conta Koiwai")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),visualTransformation=PasswordVisualTransformation(),enabled=!busy)
                KoiAction("Entrar",{action{app.auth.login(email,password);"Você entrou. Abra o chat para ver sua conversa."}},Modifier.fillMaxWidth(),!busy && email.isNotBlank() && password.isNotBlank())
                OutlinedButton(onClick={action{if(app.auth.signup(email,password))"Conta criada e conectada." else "Confira o e-mail de confirmação. Depois volte aqui e toque em Entrar."}},enabled=!busy && email.isNotBlank() && password.length>=6,modifier=Modifier.fillMaxWidth()){Text("Criar conta")}
            } else {
                KoiChip("Conta conectada",KoiColors.Blue)
                Text(account!!.email,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                Text("Consulte e sincronize suas conversas nesta conta.",color=KoiColors.Muted,fontSize=13.sp)
                KoiAction("Sincronizar agora",{action{val sync=app.cloudSync ?: error("Sincronização indisponível");sync.run();"Conversa sincronizada com a nuvem."}},Modifier.fillMaxWidth(),!busy)
            }
        }
        if(account!=null) {
            KoiPanel(Modifier.fillMaxWidth()) {
                Eyebrow("HISTÓRICO ANTERIOR")
                Text("Conversas de antes do login",fontWeight=FontWeight.SemiBold)
                Text("Copie para esta conta o histórico que estava salvo apenas neste celular.",color=KoiColors.Muted,fontSize=13.sp)
                OutlinedButton(onClick={importConfirm=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Copiar histórico local")}
            }
            TextButton(onClick={action{app.auth.logout();"Você saiu. O histórico da conta permanece guardado."}},enabled=!busy){Text("Sair da conta",color=KoiColors.Red)}
        }
        if(busy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth(),color=KoiColors.Blue)
        info?.let {KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){Text(it,fontSize=14.sp)}}
        Text("Sua conta e seu histórico local têm espaços separados.",color=KoiColors.Muted,fontSize=11.sp)
    }
    if(importConfirm) AlertDialog(onDismissRequest={importConfirm=false},title={Text("Copiar conversas locais?")},
        text={Text("As conversas anteriores ao login serão enviadas para a conta conectada. O original continuará neste celular.")},
        confirmButton={TextButton(onClick={importConfirm=false;action{app.importLocalHistory();app.cloudSync?.run();"Histórico local copiado e sincronizado."}}){Text("Copiar")}},
        dismissButton={TextButton(onClick={importConfirm=false}){Text("Cancelar")}})
}
