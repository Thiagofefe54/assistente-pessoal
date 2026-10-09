"""Bounded literal retrieval with original sources. No inferred or generated memories."""
import re
from datetime import timedelta
from urllib.parse import urlencode
from fastapi import HTTPException
from backend.app.core.journal import cloud
from backend.app.core.personal import load_records
from backend.app.core.schedule import plain
from difflib import SequenceMatcher


def recall(owner, authorization, query, today, start=None, end=None, diary_only=False):
    end = end or today
    start = start or end - timedelta(days=89)
    if start > end or (end-start).days > 365:
        raise HTTPException(422, 'Escolha um período de até 366 dias, com início antes do fim.')
    terms = re.findall(r'\w+', plain(query))
    if len(terms)>8: raise HTTPException(422,'Busque com até oito palavras por vez.')
    def matches(text):
        return matches_terms(text,terms)
    found=[]
    records=load_records(owner,authorization)
    for r in records:
        if r.get('archived_at') or (diary_only and r['kind']!='diary'): continue
        day=r.get('happened_on')
        if day and not start.isoformat() <= day <= end.isoformat(): continue
        if not day and (diary_only or not terms): continue
        text=r['title']+'\n'+r['content']
        if matches(text): found.append({'source':'diary' if r['kind']=='diary' else 'record',
            'id':r['id'],'day':day,'title':r['title'],'excerpt':excerpt(text,terms),
            'area':{'note':'Notas','list':'Listas','goal':'Metas','workout':'Treinos',
                    'diary':'Diário','bill':'Contas','budget':'Orçamento'}.get(r['kind'],'Finanças')})
    chats=[]
    if not diary_only:
        facts=cloud('memory_facts?'+urlencode({'user_id':'eq.'+str(owner),'select':'id,content,category',
                                             'order':'slot.asc','limit':20}),authorization)
        if not isinstance(facts,list) or len(facts)>20:
            raise HTTPException(503,'Não consegui conferir as fontes das lembranças.')
        for r in facts:
            if not isinstance(r,dict) or not isinstance(r.get('id'),str) or not isinstance(r.get('content'),str) or not 1<=len(r['content'])<=500:
                raise HTTPException(503,'Fonte de lembrança inválida.')
            if terms and matches(r['content']): found.append({'source':'memory','id':r['id'],'day':None,
                'title':'Lembrança confirmada','excerpt':excerpt(r['content'],terms),'area':'Lembranças'})
        for offset in range(0,300,100):
            page=cloud('chat_messages?'+urlencode({'user_id':'eq.'+str(owner),'role':'eq.user',
                'select':'id,content,local_date,occurred_at','and':f'(local_date.gte.{start.isoformat()},local_date.lte.{end.isoformat()})',
                'order':'occurred_at.desc,id.desc','limit':100,'offset':offset}),authorization)
            if not isinstance(page,list) or len(page)>100: raise HTTPException(503,'Não consegui conferir as fontes da conversa.')
            chats.extend(page)
            if len(page)<100: break
        for r in chats:
            if not isinstance(r,dict) or not isinstance(r.get('id'),str) or not isinstance(r.get('local_date'),str) or not isinstance(r.get('content'),str) or not 1<=len(r['content'])<=20000:
                raise HTTPException(503,'Fonte de conversa inválida.')
            if matches(r['content']): found.append({'source':'chat','id':r['id'],'day':r['local_date'],
                'title':'Você contou no chat','excerpt':excerpt(r['content'],terms),'area':'Conversa'})
    found.sort(key=lambda r:(r['day'] or '', r['source']=='memory',r['id']),reverse=True)
    return {'query':query,'start':start.isoformat(),'end':end.isoformat(),'results':found[:12],
            'matches_in_scanned_data':len(found),'scanned_records':len(records),'scanned_messages':len(chats),
            'partial':len(found)>12 or len(records)>=2000 or len(chats)>=300,
            'scope':'Busca por palavras, variações próximas e sinônimos limitados, com até 12 fontes. Conversas: 300 mensagens recentes do período; registros: até 2.000. Lembranças confirmadas não têm data do acontecimento.'}


def excerpt(text, terms):
    # Keep an exact contiguous original excerpt; normalization is only for matching.
    offset=next((plain(text).find(term) for term in terms if term in plain(text)),0)
    # Unicode normalization can change indices, so fall back to the start rather than inventing text.
    if len(plain(text))!=len(text): offset=0
    start=max(0,offset-60)
    return ('…' if start else '')+text[start:start+360]+('…' if len(text)>start+360 else '')


# Retrieval only: these aliases never authorize a write or create a remembered fact.
TOPICS = (
    ('academia','treino','treinar','musculacao'),
    ('estudo','estudar','estudos','escola','aula'),
    ('trabalho','trabalhar','trabalhei','emprego'),
    ('sono','dormi','dormir','acordei'),
    ('dinheiro','financas','financeiro'),
)

def matches_terms(text,terms):
    normalized=plain(text);words=re.findall(r'\w+',normalized)
    def match(term):
        if term in normalized:return True
        aliases=next((group for group in TOPICS if term in group),())
        if any(alias in normalized for alias in aliases):return True
        # Never fuzzy-match amounts, dates, short words or identifiers.
        return len(term)>=5 and term.isalpha() and any(
            word.isalpha() and len(word)>=5 and abs(len(term)-len(word))<=1
            and SequenceMatcher(None,term,word).ratio()>=.88 for word in words)
    return all(match(term) for term in terms)
