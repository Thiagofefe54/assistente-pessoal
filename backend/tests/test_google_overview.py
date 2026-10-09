import io
import json
import unittest
from datetime import date
from unittest.mock import patch
from uuid import UUID
from fastapi import HTTPException
from backend.app.core import google_overview as o
from backend.app.core import google_connections as g
from backend.tests.test_auth import call,USER

DAY=date(2026,10,10);ZONE='America/Sao_Paulo';OWNER=UUID(int=1)
ROWS=[{'id':str(UUID(int=i+2)),'email':f'fixture{i}@example.com','services':['calendar']} for i in range(3)]
LOCAL={'scheduled':[{'time':'09:30','title':'Study'}],'priorities':[{'title':'Read'}],'partial':False}
def event(title,start,end,**extra):return {'title':title,'start':{'dateTime':f'2026-10-10T{start}:00-03:00'},
    'end':{'dateTime':f'2026-10-10T{end}:00-03:00'},**extra}


class OverviewTests(unittest.TestCase):
    def setUp(self):
        self.p=patch.object(o.google,'accounts',return_value=ROWS);self.p.start();self.addCleanup(self.p.stop)
        p=patch('backend.app.core.google_day.day_plan',return_value=LOCAL);p.start();self.addCleanup(p.stop)
    def test_aggregate_merges_busy_intervals_and_preserves_sources(self):
        data={ROWS[0]['id']:[event('First','09:00','10:00')],ROWS[1]['id']:[event('Second','09:45','11:00')],ROWS[2]['id']:[]}
        with patch.object(o.google,'read',side_effect=lambda owner,c,*args,**kw:{'items':data[c],'partial':False}) as req:
            result=o.overview(OWNER,'Bearer fixture',DAY,ZONE)
        self.assertEqual(3,req.call_count);self.assertEqual(1,result['calendar_conflict_count'])
        self.assertEqual([{'title':'Study','time':'09:30'}],result['plan']['conflicts'])
        self.assertEqual([{'start':'08:00','end':'09:00'},{'start':'11:00','end':'22:00'}],result['plan']['windows'])
        self.assertEqual(ROWS[1]['email'],result['calendar_conflicts'][0]['second']['email'])
        self.assertNotIn('etag',result['items'][0])
    def test_foreign_id_denied_before_any_provider_read(self):
        with patch.object(o.google,'read') as req:
            with self.assertRaises(HTTPException) as error:o.overview(OWNER,'Bearer fixture',DAY,ZONE,[UUID(int=999)])
        self.assertEqual(404,error.exception.status_code);req.assert_not_called()
    def test_selection_reads_only_owner_account(self):
        with patch.object(o.google,'read',return_value={'items':[],'partial':False}) as req:
            result=o.overview(OWNER,'Bearer fixture',DAY,ZONE,[UUID(ROWS[1]['id'])])
        self.assertEqual(1,req.call_count);self.assertEqual([ROWS[1]['email']],[r['email'] for r in result['accounts']])
    def test_failure_is_partial_and_does_not_expose_upstream_detail(self):
        def read(owner,connection,*args,**kwargs):
            if connection==ROWS[0]['id']:raise HTTPException(409,'SECRET upstream token')
            return {'items':[],'partial':False}
        with patch.object(o.google,'read',side_effect=read):result=o.overview(OWNER,'Bearer fixture',DAY,ZONE)
        self.assertTrue(result['partial']);self.assertTrue(result['errors'][0]['reconnect']);self.assertNotIn('SECRET',str(result))
    def test_all_failed_never_claims_free_day(self):
        with patch.object(o.google,'read',side_effect=HTTPException(503,'provider')):result=o.overview(OWNER,'Bearer fixture',DAY,ZONE)
        self.assertEqual([],result['plan']['windows']);self.assertEqual(3,len(result['errors']));self.assertTrue(result['partial'])
    def test_free_declined_and_touching_events_are_not_collisions(self):
        events=[event('A','09:00','10:00'),event('B','10:00','11:00'),event('Free','09:00','11:00',blocks_time=False)]
        with patch.object(o.google,'read',return_value={'items':events,'partial':False}):
            result=o.overview(OWNER,'Bearer fixture',DAY,ZONE,[UUID(ROWS[0]['id'])])
        self.assertEqual(0,result['calendar_conflict_count']);self.assertEqual('11:00',result['plan']['windows'][-1]['start'])
    def test_invalid_event_partial_and_all_day_is_busy(self):
        events=[{'title':'All day','start':{'date':'2026-10-10'},'end':{'date':'2026-10-11'}},
            event('Invalid','12:00','11:00')]
        with patch.object(o.google,'read',return_value={'items':events,'partial':False}):
            result=o.overview(OWNER,'Bearer fixture',DAY,ZONE,[UUID(ROWS[0]['id'])])
        self.assertTrue(result['partial']);self.assertEqual([],result['plan']['windows']);self.assertEqual(1,len(result['items']))
    def test_conflict_response_is_bounded(self):
        with patch.object(o.google,'read',return_value={'items':[event(str(i),'09:00','10:00') for i in range(20)],'partial':False}):
            result=o.overview(OWNER,'Bearer fixture',DAY,ZONE)
        self.assertEqual(20,len(result['calendar_conflicts']));self.assertTrue(result['conflicts_partial']);self.assertEqual(1770,result['calendar_conflict_count'])


class OverviewRouteTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        from backend.app.core.config import settings
        p=patch.multiple(settings,environment='development',supabase_url='https://project.supabase.co',supabase_publishable_key='sb_publishable_test')
        p.start();self.addCleanup(p.stop)
    async def test_auth_validation_and_owner_source(self):
        with patch.object(o,'overview',return_value={'date':DAY.isoformat()}) as read:
            status,_=await call('/api/v1/assistant/google-day',body={})
            self.assertEqual(401,status);read.assert_not_called()
            with patch('backend.app.core.auth.build_opener') as auth:
                auth.return_value.open.side_effect=lambda *args,**kwargs:io.BytesIO(json.dumps({'id':USER,'user_metadata':{'id':'attacker'}}).encode())
                for body in ({'timezone':'invalid-zone'},{'timezone':''},{'user_id':'foreign'},{'connection_ids':[]},
                             {'connection_ids':[str(UUID(int=i)) for i in range(4)]}):
                    status,_=await call('/api/v1/assistant/google-day',token='Bearer fixture',body=body)
                    self.assertEqual(422,status)
                status,_=await call('/api/v1/assistant/google-day',token='Bearer fixture',body={'day':DAY.isoformat(),'timezone':ZONE})
                self.assertEqual(200,status);self.assertEqual(USER,str(read.call_args.args[0]))


class SearchTests(unittest.TestCase):
    def setUp(self):
        p=patch.object(g,'account',return_value={'id':str(UUID(int=2)),'email':'fixture@example.com','updated_at':'today',
            'scopes':[g.SCOPE['mail'],g.SCOPE['drive']]});p.start();self.addCleanup(p.stop)
        p=patch.object(g,'access_token',return_value='fixture');p.start();self.addCleanup(p.stop)
    def test_gmail_search_uses_local_day_seconds_and_metadata_only(self):
        from urllib.parse import urlparse,parse_qs
        with patch.object(g,'request_json',side_effect=[{'messages':[{'id':'abcd'}]},
            {'payload':{'headers':[{'name':'Subject','value':'Study'}]},'snippet':'PRIVATE','body':'PRIVATE'}]) as req:
            result=g.consult(OWNER,UUID(int=2),'mail','Study','2026-10-10',None,ZONE)
        q=parse_qs(urlparse(req.call_args_list[0].args[0]).query)['q'][0]
        self.assertIn('subject:"Study"',q);self.assertIn('after:1791601199',q);self.assertIn('before:1791687600',q)
        self.assertIn('format=metadata',req.call_args_list[1].args[0]);self.assertNotIn('PRIVATE',str(result))
    def test_drive_name_is_escaped_and_partial_search_is_reported(self):
        from urllib.parse import urlparse,parse_qs
        with patch.object(g,'request_json',return_value={'files':[],'incompleteSearch':True}) as req:
            result=g.consult(OWNER,UUID(int=2),'drive',"quinn's \\ paper",'2026-10-10',None,ZONE)
        q=parse_qs(urlparse(req.call_args.args[0]).query)['q'][0]
        self.assertIn("name contains 'quinn\\'s \\\\ paper'",q);self.assertIn('2026-10-10T03:00:00+00:00',q);self.assertTrue(result['partial'])
