import unittest
from datetime import datetime,date,timedelta
from uuid import UUID
from unittest.mock import patch
from fastapi import HTTPException
from backend.app.core import companion
from backend.app.api.routes.assistant import CheckinRequest,TaskActionRequest,UndoRequest
from backend.tests.test_assistant_panel import row
from backend.tests.test_auth import call

OWNER=UUID(int=1);RID=UUID(int=2);TASK=UUID(int=3)
NOW=datetime.fromisoformat('2026-10-09T01:00:00+00:00')
VERSION='2026-10-08T12:00:00+00:00'
def task(**extra):
    return dict(id=str(TASK),title='Teste estudo',due_date='2026-10-08',due_time='19:00:00',timezone='America/Sao_Paulo',recurrence='daily',updated_at=VERSION,completed_at=None,archived_at=None)|extra

class CompanionTests(unittest.TestCase):
    def review(self,tasks=None,records=None,done=None,now=NOW):
        with patch.object(companion,'load_records',return_value=records or []),patch.object(companion,'load_payments',return_value=[]),patch.object(companion,'load_task_rows',return_value=tasks or []),patch.object(companion,'completions',return_value=done or []),patch('backend.app.core.ai.generate') as model:
            result=companion.review(OWNER,'Bearer fixture','America/Sao_Paulo',now)
        model.assert_not_called();return result

    def test_review_counts_local_day_and_unique_habit_days(self):
        done=[dict(task_id=str(TASK),title='Teste',completed_at=stamp) for stamp in ['2026-10-09T00:30:00+00:00','2026-10-08T23:00:00+00:00','2026-10-07T20:00:00+00:00']]
        r=self.review([task()],done=done)
        self.assertEqual('2026-10-08',r['date']);self.assertEqual(2,r['completed_count'])
        self.assertEqual(2,r['habits'][0]['days_done']);self.assertTrue(r['habits'][0]['done_today'])
        self.assertEqual('2026-10-09',r['suggestions'][0]['target_date']);self.assertEqual('19:00',r['suggestions'][0]['time'])

    def test_future_done_archived_never_suggested(self):
        r=self.review([task(due_date='2026-10-10'),task(completed_at='done'),task(archived_at='gone')])
        self.assertEqual([],r['suggestions']);self.assertEqual(0,r['pending_count']);self.assertFalse(r['habits'][0]['can_complete'])

    def test_sleep_counts_end_day_not_start_day_and_no_invented_interval(self):
        a=row('diary','2026-10-07',title='Sono');a['details']={'category':'sleep','started_at':'2026-10-07T23:00:00-03:00','ended_at':'2026-10-08T07:00:00-03:00'}
        b=row('diary','2026-10-08',title='Aberto');b['details']={'category':'sleep','started_at':'2026-10-08T21:00:00-03:00'};b['updated_at']=VERSION
        r=self.review(records=[a,b]);self.assertEqual(480,r['sleep_minutes']);self.assertEqual(1,r['sleep_intervals']);self.assertEqual(1,len(r['active_checkins']))

    def test_review_category_warning_ignores_other_category(self):
        food=row('expense','2026-10-08',100);food['details']={'finance_category':'food'}
        transport=row('expense','2026-10-08',900);transport['details']={'finance_category':'transport'}
        budget=row('budget','2026-10-01',500);budget['details']={'finance_category':'food'}
        r=self.review(records=[food,transport,budget]);self.assertFalse(any(a['key']=='budget' for a in r['alerts']))

    def test_review_reports_caps(self):
        r=self.review([task(id=str(i)) for i in range(15)])
        self.assertTrue(r['partial']);self.assertEqual(12,len(r['habits']));self.assertEqual(3,len(r['suggestions']))

    def test_completions_are_paginated_owner_scoped(self):
        with patch.object(companion,'cloud',side_effect=[[{}]*100,[]]) as cloud:
            r=companion.completions(OWNER,'Bearer fixture',NOW-timedelta(days=7),NOW)
        self.assertEqual(100,len(r));self.assertIn('offset=100',cloud.call_args.args[0]);self.assertIn(str(OWNER),cloud.call_args.args[0]);self.assertEqual('Bearer fixture',cloud.call_args.args[1])

    def test_checkin_start_has_source_receipt_test_marker_and_timezone(self):
        b=CheckinRequest(request_id=RID,category='sleep',phase='start',test_data=True,note='Fictício')
        with patch.object(companion,'cloud',side_effect=[[],{'request_id':str(RID),'record':{'id':'x','updated_at':VERSION}}]) as cloud:
            result=companion.checkin(OWNER,'Bearer fixture',b,NOW)
        payload=cloud.call_args.args[3];self.assertEqual('create',payload['action'])
        self.assertEqual('2026-10-08',payload['fields']['happened_on']);self.assertIn('-03:00',payload['fields']['details']['started_at']);self.assertTrue(payload['fields']['title'].startswith('Teste'))
        self.assertEqual(str(RID),result['receipt']['request_id'])

    def test_retry_uses_receipt_even_next_day_and_does_not_write(self):
        b=CheckinRequest(request_id=RID,category='work')
        cached={'result':{'request_id':str(RID),'record':{'id':'x'}},'source_hash':companion.digest(b.model_dump(mode='json')),'undone':False}
        with patch.object(companion,'cloud',return_value=[cached]) as cloud:
            r=companion.checkin(OWNER,'x',b,NOW+timedelta(days=1))
        self.assertEqual(1,cloud.call_count);self.assertFalse(r['receipt']['undone'])
        cached['source_hash']='changed'
        with patch.object(companion,'cloud',return_value=[cached]):
            with self.assertRaises(HTTPException) as error:companion.checkin(OWNER,'x',b,NOW)
        self.assertEqual(409,error.exception.status_code)

    def test_finish_is_version_guarded_and_preserves_start(self):
        record=row('diary','2026-10-08',title='Teste');record.update(id=str(TASK),updated_at=VERSION,details={'category':'study','started_at':'2026-10-08T18:00:00-03:00'})
        b=CheckinRequest(request_id=RID,category='study',phase='finish',record_id=TASK,expected_updated_at=VERSION)
        with patch.object(companion,'cloud',side_effect=[[],{'request_id':str(RID),'record':record}]) as cloud,patch.object(companion,'load_records',return_value=[record]):
            companion.checkin(OWNER,'x',b,NOW)
        data=cloud.call_args.args[3];self.assertEqual('update',data['action']);self.assertEqual(VERSION,data['expected_updated_at']);self.assertEqual(record['details']['started_at'],data['fields']['details']['started_at'])
        record['updated_at']='new version'
        with patch.object(companion,'cloud',return_value=[]) as cloud,patch.object(companion,'load_records',return_value=[record]):
            with self.assertRaises(HTTPException):companion.checkin(OWNER,'x',b,NOW)
        self.assertEqual(1,cloud.call_count)

    def test_finish_rejects_missing_or_older_than_48h(self):
        with self.assertRaises(ValueError):CheckinRequest(request_id=RID,category='sleep',phase='finish')
        record=row('diary','2026-10-01',title='Teste');record.update(id=str(TASK),updated_at=VERSION,details={'category':'sleep','started_at':'2026-10-01T18:00:00-03:00'})
        b=CheckinRequest(request_id=RID,category='sleep',phase='finish',record_id=TASK,expected_updated_at=VERSION)
        with patch.object(companion,'cloud',return_value=[]),patch.object(companion,'load_records',return_value=[record]):
            with self.assertRaises(HTTPException) as error:companion.checkin(OWNER,'x',b,NOW)
        self.assertEqual(422,error.exception.status_code)

    def test_reschedule_changes_only_date_and_rejects_foreign_or_stale(self):
        b=TaskActionRequest(request_id=RID,task_id=TASK,expected_updated_at=VERSION,action='reschedule',target_date=date(2026,10,9))
        with patch.object(companion,'cloud',side_effect=[[],{'request_id':str(RID),'task':task()}]) as cloud,patch.object(companion,'load_task_rows',return_value=[task()]):
            companion.task_action(OWNER,'x',b,NOW)
        self.assertEqual({'due_date':'2026-10-09'},cloud.call_args.args[3]['fields'])
        with patch.object(companion,'cloud',return_value=[]) as cloud,patch.object(companion,'load_task_rows',return_value=[]):
            with self.assertRaises(HTTPException):companion.task_action(OWNER,'x',b,NOW)
        self.assertEqual(1,cloud.call_count)

    def test_future_habit_cannot_be_completed_and_date_window_enforced(self):
        for action,target in [('complete',None),('reschedule',date(2026,11,1))]:
            b=TaskActionRequest(request_id=RID,task_id=TASK,expected_updated_at=VERSION,action=action,target_date=target)
            with patch.object(companion,'cloud',return_value=[]),patch.object(companion,'load_task_rows',return_value=[task(due_date='2026-10-10')]):
                with self.assertRaises(HTTPException):companion.task_action(OWNER,'x',b,NOW)

    def test_undo_uses_owner_token_existing_atomic_rpc(self):
        for kind in ('task','record'):
            with patch.object(companion,'cloud',return_value={'request_id':str(RID),'undone':True}) as cloud:
                r=companion.undo('Bearer owner',UndoRequest(request_id=RID,target_kind=kind))
            self.assertEqual('Bearer owner',cloud.call_args.args[1]);self.assertTrue(r['receipt']['undone'])

class CompanionAuthTests(unittest.IsolatedAsyncioTestCase):
    async def test_new_routes_do_not_accept_anonymous(self):
        for route in ('review','checkin','task-action','undo'):
            status,_=await call(path='/api/v1/assistant/'+route,body={})
            self.assertEqual(401,status)
