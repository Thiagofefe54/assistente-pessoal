"""Prioritize real pending tasks. Suggestions do not write or invent durations."""
from backend.app.core.tasks import load_task_rows


def day_plan(owner,authorization,today):
    rows=load_task_rows(owner,authorization)
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
            'partial':len(fixed)>12 or len(other)>8 or len(rows)>=500,
            'note':'Sugestão baseada nas tarefas registradas. Horários existentes são preservados; nenhuma tarefa foi alterada. Sem estimar duração ou criar horários.'}
