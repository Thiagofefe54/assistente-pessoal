"""Owner-bound OAuth, persistent encrypted tokens, bounded reads. No AI calls."""
import base64
import hashlib
import hmac
import json
import secrets
import time
from datetime import date, datetime, timezone, timedelta
from zoneinfo import ZoneInfo
from urllib.parse import urlencode, urlsplit, quote
from urllib.request import Request, build_opener
from urllib.error import HTTPError, URLError
from uuid import UUID

from cryptography.fernet import Fernet, InvalidToken
from fastapi import HTTPException
from backend.app.core.auth import NoRedirect
from backend.app.core.config import settings

CALLBACK_PATH = '/api/v1/connections/google/callback'
SCOPE = {
    'calendar': 'https://www.googleapis.com/auth/calendar.events',
    'calendars': 'https://www.googleapis.com/auth/calendar.calendarlist.readonly',
    'tasks': 'https://www.googleapis.com/auth/tasks',
    'mail': 'https://www.googleapis.com/auth/gmail.readonly',
    'mail_send': 'https://www.googleapis.com/auth/gmail.send',
    'drive': 'https://www.googleapis.com/auth/drive.readonly',
}
ALL_SCOPES = {'openid', 'email', *SCOPE.values()}
TTL = 600


def ready():
    return bool(settings.google_enabled and settings.google_client_id
                and settings.google_client_secret.get_secret_value()
                and settings.connections_supabase_secret_key.get_secret_value()
                and settings.connections_encryption_key.get_secret_value()
                and settings.supabase_url and valid_redirect())


def valid_redirect():
    u = urlsplit(settings.google_redirect_uri)
    return (u.scheme == 'https' and bool(u.hostname) and not u.username
            and not u.password and not u.query and not u.fragment and u.path == CALLBACK_PATH)


def require_ready():
    if not ready():
        raise HTTPException(503, 'As conexões Google ainda estão sendo configuradas.')


def cipher():
    try:
        return Fernet(settings.connections_encryption_key.get_secret_value().encode())
    except (ValueError, TypeError):
        raise HTTPException(503, 'A proteção das conexões não está configurada.') from None


def seal(owner, purpose, value):
    return cipher().encrypt(json.dumps({'owner': str(owner), 'purpose': purpose,
                                       'value': value}, separators=(',', ':')).encode()).decode()


def unseal(owner, purpose, value):
    try:
        data = json.loads(cipher().decrypt(value.encode()))
        if data['owner'] != str(owner) or data['purpose'] != purpose:
            raise ValueError()
        return data['value']
    except (InvalidToken, ValueError, KeyError, TypeError, AttributeError):
        raise HTTPException(503, 'Não consegui conferir a proteção desta conexão.') from None


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


def stamp(seconds=None):
    return datetime.fromtimestamp(time.time() if seconds is None else seconds, timezone.utc).isoformat()


def request_json(url, *, method='GET', payload=None, headers=None, form=False):
    body = None if payload is None else (urlencode(payload).encode() if form else json.dumps(payload).encode())
    headers = {'Accept': 'application/json', **(headers or {})}
    if body is not None:
        headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
    try:
        with build_opener(NoRedirect()).open(Request(url, data=body, method=method, headers=headers), timeout=15) as r:
            raw = r.read(1_000_001)
        if len(raw) > 1_000_000:
            raise ValueError()
        value = json.loads(raw) if raw else {}
        if not isinstance(value, (dict, list)):
            raise ValueError()
        return value
    except HTTPError as e:
        code = e.code
        e.close()
        if code in (400, 401):
            raise HTTPException(409, 'A autorização expirou ou foi recusada. Conecte a conta novamente.') from None
        if code == 403:
            raise HTTPException(403, 'O Google não permitiu esta consulta. Confira a permissão e a API habilitada.') from None
        if code == 412:
            raise HTTPException(409, 'O item Google mudou. Consulte novamente antes de alterar.') from None
        if code == 429:
            raise HTTPException(429, 'O serviço atingiu um limite temporário. Tente mais tarde.') from None
        raise HTTPException(503, 'Não consegui acessar o serviço de conexões agora.') from None
    except (URLError, TimeoutError, OSError, ValueError, TypeError):
        raise HTTPException(503, 'Não consegui acessar o serviço de conexões agora.') from None


