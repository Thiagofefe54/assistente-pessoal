import base64
import json
import unittest
from datetime import datetime
from uuid import UUID
from unittest.mock import patch
from fastapi import HTTPException
from pydantic import ValidationError
from backend.app.core import personal, actions
from backend.app.core.schedule import relative_deadline, simple_relative_title
from backend.app.api.routes.chat import ChatRequest

OWNER=UUID(int=1); REQUEST=UUID(int=2); TARGET=str(UUID(int=3))
ROW=dict(id=TARGET,kind='expense',title='Lanche',content='',amount_cents=1250,
         progress=0,happened_on='2026-10-08',archived_at=None,updated_at='version')

class MobileV1Tests(unittest.TestCase):
    def test_original_clock_rounds_up_and_crosses_midnight(self):
        self.assertEqual(('2026-10-08','13:20'),relative_deadline('daqui 10 minutos','2026-10-08T13:09:23-03:00','America/Sao_Paulo'))
        self.assertEqual(('2026-10-09','00:05'),relative_deadline('em 10 minutos','2026-10-08T23:55:00-03:00','America/Sao_Paulo'))
        self.assertIsNone(relative_deadline('Renomeie para "daqui 10 minutos"','2026-10-08T13:00:00-03:00','America/Sao_Paulo'))
        with self.assertRaises(ValueError):relative_deadline('daqui 99999 dias','2026-10-08T13:00:00-03:00','America/Sao_Paulo')

    def test_clear_relative_reminder_is_created_without_model(self):
        message='crie uma tarefa me lembrando de arrumar a casa daqui 10 minutos'
        self.assertEqual('arrumar a casa',simple_relative_title(message))
        with patch.object(actions,'generate') as model,patch.object(actions,'cloud',side_effect=[[],dict(request_id=str(REQUEST),action='create',task=dict(ROW,title='arrumar a casa'))]) as cloud:
            result=actions.direct_conversation(message,[],[],dict(tasks=[],now='2026-10-08T13:09:23-03:00'),OWNER,'Bearer fiction',REQUEST,'America/Sao_Paulo')
        model.assert_not_called()
        self.assertEqual('13:20',cloud.call_args.args[3]['fields']['due_time'])
        self.assertIsNotNone(result['action_receipt'])

    def test_task_commands_are_not_stolen_by_note_keywords(self):
        self.assertFalse(personal.applies('crie uma tarefa de revisar minhas metas'))
        self.assertTrue(personal.applies('Crie uma lista de compras'))
        self.assertTrue(personal.applies('Lembre que gosto de roxo'))

    def test_followup_selects_record_tool_without_reusing_old_authorization(self):
        history=[dict(role='user',content='Crie uma lista de compras'),dict(role='assistant',content='Salvei sua lista')]
        self.assertTrue(personal.follows_record('Marque leite como comprado',history))
        self.assertFalse(personal.follows_record('oi',history))
        self.assertFalse(personal.follows_record('Não marque leite como comprado',history))
        self.assertFalse(personal.follows_record('Conclua a tarefa estudar',history))

    def test_marking_a_list_item_uses_a_real_list_version(self):
        row=dict(ROW,kind='list',amount_cents=None,content='[ ] Leite')
        plan=dict(reply='Vou marcar',action='update',target_kind='record',record_id=TARGET,fields=dict(content='[x] Leite'))
        with patch.object(personal,'cloud',side_effect=[[],[],[row],dict(record=dict(row,content='[x] Leite'),target_kind='record',action='update',request_id=str(REQUEST))]) as cloud,patch.object(personal,'generate',return_value=json.dumps(plan)):
            result=personal.personal_conversation('Marque leite como comprado',[],OWNER,'Bearer fiction',REQUEST,'UTC','2026-10-08T13:00:00+00:00')
        self.assertEqual('version',cloud.call_args.args[3]['expected_updated_at'])
        self.assertEqual('list',result['action_receipt']['record_kind'])

    def test_tool_schema_does_not_mix_note_and_memory_fields(self):
        for message,target,excluded in [('Crie uma nota fictícia','record',{'category'}),('Lembre que gosto de roxo','memory',{'title','kind','amount_cents','progress','happened_on'})]:
            with patch.object(personal,'cloud',side_effect=[[],[],[]]),patch.object(personal,'generate',return_value=json.dumps(dict(reply='Conversa',action=None,target_kind=None,record_id=None,fields=None))) as model:
                personal.personal_conversation(message,[],OWNER,'Bearer fixture',REQUEST,'UTC','2026-10-08T13:00:00+00:00')
            properties=model.call_args.kwargs['response_format']['json_schema']['schema']['properties']
            self.assertEqual([target,None],properties['target_kind']['enum'])
            fields=properties['fields']['anyOf'][0]
            self.assertFalse(excluded & set(fields['properties']))
            self.assertEqual(set(fields['required']),set(fields['properties']))

    def run_plan(self,message,plan,rpc=None):
        values=[[],[],[dict(ROW)]]
        if rpc is not None: values.append(rpc)
        with patch.object(personal,'cloud',side_effect=values) as cloud,patch.object(personal,'generate',return_value=json.dumps(plan)):
            result=personal.personal_conversation(message,[],OWNER,'Bearer fiction',REQUEST,'UTC','2026-10-08T13:00:00+00:00')
        return result,cloud

    def test_financial_create_has_exact_cents_and_verified_receipt(self):
        plan=dict(reply='Vou salvar',action='create',target_kind='record',record_id=None,fields=dict(kind='expense',title='Lanche',amount_cents=1250))
        result,cloud=self.run_plan('Registre uma despesa Lanche de R$12,50',plan,dict(record=ROW,target_kind='record',action='create',request_id=str(REQUEST)))
        self.assertEqual(1250,cloud.call_args.args[3]['fields']['amount_cents'])
        self.assertEqual('expense',result['action_receipt']['record_kind'])
        self.assertEqual('Bearer fiction',cloud.call_args.args[1])

    def test_query_and_negation_cannot_authorize_model_mutation(self):
        plan=dict(reply='Vou salvar',action='create',target_kind='record',record_id=None,fields=dict(kind='expense',title='Lanche',amount_cents=1250))
        for message in ('Não registre essa despesa','Como registrar despesas?','Ela disse "registre uma despesa"'):
            result,cloud=self.run_plan(message,plan)
            self.assertIsNone(result['action_receipt']);self.assertEqual(3,cloud.call_count)

    def test_update_uses_snapshot_version_and_cloud_failure_is_not_success(self):
        plan=dict(reply='Pronto',action='update',target_kind='record',record_id=TARGET,fields=dict(amount_cents=1500))
        result,cloud=self.run_plan('Altere a despesa Lanche para R$15',plan,dict(record=dict(ROW,amount_cents=1500),target_kind='record',action='update',request_id=str(REQUEST)))
        self.assertEqual('version',cloud.call_args.args[3]['expected_updated_at'])
        with self.assertRaises(HTTPException):self.run_plan('Altere a despesa Lanche para R$15',plan,HTTPException(409,'changed'))

    def test_recovery_validates_message_and_does_not_write_twice(self):
        receipt=dict(source_hash='a'*64,undone=False,result=dict(record=ROW,target_kind='record',action='create',request_id=str(REQUEST)))
        with patch.object(personal,'cloud',return_value=[receipt]) as cloud:
            self.assertIn('Salvei',personal.existing(OWNER,'Bearer fiction',REQUEST,'a'*64)['reply'])
            self.assertEqual(1,cloud.call_count)
            with self.assertRaises(HTTPException):personal.existing(OWNER,'Bearer fiction',REQUEST,'b'*64)

    def test_image_and_clock_validation(self):
        for image in ('https://example.com/private','!invalid!',base64.b64encode(b'notjpeg').decode()):
            with self.assertRaises(ValidationError):ChatRequest(message='veja',image_jpeg_base64=image)
        with self.assertRaises(ValidationError):ChatRequest(message='oi',requested_at=datetime(2026,10,8))
        value=ChatRequest(message='veja',image_jpeg_base64=base64.b64encode(b'\xff\xd8\xff\xe0fixture').decode(),requested_at=datetime.fromisoformat('2026-10-08T13:00:00-03:00'))
        self.assertIsNotNone(value.requested_at)
