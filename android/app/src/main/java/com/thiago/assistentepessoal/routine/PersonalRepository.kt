package com.thiago.assistentepessoal.routine

import com.thiago.assistentepessoal.cloud.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import java.util.UUID
import java.security.MessageDigest
import java.time.LocalDate

val personalKinds=linkedMapOf("note" to "Notas","list" to "Listas","goal" to "Metas","workout" to "Treinos","expense" to "Despesas","income" to "Receitas","bill" to "Contas","budget" to "Orçamento","diary" to "Diário")
data class PersonalRecord(val id:String,val kind:String,val title:String,val content:String,
    val cents:Long?,val progress:Int,val date:String?,val archived:Boolean,val version:String,val details:String="{}")
fun personalRecord(row:JSONObject)=PersonalRecord(row.getString("id"),row.getString("kind"),row.getString("title"),row.getString("content"),
    if(row.isNull("amount_cents"))null else row.getLong("amount_cents"),row.getInt("progress"),
    if(row.isNull("happened_on"))null else row.getString("happened_on"),!row.isNull("archived_at"),row.getString("updated_at"),row.optJSONObject("details")?.toString() ?: "{}")
fun money(cents:Long)=java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("pt-BR")).format(java.math.BigDecimal.valueOf(cents,2))
fun parseCents(text:String):Long?=runCatching {
    val input=text.trim().removePrefix("R$").trim()
    val normalized=if(Regex("[0-9]{1,3}(\\.[0-9]{3})+,[0-9]{1,2}").matches(input))input.replace(".","").replace(",",".") else input.replace(",",".")
    require(Regex("[0-9]{1,9}(\\.[0-9]{1,2})?").matches(normalized))
    java.math.BigDecimal(normalized).movePointRight(2).longValueExact().also{require(it in 0..100000000000L)}
}.getOrNull()

