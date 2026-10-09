"""Prioritize real pending tasks. Suggestions do not write or invent durations."""
from backend.app.core.tasks import load_task_rows
from datetime import datetime
from zoneinfo import ZoneInfo


def day_plan(owner,authorization,today,timezone='America/Sao_Paulo'):
    rows=load_task_rows(owner,authorization)
    zone=ZoneInfo(timezone)
    normalized=[];invalid=False
    for row in rows:
        r=dict(row)
        if r.get('due_date') and r.get('due_time'):
            try:
                moment=datetime.fromisoformat(r['due_date']+'T'+r['due_time']).replace(tzinfo=ZoneInfo(r.get('timezone') or timezone)).astimezone(zone)
                r['due_date'],r['due_time']=moment.date().isoformat(),moment.strftime('%H:%M:%S')
            except (KeyError,ValueError,TypeError):
                invalid=True;continue
        normalized.append(r)
    rows=normalized
    pending=[r for r in rows if not r.get('archived_at') and not r.get('completed_at')]
    eligible=[r for r in pending if not r.get('due_date') or r['due_date']<=today.isoformat()]
    fixed=sorted((r for r in eligible if r.get('due_date')==today.isoformat() and r.get('due_time')),
                 key=lambda r:(r['due_time'],r['title'],r['id']))
    other=sorted((r for r in eligible if r not in fixed),key=lambda r:(r.get('due_date') or '9999',r['title'],r['id']))
    def view(r): return {'id':r['id'],'title':r['title'],'date':r.get('due_date'),
                        'time':(r.get('due_time') or '')[:5], 'overdue':bool(r.get('due_date') and r['due_date']<today.isoformat()),
                        'recurrence':r.get('recurrence','none')}
    return {'date':today.isoformat(),'scheduled':[view(r) for r in fixed[:12]],
            'priorities':[view(r) for r in other[:8]],'eligible_count':len(eligible),
            'overdue_count':sum(bool(r.get('due_date') and r['due_date']<today.isoformat()) for r in eligible),
            'partial':invalid or len(fixed)>12 or len(other)>8 or len(rows)>=500,
            'note':'Sugestão baseada nas tarefas registradas, com horários exibidos no fuso '+timezone+'. Nenhuma tarefa foi alterada. Sem estimar duração ou criar horários.'}
