"""Personal Meu Pluggy balance read. No imports, payments, model calls or refresh jobs."""
import json
from decimal import Decimal, InvalidOperation
from datetime import datetime, timezone
from hashlib import sha256
from threading import Lock
from time import monotonic
from urllib.request import Request, build_opener
from uuid import UUID
from fastapi import HTTPException
from backend.app.core.auth import NoRedirect
from backend.app.core.config import settings

_lock = Lock()
_cache = None
_cache_key = None
_deadline = 0.0


def connection_config(owner):
    if not settings.pluggy_enabled:
        raise HTTPException(503, 'Conexão bancária ainda não ativada. Complete o consentimento no Meu Pluggy.')
    try:
        expected = UUID(settings.pluggy_owner_id)
    except ValueError:
        raise HTTPException(503, 'Conexão bancária ainda não configurada.') from None
    if UUID(str(owner)) != expected:
        raise HTTPException(403, 'Esta conexão pessoal não está disponível para esta conta.')
    client = settings.pluggy_client_id.get_secret_value()
    secret = settings.pluggy_client_secret.get_secret_value()
    try:
        items = list(dict.fromkeys(str(UUID(x.strip())) for x in settings.pluggy_item_ids.get_secret_value().split(',')))
        if not client or not secret or not 1 <= len(items) <= 3:
            raise ValueError()
    except ValueError:
        raise HTTPException(503, 'Conexão bancária ainda não configurada.') from None
    return client, secret, items


def status(owner):
    try:
        connection_config(owner)
        ready = True
    except HTTPException:
        ready = False
    return {'configured': ready, 'provider': 'Meu Pluggy', 'read_only': True,
            'note': 'Configuração não comprova conexão ativa. Consulte os dados para verificar. Atualização depende do provedor e do consentimento.'}


def _request(path, key=None, body=None):
    # Only fixed host/path built by this module; no user URLs or redirects.
    headers = {'Accept': 'application/json'}
    if key:
        headers['X-API-KEY'] = key
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers['Content-Type'] = 'application/json'
    req = Request('https://api.pluggy.ai' + path, data=data, headers=headers)
    with build_opener(NoRedirect()).open(req, timeout=8) as response:
        raw = response.read(262145)
    if len(raw) > 262144:
        raise ValueError('Oversized response')
    result = json.loads(raw, parse_float=Decimal)
    if not isinstance(result, dict):
        raise ValueError('Invalid response')
    return result


def _cents(value):
    if type(value) not in (int, Decimal):
        raise ValueError('Invalid money')
    money = Decimal(value)
    cents = money * 100
    if not money.is_finite() or cents != cents.to_integral_value() or abs(cents) > 100000000000000:
        raise ValueError('Invalid money')
    return int(cents)


def summary(owner):
    global _cache, _cache_key, _deadline
    client, secret, items = connection_config(owner)  # Authorization before any cache read.
    fingerprint = sha256(json.dumps([str(owner), client, secret, items]).encode()).digest()
    with _lock:
        if fingerprint == _cache_key and monotonic() < _deadline:
            if _cache is None:
                raise HTTPException(503, 'Consulta bancária indisponível. Aguarde um minuto e confira seu consentimento no Meu Pluggy.')
            return json.loads(json.dumps(_cache))
        _cache_key = fingerprint
        _deadline = monotonic() + 60
        _cache = None
        try:
            key = _request('/auth', body={'clientId': client, 'clientSecret': secret})['apiKey']
            if not isinstance(key, str) or not key:
                raise ValueError('Invalid key')
            accounts = []; seen = set(); partial = False
            for item in items:
                data = _request('/accounts?itemId=' + item + '&type=BANK', key=key)
                rows = data['results']
                if not isinstance(rows, list) or len(rows) > 200:
                    raise ValueError('Invalid accounts')
                partial |= data.get('totalPages', 1) != 1 or len(rows) > 20
                for row in rows[:20]:
                    if not isinstance(row, dict):
                        raise ValueError('Invalid account')
                    if row.get('type') != 'BANK' or row.get('currencyCode') != 'BRL':
                        partial = True
                        continue
                    if not isinstance(row.get('id'), str) or not isinstance(row.get('itemId'), str):
                        raise ValueError('Invalid identity')
                    identity = str(UUID(row['id']))
                    if str(UUID(row['itemId'])) != item:
                        raise ValueError('Foreign connection')
                    if identity in seen:
                        continue
                    seen.add(identity)
                    stamp = row.get('updatedAt')
                    if not isinstance(stamp, str) or datetime.fromisoformat(stamp.replace('Z', '+00:00')).tzinfo is None:
                        raise ValueError('Invalid date')
                    accounts.append({'label': 'Conta ' + str(len(accounts) + 1),
                                     'balance_cents': _cents(row['balance']),
                                     'provider_updated_at': stamp})
        except (OSError, ValueError, KeyError, TypeError, InvalidOperation):
            raise HTTPException(503, 'Não consegui consultar os dados bancários. Confira o consentimento no Meu Pluggy e tente após um minuto.') from None
        _cache = {'provider': 'Meu Pluggy', 'accounts': accounts,
                  'total_cents': sum(x['balance_cents'] for x in accounts), 'partial': partial,
                  'checked_at': datetime.now(timezone.utc).isoformat(),
                  'note': 'Saldo informado pelo provedor, possivelmente desatualizado. Atualização da Koi consulta dados existentes; não força sincronização do banco. Contas BRL consultadas, sem cartões, investimentos ou pagamentos. Nenhum valor foi importado às receitas/despesas.'}
        return json.loads(json.dumps(_cache))
