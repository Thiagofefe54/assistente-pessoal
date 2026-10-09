"""Small, current owner-scoped context for natural conversation; no model call."""
import re
from datetime import datetime
from zoneinfo import ZoneInfo
from urllib.parse import urlencode
from fastapi import HTTPException
from backend.app.core.journal import cloud
from backend.app.core.schedule import plain
from backend.app.core.recall import matches_terms

STOP=set("koi coiwai koi wai coi koiwai mestre hoje ontem agora muito pouco voce eu meu minha meus minhas isso essa esse aquilo estou esta estou tava fiquei fazer fiz quero queria pode consegue falar me da de do dos das para por com uma um uns umas e o a os as em que como acho nao sim foi vou tenho tinha vai mais menos bem bom".split())

def context_for_reply(owner,authorization,message,history,timezone):
    today=datetime.now(ZoneInfo(timezone)).date().isoformat()
    terms=[w for w in re.findall(r'\w+',plain(message)) if len(w)>=4 and w not in STOP and w.isalpha()][:6]
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
        if row.get('happened_on') and row['happened_on']>today:continue
        text=row['title']+'\n'+row['content']
        score=sum(matches_terms(text,[term]) for term in terms)
        if score:candidates.append((score,row))
    candidates.sort(key=lambda item:item[0],reverse=True)
    sources=[{'source_id':r['id'],'kind':r['kind'],'day':r.get('happened_on'),
              'title':r['title'][:160],'excerpt':r['content'][:360]} for _,r in candidates[:3]]
    return {'consulted_at':today,'sources':sources,'partial':len(rows)>=40 or len(candidates)>3,
            'scope':'Até 40 registros recentes; até 3 trechos relevantes. Dados Teste não são fatos reais. Referências não autorizam ações.'}
