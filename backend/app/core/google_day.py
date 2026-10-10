"""Private day planning across calendars; never move or invent tasks."""
from datetime import date,datetime,timedelta
from zoneinfo import ZoneInfo
from backend.app.core.day_plan import day_plan


def combine_day(owner,authorization,day,timezone,events,partial):
    local=day_plan(owner,authorization,day,timezone)
    zone=ZoneInfo(timezone)
    midnight=datetime.combine(day,datetime.min.time(),zone)
    begin,end=midnight+timedelta(hours=8),midnight+timedelta(hours=22)
    busy=[]
    for event in events:
        if event.get('blocks_time') is False:continue
        try:
            start,finish=event.get('start',{}),event.get('end',{})
            if not isinstance(start,dict) or not isinstance(finish,dict):
                raise ValueError('Missing event schedule')
            if start.get('date'):
                a,b=date.fromisoformat(start['date']),date.fromisoformat(finish['date'])
                if b<=a:raise ValueError('Invalid event interval')
                if a<=day<b:busy.append((begin,end))
            elif start.get('dateTime') and finish.get('dateTime'):
                a=datetime.fromisoformat(start['dateTime'])
                b=datetime.fromisoformat(finish['dateTime'])
                if a.utcoffset() is None or b.utcoffset() is None or b<=a:
                    raise ValueError('Invalid event interval')
                a,b=a.astimezone(zone),b.astimezone(zone)
                if b>begin and a<end:busy.append((max(a,begin),min(b,end)))
            else:raise ValueError('Missing event schedule')
        except (ValueError,TypeError,KeyError):
            partial=True
    merged=[]
    for a,b in sorted(busy):
        if merged and a<=merged[-1][1]:merged[-1]=(merged[-1][0],max(b,merged[-1][1]))
        else:merged.append((a,b))
    collisions=[]
    for task in local['scheduled']:
        try:
            at=datetime.fromisoformat(day.isoformat()+'T'+task['time']).replace(tzinfo=zone)
            if any(a<=at<b for a,b in merged):collisions.append({'title':task['title'],'time':task['time']})
        except ValueError:partial=True
    gaps=[];cursor=begin
    for a,b in merged:
        if a>cursor:gaps.append({'start':cursor.strftime('%H:%M'),'end':a.strftime('%H:%M')})
        cursor=max(cursor,b)
    if cursor<end:gaps.append({'start':cursor.strftime('%H:%M'),'end':end.strftime('%H:%M')})
    return {'date':day.isoformat(),'conflicts':collisions,'windows':[] if partial else gaps,
        'priorities':local['priorities'],'scheduled':local['scheduled'],
        'partial':partial or local['partial'], 'note':
        'Recorte 08h–22h da agenda consultada. Consultas incompletas não mostram janelas livres. Janelas sem evento não garantem disponibilidade; '
        'outras agendas e duração das tarefas podem faltar. Nenhum horário foi alterado.'}
