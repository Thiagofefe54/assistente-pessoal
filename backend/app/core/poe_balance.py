"""Bounded, cached usage read. Never generates text or discloses usage history."""
import json
from datetime import datetime, timezone
from threading import Lock
from time import monotonic
from urllib.request import Request, build_opener
from fastapi import HTTPException
from backend.app.core.auth import NoRedirect
from backend.app.core.config import settings

_lock = Lock()
_cached = None
_deadline = 0.0


def balance():
    global _cached, _deadline
    if settings.ai_provider != 'poe':
        return {'provider': settings.ai_provider, 'available_points': None, 'checked_at': None, 'shared': True}
    key = settings.poe_api_key.get_secret_value()
    if not key:
        raise HTTPException(503, 'Poe ainda não configurado no servidor.')
    with _lock:
        if monotonic() < _deadline:
            if _cached is None:
                raise HTTPException(503, 'Não consegui consultar o saldo do Poe. Aguarde um minuto.')
            return dict(_cached)
        # Negative caching also prevents repeated upstream calls on outages.
        _deadline = monotonic() + 60
        _cached = None
        request = Request('https://api.poe.com/usage/current_balance',
                          headers={'Authorization': 'Bearer ' + key, 'Accept': 'application/json'})
        try:
            with build_opener(NoRedirect()).open(request, timeout=8) as response:
                raw = response.read(10001)
            if len(raw) > 10000:
                raise ValueError('Oversized balance')
            points = json.loads(raw)['current_point_balance']
            if type(points) is not int or points < 0:
                raise ValueError('Invalid balance')
        except (OSError, ValueError, KeyError, TypeError):
            raise HTTPException(503, 'Não consegui consultar o saldo do Poe. Aguarde um minuto.') from None
        _cached = {'provider': 'poe', 'available_points': points,
                   'checked_at': datetime.now(timezone.utc).isoformat(), 'shared': True}
        return dict(_cached)
