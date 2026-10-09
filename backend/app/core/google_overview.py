"""Bounded, owner-scoped Google overview. Results never go to the language model."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from fastapi import HTTPException
from backend.app.core import google_assistant as google
from backend.app.core.google_day import combine_day


def interval(event,day,zone):
    start,end=event.get('start',{}),event.get('end',{})
    if start.get('date'):
        a=datetime.fromisoformat(start['date']).replace(tzinfo=zone)
        b=datetime.fromisoformat(end['date']).replace(tzinfo=zone)
    else:
        a,b=datetime.fromisoformat(start['dateTime']),datetime.fromisoformat(end['dateTime'])
        if a.utcoffset() is None or b.utcoffset() is None:raise ValueError('Missing zone')
        a,b=a.astimezone(zone),b.astimezone(zone)
    if b<=a:raise ValueError('Invalid interval')
    midnight=datetime.combine(day,datetime.min.time(),zone)
    a,b=max(a,midnight),min(b,midnight+timedelta(days=1))
    return (a,b) if b>a else None


def overview(owner,authorization,day,timezone,connection_ids=None):
    rows=google.accounts(owner)
    if connection_ids:
        selected={str(value) for value in connection_ids}
        if not selected.issubset({str(row['id']) for row in rows}):
            raise HTTPException(404,'Uma das conexões Google não está disponível.')
        rows=[r for r in rows if str(r['id']) in selected]
    rows=rows[:3]
    if not rows:raise HTTPException(409,'Conecte uma conta com acesso à Agenda Google.')
    def fetch(row):
        try:
            if 'calendar' not in row['services']:raise HTTPException(403,'Agenda não autorizada.')
            return row,google.read(owner,row['id'],'calendar',day.isoformat(),day.isoformat(),timezone=timezone),None
        except HTTPException as error:
            return row,None,{'connection_id':row['id'],'email':row['email'],
                'reconnect':error.status_code in (403,409),
                'message':'Não foi possível consultar esta agenda. Confira a conexão e tente novamente.'}
    with ThreadPoolExecutor(max_workers=3) as pool:
        results=list(pool.map(fetch,rows))
    events=[];errors=[];checked=[];partial=False;zone=ZoneInfo(timezone)
    for row,data,error in results:
        if error:errors.append(error);partial=True;continue
        partial=partial or data['partial']
        checked.append({'connection_id':row['id'],'email':row['email'],'event_count':len(data['items'])})
        for item in data['items']:
            try:
                times=interval(item,day,zone)
                if not times:continue
                a,b=times
                events.append({'title':item['title'],'start':item['start'],'end':item['end'],
                    'blocks_time':item.get('blocks_time',True),'connection_id':row['id'],'email':row['email'],
                    'starts_at':a.isoformat(),'ends_at':b.isoformat(),
                    'all_day':bool(item.get('start',{}).get('date'))})
            except (ValueError,KeyError,TypeError):partial=True
    def instant(value):return datetime.fromisoformat(value).timestamp()
    events.sort(key=lambda e:(instant(e['starts_at']),e['email'],e['title']))
    # Compare strict overlapping busy intervals. Touching endpoints are not conflicts.
    conflicts=[];conflict_count=0
    for i,event in enumerate(events):
        if event.get('blocks_time') is False:continue
        for other in events[i+1:]:
            if other.get('blocks_time') is False:continue
            start=max((event['starts_at'],other['starts_at']),key=instant)
            end=min((event['ends_at'],other['ends_at']),key=instant)
            if instant(start)<instant(end):
                conflict_count+=1
                if len(conflicts)<20:conflicts.append({'first':{'title':event['title'],'email':event['email']},
                    'second':{'title':other['title'],'email':other['email']},
                    'start':start,'end':end})
    plan=combine_day(owner,authorization,day,timezone,events,partial)
    plan['note']='Recorte 08h–22h das agendas principais consultadas. Eventos livres/recusados não bloqueiam. Outras agendas, atividades e duração das tarefas podem faltar. Nenhum horário foi alterado.'
    if not checked:
        plan['windows']=[]
        plan['note']='Nenhuma agenda pôde ser consultada. Não é possível indicar janelas sem eventos agora. Suas tarefas da Koi aparecem abaixo.'
    return {'date':day.isoformat(),'timezone':timezone,'accounts':checked,'errors':errors,
        'items':events,'calendar_conflicts':conflicts,'calendar_conflict_count':conflict_count,
        'conflicts_partial':conflict_count>20,'partial':plan['partial'],'plan':plan,
        'checked_at':google.g.stamp(),'note':'Consulta privada ao vivo; sem IA, histórico ou cache.'}
