"""Account-owned check-ins, daily review and explicit actions. No model calls."""
import hashlib
import json
from datetime import datetime, time, timedelta
from urllib.parse import urlencode
from uuid import UUID, uuid5
from zoneinfo import ZoneInfo
from fastapi import HTTPException
from backend.app.core.journal import cloud
from backend.app.core.personal import load_records, load_payments
from backend.app.core.tasks import load_task_rows
from backend.app.core.life import overview, bill_dates, sleep_minutes, validate_details

LABELS={'work':'Trabalho','gym':'Academia','study':'Estudos','home':'Casa','sleep':'Sono','other':'Meu dia'}
NAMESPACE=UUID('f8bfdcb6-cd46-40e2-b4ed-faa49dbe699f')


def clock(timezone,now=None):
    return (now or datetime.now(ZoneInfo(timezone))).astimezone(ZoneInfo(timezone))


def completions(owner,auth,start,end):
    rows=[]
    for offset in range(0,500,100):
        page=cloud('koi_task_completions?'+urlencode({'user_id':'eq.'+str(owner),
            'select':'id,task_id,title,scheduled_date,completed_at','and':f'(completed_at.gte.{start.isoformat()},completed_at.lt.{end.isoformat()})',
            'order':'completed_at.desc,id.asc','limit':100,'offset':offset}),auth)
        if not isinstance(page,list) or len(page)>100:raise HTTPException(503,'Não consegui conferir as conclusões.')
        rows.extend(page)
        if len(page)<100:break
    return rows