def database(table, query=None, method='GET', payload=None, upsert=False):
    require_ready()
    key = settings.connections_supabase_secret_key.get_secret_value()
    # Separate privileged connection client; never inherit a user's bearer token.
    headers = {'apikey': key,
               'Prefer': 'return=representation' + (',resolution=merge-duplicates' if upsert else '')}
    if not key.startswith('sb_secret_'):
        headers['Authorization'] = 'Bearer ' + key
    return request_json(settings.supabase_url.rstrip('/') + '/rest/v1/' + table +
                        ('?' + urlencode(query) if query else ''),
                        method=method, payload=payload, headers=headers)


def metadata(row):
    return {'id': str(UUID(row['id'])), 'email': row['email'],
            'services': [name for name, scope in SCOPE.items() if scope in row['scopes']],
            'updated_at': row['updated_at']}


def list_accounts(owner):
    if not ready():
        return {'configured': False, 'accounts': []}
    rows = database('koi_google_connections', {'user_id': 'eq.' + str(owner),
                    'select': 'id,email,scopes,updated_at', 'order': 'updated_at.desc', 'limit': 3})
    return {'configured': True, 'accounts': [metadata(r) for r in rows]}


def create_pending(owner, phase, value):
    token = secrets.token_urlsafe(32)
    database('koi_google_pending', method='POST', payload={'state_hash': digest(token),
             'user_id': str(owner), 'phase': phase, 'secret': seal(owner, 'oauth', value),
             'expires_at': stamp(time.time() + TTL)})
    return token


def start(owner):
    require_ready()
    # Expired handoffs contain only encrypted flow secrets and are safe to remove.
    database('koi_google_pending', {'user_id': 'eq.' + str(owner), 'expires_at': 'lt.' + stamp()}, method='DELETE')
    rows = database('koi_google_pending', {'user_id': 'eq.' + str(owner), 'select': 'state_hash', 'limit': 6})
    if len(rows) >= 6:
        raise HTTPException(429, 'Há conexões aguardando autorização. Aguarde dez minutos para recomeçar.')
    ticket = create_pending(owner, 'begin', {})
    origin = settings.google_redirect_uri.removesuffix(CALLBACK_PATH)
    return {'url': origin + '/api/v1/connections/google/begin?ticket=' + ticket,
            'expires_in': TTL}


def pending(token, phase, method='GET'):
    if not isinstance(token, str) or not 40 <= len(token) <= 128:
        raise HTTPException(400, 'Conexão inválida ou expirada. Comece novamente no app.')
    rows = database('koi_google_pending', {'state_hash': 'eq.' + digest(token),
                    'phase': 'eq.' + phase, 'expires_at': 'gt.' + stamp()}, method=method)
    if not isinstance(rows, list) or len(rows) != 1:
        raise HTTPException(400, 'Conexão inválida ou já utilizada. Comece novamente no app.')
    return rows[0]


def cookie_name(state):
    return '__Secure-koi-google-' + digest(state)[:16]


def begin(ticket):
    # Atomic DELETE claims the handoff once, even across server restarts/workers.
    row = pending(ticket, 'begin', 'DELETE')
    owner = UUID(row['user_id'])
    unseal(owner, 'oauth', row['secret'])
    verifier = secrets.token_urlsafe(48)
    cookie = secrets.token_urlsafe(32)
    state = create_pending(owner, 'callback', {'verifier': verifier, 'cookie_hash': digest(cookie)})
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
    url = 'https://accounts.google.com/o/oauth2/v2/auth?' + urlencode({
        'client_id': settings.google_client_id, 'redirect_uri': settings.google_redirect_uri,
        'response_type': 'code', 'scope': ' '.join(sorted(ALL_SCOPES)),
        'access_type': 'offline', 'prompt': 'select_account consent',
        'state': state, 'code_challenge': challenge, 'code_challenge_method': 'S256'})
    return url, state, cookie


