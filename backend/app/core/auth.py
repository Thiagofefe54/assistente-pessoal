"""Validate each bearer token with Supabase Auth; never trust client user IDs."""
import json
from urllib.error import HTTPError, URLError
from urllib.request import HTTPRedirectHandler, Request as URLRequest, build_opener
from uuid import UUID

from fastapi import Depends, HTTPException, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from backend.app.core.config import settings

bearer = HTTPBearer(auto_error=False)


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def unauthorized():
    return HTTPException(401, "Entre novamente na sua conta.",
                         headers={"WWW-Authenticate": "Bearer"})


def current_user(request: Request,
                 credentials: HTTPAuthorizationCredentials | None = Depends(bearer)) -> UUID:
    # HTTPS may terminate at a trusted proxy configured explicitly in Uvicorn.
    if request.url.scheme != "https":
        raise HTTPException(400, "A autenticação requer HTTPS.")
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise unauthorized()
    if not settings.supabase_url or not settings.supabase_publishable_key:
        raise HTTPException(503, "Autenticação do servidor ainda não configurada.")
    upstream = URLRequest(
        settings.supabase_url.rstrip("/") + "/auth/v1/user",
        headers={"apikey": settings.supabase_publishable_key,
                 "Authorization": "Bearer " + credentials.credentials},
    )
    try:
        with build_opener(NoRedirect()).open(upstream, timeout=8) as response:
            user = json.load(response)
        if user.get("is_anonymous") is True:
            raise unauthorized()
        return UUID(user["id"])
    except HTTPError as error:
        error.close()
        if error.code in (401, 403):
            raise unauthorized() from None
        raise HTTPException(503, "Serviço de autenticação indisponível.") from None
    except (URLError, TimeoutError, OSError, ValueError, KeyError, TypeError, AttributeError):
        raise HTTPException(503, "Não foi possível verificar sua conta.") from None
