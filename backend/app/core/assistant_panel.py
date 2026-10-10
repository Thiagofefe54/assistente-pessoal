"""Account-owned summaries and exact comparisons; no model calls or mutations."""
from calendar import monthrange
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from backend.app.core.life import overview, bill_dates
from backend.app.core.personal import load_records, load_payments
from backend.app.core.tasks import task_context, local_schedule


def comparison(records, today, period='month'):
    if period == 'week':
        start = today - timedelta(days=today.weekday())
        previous_start = start - timedelta(days=7)
        previous_end = today - timedelta(days=7)
    else:
        start = today.replace(day=1)
        previous_start = (start - timedelta(days=1)).replace(day=1)
        previous_end = previous_start.replace(day=min(today.day, monthrange(previous_start.year, previous_start.month)[1]))
    def totals(first, last):
        return {kind: sum(r.get('amount_cents') or 0 for r in records
                         if r['kind'] == kind and not r.get('archived_at')
                         and r.get('happened_on') and first.isoformat() <= r['happened_on'] <= last.isoformat())
                for kind in ('income', 'expense')}
    current, previous = totals(start, today), totals(previous_start, previous_end)
    return {'period': period, 'current_start': start.isoformat(), 'current_end': today.isoformat(),
            'previous_start': previous_start.isoformat(), 'previous_end': previous_end.isoformat(),
            'current': current, 'previous': previous,
            'difference': {kind: current[kind] - previous[kind] for kind in current}}


def day_panel(owner, authorization, timezone, now=None):
    moment = (now or datetime.now(ZoneInfo(timezone))).astimezone(ZoneInfo(timezone))
    today = moment.date()
    records = load_records(owner, authorization)
    payments = load_payments(owner, authorization)
    tasks = task_context(owner, authorization, timezone, 'hoje', moment)
    life = overview(records, payments, today)
    displayed = [dict(r, due_date=local_schedule(r, timezone)[0], due_time=local_schedule(r, timezone)[1]) for r in tasks['tasks']]
    pending = [r for r in displayed if not r.get('archived_at') and not r.get('completed_at') and r.get('due_date') == today.isoformat()]
    pending.sort(key=lambda r: (r.get('due_time') or '99:99', r['title']))
    paid = {(p['bill_id'], p['occurrence_on']) for p in payments}
    bills = [{'title': r['title'], 'due': due, 'amount_cents': r['amount_cents']}
             for r in records if r['kind'] == 'bill'
             for due in bill_dates(r, today, today + timedelta(days=7)) if (r['id'], due) not in paid]
    bills.sort(key=lambda r: (r['due'], r['title']))
    diary = [r for r in life['diary_this_week'] if r['day'] == today.isoformat()]
    return {'date': today.isoformat(), 'timezone': timezone, 'generated_at': moment.isoformat(),
            'tasks_pending_today': tasks['focus_pending'],
            'tasks': [{'title': r['title'], 'time': (r.get('due_time') or '')[:5]} for r in pending[:8]],
            'task_list_partial': not tasks['complete_list'] or len(pending) > 8 or tasks['total'] >= 500,
            'bills_next_seven_days': bills[:8], 'bill_count': len(bills),
            'month': life['month'], 'recorded_cents': life['recorded_cents'],
            'unpaid_month_cents': life['unpaid_cents'],
            'budgets_exceeded': sum(r['exceeded'] for r in life['budgets']),
            'diary_today': [{'title': r['title'], 'day': r['day']} for r in diary[-5:]],
            'sleep_average_minutes': life['sleep_average_minutes'],
            'sleep_complete_intervals': life['sleep_complete_intervals'],
            'comparison': comparison(records, today),
            'records_partial': len(records) >= 2000 or len(payments) >= 2000,
            'source': 'Seus registros na Koiwai. Valores não representam saldo bancário.'}