def callback(state, cookie, code, error=None):
    row = pending(state, 'callback')
    owner = UUID(row['user_id'])
    flow = unseal(owner, 'oauth', row['secret'])
    if not cookie or not hmac.compare_digest(digest(cookie), flow['cookie_hash']):
        raise HTTPException(400, 'Abra a autorização no mesmo navegador em que começou.')
    pending(state, 'callback', 'DELETE')  # Only a valid browser can consume the state.
    if error:
        raise HTTPException(400, 'A autorização não foi concluída. Você pode tentar novamente no app.')
    if not code or len(code) > 4096:
        raise HTTPException(400, 'O Google não retornou uma autorização válida.')
    tokens = request_json('https://oauth2.googleapis.com/token', method='POST', form=True, payload={
        'client_id': settings.google_client_id, 'client_secret': settings.google_client_secret.get_secret_value(),
        'redirect_uri': settings.google_redirect_uri, 'code': code,
        'code_verifier': flow['verifier'], 'grant_type': 'authorization_code'})
    tokens = token_response(tokens, require_refresh=True)
    access = tokens.get('access_token')
    refresh = tokens.get('refresh_token')
    scopes = set(tokens.get('scope', '').split()) & ALL_SCOPES
    if not access or not refresh or tokens.get('token_type', '').lower() != 'bearer':
        raise HTTPException(409, 'Não recebi acesso renovável. Conecte novamente e revise as permissões.')
    user = request_json('https://openidconnect.googleapis.com/v1/userinfo',
                        headers={'Authorization': 'Bearer ' + access})
    if not isinstance(user, dict):
        raise HTTPException(503, 'O Google retornou uma identidade inválida.')
    subject, email = user.get('sub'), user.get('email')
    if (not isinstance(subject, str) or not 1 <= len(subject) <= 255 or
            not isinstance(email, str) or not 1 <= len(email) <= 320 or user.get('email_verified') is not True):
        raise HTTPException(409, 'Não consegui confirmar a identidade desta conta Google.')
    secret = seal(owner, 'google:' + subject, {'access': access, 'refresh': refresh,
                  'expires_at': time.time() + tokens['expires_in']})
    result = database('koi_google_connections', {'on_conflict': 'user_id,subject'}, 'POST', {
        'user_id': str(owner), 'subject': subject, 'email': email, 'scopes': sorted(scopes),
        'secret': secret, 'updated_at': stamp()}, upsert=True)
    if not isinstance(result, list) or len(result) != 1:
        raise HTTPException(503, 'Não consegui salvar esta conexão.')
    return metadata(result[0])


def account(owner, connection):
    connection = UUID(str(connection))
    rows = database('koi_google_connections', {'user_id': 'eq.' + str(owner),
                    'id': 'eq.' + str(connection), 'select': '*', 'limit': 1})
    if not rows:
        raise HTTPException(404, 'Esta conexão não está disponível para sua conta Koiwai.')
    return rows[0]


def disconnect(owner, connection):
    account(owner, connection)
    rows = database('koi_google_connections', {'user_id': 'eq.' + str(owner),
                    'id': 'eq.' + str(UUID(str(connection)))}, 'DELETE')
    if not rows:
        raise HTTPException(404, 'A conexão já foi removida.')
    # Google revocation can revoke sibling grants; disconnect only removes this Koi binding.
    return {'disconnected': True, 'note': 'Conexão removida da Koi. Você também pode revogar o acesso nas configurações Google.'}