class PersonalRepository(private val auth:CloudAuth,private val owner:String,private val onLoaded:(List<PersonalRecord>,List<BillPayment>)->Unit={_,_->}) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val _records=MutableStateFlow<List<PersonalRecord>?>(null)
    val records=_records.asStateFlow()
    private val _payments=MutableStateFlow<List<BillPayment>>(emptyList())
    val payments=_payments.asStateFlow()
    private val _busy=MutableStateFlow(false)
    val busy=_busy.asStateFlow()
    private val _info=MutableStateFlow<String?>(null)
    val info=_info.asStateFlow()
    private val _undo=MutableStateFlow<String?>(null)
    val undo=_undo.asStateFlow()
    // Keep ambiguous requests unchanged. Retrying must reuse the same identity.
    private val pending=mutableMapOf<String,Pair<String,JSONObject>>()
    fun close(){scope.cancel()}
    private suspend fun request(path:String,method:String="GET",body:JSONObject?=null):String {
        check(auth.account.value?.id==owner)
        return withContext(Dispatchers.IO){CloudApi.request(path,method,body?.toString(),auth.token(owner),"return=representation")}
    }
    private suspend fun fetch(){
        val result=mutableListOf<PersonalRecord>()
        for(offset in 0 until 2000 step 100){
            val rows=JSONArray(request("/rest/v1/koi_personal_records?user_id=eq.$owner&select=*&order=updated_at.desc,id.asc&limit=100&offset=$offset"))
            result.addAll((0 until rows.length()).map{personalRecord(rows.getJSONObject(it))})
            if(rows.length()<100)break
        }
        val paid=mutableListOf<BillPayment>()
        for(offset in 0 until 2000 step 100){
            val rows=JSONArray(request("/rest/v1/koi_bill_payments?user_id=eq.$owner&select=bill_id,occurrence_on,expense_id&order=occurrence_on.desc,bill_id.asc&limit=100&offset=$offset"))
            paid.addAll((0 until rows.length()).map{val r=rows.getJSONObject(it);BillPayment(r.getString("bill_id"),r.getString("occurrence_on"),r.getString("expense_id"))})
            if(rows.length()<100)break
        }
        if(auth.account.value?.id==owner){_records.value=result;_payments.value=paid;onLoaded(result,paid)}
    }
    private fun action(block:suspend()->Unit){
        if(_busy.value)return
        _busy.value=true;_info.value=null
        scope.launch {try{block()}catch(e:Exception){
            if(e is CancellationException)throw e
            _info.value=when{
                auth.account.value?.id!=owner->"A conta mudou. Reabra esta área."
                e is CloudException && e.code==409->"O registro mudou ou a área está cheia. Atualize para conferir."
                else->"Não consegui confirmar a ação. Atualize ou repita o mesmo pedido para conferir sem duplicar."
            }
        }finally{_busy.value=false}}
    }
    fun refresh()=action{fetch()}
    fun save(kind:String,title:String,content:String,cents:Long?,progress:Int,date:String?,existing:PersonalRecord?=null,id:String=UUID.randomUUID().toString(),details:JSONObject=JSONObject(),onSaved:()->Unit={}) {
        if(kind !in personalKinds || title.trim().isEmpty() || title.length>160 || content.length>8000 || progress !in 0..100)return
        if((kind in listOf("expense","income","bill","budget"))!=(cents!=null))return
        if(kind in listOf("bill","budget","diary") && date==null)return
        if(date!=null && runCatching{LocalDate.parse(date)}.isFailure)return
        val fields=JSONObject().put("kind",kind).put("title",title.trim()).put("content",content)
            .put("amount_cents",cents ?: JSONObject.NULL).put("progress",progress).put("happened_on",date ?: JSONObject.NULL).put("details",details)
        mutate(if(existing==null)"create" else "update",existing,fields,id,onSaved)
    }
    fun archive(record:PersonalRecord)=mutate(if(record.archived)"restore" else "archive",record,JSONObject(),record.id)
    fun pay(record:PersonalRecord,due:String)=action{
        val key="pay:${record.id}:$due"
        val signature=MessageDigest.getInstance("SHA-256").digest((key+record.version).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        val body=pending[key]?.takeIf{it.first==signature}?.second ?: JSONObject().put("request_id",UUID.randomUUID().toString()).put("source_hash",signature)
            .put("bill_id",record.id).put("record_id",UUID.randomUUID().toString()).put("expected_updated_at",record.version).put("occurrence_on",due).put("paid_on",LocalDate.now().toString())
        pending[key]=signature to body
        val result=JSONObject(request("/rest/v1/rpc/pay_koi_bill","POST",body))
        check(result.getString("request_id")==body.getString("request_id"))
        _undo.value=result.getString("request_id");pending.remove(key);_info.value="Pagamento registrado e despesa salva 💜";fetch()
    }
    private fun mutate(operation:String,record:PersonalRecord?,fields:JSONObject,id:String,onSaved:()->Unit={})=action{
        val key="$operation:$id"
        val signature=MessageDigest.getInstance("SHA-256").digest((key+fields.toString()).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        val cached=pending[key]?.takeIf{it.first==signature}
        val body=cached?.second ?: JSONObject().put("request_id",UUID.randomUUID().toString()).put("source_hash",signature)
            .put("target_kind","record").put("action",operation).put("record_id",record?.id ?: id)
            .put("expected_updated_at",record?.version ?: JSONObject.NULL).put("fields",fields)
        pending[key]=signature to body
        val result=try{JSONObject(request("/rest/v1/rpc/apply_koi_personal_action","POST",body))}
            catch(e:CloudException){if(e.code==409)pending.remove(key);throw e}
        check(result.getString("request_id")==body.getString("request_id"))
        _undo.value=result.getString("request_id");pending.remove(key);onSaved()
        _info.value="${if(operation=="archive")"Arquivado" else if(operation=="restore")"Recuperado" else "Salvo"} na sua conta 💜"
        try{fetch()}catch(e:Exception){if(e is CancellationException)throw e;_info.value="Ação salva. Atualize a lista para conferir."}
    }
    fun undoLast()=action{
        val id=_undo.value ?: return@action
        request("/rest/v1/rpc/undo_koi_personal_action","POST",JSONObject().put("request_id",id))
        _undo.value=null;_info.value="Ação desfeita 💜";fetch()
    }
}
