from typing import Literal
from uuid import UUID
from fastapi import APIRouter, Depends, Request, Response, HTTPException, Query
from fastapi.responses import HTMLResponse, RedirectResponse
from pydantic import BaseModel, ConfigDict
from backend.app.core.auth import current_user
from backend.app.core import google_connections as google

router = APIRouter(tags=['Conexões Google'])
HEADERS = {'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer',
           'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'",
           'X-Content-Type-Options': 'nosniff'}


class GoogleRequest(BaseModel):
    model_config = ConfigDict(extra='forbid')
    timezone: str | None = None  # Shared Android transport adds this field.
    connection_id: UUID | None = None
    service: Literal['calendar','calendars','tasks','mail','drive'] | None = None


@router.post('/assistant/google-status')
def status(body: GoogleRequest, response: Response, owner: UUID = Depends(current_user)):
    response.headers.update(HEADERS)
    return google.list_accounts(owner)


@router.post('/assistant/google-connect')
def connect(body: GoogleRequest, response: Response, owner: UUID = Depends(current_user)):
    response.headers.update(HEADERS)
    return google.start(owner)


@router.post('/assistant/google-disconnect')
def disconnect(body: GoogleRequest, response: Response, owner: UUID = Depends(current_user)):
    response.headers.update(HEADERS)
    if body.connection_id is None:
        raise HTTPException(422, 'Escolha a conexão Google.')
    return google.disconnect(owner, body.connection_id)


@router.post('/assistant/google-read')
def read(body: GoogleRequest, response: Response, owner: UUID = Depends(current_user)):
    response.headers.update(HEADERS)
    if body.connection_id is None or body.service is None:
        raise HTTPException(422, 'Escolha a conta e o serviço Google.')
    return google.consult(owner, body.connection_id, body.service)


@router.get('/connections/google/begin')
def begin(request: Request, ticket: str = Query(min_length=40, max_length=128)):
    if request.url.scheme != 'https':
        raise HTTPException(400, 'A conexão Google requer HTTPS.')
    url, state, cookie = google.begin(ticket)
    response = RedirectResponse(url, status_code=303, headers=HEADERS)
    response.set_cookie(google.cookie_name(state), cookie, max_age=google.TTL, secure=True,
                        httponly=True, samesite='lax', path=google.CALLBACK_PATH)
    return response


@router.get('/connections/google/callback')
def callback(request: Request, state: str = Query(min_length=40,max_length=128),
             code: str | None = Query(default=None,max_length=4096),
             error: str | None = Query(default=None,max_length=200)):
    if request.url.scheme != 'https':
        raise HTTPException(400, 'A conexão Google requer HTTPS.')
    name = google.cookie_name(state)
    try:
        google.callback(state, request.cookies.get(name), code, error)
        message = 'Conta Google conectada. Volte à Koi e toque em Atualizar contas.'
        status = 200
    except HTTPException as exc:
        # Never echo OAuth code, state, token, user-controlled text or upstream errors.
        message = 'Não foi possível concluir a conexão. Volte à Koi e comece novamente; revise as permissões no Google.'
        status = exc.status_code
    response = HTMLResponse('<!doctype html><html lang="pt-BR"><meta name="viewport" content="width=device-width">'
        '<title>Koiwai · conexão Google</title><body style="background:#171025;color:#fff;font-family:sans-serif;padding:32px">'
        '<h1>Koiwai 💜</h1><p>' + message + '</p></body></html>', status_code=status, headers=HEADERS)
    response.delete_cookie(name, path=google.CALLBACK_PATH, secure=True, httponly=True, samesite='lax')
    return response
