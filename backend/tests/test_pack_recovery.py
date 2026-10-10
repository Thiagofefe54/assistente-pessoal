import unittest
from unittest.mock import patch
from urllib.parse import parse_qs,urlsplit
from uuid import UUID
from fastapi import HTTPException
from backend.app.core import google_assistant as google
from backend.app.core import google_connections as connections
from backend.app.core.grounded_context import context_for_reply

OWNER=UUID(int=1)

class PackRecoveryTests(unittest.TestCase):
    def test_conversation_consults_routine_without_treating_plans_as_completed(self):
        tasks=[{'id':'future','title':'Teste treino','due_date':'2099-01-01','due_time':'18:00','timezone':'UTC','completed_at':None},
               {'id':'archived','title':'Teste treino antigo','archived_at':'2026-01-01'}]
        with patch('backend.app.core.grounded_context.cloud',return_value=[]),patch('backend.app.core.grounded_context.load_task_rows',return_value=tasks) as load:
            result=context_for_reply(OWNER,'Bearer fixture','Estou cansado do treino',[],'UTC',include_tasks=True)
        load.assert_called_once_with(OWNER,'Bearer fixture')
        self.assertEqual('pending',result['routine']['tasks'][0]['status'])
        self.assertEqual(['future'],[r['source_id'] for r in result['routine']['tasks']])
        self.assertIn('não provam',result['routine']['scope'])

    def test_older_memory_is_found_without_increasing_model_excerpt_budget(self):
        unrelated=[{'id':str(i),'kind':'note','title':'Teste leitura','content':'Um livro fictício','happened_on':None} for i in range(40)]
        old={'id':'old','kind':'diary','title':'Teste academia','content':'Treino fictício às 18h','happened_on':'2026-01-01'}
        with patch('backend.app.core.grounded_context.cloud',side_effect=[unrelated,[old]]) as cloud:
            result=context_for_reply(OWNER,'Bearer fixture','Como melhorar minha academia?',[],'UTC')
        self.assertEqual(['old'],[i['source_id'] for i in result['sources']])
        self.assertEqual(2,cloud.call_count)
        for call in cloud.call_args_list:
            params=parse_qs(urlsplit(call.args[0]).query)
            self.assertEqual('eq.'+str(OWNER),params['user_id'][0])
        self.assertEqual('40',params['offset'][0])

    def test_memory_scan_has_hard_bound_and_marks_partial(self):
        rows=[{'id':str(i),'kind':'note','title':'Teste treino','content':'Fictício','happened_on':None} for i in range(40)]
        with patch('backend.app.core.grounded_context.cloud',return_value=rows) as cloud:
            result=context_for_reply(OWNER,'Bearer fixture','Treino',[],'UTC')
        self.assertEqual(5,cloud.call_count);self.assertTrue(result['partial'])
        self.assertLessEqual(len(result['sources']),3)

    def test_empty_google_page_still_follows_cursor(self):
        with patch.object(google,'google_call',side_effect=[{'items':[],'nextPageToken':'page2'},{'items':[{'id':'found'}]}]) as call:
            result,partial=google.paged_items(OWNER,'fixture','calendar','calendars/primary/events',{'maxResults':20})
        self.assertEqual([{'id':'found'}],result);self.assertFalse(partial)
        self.assertIn('pageToken=page2',call.call_args.args[3])

    def test_repeated_google_cursor_stops_and_marks_partial(self):
        with patch.object(google,'google_call',return_value={'items':[],'nextPageToken':'same'}) as call:
            result,partial=google.paged_items(OWNER,'fixture','tasks','lists/example/tasks',{'maxResults':20})
        self.assertTrue(partial);self.assertEqual(2,call.call_count)

    def test_selected_calendar_is_encoded_and_stays_on_owner_connection(self):
        with patch.object(connections,'account',return_value={'id':str(OWNER),'email':'fixture@example.com','scopes':[],'updated_at':'2026-10-10'}),patch.object(google,'google_call',return_value={'items':[]}) as call:
            google.read(OWNER,'fixture','calendar','2026-10-10','2026-10-10',calendar_id='secondary@example.com/agenda')
        self.assertEqual(OWNER,call.call_args.args[0])
        self.assertIn('calendars/secondary%40example.com%2Fagenda/events?',call.call_args.args[3])

    def test_bad_selector_never_contacts_google(self):
        with patch.object(google,'google_call') as call:
            with self.assertRaises(HTTPException):google.read(OWNER,'fixture','mail',calendar_id='private')
        call.assert_not_called()

    def test_selected_task_list_reads_completed_tasks_without_crossing_lists(self):
        with patch.object(connections,'account',return_value={'id':str(OWNER),'email':'fixture@example.com','scopes':[],'updated_at':'2026-10-10'}),patch.object(google,'google_call',side_effect=[
            {'id':'chosen','title':'Teste'}, {'items':[{'id':'done','title':'Teste estudar','status':'completed'}]}]) as call:
            result=google.read(OWNER,'fixture','task_items',list_id='chosen')
        self.assertEqual('completed',result['items'][0]['status'])
        self.assertIn('users/@me/lists/chosen',call.call_args_list[0].args[3])
        self.assertIn('showCompleted=true',call.call_args.args[3])
