"""Read verified account data after semantic selection, without another generation."""
from datetime import datetime
from zoneinfo import ZoneInfo
from backend.app.core.life import overview
from backend.app.core.personal import load_records, load_payments


def money(cents):
    whole = f'{cents // 100:,}'.replace(',', '.')
    return f'R$ {whole},{cents % 100:02}'


def direct_read(semantic, owner, authorization, timezone, now):
    query = semantic.read_query
    expected = 'diary' if query == 'diary_this_week' else 'record'
    if not query or semantic.domain != expected or semantic.operation != 'read' or semantic.speech_act not in ('question', 'request'):
        return None
    today = datetime.fromisoformat(now).astimezone(ZoneInfo(timezone)).date()
    records = load_records(owner, authorization)
    payments = load_payments(owner, authorization) if query == 'unpaid_bills_this_month' else []
    data = overview(records, payments, today)
    month = today.strftime('%m/%Y')
    if query == 'unpaid_bills_this_month':
        # Apply filter before truncation so paid rows cannot hide unpaid rows.
        from backend.app.core.life import bill_dates
        from datetime import timedelta
        start = today.replace(day=1)
        end = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
        paid = {(p['bill_id'], p['occurrence_on']) for p in payments}
        pending = [{'title':r['title'], 'due':day, 'amount_cents':r['amount_cents']}
                   for r in records if r['kind']=='bill'
                   for day in bill_dates(r,start,end) if (r['id'],day) not in paid]
        pending.sort(key=lambda b:(b['due'], b['title']))
        lines = [f"• {b['title']} — {money(b['amount_cents'])}, vence {b['due'][8:10]}/{b['due'][5:7]}" for b in pending[:20]]
        answer = f'Suas contas pendentes de {month} 💜\n' + '\n'.join(lines) if lines else f'Nenhuma conta pendente registrada para {month} 💜'
        if len(pending)>20: answer += f'\nMostrei 20 de {len(pending)} vencimentos; veja os demais em Contas.'
        if pending: answer += '\nTotal pendente: ' + money(sum(b['amount_cents'] for b in pending))
    elif query in ('expenses_this_month', 'income_this_month'):
        kind = 'expense' if query=='expenses_this_month' else 'income'
        answer = f"Até hoje, em {month}, você registrou {money(data['recorded_cents'][kind])} em {'despesas' if kind=='expense' else 'receitas'} 💜"
    elif query == 'budget_this_month':
        answer = f"Despesas registradas em {month}, até hoje: {money(data['recorded_cents']['expense'])} 💜"
        for item in data['budgets'][:20]:
            answer += f"\n• {item['title']}: limite {money(item['limit_cents'])} — {'ultrapassado' if item['exceeded'] else 'dentro do limite'}"
        if not data['budget_count']: answer += '\nVocê ainda não cadastrou um limite para este mês.'
        if data['budget_count']>20: answer += f"\nMostrei 20 de {data['budget_count']} limites; veja os demais em Orçamento."
    elif query == 'diary_this_week':
        items = data['diary_this_week'][-20:]
        answer = 'Acontecimentos registrados nesta semana 💜\n' + '\n'.join(f"• {r['day'][8:10]}/{r['day'][5:7]} — {r['title']}" for r in items) if items else 'Ainda não há acontecimentos registrados nesta semana 💜'
        if data['diary_week_count']>20: answer += f"\nMostrei os últimos 20 de {data['diary_week_count']} registros; veja os demais no Diário."
    return {'reply': answer, 'task_draft': None, 'action_receipt': None}