def token_response(tokens, require_refresh=False):
    if not isinstance(tokens, dict):
        raise HTTPException(503, 'O Google retornou uma autorização inválida.')
    for field in ('access_token', 'refresh_token'):
        value = tokens.get(field)
        if (field == 'access_token' or require_refresh or value is not None) and (
                not isinstance(value, str) or not 1 <= len(value) <= 8192 or
                any(ord(c) < 32 or ord(c) == 127 for c in value)):
            raise HTTPException(409, 'Conecte novamente para obter uma autorização válida.')
    kind, scope = tokens.get('token_type'), tokens.get('scope', '')
    expiry = tokens.get('expires_in', 3600)
    if (not isinstance(kind, str) or kind.lower() != 'bearer' or
            not isinstance(scope, str) or len(scope) > 8192 or
            isinstance(expiry, bool) or not isinstance(expiry, (int, str))):
        raise HTTPException(503, 'O Google retornou uma autorização inválida.')
    try:
        expiry = int(expiry)
        if expiry <= 0:
            raise ValueError()
    except (ValueError, OverflowError):
        raise HTTPException(503, 'O Google retornou uma validade inválida.') from None
    return {**tokens, 'expires_in': min(expiry, 3600)}


def access_token(owner, row):
    value = unseal(owner, 'google:' + row['subject'], row['secret'])
    if value['expires_at'] > time.time() + 60:
        return value['access']
    tokens = request_json('https://oauth2.googleapis.com/token', method='POST', form=True, payload={
        'client_id': settings.google_client_id, 'client_secret': settings.google_client_secret.get_secret_value(),
        'refresh_token': value['refresh'], 'grant_type': 'refresh_token'})
    tokens = token_response(tokens)
    if not tokens.get('access_token') or tokens.get('token_type', '').lower() != 'bearer':
        raise HTTPException(409, 'Conecte a conta Google novamente para renovar o acesso.')
    value.update(access=tokens['access_token'], refresh=tokens.get('refresh_token', value['refresh']),
                 expires_at=time.time() + tokens['expires_in'])
    # Update existing row only. Never resurrect a concurrently disconnected connection.
    rows = database('koi_google_connections', {'id': 'eq.' + row['id'], 'user_id': 'eq.' + str(owner),
                    'updated_at': 'eq.' + row['updated_at']}, 'PATCH',
                    {'secret': seal(owner, 'google:' + row['subject'], value), 'updated_at': stamp()})
    if not rows:
        raise HTTPException(409, 'A conexão mudou. Atualize antes de consultar novamente.')
    return value['access']