def review(owner,auth,timezone,now=None):
    moment=clock(timezone,now);today=moment.date();zone=ZoneInfo(timezone)
    records=load_records(owner,auth);payments=load_payments(owner,auth);tasks=load_task_rows(owner,auth)
    start=datetime.combine(today-timedelta(days=6),time.min,zone)
    done=completions(owner,auth,start,datetime.combine(today+timedelta(days=1),time.min,zone))
    def done_day(r):return datetime.fromisoformat(r['completed_at']).astimezone(zone).date()
    done_today=[r for r in done if done_day(r)==today]
    pending=[r for r in tasks if not r.get('completed_at') and not r.get('archived_at')]
    def local_today(r):return moment.astimezone(ZoneInfo(r.get('timezone') or timezone)).date()
    def late(r):
        if not r.get('due_date'):return False
        local=moment.astimezone(ZoneInfo(r.get('timezone') or timezone))
        return r['due_date']<local.date().isoformat() or (r['due_date']==local.date().isoformat() and bool(r.get('due_time')) and r['due_time'][:5]<local.strftime('%H:%M'))
    eligible=[r for r in pending if not r.get('due_date') or r['due_date']<=local_today(r).isoformat()]
    overdue=sorted((r for r in pending if late(r)),key=lambda r:(r['due_date'],r.get('due_time') or '',r['id']))
    habits=[]
    for r in pending:
        if r.get('recurrence','none')=='none':continue
        days={done_day(d) for d in done if d['task_id']==r['id']}
        habits.append({'id':r['id'],'title':r['title'],'date':r.get('due_date'),'time':(r.get('due_time') or '')[:5],
            'recurrence':r['recurrence'],'updated_at':r['updated_at'],'days_done':len(days),'done_today':today in days,
            'can_complete':bool(r.get('due_date') and r['due_date']<=local_today(r).isoformat())})
    habits.sort(key=lambda r:(not r['can_complete'],r['date'] or '9999',r['title']))
    diary=[r for r in records if r['kind']=='diary' and not r.get('archived_at')]
    diary_today=[r for r in diary if r.get('happened_on')==today.isoformat()]
    active=[];sleep=0;sleep_count=0
    for r in diary:
        details=r.get('details',{});started=details.get('started_at');ended=details.get('ended_at')
        if started and not ended and timedelta(0)<=moment-datetime.fromisoformat(started)<=timedelta(hours=48):
            active.append({'id':r['id'],'title':r['title'],'category':details.get('category','other'),
                'started_at':started,'updated_at':r['updated_at']})
        if details.get('category')=='sleep' and started and ended and datetime.fromisoformat(ended).astimezone(zone).date()==today:
            sleep+=sleep_minutes(details);sleep_count+=1
    active.sort(key=lambda r:r['started_at'],reverse=True)
    life=overview(records,payments,today)
    paid={(p['bill_id'],p['occurrence_on']) for p in payments}
    bills=[(r,day) for r in records if r['kind']=='bill' for day in bill_dates(r,today-timedelta(days=30),today+timedelta(days=4)) if (r['id'],day) not in paid]
    alerts=[]
    if overdue:alerts.append({'key':'tasks','area':'Tarefas','text':f'{len(overdue)} missão(ões) passaram do horário ou da data. Vamos escolher o próximo passo?'})
    if bills:alerts.append({'key':'bills','area':'Contas','text':f'{len(bills)} vencimento(s) pendente(s), dos últimos 30 dias até os próximos 3 dias.'})
    budgets=[r for r in life['budgets'] if r['spent_cents']>0 and r['spent_cents']*10>=r['limit_cents']*8]
    if budgets:alerts.append({'key':'budget','area':'Orçamento','text':f'{len(budgets)} limite(s) chegaram a 80% ou mais. Os gastos são separados por categoria.'})
    suggestions=[]
    recent_categories={r.get('details',{}).get('category') for r in diary_today}
    context='Você registrou atividades hoje. Podemos ajustar uma pendência para amanhã.' if recent_categories & {'gym','work','study','sleep'} else 'Essa missão está atrasada. Você pode levá-la para amanhã e manter o horário.'
    for r in overdue[:3]:
        suggestions.append({'task_id':r['id'],'title':r['title'],'updated_at':r['updated_at'],
            'target_date':(local_today(r)+timedelta(days=1)).isoformat(),'time':(r.get('due_time') or '')[:5],
            'timezone':r.get('timezone') or timezone,'reason':context})
    encouragement=(f'Você registrou {len(done_today)} conquista(s) hoje, Mestre. Um passo de cada vez também é progresso 💜'
        if done_today else 'Estou aqui, Mestre. Uma pequena conquista já pode deixar seu dia mais leve 💜')
    return {'date':today.isoformat(),'generated_at':moment.isoformat(),'completed_count':len(done_today),
        'completed':[{'title':r['title'],'time':datetime.fromisoformat(r['completed_at']).astimezone(zone).strftime('%H:%M')} for r in done_today[:10]],
        'pending_count':len(eligible),'overdue_count':len(overdue),'habit_count':len(habits),'habits':habits[:12],
        'active_checkins':active[:12],'diary_count':len(diary_today),'sleep_minutes':sleep if sleep_count else None,
        'sleep_intervals':sleep_count,'alerts':alerts,'suggestions':suggestions,'encouragement':encouragement,
        'partial':len(records)>=2000 or len(payments)>=2000 or len(tasks)>=500 or len(done)>=500 or len(habits)>12 or len(active)>12 or life['budget_count']>40 or len(done_today)>10,
        'scope':'Conclusões são eventos registrados; reabrir uma tarefa não apaga sua conquista anterior. Hábitos mostram dias com conclusão nos últimos 7 dias, não frequência ideal. Sono usa intervalos informados, não medição. Sugestões não alteram nada sozinhas.'}


def digest(payload):return hashlib.sha256(json.dumps(payload,sort_keys=True,ensure_ascii=False,default=str).encode()).hexdigest()


def cached(owner,auth,request_id,source,kind):
    table='koi_action_receipts' if kind=='task' else 'koi_tool_receipts'
    rows=cloud(table+'?'+urlencode({'user_id':'eq.'+str(owner),'request_id':'eq.'+str(request_id),
        'select':'result,source_hash,undone','limit':1}),auth)
    if not rows:return None
    if rows[0]['source_hash']!=source:raise HTTPException(409,'Este pedido mudou. Abra uma nova ação.')
    return rows[0]['result'] | {'undone':rows[0]['undone']}


