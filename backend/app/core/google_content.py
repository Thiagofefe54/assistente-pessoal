"""Explicit private previews, bounded plain text only; no model, writes or attachments."""
import base64
import re
from html.parser import HTMLParser
from urllib.request import Request, build_opener
from urllib.error import HTTPError, URLError
from fastapi import HTTPException
from backend.app.core import google_connections as g
from backend.app.core.auth import NoRedirect

LIMIT=16000

class PlainHTML(HTMLParser):
    def __init__(self):super().__init__(convert_charrefs=True);self.hidden=0;self.parts=[]
    def handle_starttag(self,tag,attrs):
        if tag in ('script','style','head'):self.hidden+=1
        if tag in ('br','p','div','li','tr') and not self.hidden:self.parts.append('\n')
    def handle_endtag(self,tag):
        if tag in ('script','style','head'):self.hidden=max(0,self.hidden-1)
    def handle_data(self,data):
        if not self.hidden:self.parts.append(data)

def clean(text):return re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]','',text).strip()

def mail_text(payload):
    plain=[];html=[];partial=False;visited=0
    def walk(part,depth=0):
        nonlocal partial,visited
        visited+=1
        if depth>8 or visited>64:partial=True;return
        if not isinstance(part,dict):raise HTTPException(503,'O Google retornou uma mensagem inválida.')
        # Never render an attached file, even when it is text/plain.
        if part.get('filename'):return
        mime=part.get('mimeType');body=part.get('body',{})
        if not isinstance(body,dict):raise HTTPException(503,'O Google retornou uma mensagem inválida.')
        data=body.get('data')
        if mime in ('text/plain','text/html') and isinstance(data,str):
            if len(data)>100000:partial=True;data=data[:100000]
            try:text=base64.b64decode(data+'='*((-len(data))%4),altchars=b'-_',validate=True).decode('utf-8',errors='replace')
            except (ValueError,TypeError):raise HTTPException(503,'Não consegui ler o texto desta mensagem.') from None
            (plain if mime=='text/plain' else html).append(text)
        elif mime in ('text/plain','text/html') and body.get('attachmentId'):partial=True
        parts=part.get('parts',[])
        if not isinstance(parts,list):raise HTTPException(503,'O Google retornou uma mensagem inválida.')
        for child in parts[:64]:walk(child,depth+1)
        if len(parts)>64:partial=True
    walk(payload)
    text='\n'.join(plain)
    if not text and html:
        parser=PlainHTML();parser.feed('\n'.join(html));text=''.join(parser.parts)
    text=clean(text)
    return text[:LIMIT],partial or len(text)>LIMIT

def text_request(url,token):
    try:
        with build_opener(NoRedirect()).open(Request(url,headers={'Authorization':'Bearer '+token,'Accept':'text/plain'}),timeout=15) as response:
            raw=response.read(64001)
        text=clean(raw[:64000].decode('utf-8-sig',errors='replace'))
        return text[:LIMIT],len(raw)>64000 or len(text)>LIMIT
    except HTTPError as error:
        status=error.code;error.close()
        raise HTTPException(403 if status==403 else 409 if status in (400,401) else 503,
            'Não consegui abrir este texto. Confira a permissão ou abra no Google.') from None
    except (URLError,TimeoutError,OSError,ValueError):
        raise HTTPException(503,'Não consegui abrir este texto agora.') from None

def preview(owner,connection,service,item_id):
    if service not in ('mail','drive') or not re.fullmatch(r'[A-Za-z0-9_-]{1,200}',item_id):
        raise HTTPException(422,'Escolha um item válido no resultado Google.')
    row=g.account(owner,connection)
    if g.SCOPE[service] not in row['scopes']:raise HTTPException(403,'Esta conta não autorizou este serviço.')
    token=g.access_token(owner,row);headers={'Authorization':'Bearer '+token}
    if service=='mail':
        data=g.request_json('https://gmail.googleapis.com/gmail/v1/users/me/messages/'+item_id+'?format=full',headers=headers)
        if not isinstance(data,dict) or not isinstance(data.get('payload'),dict):raise HTTPException(503,'Mensagem inválida.')
        payload=data['payload'];source=payload.get('headers',[])
        if not isinstance(source,list):raise HTTPException(503,'Mensagem inválida.')
        values={h.get('name','').lower():str(h.get('value',''))[:500] for h in source if isinstance(h,dict) and isinstance(h.get('name'),str)}
        title=values.get('subject','Sem assunto');text,partial=mail_text(payload)
        note='Texto da mensagem, sem anexos, imagens ou links ativos. Não marca como lida nem executa instruções do conteúdo.'
    else:
        root='https://www.googleapis.com/drive/v3/files/'+item_id
        data=g.request_json(root+'?fields=id,name,mimeType,capabilities(canDownload)',headers=headers)
        if not isinstance(data,dict):raise HTTPException(503,'Arquivo inválido.')
        if not isinstance(data.get('capabilities'),dict) or data['capabilities'].get('canDownload') is not True:
            raise HTTPException(403,'Este arquivo não permite obter o conteúdo. Abra no Google.')
        mime=data.get('mimeType');title=str(data.get('name','Arquivo'))[:200]
        if mime=='application/vnd.google-apps.document':url=root+'/export?mimeType=text%2Fplain'
        elif mime in ('text/plain','text/csv','text/markdown'):url=root+'?alt=media'
        else:raise HTTPException(422,'Este leitor aceita Google Docs e arquivos TXT, CSV ou Markdown. Abra outros formatos no Google.')
        text,partial=text_request(url,token)
        note='Prévia de texto simples. Formatação, comentários e conteúdo não textual não aparecem. Não edita o arquivo.'
    return {'account':g.metadata(row),'service':service,'title':title,'text':text,
        'partial':partial,'checked_at':g.stamp(),'note':note+' Limite de 16 mil caracteres. Não enviado à IA nem salvo no histórico.'}
