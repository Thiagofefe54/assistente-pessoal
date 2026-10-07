import json
import unittest
from datetime import date
from unittest.mock import patch
from uuid import UUID

from fastapi import HTTPException
from backend.app.core import journal

OWNER=UUID('12345678-1234-4234-9234-123456789012')
SOURCE='12345678-1234-4234-9234-123456789013'
DAY=date(2026,10,7)
ROWS=[{'id':SOURCE,'content':'Meu projeto fictício se chama Farol de Jade.',
       'occurred_at':'2026-10-07T09:00:00Z','timezone':'UTC'}]


class JournalTests(unittest.TestCase):
    def test_sources_must_belong_to_actual_user_messages(self):
        for item in [{'text':'Inventado','source_ids':['foreign']},
                     {'text':'Sem fonte','source_ids':[]},
                     {'text':'x'*501,'source_ids':[SOURCE]},
                     {'text':'x','source_ids':[SOURCE],'category':'admin'}]:
            with patch.object(journal,'generate',return_value=json.dumps({'items':[item]})):
                with self.assertRaises(HTTPException) as error: journal.drafts(ROWS,'suggestions')
                self.assertEqual(502,error.exception.status_code)
        with patch.object(journal,'generate',return_value=json.dumps({'items':[
            {'text':'Meu projeto se chama Farol de Jade.','source_ids':[SOURCE],'category':'goal'}]})):
            self.assertEqual([SOURCE],journal.drafts(ROWS,'suggestions')[0]['source_ids'])

    def test_empty_diary_never_triggers_model_or_saved_report(self):
        with patch.object(journal,'cloud',return_value=[]),patch.object(journal,'generate') as generate:
            self.assertEqual([],journal.drafts([],'suggestions'))
            with self.assertRaises(HTTPException):journal.daily_report(OWNER,'Bearer token',DAY,True)
            generate.assert_not_called()

    def test_pages_owner_date_and_user_role_and_refuses_oversized_day(self):
        with patch.object(journal,'cloud',side_effect=[ROWS*100,[]]) as cloud:
            journal.day_messages(OWNER,'Bearer token',DAY)
            for args in cloud.call_args_list:
                path=args.args[0]
                self.assertIn('user_id=eq.'+str(OWNER),path)
                self.assertIn('local_date=eq.2026-10-07',path)
                self.assertIn('role=eq.user',path)
            self.assertIn('offset=100',cloud.call_args_list[1].args[0])
        with patch.object(journal,'cloud',return_value=[dict(ROWS[0],content='x'*1000)]*100):
            with self.assertRaises(HTTPException) as error:journal.day_messages(OWNER,'Bearer token',DAY)
            self.assertEqual(422,error.exception.status_code)

    def test_unchanged_report_is_cached_without_model_call(self):
        old={'source_hash':journal.fingerprint(ROWS),'items':[],'updated_at':'now'}
        with patch.object(journal,'cloud',return_value=[old]),patch.object(journal,'day_messages',return_value=ROWS),patch.object(journal,'generate') as model:
            self.assertEqual(old,journal.daily_report(OWNER,'Bearer token',DAY,True)['report'])
            model.assert_not_called()

    def test_late_sync_during_generation_never_overwrites_report(self):
        with patch.object(journal,'cloud',return_value=[] ) as cloud, \
             patch.object(journal,'day_messages',side_effect=[ROWS,ROWS+[dict(ROWS[0],id=str(UUID(int=9)))]]), \
             patch.object(journal,'drafts',return_value=[{'text':'Resumo','source_ids':[SOURCE]}]):
            with self.assertRaises(HTTPException) as error:journal.daily_report(OWNER,'Bearer token',DAY,True)
            self.assertEqual(409,error.exception.status_code)
            self.assertEqual(1,cloud.call_count)

    def test_stale_report_update_refused_without_retry(self):
        old={'source_hash':'a'*64,'updated_at':'2026-10-07T09:00:00+00:00'}
        with patch.object(journal,'cloud',side_effect=[[old],[]]) as cloud, \
             patch.object(journal,'day_messages',return_value=ROWS), \
             patch.object(journal,'drafts',return_value=[{'text':'Resumo','source_ids':[SOURCE]}]):
            with self.assertRaises(HTTPException) as error:journal.daily_report(OWNER,'Bearer token',DAY,True)
            self.assertEqual(409,error.exception.status_code)
            self.assertIn('updated_at=eq.',cloud.call_args.args[0])
            self.assertEqual('PATCH',cloud.call_args.args[2])