def receipt(result,kind):
    row=result.get('task' if kind=='task' else 'record',{})
    return {'request_id':str(result['request_id']),'target_kind':kind,'record_id':row.get('id'),
        'updated_at':row.get('updated_at'),'undone':bool(result.get('undone'))}


def checkin(owner,auth,body,now=None):
    source=digest(body.model_dump(mode='json'));request_id=body.request_id
    previous=cached(owner,auth,request_id,source,'record')
    if previous:return {'message':'Este registro já foi processado.','receipt':receipt(previous,'record')}
    moment=clock(body.timezone,now).replace(microsecond=0)
    if body.phase=='finish':
        row=next((r for r in load_records(owner,auth) if r['id']==str(body.record_id)),None)
        if not row or row['kind']!='diary' or row.get('archived_at') or row['updated_at']!=body.expected_updated_at:
            raise HTTPException(409,'O acontecimento mudou. Atualize para conferir.')
        details=dict(row.get('details',{}))
        if details.get('category')!=body.category or not details.get('started_at') or details.get('ended_at'):
            raise HTTPException(409,'Esse acontecimento não tem um início aberto para finalizar.')
        details['ended_at']=moment.isoformat()
        try:validate_details('diary',details,row['happened_on'])
        except ValueError:raise HTTPException(422,'Confira o intervalo no Diário; o limite é de 48 horas.') from None
        fields={'details':details};action='update';rid=str(body.record_id);version=body.expected_updated_at
    else:
        details={'category':body.category}
        if body.phase=='start':details['started_at']=moment.isoformat()
        fields={'kind':'diary','title':('Teste — ' if body.test_data else '')+('Comecei: ' if body.phase=='start' else 'Check-in: ')+LABELS[body.category],
            'content':('Dado fictício de teste. ' if body.test_data else '')+body.note.strip(),
            'happened_on':moment.date().isoformat(),'details':details}
        action='create';rid=str(uuid5(NAMESPACE,f'{owner}:{request_id}'));version=None
    result=cloud('rpc/apply_koi_personal_action',auth,'POST',{'request_id':str(request_id),'source_hash':source,
        'target_kind':'record','action':action,'record_id':rid,'expected_updated_at':version,'fields':fields})
    return {'message':'Registrei esse momento no seu Diário, Mestre 💜','receipt':receipt(result,'record')}


def task_action(owner,auth,body,now=None):
    source=digest(body.model_dump(mode='json'));previous=cached(owner,auth,body.request_id,source,'task')
    if previous:return {'message':'Esta ação já foi processada.','receipt':receipt(previous,'task')}
    row=next((r for r in load_task_rows(owner,auth) if r['id']==str(body.task_id)),None)
    if not row or row.get('archived_at') or row.get('completed_at') or row['updated_at']!=body.expected_updated_at:
        raise HTTPException(409,'A missão mudou. Atualize antes de agir.')
    today=clock(row.get('timezone') or body.timezone,now).date()
    fields={}
    if body.action=='reschedule':
        if not today<=body.target_date<=today+timedelta(days=7):raise HTTPException(422,'Escolha uma data entre hoje e os próximos 7 dias.')
        fields={'due_date':body.target_date.isoformat()}
    elif row.get('recurrence','none')!='none' and row.get('due_date') and row['due_date']>today.isoformat():
        raise HTTPException(409,'A próxima etapa do hábito ainda é futura. Confira a data em Hábitos.')
    result=cloud('rpc/apply_koi_action',auth,'POST',{'request_id':str(body.request_id),'source_hash':source,
        'action':'update' if body.action=='reschedule' else 'complete','task_id':str(body.task_id),
        'expected_updated_at':body.expected_updated_at,'fields':fields})
    return {'message':'Missão reorganizada, mantendo seu horário 💜' if fields else 'Mais uma conquista, Mestre! 💜','receipt':receipt(result,'task')}


def undo(auth,body):
    kind=body.target_kind
    result=cloud('rpc/'+('undo_koi_action' if kind=='task' else 'undo_koi_personal_action'),auth,'POST',{'request_id':str(body.request_id)})
    return {'message':'Ação desfeita 💜','receipt':receipt(result,kind)}