def consult(owner, connection, service, query=None, start=None, end=None, local_timezone='America/Sao_Paulo'):
    if service not in SCOPE or service=='mail_send':
        raise HTTPException(422, 'Serviço Google inválido.')
    row = account(owner, connection)
    if SCOPE[service] not in row['scopes']:
        raise HTTPException(403, 'Esta conta não autorizou esse serviço. Conecte novamente para ampliar o acesso.')
    token = access_token(owner, row)
    # Search is submitted only to the selected Google service, never to the model.
    zone=ZoneInfo(local_timezone)
    first=date.fromisoformat(start) if start else None
    last=date.fromisoformat(end) if end else first
    if first and last and (last<first or (last-first).days>31):
        raise HTTPException(422,'Consulte até 31 dias por vez.')
    if query and (len(query)>200 or any(ord(c)<32 for c in query)):
        raise HTTPException(422,'Use um termo de busca de até 200 caracteres.')
    def midnight(day):return datetime.combine(day,datetime.min.time(),zone)
    mail_terms=[];drive_terms=['trashed = false']
    if query:
        # Only a literal subject/name phrase; input cannot add search operators.
        phrase=query.replace('\\','\\\\').replace('"','\\"')
        mail_terms.append('subject:"'+phrase+'"')
        name=query.replace('\\','\\\\').replace("'","\\'")
        drive_terms.append("name contains '"+name+"'")
    if first:
        mail_terms.append('after:'+str(int(midnight(first).timestamp())-1))
        drive_terms.append("modifiedTime >= '"+midnight(first).astimezone(timezone.utc).isoformat()+"'")
    if last:
        finish=midnight(last+timedelta(days=1))
        mail_terms.append('before:'+str(int(finish.timestamp())))
        drive_terms.append("modifiedTime < '"+finish.astimezone(timezone.utc).isoformat()+"'")
    urls = {
        'calendar': 'https://www.googleapis.com/calendar/v3/calendars/primary/events?' + urlencode({
            'timeMin': stamp(), 'singleEvents': 'true', 'orderBy': 'startTime', 'maxResults': 20}),
        'calendars': 'https://www.googleapis.com/calendar/v3/users/me/calendarList?maxResults=20',
        'tasks': 'https://tasks.googleapis.com/tasks/v1/users/@me/lists?maxResults=20',
        'mail': 'https://gmail.googleapis.com/gmail/v1/users/me/messages?'+urlencode({'maxResults':5,**({'q':' '.join(mail_terms)} if mail_terms else {})}),
        'drive': 'https://www.googleapis.com/drive/v3/files?' + urlencode({'pageSize': 20,
            'q': ' and '.join(drive_terms), 'fields': 'nextPageToken,incompleteSearch,files(id,name,mimeType,webViewLink,modifiedTime)',
            'orderBy': 'modifiedTime desc'}),
    }
    data = request_json(urls[service], headers={'Authorization': 'Bearer ' + token})
    key = {'mail': 'messages', 'drive': 'files'}.get(service, 'items')
    if not isinstance(data, dict) or not isinstance(data.get(key, []), list):
        raise HTTPException(503, 'O Google retornou uma consulta inválida.')
    # Mail list returns IDs only: no bulk message-body ingestion or AI transfer.
    result = []
    for item in data.get(key, [])[:5 if service == 'mail' else 20]:
        if not isinstance(item, dict):
            raise HTTPException(503, 'O Google retornou um item inválido.')
        if service == 'calendar':
            result.append({k: item[k] for k in ('id','summary','start','end','status','htmlLink') if k in item})
        elif service == 'calendars':
            result.append({k: item[k] for k in ('id','summary','primary','accessRole') if k in item})
        elif service == 'tasks':
            result.append({k: item[k] for k in ('id','title','updated') if k in item})
        elif service == 'mail':
            message_id = item.get('id')
            if not isinstance(message_id, str) or not 1 <= len(message_id) <= 200:
                raise HTTPException(503, 'O Google retornou uma mensagem inválida.')
            detail = request_json('https://gmail.googleapis.com/gmail/v1/users/me/messages/' +
                quote(message_id, safe='') + '?' + urlencode({'format':'metadata',
                    'metadataHeaders':['Subject','From','Date']}, doseq=True),
                headers={'Authorization':'Bearer '+token})
            if (not isinstance(detail, dict) or not isinstance(detail.get('payload', {}), dict)
                    or not isinstance(detail.get('payload', {}).get('headers', []), list)):
                raise HTTPException(503, 'O Google retornou uma mensagem inválida.')
            fields = {h.get('name','').lower():str(h.get('value',''))[:500]
                      for h in detail.get('payload',{}).get('headers',[]) if isinstance(h,dict) and isinstance(h.get('name'), str)}
            result.append({'id':message_id,'subject':fields.get('subject','Sem assunto'),
                           'from':fields.get('from',''), 'date':fields.get('date','')})
        else:
            result.append({k: item[k] for k in ('id','name','mimeType','webViewLink','modifiedTime') if k in item})
    # Bound provider text and nested event dates before returning to the mobile UI.
    result = [{k: (v[:1000] if isinstance(v, str) else
                    {a: b[:200] for a, b in v.items() if a in ('date','dateTime','timeZone') and isinstance(b, str)}
                    if isinstance(v, dict) else v if isinstance(v, bool) else None)
               for k, v in item.items()} for item in result]
    return {'account': metadata(row), 'service': service, 'items': result,
            'partial': bool(data.get('nextPageToken') or data.get('incompleteSearch')), 'checked_at': stamp(),
            'note':('Busca por assunto no Gmail; até cinco cabeçalhos, sem corpo da mensagem.' if service=='mail' else
                    'Busca por nome no Drive; até vinte metadados, sem conteúdo dos arquivos.' if service=='drive' else '')}
