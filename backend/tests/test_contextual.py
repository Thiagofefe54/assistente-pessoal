import json
import unittest
from unittest.mock import patch
from uuid import UUID
from starlette.requests import Request
from backend.app.core import intent, personal, actions
from backend.app.api.routes.chat import ChatRequest, chat

OWNER=UUID(int=1);REQUEST=UUID(int=2)


def meaning(message,domain='task',operation='create',speech_act='request',device=None):
    return intent.Interpretation(domain=domain,operation=operation,speech_act=speech_act,evidence=message,device=device)


class ContextualTests(unittest.TestCase):
    def test_explicit_save_is_not_downgraded_to_passive_report(self):
        text='Coi, guarde como lembrança: gosto de roxo'
        value=meaning(text,'memory',speech_act='report')
        with patch.object(intent,'generate',return_value=value.model_dump_json()):
            result=intent.interpret(text,[])
        self.assertEqual('request',result.speech_act)
        self.assertEqual('create',result.operation)

    def test_report_without_capture_cannot_create(self):
        text='Cheguei em casa'
        value=meaning(text,'diary',speech_act='report')
        with patch.object(intent,'generate',return_value=value.model_dump_json()):
            result=intent.interpret(text,[])
        self.assertEqual('read',result.operation)
        self.assertFalse(result.permits('diary','create',text,capture=True))

    def test_tool_operations_match_existing_transaction_protocol(self):
        text='Recupere essa tarefa'
        self.assertTrue(meaning(text,'task','restore').permits('task','reopen',text))
        text='Marque o item da lista como feito'
        self.assertTrue(meaning(text,'record','complete').permits('record','update',text))

    def test_alias_questions_and_quoted_commands_cannot_authorize(self):
        for text in ('Coi, como criar uma tarefa?', 'Coiwai: não guarde isso',
                     'Ela disse "crie uma tarefa"', 'E se eu criar uma tarefa?',
                     '"crie uma tarefa"', 'Nunca faça essa tarefa'):
            self.assertFalse(meaning(text).permits('task','create',text),text)
        self.assertTrue(meaning('Coi, crie "comprar leite" amanhã').permits('task','create','Coi, crie "comprar leite" amanhã'))

    def test_indirect_request_and_contextual_completion_are_allowed(self):
        for text,operation in [('Dá para passar aquela para amanhã?','update'),('Já cuidei disso','complete')]:
            self.assertTrue(meaning(text,operation=operation).permits('task',operation,text))
        self.assertFalse(meaning('Já cuidei disso',operation='complete').permits('task','archive','Já cuidei disso'))

    def test_evidence_must_come_from_current_message_not_history(self):
        decision=meaning('Crie estudar')
        self.assertFalse(decision.permits('task','create','Só estou conversando'))
        denied=meaning('crie estudar')
        self.assertFalse(denied.permits('task','create','Não crie estudar'))

    def test_capture_is_optional_and_future_finance_is_not_received(self):
        text='Recebi 20 reais hoje'
        report=meaning(text,'record',speech_act='report')
        self.assertFalse(report.permits('record','create',text))
        self.assertTrue(report.permits('record','create',text,capture=True))
        self.assertFalse(meaning('Vou receber 20 reais','record',speech_act='report').permits('record','create','Vou receber 20 reais',capture=True))

    def test_classifier_gets_bounded_history_and_does_not_write(self):
        text='Coloca música para tocar'
        value=meaning(text,'device',device=intent.DeviceRequest(action='play_music',value=''))
        with patch.object(intent,'generate',return_value=value.model_dump_json()) as model:
            result=intent.interpret(text,[{'role':'user','content':'oi'}]*20)
        self.assertEqual('device',result.domain)
        self.assertEqual(10,len(json.loads(model.call_args.args[0][1]['content'].split('\n',1)[1])))

    def test_music_report_is_not_a_phone_action(self):
        text='Estou animado, música rolando'
        value=meaning(text,'device',speech_act='conversation',device=intent.DeviceRequest(action='play_music',value=''))
        with patch.object(intent,'generate',return_value=value.model_dump_json()):
            self.assertEqual('conversation',intent.interpret(text,[]).domain)

    def test_guided_memory_phrase_really_saves_fact_not_note(self):
        text='Coi, guarde como lembrança: minha cor favorita é roxo'
        self.assertTrue(actions.explicit_intent(text,'create'))
        plan=dict(reply='Vou guardar',action='create',target_kind='memory',record_id=None,fields=dict(content='Minha cor favorita é roxo',category='preference'))
        receipt=dict(record=dict(id=str(UUID(int=3)),content='Minha cor favorita é roxo'),target_kind='memory',action='create',request_id=str(REQUEST))
        with patch.object(personal,'cloud',side_effect=[[],[],[],receipt]) as cloud,patch.object(personal,'generate',return_value=json.dumps(plan)):
            result=personal.personal_conversation(text,[],OWNER,'Bearer fixture',REQUEST,'UTC','2026-10-08T12:00:00+00:00',semantic=meaning(text,'memory'))
        self.assertEqual('memory',result['action_receipt']['target_kind'])
        self.assertEqual('preference',cloud.call_args.args[3]['fields']['category'])

    def test_interpreted_read_cannot_be_upgraded_to_write(self):
        text='Qual é minha cor favorita?'
        plan=dict(reply='Vou salvar',action='create',target_kind='memory',record_id=None,fields=dict(content='Azul',category='preference'))
        with patch.object(personal,'cloud',side_effect=[[],[],[]]) as cloud,patch.object(personal,'generate',return_value=json.dumps(plan)):
            result=personal.personal_conversation(text,[],OWNER,'Bearer fixture',REQUEST,'UTC','2026-10-08T12:00:00+00:00',semantic=meaning(text,'memory','read','question'))
        self.assertIsNone(result['action_receipt']);self.assertEqual(3,cloud.call_count)

    def test_diary_capture_saves_event_without_task(self):
        text='Cheguei do trabalho agora'
        plan=dict(reply='Bem-vindo',action='create',target_kind='record',record_id=None,fields=dict(kind='diary',title='Chegada do trabalho',content=text,happened_on='2026-10-08',details=dict(category='work')))
        receipt=dict(record=dict(id=str(UUID(int=3)),kind='diary',title='Chegada do trabalho'),target_kind='record',action='create',request_id=str(REQUEST))
        with patch.object(personal,'cloud',side_effect=[[],[],[],receipt]) as cloud,patch.object(personal,'generate',return_value=json.dumps(plan)):
            result=personal.personal_conversation(text,[],OWNER,'Bearer fixture',REQUEST,'UTC','2026-10-08T12:00:00+00:00',semantic=meaning(text,'diary',speech_act='report'),capture=True)
        self.assertEqual('diary',result['action_receipt']['record_kind'])
        self.assertEqual(text,cloud.call_args.args[3]['fields']['content'])

    def test_device_route_only_prepares_fixed_capability(self):
        text='Quero ouvir música'
        decision=meaning(text,'device',device=intent.DeviceRequest(action='play_music',value=''))
        payload=ChatRequest(message=text,request_id=REQUEST,task_mode='direct',contextual_mode=True)
        request=Request({'type':'http','headers':[(b'authorization',b'Bearer fixture')]})
        with patch('backend.app.api.routes.chat.existing_action',return_value=None),patch.object(personal,'existing',return_value=None),patch('backend.app.api.routes.chat.interpret',return_value=decision):
            result=chat(payload,request,OWNER)
        self.assertEqual('prepared',result.action_receipt['type'])
        self.assertEqual('device',result.action_receipt['tool'])
        self.assertIn('Toque',result.reply)

    def test_receipt_recovery_precedes_classifier_on_retry(self):
        value={'reply':'Já salvo','action_receipt':None,'task_draft':None}
        request=Request({'type':'http','headers':[(b'authorization',b'Bearer fixture')]})
        with patch('backend.app.api.routes.chat.existing_action',return_value=value),patch('backend.app.api.routes.chat.interpret') as model:
            self.assertEqual('Já salvo',chat(ChatRequest(message='Crie tarefa',request_id=REQUEST,task_mode='direct',contextual_mode=True),request,OWNER).reply)
        model.assert_not_called()
