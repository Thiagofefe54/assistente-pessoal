import json
import unittest
from datetime import datetime, timezone
from unittest.mock import patch
from uuid import UUID

from fastapi import HTTPException
from pydantic import ValidationError
from backend.app.core.tasks import task_context, TaskProposal
from backend.app.core.conversation import converse


class TaskChatTests(unittest.TestCase):
    def test_named_old_task_is_visible_and_archives_are_not_pending(self):
        rows=[dict(title=f'Tarefa recente {n}',due_date='2026-10-07',due_time=None,recurrence='none',completed_at=None) for n in range(60)]
        rows += [dict(title='Missão antiga única',due_date='2025-01-01',due_time=None,recurrence='none',completed_at=None,archived_at='2026-10-01')]
        with patch('backend.app.core.tasks.cloud',return_value=rows):
            value=task_context(UUID(int=1),'Bearer fake','UTC','Reabra Missão antiga única de 01/01/2025')
            self.assertEqual('Missão antiga única',value['tasks'][0]['title'])
            self.assertEqual('2025-01-01',value['focus_date']);self.assertEqual(60,value['pending'])

    def test_owner_token_pagination_and_focus_are_current_and_bounded(self):
        rows=[dict(title=f'Tarefa {n}',due_date='2026-10-07',due_time=None,
                   timezone='UTC',recurrence='none',completed_at=None) for n in range(105)]
        with patch('backend.app.core.tasks.cloud',side_effect=[rows[:100],rows[100:]]) as cloud, \
             patch('backend.app.core.tasks.datetime') as clock:
            clock.now.return_value=datetime(2026,10,7,1,tzinfo=timezone.utc)
            result=task_context(UUID(int=1),'Bearer fake','UTC','tarefas hoje')
        self.assertEqual(105,result['total']);self.assertEqual(105,result['focus_pending'])
        self.assertEqual(60,result['shown']);self.assertFalse(result['complete_list'])
        self.assertIn('user_id=eq.00000000-0000-0000-0000-000000000001',cloud.call_args.args[0])
        self.assertIn('offset=100',cloud.call_args.args[0])
        self.assertEqual('Bearer fake',cloud.call_args.args[1])

    def test_empty_tasks_are_authoritative_and_malformed_data_fails_closed(self):
        with patch('backend.app.core.tasks.cloud',return_value=[]):
            self.assertEqual([],task_context(UUID(int=1),'Bearer fake','UTC','oi')['tasks'])
        for value in ({},[None],[{'title':None}]):
            with patch('backend.app.core.tasks.cloud',return_value=value):
                with self.assertRaises(HTTPException):task_context(UUID(int=1),'Bearer fake','UTC','oi')

    def test_draft_validation_rejects_ambiguous_or_invalid_schedule_and_extra_actions(self):
        base=dict(title='Estudar',notes='',due_date=None,due_time=None,recurrence='none')
        self.assertEqual('Estudar',TaskProposal(**base).title)
        self.assertEqual('09:00',TaskProposal(**(base|{'due_date':'2026-10-07','due_time':'09:00:00'})).due_time)
        for changes in ({'due_date':'2026-02-30'},{'due_time':'09:00'},{'recurrence':'daily'},
                        {'due_date':'2026-10-07','due_time':'24:00'}, {'due_date':'2026-10-07','due_time':'09:00:30'}, {'title':' '}, {'action':'delete'}):
            with self.assertRaises(ValidationError):TaskProposal(**(base|changes))

    def test_conversation_is_read_only_and_draft_never_claims_saved(self):
        draft=dict(title='Estudar',notes='',due_date='2026-10-07',due_time=None,recurrence='none')
        with patch('backend.app.core.conversation.generate',return_value=json.dumps({'reply':'Criei!','task_draft':draft})) as model:
            result=converse('Adicione estudar hoje',[],[],{'today':'2026-10-07','tasks':[]})
        self.assertIn('ainda não foi adicionada',result['reply'])
        self.assertEqual(draft,result['task_draft'])
        self.assertEqual('system',model.call_args.args[0][0]['role'])
        self.assertIn('task_snapshot',model.call_args.args[0][1]['content'])
        self.assertTrue(model.call_args.kwargs['response_format']['json_schema']['strict'])

    def test_invalid_model_output_is_never_a_success(self):
        for raw in ('texto','{}',json.dumps({'reply':'oi','task_draft':{'title':'x'}}),
                    json.dumps({'reply':' ','task_draft':None})):
            with patch('backend.app.core.conversation.generate',return_value=raw):
                with self.assertRaises(HTTPException) as caught:converse('oi',[],[],{})
                self.assertEqual(502,caught.exception.status_code)
