"""Read bounded, confirmed facts with the caller's token, honoring database RLS."""
import json
from urllib.error import HTTPError, URLError
from urllib.request import Request, build_opener
from uuid import UUID

from fastapi import HTTPException

from backend.app.core.auth import NoRedirect
from backend.app.core.config import settings


def confirmed_facts(user_id: UUID, authorization: str) -> list[dict[str, str]]:
    request = Request(settings.supabase_url.rstrip('/') +
        '/rest/v1/memory_facts?select=content,category&user_id=eq.' + str(user_id) +
        '&order=slot.asc&limit=20', headers={
            'apikey': settings.supabase_publishable_key, 'Authorization': authorization,
            'Accept': 'application/json', 'User-Agent': 'Koiwai/0.1'})
    try:
        with build_opener(NoRedirect()).open(request, timeout=8) as response:
            body = response.read(50001)
        if len(body) > 50000:
            raise ValueError('Oversized response')
        rows = json.loads(body)
        if not isinstance(rows, list) or len(rows) > 20:
            raise ValueError('Invalid response')
        for row in rows:
            if not isinstance(row, dict) or not isinstance(row.get('content'), str) or not 1 <= len(row['content']) <= 500:
                raise ValueError('Invalid fact')
            if row.get('category') not in ('preference', 'goal', 'routine', 'note'):
                raise ValueError('Invalid category')
        return rows
    except HTTPError as error:
        error.close()
        raise HTTPException(503, 'Não consegui consultar suas lembranças. Tente novamente.') from None
    except (URLError, TimeoutError, OSError, ValueError, TypeError):
        raise HTTPException(503, 'Não consegui consultar suas lembranças. Tente novamente.') from None
