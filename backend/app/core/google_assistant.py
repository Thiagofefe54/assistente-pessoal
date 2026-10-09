"""Google tools: private live-read cards and explicit, owner-bound writes."""
import json
import re
from datetime import date, datetime, timedelta
from urllib.parse import quote, urlencode
from uuid import UUID
from zoneinfo import ZoneInfo
from email.utils import parsedate_to_datetime
from fastapi import HTTPException
from backend.app.core import google_connections as g
from backend.app.core.schedule import plain


def accounts(owner, selector=None):
    rows=g.list_accounts(owner)['accounts']
    rows=sorted(rows,key=lambda r:r['email'].casefold())
    if selector:
        rows=[r for r in rows if r['email'].casefold()==selector.casefold()]
    return rows


def google_call(owner, connection, service, path, method='GET', payload=None, etag=None):
    row=g.account(owner,connection)
    if g.SCOPE[service] not in row['scopes']:
        raise HTTPException(403,'Esta conta não autorizou este serviço Google.')
    headers={'Authorization':'Bearer '+g.access_token(owner,row)}
    if etag:headers['If-Match']=etag
    origin={'tasks':'https://tasks.googleapis.com/tasks/v1/',
            'calendar':'https://www.googleapis.com/calendar/v3/'}[service]
    data=g.request_json(origin+path,method=method,payload=payload,headers=headers)
    if not isinstance(data,dict):raise HTTPException(503,'O Google retornou uma resposta inválida.')
    return data


def items(data):
    value=data.get('items',[])
    if not isinstance(value,list) or any(not isinstance(i,dict) for i in value):
        raise HTTPException(503,'O Google retornou uma lista inválida.')
    return value


def identifier(value):
    if not isinstance(value,str) or not 1<=len(value)<=1024:
        raise HTTPException(503,'O Google retornou um identificador inválido.')
    return quote(value,safe='')


def explicit_times(message):
    text=plain(message)
    times=set()
    for h,m in re.findall(r'\b(\d{1,2}):(\d{2})\b',text):
        if int(h)<24 and int(m)<60:times.add((int(h),int(m)))
    for h,m in re.findall(r'\b(\d{1,2})h(\d{2})?\b',text):
        if int(h)<24 and int(m or '0')<60:times.add((int(h),int(m or '0')))
    for h in re.findall(r'\b(?:das|as|ate)\s+(\d{1,2})\b',text):
        if int(h)<24:times.add((int(h),0))
    return times


def event_time(value):
    if not isinstance(value,dict):return {}
    return {k:v[:100] for k,v in value.items() if k in ('date','dateTime','timeZone') and isinstance(v,str)}


def version(value):
    return value if isinstance(value,str) and 1<=len(value)<=2048 and not any(ord(c)<32 or ord(c)==127 for c in value) else None


