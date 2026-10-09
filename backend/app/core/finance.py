"""Exact category totals. Legacy records remain 'other'; budgets default to total."""
CATEGORIES = {'food':'Alimentação','transport':'Transporte','home':'Casa','health':'Saúde',
              'study':'Estudos','leisure':'Lazer','work':'Trabalho','other':'Outros'}


def category(row):
    return row.get('details', {}).get('finance_category', 'all' if row['kind']=='budget' else 'other')


def spent(records, today, selected='all'):
    return sum(r.get('amount_cents') or 0 for r in records if r['kind']=='expense'
               and not r.get('archived_at') and r.get('happened_on')
               and today.replace(day=1).isoformat() <= r['happened_on'] <= today.isoformat()
               and (selected=='all' or category(r)==selected))


def category_totals(records, today):
    return [{'category':key,'label':label,'expense_cents':spent(records,today,key)}
            for key,label in CATEGORIES.items()]
