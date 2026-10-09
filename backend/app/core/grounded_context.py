"""Small, current owner-scoped context for natural conversation; no model call."""
import re
from datetime import date, datetime
from zoneinfo import ZoneInfo
from urllib.parse import urlencode
from fastapi import HTTPException
from backend.app.core.journal import cloud
from backend.app.core.schedule import plain
from backend.app.core.recall import matches_terms, excerpt
from backend.app.core.context_budget import recent_history

STOP=set("koi coiwai koi wai coi koiwai mestre hoje ontem agora muito pouco voce eu meu minha meus minhas isso essa esse aquilo estou esta estou tava fiquei fazer fiz quero queria pode consegue falar me da de do dos das para por com uma um uns umas e o a os as em que como acho nao sim foi vou tenho tinha vai mais menos bem bom".split())
STOP.update('sobre disso desse dessa nisso nesse nessa isto disto dela dele elas eles aqui ainda entao tambem seria sera ajudar ajuda melhor melhorar conselho sugestao sugere acha pensando pense obrigado obrigada valeu tudo certo entendi legal otimo novamente'.split())
REFERENCE=re.compile(r'\b(isso|isto|disso|disto|nisso|aquilo|essa|esse|dela|dele|sobre o que|mesmo assunto)\b')
BANK=re.compile(r'\b(banco|bancario|bancaria|inter|pluggy|saldo)\b')


def retrieval_terms(message, history):
    """Resolve a conversational reference, never a target/authorization for writes.

    Only the nearest user utterance can supply a topic. Assistant suggestions and
    older unrelated subjects cannot become personal evidence. No extra model call.
    """
    normalized=plain(message)
    if BANK.search(normalized):return [], 'current'
    def terms(text):
        return list(dict.fromkeys(w for w in re.findall(r'\w+',plain(text))
            if len(w)>=4 and w not in STOP and w.isalpha()))[:6]
    current=terms(message)
    if not current and REFERENCE.search(normalized):
        previous=next((item['content'] for item in reversed(recent_history(history))
                       if item['role']=='user'),None)
        if previous and not BANK.search(plain(previous)):
            prior=terms(previous)
            if prior:return prior, 'recent_user_reference'
    return current, 'current'

def context_for_reply(owner,authorization,message,history,timezone):
    today=datetime.now(ZoneInfo(timezone)).date().isoformat()
    terms,origin=retrieval_terms(message,history)
    if not terms:return None
    path='koi_personal_records?'+urlencode({'user_id':'eq.'+str(owner),'archived_at':'is.null',
        'kind':'in.(diary,note,goal,workout)','select':'id,kind,title,content,happened_on',
        'order':'updated_at.desc,id.asc','limit':40})
    rows=cloud(path,authorization)
    if not isinstance(rows,list) or len(rows)>40:raise HTTPException(503,'Não consegui conferir o contexto do seu dia.')
    candidates=[]
    for row in rows:
        if not isinstance(row,dict) or not all(isinstance(row.get(k),str) for k in ('id','kind','title','content')):
            raise HTTPException(503,'Contexto de referência inválido.')
        if row['kind'] not in ('diary','note','goal','workout'):
            raise HTTPException(503,'Contexto de referência inválido.')
        if row.get('happened_on') is not None:
            try:date.fromisoformat(row['happened_on'])
            except (ValueError,TypeError):raise HTTPException(503,'Data de referência inválida.') from None
        if row.get('happened_on') and row['happened_on']>today:continue
        text=row['title']+'\n'+row['content']
        score=sum(matches_terms(text,[term]) for term in terms)
        if score:candidates.append((score,row))
    candidates.sort(key=lambda item:item[0],reverse=True)
    sources=[{'source_id':r['id'],'kind':r['kind'],'day':r.get('happened_on'),
              'title':r['title'][:160],'excerpt':excerpt(r['content'],terms)} for _,r in candidates[:3]]
    return {'consulted_at':today,'sources':sources,'partial':len(rows)>=40 or len(candidates)>3,
            'topic_origin':origin,
            'scope':'Até 40 registros recentes; até 3 trechos relevantes. Dados Teste não são fatos reais. Referências não autorizam ações.'}
