"""Narrow read-only balance requests and explicit snapshot discrepancies."""
import re
from backend.app.core.schedule import plain


def bank_reply(message, history):
    text = plain(message).strip(' .!?')
    # Current correction plus recent bank context; never record a manual balance.
    recent = ' '.join(plain(h.get('content', '')) for h in history[-4:])
    correction = re.search(r'\b(nao tenho esse dinheiro|saldo (?:esta |ta )?(?:errado|incorreto))\b', text) or re.fullmatch(r'(?:eu |so )?tenho \d+[,.]\d{2}', text)
    bank_context = re.search(r'\b(saldo|inter|dinheiro|banco)\b', recent)
    if correction and (bank_context or re.search(r'\b(saldo|dinheiro|banco|inter)\b', text)):
        return {'reply': 'Entendi, mestre 💜 O valor que você conferiu no Inter pode ser mais recente. O cartão mostra uma fotografia do saldo: tocar em consultar lê os dados da Pluggy, mas não atualiza o banco na hora. No Meu Pluggy, a atualização é diária. Não vou tratar aquele valor como dinheiro disponível nem mudar seus registros com essa correção. Confira a data do provedor no cartão.',
                'task_draft': None, 'action_receipt': None}
    if re.fullmatch(r'(?:koi[, ]+|coi[, ]+)?(?:quanto (?:dinheiro )?eu tenho(?: (?:no (?:meu )?banco|no inter))?|qual (?:e )?(?:o )?meu saldo(?: (?:no banco|no inter))?|(?:consulte|consultar|mostre|ver|confira) (?:o )?(?:meu )?saldo(?: (?:do banco|do inter))?)', text):
        return {'reply': 'Vou consultar o último saldo informado pelo Inter através da Pluggy, mestre 💜 Confira a data da atualização no cartão: esse valor pode ter mudado desde então.',
                'task_draft': None, 'action_receipt': {'tool': 'bank', 'type': 'read', 'query': 'balance'}}
    return None