def read(owner, connection, service, start=None, end=None, query=None, timezone='America/Sao_Paulo'):
    if service not in ('calendar','task_items'):
        result=g.consult(owner,connection,service)
        if (start or end) and service in ('mail','drive'):
            first=date.fromisoformat(start) if start else None
            last=date.fromisoformat(end) if end else first
            filtered=[]
            for item in result['items']:
                try:
                    at=(parsedate_to_datetime(item.get('date','')) if service=='mail' else
                        datetime.fromisoformat(item.get('modifiedTime',''))).astimezone(ZoneInfo(timezone)).date()
                    if (first is None or at>=first) and (last is None or at<=last):filtered.append(item)
                except (TypeError,ValueError):pass
            result['items']=filtered
            result['partial']=True  # Date filtering is within the bounded recent slice.
        if query:
            needle=plain(query)
            result['items']=[i for i in result['items'] if needle in plain(str(i.get('name',i.get('subject',''))))]
            result['partial']=True
        return result
    row=g.account(owner,connection)
    today=datetime.now(ZoneInfo(timezone)).date()
    first=date.fromisoformat(start) if start else today
    last=date.fromisoformat(end) if end else first if start else first+timedelta(days=6)
    if last<first or (last-first).days>31:raise HTTPException(422,'Consulte até 31 dias por vez.')
    partial=False; result=[]
    if service=='calendar':
        clock=lambda d:datetime.combine(d,datetime.min.time(),ZoneInfo(timezone)).isoformat()
        data=google_call(owner,connection,'calendar','calendars/primary/events?'+urlencode({
            'timeMin':clock(first),'timeMax':clock(last+timedelta(days=1)),
            'singleEvents':'true','orderBy':'startTime','maxResults':20}))
        partial=bool(data.get('nextPageToken'))
        for i in items(data)[:20]:
            if i.get('status')=='cancelled':continue
            if query and plain(query) not in plain(str(i.get('summary',''))):continue
            result.append({'id':i.get('id'),'title':str(i.get('summary','Sem título'))[:200],
                           'matchable':isinstance(i.get('summary'),str) and len(i['summary'])<=200,
                           'start':event_time(i.get('start')),'end':event_time(i.get('end')),'etag':version(i.get('etag'))})
    else:
        lists=google_call(owner,connection,'tasks','users/@me/lists?maxResults=3')
        partial=bool(lists.get('nextPageToken'))
        for tasklist in items(lists)[:3]:
            data=google_call(owner,connection,'tasks','lists/'+identifier(tasklist.get('id'))+
                '/tasks?maxResults=20&showCompleted=true&showHidden=false')
            partial=partial or bool(data.get('nextPageToken'))
            for i in items(data)[:20]:
                due=str(i.get('due',''))[:10]
                if start and due and not first.isoformat()<=due<=last.isoformat():continue
                if start and not due:continue
                if query and plain(query) not in plain(str(i.get('title',''))):continue
                result.append({'id':i.get('id'),'list_id':tasklist.get('id'),
                    'list':str(tasklist.get('title',''))[:200], 'title':str(i.get('title',''))[:200],
                    'matchable':isinstance(i.get('title'),str) and len(i['title'])<=200,
                    'due':due,'status':i.get('status'),'etag':version(i.get('etag'))})
    return {'account':g.metadata(row),'service':service,'items':result,
            'partial':partial,'checked_at':g.stamp(), 'note':
            'Até três listas e vinte tarefas por lista; vencimento Google Tasks é por dia, sem alarme.'
            if service=='task_items' else 'Agenda principal; até vinte eventos no período.'}


def previous(owner, request_id, message, timezone):
    rows=g.database('koi_google_actions',{'user_id':'eq.'+str(owner),'request_id':'eq.'+str(request_id),'limit':1})
    if not rows:return None
    row=rows[0]
    expected=g.digest(message+'\n'+timezone)
    if row['message_hash']!=expected:raise HTTPException(409,'Este pedido já foi usado para outra mensagem.')
    if row['phase']!='done':
        return {'reply':'Esse pedido Google já foi iniciado, mas o resultado não foi confirmado. Confira a conta Google antes de repetir com uma nova mensagem 💜','action_receipt':None}
    return g.unseal(owner,'google-action:'+str(request_id),row['response'])


