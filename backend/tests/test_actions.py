import json
import unittest
from unittest.mock import patch
from uuid import UUID
from fastapi import HTTPException
from backend.app.core import actions

OWNER=UUID(int=1);REQUEST=UUID(int=2);TARGET=str(UUID(int=3))
ROW={'id':TARGET,'title':'Estudar','due_date':'2026-10-08','due_time':'09:00:00','recurrence':'none','updated_at':'version','completed_at':None}

class ActionTests(unittest.TestCase):
    def test_hypotheses_negations_and_quoted_orders_are_not_actions(self):
        for message in ('Não conclua estudar','Como concluir estudar?','E se eu concluir estudar?', 'Ela disse "conclua estudar"'):
            self.assertFalse(actions.explicit_intent(message,'complete'))
        self.assertTrue(actions.explicit_intent('Já fiz a tarefa estudar','complete'))
        self.assertTrue(actions.explicit_intent('Pode marcar estudar como feita?','complete'))
        self.assertTrue(actions.explicit_intent('Crie "comprar leite" amanhã','create'))
    def call(self,plan,tasks=None,cloud_result=None):
        model=patch.object(actions,'generate',return_value=json.dumps(plan))
        network=patch.object(actions,'cloud',side_effect=[[],cloud_result or {'request_id':str(REQUEST),'action':plan['action']['type'],'task':ROW,'undone':False}])
        with model,network as cloud:
            message='Crie Estudar' if plan['action']['type']=='create' else 'Conclua Estudar'
            value=actions.direct_conversation(message,[],[],tasks or {'tasks':[ROW]},OWNER,'Bearer fiction',REQUEST,'UTC')
            return value,cloud
    def test_complete_uses_current_owner_version_and_verified_reply(self):
        value,cloud=self.call({'reply':'Vou pensar','action':{'type':'complete','task_id':TARGET,'draft':None,'patch':None}})
        self.assertIn('concluída',value['reply'])
        self.assertEqual('version',cloud.call_args.args[3]['expected_updated_at'])
        self.assertEqual('Bearer fiction',cloud.call_args.args[1])
        self.assertEqual(str(REQUEST),value['action_receipt']['request_id'])
    def test_create_is_direct_and_has_stable_identity(self):
        plan={'reply':'Confirma?','action':{'type':'create','task_id':None,'patch':None,'draft':{'title':'Estudar','notes':'','due_date':None,'due_time':None,'recurrence':'none'}}}
        first,c1=self.call(plan);second,c2=self.call(plan)
        self.assertIsNone(first['task_draft'])
        self.assertEqual(c1.call_args.args[3]['task_id'],c2.call_args.args[3]['task_id'])
        self.assertNotIn('Confirma',first['reply'])
    def test_receipt_recovery_and_changed_message_rejected(self):
        row={'source_hash':'a'*64,'result':{'request_id':str(REQUEST),'action':'complete','task':ROW},'undone':False}
        with patch.object(actions,'cloud',return_value=[row]):
            self.assertIn('concluída',actions.existing_action(OWNER,'Bearer fiction',REQUEST,'a'*64)['reply'])
            with self.assertRaises(HTTPException) as error: actions.existing_action(OWNER,'Bearer fiction',REQUEST,'b'*64)
            self.assertEqual(409,error.exception.status_code)
    def test_duplicate_title_requests_clarification_without_write(self):
        plan={'reply':'','action':{'type':'complete','task_id':TARGET,'draft':None,'patch':None}}
        plan['reply']='Vou concluir'
        result,cloud=self.call(plan,{'tasks':[dict(ROW,same_title_count=2),dict(ROW,id=str(UUID(int=4)))]})
        self.assertIsNone(result['action_receipt']);self.assertEqual(1,cloud.call_count)
    def test_unknown_target_and_invalid_schedule_never_write(self):
        for action in [dict(type='complete',task_id=str(UUID(int=99)),draft=None,patch=None),
            dict(type='update',task_id=TARGET,draft=None,patch=dict(title=None,notes=None,due_date=None,set_date=True,due_time=None,set_time=False,recurrence=None))]:
            with patch.object(actions,'cloud',return_value=[]) as cloud,patch.object(actions,'generate',return_value=json.dumps({'reply':'x','action':action})):
                message='Conclua' if action['type']=='complete' else 'Altere'
                with self.assertRaises(HTTPException): actions.direct_conversation(message,[],[],{'tasks':[ROW]},OWNER,'Bearer fiction',REQUEST,'UTC')
                self.assertEqual(1,cloud.call_count)
    def test_cloud_conflict_cannot_be_reported_as_success(self):
        plan={'reply':'Feito','action':{'type':'complete','task_id':TARGET,'draft':None,'patch':None}}
        with patch.object(actions,'cloud',side_effect=[[],HTTPException(409,'changed')]),patch.object(actions,'generate',return_value=json.dumps(plan)):
            with self.assertRaises(HTTPException) as error:actions.direct_conversation('Conclua',[],[],{'tasks':[ROW]},OWNER,'Bearer fiction',REQUEST,'UTC')
            self.assertEqual(409,error.exception.status_code)
    def test_read_only_plan_has_no_mutation(self):
        with patch.object(actions,'cloud',return_value=[]) as cloud,patch.object(actions,'generate',return_value=json.dumps({'reply':'Duas tarefas','action':None})):
            result=actions.direct_conversation('Quais tarefas?',[],[],{'tasks':[]},OWNER,'Bearer fiction',REQUEST,'UTC')
            self.assertIsNone(result['action_receipt']);self.assertEqual(1,cloud.call_count)

    def test_historical_replies_use_the_current_protocol_without_executing_actions(self):
        history=[{'role':'user','content':'Já terminei estudar'}, {'role':'assistant','content':'Missão concluída 💜'}]
        with patch.object(actions,'cloud',return_value=[]),patch.object(actions,'generate',return_value=json.dumps({'reply':'Oi 💜','action':None})) as model:
            actions.direct_conversation('oi',history,[],{'tasks':[]},OWNER,'Bearer fiction',REQUEST,'UTC')
        sent=model.call_args.args[0]
        self.assertIn('objeto JSON',sent[0]['content'])
        self.assertEqual(history[0],sent[-3])
        self.assertEqual({'reply':history[1]['content'],'action':None},json.loads(sent[-2]['content']))
        self.assertEqual('oi',sent[-1]['content'])
        self.assertEqual('Missão concluída 💜',history[1]['content'])

    def test_undo_carries_task_identity_for_reminder_and_list_refresh(self):
        with patch.object(actions,'cloud',return_value={'task':ROW}) as cloud:
            value=actions.undo_action(OWNER,'Bearer fiction',REQUEST)
            self.assertEqual(TARGET,value['action_receipt']['task_id'])
            self.assertEqual('undo',value['action_receipt']['type'])
            self.assertEqual('rpc/undo_koi_action',cloud.call_args.args[0])
