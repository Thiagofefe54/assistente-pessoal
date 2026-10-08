"""Calendar calculations over recorded facts, never bank balances or inferred events."""
from calendar import monthrange
from datetime import date, datetime, timedelta


def occurrence(anchor, repeat, index):
    if repeat == 'weekly': return anchor + timedelta(days=7 * index)
    if repeat == 'monthly':
        month = anchor.year * 12 + anchor.month - 1 + index
        year, number = divmod(month, 12)
        return date(year, number + 1, min(anchor.day, monthrange(year, number + 1)[1]))
    return anchor if index == 0 else None


def bill_dates(row, start, end):
    if not row.get('happened_on') or row.get('archived_at'): return []
    anchor = date.fromisoformat(row['happened_on'])
    repeat = row.get('details', {}).get('repeat', 'none')
    # Jump to the requested month, retaining original day (31 -> Feb 28 -> Mar 31).
    index = max(0, (start.year-anchor.year)*12+start.month-anchor.month-1) if repeat=='monthly' else max(0,(start-anchor).days//7-1) if repeat=='weekly' else 0
    result=[]
    for i in range(index,index+400):
        day=occurrence(anchor,repeat,i)
        if day is None or day>=end: break
        if day>=start: result.append(day.isoformat())
    return result


def overview(records, payments, today):
    start=today.replace(day=1);end=(start.replace(day=28)+timedelta(days=4)).replace(day=1)
    active=[r for r in records if not r.get('archived_at')]
    totals={kind:sum(r.get('amount_cents') or 0 for r in active if r['kind']==kind and r.get('happened_on') and start.isoformat()<=r['happened_on']<=today.isoformat()) for kind in ('expense','income')}
    paid={(p['bill_id'],p['occurrence_on']) for p in payments}
    bills=[{'id':r['id'],'title':r['title'],'due':day,'amount_cents':r['amount_cents'],'paid':(r['id'],day) in paid} for r in active if r['kind']=='bill' for day in bill_dates(r,start,end)]
    budgets=[{'id':r['id'],'title':r['title'],'limit_cents':r['amount_cents'],'spent_cents':totals['expense'],'exceeded':totals['expense']>r['amount_cents']} for r in active if r['kind']=='budget' and r.get('happened_on','')==start.isoformat()]
    monday=today-timedelta(days=today.weekday())
    diary=[{'id':r['id'],'title':r['title'],'day':r['happened_on'],'details':r.get('details',{}),'content':r['content'][:500]} for r in active if r['kind']=='diary' and r.get('happened_on') and monday.isoformat()<=r['happened_on']<=today.isoformat()]
    diary.sort(key=lambda r:(r['day'],r['details'].get('started_at','')))
    sleep=[sleep_minutes(r['details']) for r in diary if r['details'].get('category')=='sleep' and r['details'].get('started_at') and r['details'].get('ended_at')]
    return {'month':start.isoformat()[:7],'recorded_cents':totals,'bills':bills[:80],'bill_count':len(bills),'bill_snapshot_truncated':len(bills)>80,'unpaid_cents':sum(b['amount_cents'] for b in bills if not b['paid']),'budgets':budgets[:40],'budget_count':len(budgets),'diary_this_week':diary[-60:],'diary_week_count':len(diary),'sleep_complete_intervals':len(sleep),'sleep_average_minutes':sum(sleep)//len(sleep) if sleep else None}


def validate_details(kind, details, day=None):
    if kind=='bill':
        if set(details)-{'repeat'} or details.get('repeat','none') not in ('none','weekly','monthly'): raise ValueError('Invalid repeat')
    elif kind=='diary':
        if set(details)-{'category','started_at','ended_at'} or details.get('category','other') not in ('work','gym','sleep','home','study','other'): raise ValueError('Invalid diary category')
        start=datetime.fromisoformat(details['started_at']) if details.get('started_at') else None
        end=datetime.fromisoformat(details['ended_at']) if details.get('ended_at') else None
        if start and (start.utcoffset() is None or (day and start.date().isoformat()!=day)): raise ValueError('Invalid event date')
        if end and (end.utcoffset() is None or not start or end<start or (end-start).total_seconds()>172800): raise ValueError('Invalid interval')
    elif details: raise ValueError('Unexpected details')


def sleep_minutes(details):
    if not details.get('started_at') or not details.get('ended_at'): return None
    validate_details('diary',details)
    return int((datetime.fromisoformat(details['ended_at'])-datetime.fromisoformat(details['started_at'])).total_seconds()//60)
