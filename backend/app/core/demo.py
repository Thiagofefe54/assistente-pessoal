"""Explicit opt-in fixtures; account-scoped RPC receipts make retries non-destructive."""
import hashlib
import json
from datetime import date, timedelta
from urllib.parse import urlencode
from uuid import UUID, uuid5
from fastapi import HTTPException
from backend.app.core.journal import cloud

VERSION='v15'
NAMESPACE=UUID('60d7e973-08bb-44bf-9f37-4c1e68394025')

def identity(owner,key,suffix='record'):
    return str(uuid5(NAMESPACE,f'{owner}:{VERSION}:{key}:{suffix}'))

def blueprint(owner,anchor,timezone):
    fictitious='Dado fictício de teste; não descreve sua vida real.'
    items=[]
    def record(key,kind,title,day=None,amount=None,details=None,content=fictitious):
        items.append((key,'record',{'kind':kind,'title':'Teste — '+title,'content':content,
            'happened_on':day.isoformat() if day else None,'amount_cents':amount,
            'progress':0,'details':details or {}}))
    record('anchor','diary','Dia de organização',anchor,details={'category':'study'})
    record('past','diary','Academia da semana passada',anchor-timedelta(days=7),details={'category':'gym'})
    record('food','expense','Lanche',anchor,1800,{'finance_category':'food'})
    record('transport','expense','Transporte',anchor,900,{'finance_category':'transport'})
    record('income','income','Entrada fictícia',anchor,15000,{'finance_category':'work'})
    record('total-budget','budget','Limite geral',anchor.replace(day=1),20000,{'finance_category':'all'})
    record('food-budget','budget','Limite alimentação',anchor.replace(day=1),3000,{'finance_category':'food'})
    record('note','note','Ideia de organização',content=fictitious+' Organizar estudos e descanso.')
    record('bill','bill','Conta de exemplo',anchor+timedelta(days=2),3500,{'repeat':'none'})
    for key,title,day,time in [('task-today','Estudar',anchor,'19:00'),('task-pending','Separar material',anchor-timedelta(days=1),None)]:
        items.append((key,'task',{'title':'Teste — '+title,'notes':fictitious,'due_date':day.isoformat(),
            'due_time':time,'recurrence':'none','timezone':timezone}))
    items.append(('memory','memory',{'content':'Teste — Lembrança fictícia: o personagem de teste gosta de organizar estudos. Não é uma preferência real sua.','category':'note'}))
    return [{'key':key,'target_kind':kind,'id':identity(owner,key),'request_id':identity(owner,key,'request'),
             'fields':fields} for key,kind,fields in items]

def seed_demo(owner,authorization,anchor,timezone):
    # Recover the original anchor from its receipt, including retries on another device/day.
    receipts=cloud('koi_tool_receipts?'+urlencode({'user_id':'eq.'+str(owner),
        'request_id':'eq.'+identity(owner,'anchor','request'),'select':'result','limit':1}),authorization)
    if receipts:
        anchor=date.fromisoformat(receipts[0]['result']['record']['happened_on'])
    ledger=[]
    for item in blueprint(owner,anchor,timezone):
        # Stable source describes a fixture, not a user's changing personal data.
        source=hashlib.sha256(json.dumps([VERSION,item['key']],sort_keys=True).encode()).hexdigest()
        fields=item['fields']
        payload={'request_id':item['request_id'],'source_hash':source,'action':'create',
                 'expected_updated_at':None,'fields':fields}
        if item['target_kind']=='task':
            payload['task_id']=item['id']; path='rpc/apply_koi_action'
        else:
            payload.update(target_kind=item['target_kind'],record_id=item['id']);path='rpc/apply_koi_personal_action'
        try:
            result=cloud(path,authorization,'POST',payload)
            if not isinstance(result,dict) or str(result.get('request_id'))!=item['request_id']:
                raise HTTPException(503,'Recibo de teste inválido.')
        except HTTPException:
            raise HTTPException(503,'Alguns exemplos podem já ter sido criados. Toque novamente para continuar sem duplicar; registros editados não serão sobrescritos.') from None
        ledger.append({k:item[k] for k in ('target_kind','id','request_id')})
    return {'version':VERSION,'anchor':anchor.isoformat(),'items':ledger,'count':len(ledger),
        'note':'Exemplos Teste. Entram nos totais até serem arquivados ou removidos. Repetir não duplica nem restaura exemplos que você alterou ou desfez.'}
