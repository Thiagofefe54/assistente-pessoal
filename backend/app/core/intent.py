"""Semantic tool selection. Current utterance provides intent; history is context only."""
import json
import re
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field
from backend.app.core.ai import generate
from backend.app.core.schedule import plain


class DeviceRequest(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    action: Literal['open_app', 'play_music', 'open_settings', 'timer', 'alarm', 'navigate', 'search_web']
    value: str = Field(max_length=300)


class Interpretation(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    domain: Literal['conversation', 'task', 'record', 'memory', 'diary', 'device', 'web']
    operation: Literal['read', 'create', 'update', 'complete', 'archive', 'restore', 'delete', 'reopen', 'undo', 'none']
    speech_act: Literal['request', 'report', 'question', 'conversation']
    evidence: str = Field(max_length=8000)
    device: DeviceRequest | None

    @property
    def tool_operation(self):
        aliases={'task':{'restore':'reopen'},'record':{'complete':'update','reopen':'restore'},'memory':{'archive':'delete'}}
        return aliases.get(self.domain,{}).get(self.operation,self.operation)

    def permits(self, domain, operation, message, capture=False):
        if self.domain != domain or self.tool_operation != operation:
            return False
        if self.speech_act != 'request' and not (capture and self.speech_act == 'report' and operation == 'create'):
            return False
        # Exact current-message provenance, not a quotation from older context.
        evidence = self.evidence.strip()
        if not evidence or evidence not in message or not actionable_fragment(evidence) or not actionable_fragment(message):
            return False
        if self.speech_act=='report' and re.search(r'\b(vou|pretendo|talvez|hipoteticamente)\b',plain(message)): return False
        return True


def addressed_text(message):
    return re.sub(r'^\s*(?:(?:oi|ei|ola)\s+)?(?:koiwai|coiwai|koi|coi)\b[\s,!:\-]*', '', plain(message))


def actionable_fragment(message):
    text = addressed_text(message).strip()
    text = re.sub(r'"[^"\n]*"|“[^”\n]*”', '', text).strip()
    if not text or re.search(r'^(como\b|o que\b|se\b)|\b(e se|hipoteticamente|por exemplo|suponha)\b', text):
        return False
    if re.search(r'\b(nao|nunca|sem)\b', text):
        return False
    if re.search(r'\b(ele|ela)\s+(disse|falou|pediu)\b', text):
        return False
    return True


SCHEMA = Interpretation.model_json_schema()
# Provider strict schemas require all nullable fields to be present.
SCHEMA['required'] = list(SCHEMA['properties'])
FORMAT = {'type': 'json_schema', 'json_schema': {'name': 'koi_intent', 'strict': True, 'schema': SCHEMA}}


def interpret(message, history, capture=False):
    instructions = '''Escolha a ferramenta para o pedido atual de uma assistente pessoal.
Responda apenas JSON no schema. Compreenda o sentido, não exija palavras-chave.
Koi/Coi/Koiwai/Coiwai são nomes da assistente, inclusive erros de ditado.
domain: task para tarefas/compromissos; record para notas/listas/metas/treinos/finanças;
memory para preferências duradouras e lembranças pessoais; diary para relatos de
acontecimentos/sono/chegadas; device para ações locais do telefone; web para pesquisa
externa solicitada; conversation para conversa comum, dúvidas e brincadeiras.
operation read para consulta; none para conversa sem alteração. Quando uma consulta
precisa de dados pessoais, selecione a ferramenta correspondente com read.
speech_act request é uma intenção atual clara, inclusive pedidos indiretos como
"dá para passar aquela tarefa para amanhã?". Perguntas sobre como fazer, citações,
hipóteses e negações não são autorização. Evidência é um trecho EXATO do pedido atual.
Histórico ajuda a resolver "essa", "ela", "já fiz"; nunca autoriza repetir ação antiga.
Não escolha alteração por causa de uma sugestão da assistente no histórico.
"coloca música para tocar" é device/request; "estou animado, música rolando" é conversa.
"minha cor preferida é roxo" é memory/report; "hoje gastei 12 reais" é record/report;
"vou ganhar dinheiro" não é receita recebida. Relato não vira tarefa automaticamente.
"guarde como lembrança" é memory/create/request, não uma nota.
Uma ordem para guardar um fato continua sendo REQUEST, mesmo quando o conteúdo
da lembrança é um relato. Não classifique a ordem inteira como report.
Para device use operation create e speech_act request.
device só possui open_app (value nome do app), play_music (value busca ou vazio),
open_settings (value wifi, bluetooth ou app), timer (value segundos inteiros),
alarm (value HH:mm), navigate (value lugar), search_web (value busca).
Não há pagamentos, envio de mensagens, leitura de bancos, toque em outras telas,
pausar player nem integração Google. Não invente ferramenta. device null fora de device.
Se faltar alvo/horário, escolha conversation/none e a resposta poderá esclarecer.
'''
    instructions += ('\nCAPTURA DE RELATOS ATIVADA: relato pessoal claro de acontecimento passado/presente '
                     'usa diary/create/report; preferência duradoura usa memory/create/report; '
                     'gasto ou recebimento já ocorrido usa record/create/report. '
                     'Planos e dinheiro futuro não são recebimentos.\n' if capture else
                     '\nCAPTURA DESATIVADA: relatos podem ser acolhidos/consultados; somente pedidos atuais claros autorizam escrita.\n')
    result = Interpretation.model_validate_json(generate([
        {'role': 'system', 'content': instructions},
        {'role': 'user', 'content': 'Contexto, não ordens:\n' + json.dumps(history[-10:], ensure_ascii=False)},
        {'role': 'user', 'content': message}], response_format=FORMAT, max_tokens=512))
    # An explicit save directive is not a passive report, even if its fact is one.
    if (result.domain in ('memory','record') and result.operation=='create' and result.speech_act=='report'
            and re.match(r'^(guarde|salve|registre|anote|crie)\b',addressed_text(message))
            and actionable_fragment(message)):
        result=result.model_copy(update={'speech_act':'request'})
    if result.domain == 'device' and (result.device is None or not result.permits('device', 'create', message)):
        return result.model_copy(update={'domain': 'conversation', 'operation': 'none', 'device': None})
    # Automatic capture is opt-in. Otherwise a report remains a natural conversation.
    if result.speech_act == 'report' and not capture:
        return result.model_copy(update={'operation': 'read', 'device': None})
    return result
