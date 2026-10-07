import json
import unittest
from datetime import date,datetime,timezone
from unittest.mock import patch
from uuid import UUID
from fastapi import HTTPException
from backend.app.core import reports
from backend.app.core.journal import fingerprint

OWNER=UUID(int=1);DAY=date(2026,9,15)
ROWS=[{'local_date':'2026-09-15','message_count':1,'last_sequence':42}]
class ReportTests(unittest.TestCase):
    def test_first_partial_year_avoids_equivalent_automatic_semester(self):
        with (patch.object(reports,'datetime') as clock,patch.object(reports,'inventory',side_effect=[ROWS,[]]),patch.object(reports,'cloud',return_value=[])):
            clock.now.return_value=datetime(2027,1,2,tzinfo=timezone.utc)
            values=reports.due_reports(OWNER,'Bearer fiction','UTC')['periods']
            self.assertIn({'kind':'year','anchor':'2026-01-01'},values)
            self.assertNotIn({'kind':'halfyear','anchor':'2026-07-01'},values)

    def test_closed_period_regenerates_formerly_open_cache(self):
        old={'source_hash':fingerprint(ROWS),'coverage':{'closed':False},'items':[]}
        with (patch.object(reports,'datetime') as clock,patch.object(reports,'inventory',return_value=ROWS),
            patch.object(reports,'cloud',return_value=[old]),patch.object(reports,'source_units',return_value=([],[]))):
            clock.now.return_value=datetime(2026,10,2,tzinfo=timezone.utc)
            value=reports.period_report(OWNER,'Bearer fiction','month',DAY,'UTC')
            self.assertTrue(value['stale']);self.assertFalse(value['ready'])

    def test_calendar_boundaries_leap_month_and_semester(self):
        self.assertEqual((date(2026,9,28),date(2026,10,5)),reports.period('week',date(2026,10,1)))
        self.assertEqual((date(2028,2,1),date(2028,3,1)),reports.period('month',date(2028,2,29)))
        self.assertEqual((date(2026,7,1),date(2027,1,1)),reports.period('halfyear',date(2026,10,7)))
        self.assertEqual((date(2026,1,1),date(2027,1,1)),reports.period('year',DAY))
    def test_empty_period_has_no_inference(self):
        with patch.object(reports,'inventory',return_value=[]),patch.object(reports,'cloud',return_value=[]),patch.object(reports,'synthesis') as model:
            value=reports.period_report(OWNER,'Bearer fiction','month',DAY,'UTC',True)
            self.assertEqual(0,value['available_count']);model.assert_not_called()
    def test_missing_chapter_prepares_only_one_source(self):
        with patch.object(reports,'inventory',return_value=ROWS),patch.object(reports,'cloud',return_value=[]),patch.object(reports,'daily_report') as day,patch.object(reports,'synthesis') as model:
            value=reports.period_report(OWNER,'Bearer fiction','month',DAY,'UTC',True)
            self.assertEqual('day:2026-09-15',value['next_source']);self.assertEqual(1,day.call_count);model.assert_not_called()
    def test_current_report_is_cached_without_inference(self):
        old={'source_hash':fingerprint(ROWS),'items':[]}
        with patch.object(reports,'inventory',return_value=ROWS),patch.object(reports,'cloud',return_value=[old]),patch.object(reports,'synthesis') as model:
            self.assertTrue(reports.period_report(OWNER,'Bearer fiction','month',DAY,'UTC',True)['ready']);model.assert_not_called()
    def test_late_messages_prevent_saving_old_snapshot(self):
        with (patch.object(reports,'inventory',side_effect=[ROWS,ROWS+[dict(ROWS[0],local_date='2026-09-16')]]),patch.object(reports,'cloud',return_value=[]) as cloud,
             patch.object(reports,'source_units',return_value=([{'key':'day:2026-09-15','items':[]}],[])),patch.object(reports,'synthesis',return_value=[])):
            with self.assertRaises(HTTPException) as error:reports.period_report(OWNER,'Bearer fiction','month',DAY,'UTC',True)
            self.assertEqual(409,error.exception.status_code);self.assertEqual(1,cloud.call_count)
    def test_synthesis_rejects_foreign_or_missing_sources(self):
        for keys in ([],['day:2000-01-01']):
            with patch.object(reports,'generate',return_value=json.dumps({'items':[{'text':'Fixture','source_keys':keys}]})):
                with self.assertRaises(HTTPException):reports.synthesis([{'key':'day:2026-09-15','items':[{'text':'Fixture'}]}],'month')
    def test_sources_require_latest_sequence_not_only_count(self):
        old={'local_date':'2026-09-15','source_count':1,'source_sequence':41,'items':[]}
        with patch.object(reports,'cloud',return_value=[old]):
            units,missing=reports.source_units(OWNER,'Bearer fiction','week',ROWS)
            self.assertEqual([],units);self.assertEqual([('day',DAY)],missing)

    def test_saved_report_has_real_coverage_and_single_period_identity(self):
        items=[{'text':'Fixture','source_keys':['day:2026-09-15']}]
        with (patch.object(reports,'inventory',return_value=ROWS),patch.object(reports,'cloud',side_effect=[[],[{'items':items}]]) as cloud,
            patch.object(reports,'source_units',return_value=([],[])),patch.object(reports,'synthesis',return_value=items)):
            value=reports.period_report(OWNER,'Bearer fiction','month',date(2026,9,28),'UTC',True)
            payload=cloud.call_args.args[3]
            self.assertTrue(value['ready']);self.assertEqual('2026-09-01',payload['start_date'])
            self.assertTrue(payload['coverage']['partial_start']);self.assertEqual(1,payload['source_count'])

    def test_changed_report_version_cannot_be_overwritten(self):
        old={'source_hash':'old','updated_at':'2026-09-16T00:00:00Z'}
        with (patch.object(reports,'inventory',return_value=ROWS),patch.object(reports,'cloud',side_effect=[[old],[]]) as cloud,
             patch.object(reports,'source_units',return_value=([],[])),patch.object(reports,'synthesis',return_value=[])):
            with self.assertRaises(HTTPException) as error: reports.period_report(OWNER,'Bearer fiction','month',DAY,'UTC',True)
            self.assertEqual(409,error.exception.status_code);self.assertIn('updated_at=eq.',cloud.call_args.args[0])
