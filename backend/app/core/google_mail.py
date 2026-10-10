"""Reviewed mail sends, persistent claims, no model calls and no uncertain retries."""
import base64
import json
from email.message import EmailMessage
from email.policy import SMTP
from urllib.parse import urlencode
from fastapi import HTTPException
from backend.app.core import google_connections as g


def important(owner):
    results=[]
    partial=False
    for metadata in g.list_accounts(owner)['accounts']:
        if 'mail' not in metadata['services']:continue
        try:
            row=g.account(owner,metadata['id'])
            token=g.access_token(owner,row)
            data=g.request_json('https://gmail.googleapis.com/gmail/v1/users/me/messages?'+urlencode({
                'q':'is:unread is:important newer_than:1d','maxResults':20}),headers={'Authorization':'Bearer '+token})
            messages=data.get('messages',[]) if isinstance(data,dict) else None
            if not isinstance(messages,list):raise HTTPException(503,'Consulta inválida.')
            ids=[item.get('id') for item in messages if isinstance(item,dict)]
            if len(ids)!=len(messages) or any(not isinstance(i,str) or not 1<=len(i)<=200 for i in ids):raise HTTPException(503,'Consulta inválida.')
            results.append({'connection_id':metadata['id'],'ids':ids[:20],'partial':bool(data.get('nextPageToken'))})
        except HTTPException:partial=True
    return {'accounts':results,'partial':partial,'checked_at':g.stamp()}


def receipt(owner, request_id):
    rows=g.database('koi_google_actions',{'user_id':'eq.'+str(owner),'request_id':'eq.'+str(request_id),'limit':1})
    if not rows:return None
    row=rows[0]
    if row['phase']!='done':return {'sent':False,'uncertain':True,'reply':'O envio foi iniciado, mas não foi confirmado. Confira Enviados no Gmail antes de preparar outro envio.'}
    # A receipt from a different Google action cannot be interpreted as a mail send.
    return g.unseal(owner,'mail-send:'+str(request_id),row['response'])


def send(owner, connection_id, request_id, recipient, subject, body, reviewed):
    if reviewed is not True:raise HTTPException(422,'Revise destinatário, assunto e texto antes de enviar.')
    canonical=json.dumps({'connection':str(connection_id),'recipient':recipient,'subject':subject,'body':body},sort_keys=True,ensure_ascii=False)
    fingerprint=g.digest('mail-send\n'+canonical)
    query={'user_id':'eq.'+str(owner),'request_id':'eq.'+str(request_id),'limit':1}
    old=g.database('koi_google_actions',query)
    if old:
        if old[0]['message_hash']!=fingerprint:raise HTTPException(409,'Esse pedido já corresponde a outra mensagem. Confira o envio anterior.')
        return receipt(owner,request_id)
    row=g.account(owner,connection_id)
    if g.SCOPE['mail_send'] not in row['scopes']:raise HTTPException(403,'Reautorize esta conta Google para permitir envio de e-mail.')
    token=g.access_token(owner,row)
    message=EmailMessage(policy=SMTP)
    message['To']=recipient
    message['Subject']=subject
    message.set_content(body)
    raw=base64.urlsafe_b64encode(message.as_bytes()).decode()
    try:
        claimed=g.database('koi_google_actions',method='POST',payload={'user_id':str(owner),'request_id':str(request_id),'message_hash':fingerprint,'phase':'started'})
    except HTTPException:
        # A concurrent claimant may have won; replay only after checking the same fingerprint.
        old=g.database('koi_google_actions',query)
        if not old or old[0]['message_hash']!=fingerprint:raise
        return receipt(owner,request_id)
    if not isinstance(claimed,list) or len(claimed)!=1:raise HTTPException(503,'Não consegui proteger o envio. Nenhuma mensagem foi enviada.')
    result=g.request_json('https://gmail.googleapis.com/gmail/v1/users/me/messages/send',method='POST',payload={'raw':raw},headers={'Authorization':'Bearer '+token})
    if not isinstance(result,dict) or not isinstance(result.get('id'),str) or not result['id']:
        raise HTTPException(503,'O Gmail não confirmou o envio. Confira Enviados antes de repetir.')
    answer={'sent':True,'uncertain':False,'reply':'O Gmail confirmou o envio.','account':row['email'],'message_id':result['id']}
    saved=g.database('koi_google_actions',{'user_id':'eq.'+str(owner),'request_id':'eq.'+str(request_id)},method='PATCH',
        payload={'phase':'done','response':g.seal(owner,'mail-send:'+str(request_id),answer)})
    if not isinstance(saved,list) or len(saved)!=1:raise HTTPException(503,'O Gmail respondeu, mas não consegui guardar o recibo. Confira Enviados antes de repetir.')
    return answer
