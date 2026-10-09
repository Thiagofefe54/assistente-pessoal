"""Poe Responses adapter. No retries, cross-provider fallback or provider history."""
import json
import logging
from urllib.parse import urlparse
from urllib.request import Request, build_opener
from jsonschema import Draft202012Validator
from backend.app.core.auth import NoRedirect
from backend.app.core.config import settings
from backend.app.core.context_budget import compact_json


def text_completion(messages, response_format=None, max_tokens=None):
    # Some model gateways keep only one system message. Merge our trusted rules
    # and schema so the schema cannot disappear behind another system message.
    instructions='\n\n'.join(m['content'] for m in messages if m['role']=='system')
    inputs=[dict(m) for m in messages if m['role']!='system']
    if response_format:
        schema=response_format['json_schema']['schema']
        instructions+='\n\nResponda somente JSON válido, sem Markdown. Obedeça exatamente este schema: '+compact_json(schema)
    if instructions: inputs.insert(0,{'role':'system','content':instructions})
    payload={'model':settings.poe_model,'messages':inputs,'stream':False,
             'max_completion_tokens':max_tokens or settings.poe_max_output_tokens}
    if settings.poe_model=='GPT-OSS-120B': payload['extra_body']={'reasoning_effort':'low'}
    request=Request('https://api.poe.com/v1/chat/completions',data=compact_json(payload).encode('utf-8'),
        headers={'Authorization':'Bearer '+settings.poe_api_key.get_secret_value(),
                 'Content-Type':'application/json','Accept':'application/json'},method='POST')
    with build_opener(NoRedirect()).open(request,timeout=settings.groq_timeout_seconds) as response:
        raw=response.read(1_000_001)
    if len(raw)>1_000_000: raise ValueError('Oversized response')
    result=json.loads(raw);choice=result['choices'][0]
    content=choice['message']['content']
    if choice.get('finish_reason')!='stop' or not isinstance(content,str) or not content.strip():
        raise ValueError('Incomplete response')
    if response_format:
        # Accept a whole JSON code fence without another paid generation. Never
        # extract a JSON fragment from arbitrary prose or relax the schema.
        import re
        wrapped=re.fullmatch(r'\s*```(?:json)?\s*\n(.*?)\n```\s*',content,re.S)
        if wrapped: content=wrapped[1].strip()
        try: value=json.loads(content)
        except ValueError:
            logging.getLogger(__name__).warning('Poe structured rejected category=json')
            raise ValueError('Invalid structured response') from None
        error=next(Draft202012Validator(schema).iter_errors(value),None)
        if error:
            logging.getLogger(__name__).warning('Poe structured rejected category=schema validator=%s',error.validator)
            raise ValueError('Invalid structured response')
    usage=result.get('usage',{})
    if isinstance(usage,dict):
        counts=[usage.get(k) for k in ('prompt_tokens','completion_tokens','total_tokens')]
        if all(type(n) is int and 0<=n<=10_000_000 for n in counts):
            logging.getLogger(__name__).info('Poe usage input=%d output=%d total=%d structured=%s',*counts,response_format is not None)
    return content.strip()


def completion(messages, response_format=None, max_tokens=None, web=False):
    image = any(isinstance(m['content'], list) for m in messages)
    if not image and not web: return text_completion(messages,response_format,max_tokens)
    model = settings.poe_web_model if web else settings.poe_vision_model if image else settings.poe_model
    if not model: raise ValueError('Unconfigured Poe modality')
    items=[]
    for message in messages:
        value=message['content']
        if isinstance(value,list):
            value=[{'type':'input_text','text':p['text']} if p['type']=='text' else
                   {'type':'input_image','image_url':p['image_url']['url']} for p in value]
        items.append({'role':message['role'],'content':value})
    payload={'model':model,'input':items,'stream':False,'store':False,
             'truncation':'disabled','max_output_tokens':max_tokens or settings.poe_max_output_tokens}
    if model=='GPT-OSS-120B': payload['reasoning']={'effort':'low'}
    if response_format:
        spec=response_format['json_schema']
        payload['text']={'format':{'type':'json_schema','name':spec['name'],
                                  'strict':spec.get('strict',True),'schema':spec['schema']}}
    if web:
        payload.update(tools=[{'type':'web_search_preview'}],tool_choice='required',
                       include=['web_search_call.action.sources'])
    request=Request('https://api.poe.com/v1/responses',data=compact_json(payload).encode('utf-8'),
        headers={'Authorization':'Bearer '+settings.poe_api_key.get_secret_value(),
                 'Content-Type':'application/json','Accept':'application/json','User-Agent':'Koiwai/1.3'},method='POST')
    with build_opener(NoRedirect()).open(request,timeout=settings.groq_timeout_seconds) as response:
        raw=response.read(1_000_001)
    if len(raw)>1_000_000: raise ValueError('Oversized Poe response')
    result=json.loads(raw)
    if result.get('status')!='completed': raise ValueError('Incomplete Poe response')
    blocks=[p for item in result['output'] if item.get('type')=='message' and item.get('role')=='assistant'
            for p in item.get('content',[])]
    if any(p.get('type')=='refusal' for p in blocks): raise ValueError('Refused response')
    content=''.join(p['text'] for p in blocks if p.get('type')=='output_text')
    if not content.strip(): raise ValueError('Empty Poe response')
    usage=result.get('usage',{})
    if isinstance(usage,dict):
        counts=[usage.get(k) for k in ('input_tokens','output_tokens','total_tokens')]
        if all(type(n) is int and 0<=n<=10_000_000 for n in counts):
            logging.getLogger(__name__).info('Poe usage input=%d output=%d total=%d structured=%s',
                                            *counts,response_format is not None)
    if web:
        searches=[item for item in result['output'] if item.get('type')=='web_search_call' and item.get('status')=='completed']
        if not searches: raise ValueError('Search not executed')
        sources=[s.get('url','') for item in searches for s in item.get('action',{}).get('sources',[])]
        sources += [a.get('url','') for p in blocks for a in p.get('annotations',[]) if a.get('type')=='url_citation']
        urls=[]
        for url in sources:
            parsed=urlparse(url)
            if parsed.scheme=='https' and parsed.hostname and not parsed.username and len(url)<=1000 and url not in urls:
                urls.append(url)
        if not urls: raise ValueError('Search sources missing')
        content+='\n\nFontes da pesquisa:\n'+'\n'.join(urls[:5])
    return content.strip()