def handle(semantic, owner, request_id, message, timezone):
    tool=semantic.google
    if semantic.domain!='google':return None
    if not tool or semantic.speech_act not in ('question','request'):
        return {'reply':'Diga o que quer consultar ou mudar no Google, mestre 💜'}
    evidence=semantic.evidence.strip()
    if not evidence or evidence not in message:
        raise HTTPException(502,'Não consegui confirmar o pedido Google.')
    if semantic.operation=='read':
        text=plain(message)
        if (re.search(r'\b(nao|nunca)\b|\b(ele|ela)\s+(disse|falou|pediu)\b|\b(hipoteticamente|por exemplo|suponha|e se)\b',text)
                or not re.sub(r'"[^"\n]*"|“[^”\n]*”','',message).strip()):
            return {'reply':'Tudo bem, mestre 💜 Não iniciei nenhuma consulta Google nesse pedido.'}
    if semantic.operation!='read':
        old=previous(owner,request_id,message,timezone)
        if old:return old
    if tool.account and tool.account.casefold() not in message.casefold():
        return {'reply':'Informe o e-mail da conta Google que você quer usar neste pedido 💜'}
    rows=accounts(owner,tool.account)
    if not rows:return {'reply':'Não encontrei essa conta Google conectada. Confira Conexões 💜'}
    if semantic.operation=='read':
        for value in (tool.start,tool.end):
            if value:date.fromisoformat(value)
        if tool.start and tool.end and (date.fromisoformat(tool.end)-date.fromisoformat(tool.start)).days not in range(32):
            return {'reply':'Posso consultar até 31 dias por vez. Qual período você quer? 💜'}
        # The planner receives no Google data. The Android card queries privately.
        return {'reply':'Preparei a consulta Google para você, mestre 💜 Os resultados aparecem no cartão, separados por conta.',
            'action_receipt':{'tool':'google','type':'read','request_id':str(request_id),
                'service':'task_items' if tool.service=='tasks' else tool.service,
                'connection_ids':[r['id'] for r in rows], 'start':tool.start,'end':tool.end,
                'query':tool.target or tool.title,'plan':tool.plan and tool.service=='calendar'}}
    operation=semantic.operation
    if not semantic.permits('google',operation,message):
        return {'reply':'Posso ajudar com o Google; para alterar algo, faça um pedido atual e claro 💜'}
    if tool.service not in ('calendar','tasks') or operation not in ('create','update','complete','reopen'):
        return {'reply':'Nesta etapa posso consultar Google e criar/ajustar Agenda e Tasks. Envio de e-mail e outras alterações ainda não estão disponíveis 💜'}
    if len(rows)!=1:
        return {'reply':'Qual conta Google devo usar? Escreva o e-mail junto com o pedido para eu alterar a conta certa 💜'}
    for title in (tool.title,tool.target):
        if title and plain(title) not in plain(message):
            return {'reply':'Inclua o título da tarefa ou compromisso no pedido atual, por favor 💜'}
    if operation=='create' and not tool.title:
        return {'reply':'Qual será o título no Google? 💜'}
    if operation!='create' and not tool.target:
        return {'reply':'Qual é o título exato do item Google que você quer alterar? 💜'}
    connection=rows[0]['id']; body={}; path=''; method='POST'; etag=None
    if tool.service=='calendar':
        if operation not in ('create','update'):
            return {'reply':'Eventos Google podem ser criados ou ajustados; concluir é uma ação de Tasks 💜'}
        if not tool.start or not tool.end:
            return {'reply':'Diga a data, o horário de início e o de fim do compromisso Google 💜'}
        first,last=datetime.fromisoformat(tool.start),datetime.fromisoformat(tool.end)
        if first.utcoffset() is None or last.utcoffset() is None or not timedelta(0)<last-first<=timedelta(hours=48):
            return {'reply':'Use horários com fuso e duração de até 48 horas 💜'}
        source=explicit_times(message)
        local_first,local_last=first.astimezone(ZoneInfo(timezone)),last.astimezone(ZoneInfo(timezone))
        if (local_first.hour,local_first.minute) not in source or (local_last.hour,local_last.minute) not in source:
            return {'reply':'Escreva os horários de início e fim em números, como das 19:00 às 20:00, para eu conferir sem inventar a duração 💜'}
        body={'start':{'dateTime':first.isoformat()},'end':{'dateTime':last.isoformat()}}
        if tool.title:body['summary']=tool.title
        path='calendars/primary/events?sendUpdates=none'
        if operation=='create':body['id']='koi'+UUID(str(request_id)).hex
        else:
            data=read(owner,connection,'calendar',query=tool.target,timezone=timezone)
            if data['partial']:return {'reply':'A consulta da agenda ficou parcial. Ajuste esse evento pela Agenda para não alterar o item errado 💜'}
            candidates=data['items']
            exact=[i for i in candidates if i.get('matchable',True) and plain(i['title'])==plain(tool.target)]
            if len(exact)!=1:return {'reply':'Não encontrei um único evento com esse título nos próximos sete dias. Informe um título único ou ajuste pela Agenda 💜'}
            path='calendars/primary/events/'+identifier(exact[0]['id'])+'?sendUpdates=none'
            method='PATCH';etag=exact[0]['etag']
    else:
        if tool.start:
            body['due']=date.fromisoformat(tool.start).isoformat()+'T00:00:00.000Z'
        if tool.title:body['title']=tool.title
        path='lists/@default/tasks'
        if operation!='create':
            data=read(owner,connection,'task_items',query=tool.target,timezone=timezone)
            if data['partial']:return {'reply':'Há mais tarefas fora desta consulta. Ajuste pelo Google Tasks para não alterar o item errado 💜'}
            exact=[i for i in data['items'] if i.get('matchable',True) and plain(i['title'])==plain(tool.target)]
            if len(exact)!=1:return {'reply':'Não encontrei uma única tarefa com esse título nas listas consultadas. Informe um título único ou ajuste pelo Google Tasks 💜'}
            target=exact[0]
            path='lists/'+identifier(target['list_id'])+'/tasks/'+identifier(target['id'])
            method='PATCH';etag=target['etag']
            if operation=='complete':body={'status':'completed'}
            elif operation=='reopen':body={'status':'needsAction','completed':None}
            elif not body:return {'reply':'O que quer alterar: título ou vencimento por dia? 💜'}
    if method=='PATCH' and not etag:
        raise HTTPException(409,'Não consegui verificar a versão atual do item Google.')
    old=previous(owner,request_id,message,timezone)
    if old:return old
    # Claim before any external write. Uncertain writes are never silently repeated.
    try:
        claimed=g.database('koi_google_actions',method='POST',payload={'user_id':str(owner),
            'request_id':str(request_id),'message_hash':g.digest(message+'\n'+timezone),'phase':'started'})
    except HTTPException:
        old=previous(owner,request_id,message,timezone)
        if old:return old
        raise
    if not isinstance(claimed,list) or len(claimed)!=1:
        raise HTTPException(503,'Não consegui proteger esse pedido contra repetição. Nenhuma ação Google foi enviada.')
    result=google_call(owner,connection,tool.service,path,method,body,etag)
    if not result.get('id') or (operation=='complete' and result.get('status')!='completed'):
        raise HTTPException(503,'O Google não confirmou o resultado. Confira antes de repetir.')
    for key in ('title','summary','status'):
        if key in body and result.get(key)!=body[key]:
            raise HTTPException(503,'O Google não confirmou a alteração pedida. Confira antes de repetir.')
    if 'due' in body and str(result.get('due',''))[:10]!=body['due'][:10]:
        raise HTTPException(503,'O Google não confirmou o vencimento pedido. Confira antes de repetir.')
    if tool.service=='calendar':
        if operation=='create' and result['id']!=body['id']:
            raise HTTPException(503,'O Google não confirmou a identidade do evento.')
        for key in ('start','end'):
            if datetime.fromisoformat(result.get(key,{}).get('dateTime',''))!=datetime.fromisoformat(body[key]['dateTime']):
                raise HTTPException(503,'O Google não confirmou os horários pedidos.')
    answer={'reply':f"Prontinho, mestre 💜 O Google confirmou a ação na conta {rows[0]['email']}. " +
        ('No Tasks, o vencimento é por dia; ele não cria um alarme no celular.' if tool.service=='tasks' and tool.start else ''),
        'action_receipt':{'tool':'google','type':'saved','request_id':str(request_id),
                          'service':tool.service,'connection_ids':[connection]}}
    saved=g.database('koi_google_actions',{'user_id':'eq.'+str(owner),'request_id':'eq.'+str(request_id)},'PATCH',
        {'phase':'done','response':g.seal(owner,'google-action:'+str(request_id),answer)})
    if not isinstance(saved,list) or len(saved)!=1:
        raise HTTPException(503,'O Google respondeu, mas não consegui guardar o recibo. Confira antes de repetir.')
    return answer
