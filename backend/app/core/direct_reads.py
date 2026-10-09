"""Read verified account data after semantic selection, without another generation."""
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from backend.app.core.life import overview
from backend.app.core.personal import load_records, load_payments


def money(cents):
    sign = '-' if cents < 0 else ''
    cents = abs(cents)
    whole = f'{cents // 100:,}'.replace(',', '.')
    return f'{sign}R$ {whole},{cents % 100:02}'


def direct_read(semantic, owner, authorization, timezone, now):
    query = semantic.read_query
    expected = 'diary' if query and query.startswith('diary_') else 'memory' if query == 'memory_all' else 'conversation' if query == 'day_overview' else 'record'
    if not query or semantic.domain != expected or semantic.operation != 'read' or semantic.speech_act not in ('question', 'request'):
        return None
    today = datetime.fromisoformat(now).astimezone(ZoneInfo(timezone)).date()
    if query == 'memory_all':
        from backend.app.core.memory import confirmed_facts
        facts = confirmed_facts(owner, authorization)
        answer = 'O que tenho guardado sobre você 💜\n' + '\n'.join('• ' + f['content'] for f in facts) if facts else 'Ainda não há lembranças confirmadas sobre você 💜'
        return {'reply': answer, 'task_draft': None, 'action_receipt': None}
    if query == 'day_overview':
        from backend.app.core.assistant_panel import day_panel
        data = day_panel(owner, authorization, timezone, datetime.fromisoformat(now))
        answer = f"Seu dia, em um cantinho só 💜\n{data['tasks_pending_today']} tarefas pendentes para hoje."
        for item in data['tasks']: answer += f"\n• {item['time'] or 'Sem horário'} — {item['title']}"
        answer += f"\nContas nos próximos 7 dias: {data['bill_count']}.\nNeste mês: {money(data['recorded_cents']['income'])} recebidos e {money(data['recorded_cents']['expense'])} gastos registrados."
        if data['task_list_partial'] or data['records_partial']: answer += '\nEste resumo tem uma lista limitada; confira os detalhes na Rotina.'
        answer += '\nValores informados na Koiwai; não são saldo bancário.'
        return {'reply': answer, 'task_draft': None, 'action_receipt': None}
    records = load_records(owner, authorization)
    if query in ('finance_compare_month', 'finance_compare_week'):
        from backend.app.core.assistant_panel import comparison
        data = comparison(records, today, 'week' if query.endswith('week') else 'month')
        answer = f"Seu comparativo de registros 💜\nAtual: {data['current_start']} a {data['current_end']}\nAnterior: {data['previous_start']} a {data['previous_end']}"
        for kind, label in (('expense', 'Despesas'), ('income', 'Receitas')):
            delta = data['difference'][kind]
            answer += f"\n{label}: {money(data['current'][kind])} agora • {money(data['previous'][kind])} antes ({money(abs(delta))} {'a mais' if delta >= 0 else 'a menos'})."
        answer += '\nSomente valores registrados; não representam saldo bancário.'
        if len(records) >= 2000: answer += '\nConsulta limitada aos 2.000 registros mais recentes.'
        return {'reply': answer, 'task_draft': None, 'action_receipt': None}
    payments = load_payments(owner, authorization) if query == 'unpaid_bills_this_month' else []
    data = overview(records, payments, today)
    month = today.strftime('%m/%Y')
    if query == 'unpaid_bills_this_month':
        # Apply filter before truncation so paid rows cannot hide unpaid rows.
        from backend.app.core.life import bill_dates
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
    elif query and query.startswith('diary_'):
        start = today.replace(day=1) if query == 'diary_this_month' else today - timedelta(days=today.weekday())
        end = today
        if query == 'diary_last_week': start, end = start - timedelta(days=7), start - timedelta(days=1)
        found = sorted((r for r in records if r['kind'] == 'diary' and not r.get('archived_at') and r.get('happened_on') and start.isoformat() <= r['happened_on'] <= end.isoformat()), key=lambda r:(r['happened_on'], r.get('updated_at',''), r['id']))
        items = found[-20:]
        answer = f'Acontecimentos registrados de {start:%d/%m} a {end:%d/%m} 💜\n' + '\n'.join(f"• {r['happened_on'][8:10]}/{r['happened_on'][5:7]} — {r['title']}" for r in items) if items else 'Ainda não há acontecimentos registrados nesse período 💜'
        if len(found)>20: answer += f"\nMostrei os últimos 20 de {len(found)} registros; veja os demais no Diário."
    else:
        return None
    if len(records) >= 2000: answer += '\nConsulta limitada aos 2.000 registros mais recentes.'
    return {'reply': answer, 'task_draft': None, 'action_receipt': None}
