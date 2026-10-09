import unittest
from unittest.mock import patch
from uuid import UUID
from starlette.requests import Request
from backend.app.core.schedule import explicit_reminder
from backend.app.api.routes.chat import chat,ChatRequest


class ReminderCostTests(unittest.TestCase):
    def test_exact_failed_message_creates_schedule_without_any_generation(self):
        message='Me lembra de estudar amanhã às 19h.'
        payload=ChatRequest(message=message,task_mode='direct',contextual_mode=True,
            request_id=UUID(int=2),requested_at='2026-10-08T21:34:00-03:00')
        request=Request({'type':'http','headers':[(b'authorization',b'Bearer fixture')]})
        task={'id':str(UUID(int=3)),'title':'estudar','due_date':'2026-10-09',
              'due_time':'19:00','recurrence':'none'}
        receipt={'action':'create','request_id':str(payload.request_id),'task':task}
        def cloud(path,authorization,*args):
            self.assertEqual('Bearer fixture',authorization)
            return receipt if path=='rpc/apply_koi_action' else []
        with patch('backend.app.api.routes.chat.existing_action',return_value=None), \
             patch('backend.app.core.personal.existing',return_value=None), \
             patch('backend.app.api.routes.chat.interpret') as classifier, \
             patch('backend.app.core.actions.generate') as generator, \
             patch('backend.app.api.routes.chat.confirmed_facts',return_value=[]), \
             patch('backend.app.api.routes.chat.task_context',return_value={
                 'now':'2026-10-08T21:34:00-03:00','tasks':[]}), \
             patch('backend.app.core.actions.cloud',side_effect=cloud) as api:
            response=chat(payload,request,UUID(int=1))
        classifier.assert_not_called();generator.assert_not_called()
        self.assertEqual('create',response.action_receipt['type'])
        write=[c for c in api.call_args_list if c.args[0]=='rpc/apply_koi_action']
        self.assertEqual(1,len(write))
        self.assertEqual('2026-10-09',write[0].args[3]['fields']['due_date'])
        self.assertEqual('19:00',write[0].args[3]['fields']['due_time'])
        self.assertEqual('estudar',write[0].args[3]['fields']['title'])

    def test_conditional_quoted_negated_and_compound_requests_stay_semantic(self):
        for message in ['Não me lembra de estudar amanhã às 19h.',
                        'Como me lembra de estudar amanhã às 19h?',
                        'Ele disse "me lembra de estudar amanhã às 19h".',
                        'Me lembra de estudar e apague uma tarefa amanhã às 19h.',
                        'Me lembra de não estudar amanhã às 19h.',
                        'Me lembra de estudar amanhã às 29h.']:
            self.assertIsNone(explicit_reminder(message,'2026-10-08T21:00:00-03:00','America/Sao_Paulo'),message)

    def test_timezone_and_year_boundary(self):
        self.assertEqual(('estudar','2027-01-01','09:15'),explicit_reminder(
            'Koi, me lembre de estudar amanhã às 9h15','2027-01-01T01:30:00+00:00','America/Sao_Paulo'))
