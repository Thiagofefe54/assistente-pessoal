import unittest
from datetime import date
from unittest.mock import patch
from backend.app.core.google_day import combine_day


class GoogleDayTests(unittest.TestCase):
    def local(self):return {'scheduled':[{'time':'09:30','title':'Study'},{'time':'11:00','title':'Break'}],
                            'priorities':[{'title':'Read'}],'partial':False}

    def test_merges_overlapping_events_and_flags_only_real_overlap(self):
        events=[{'start':{'dateTime':'2026-10-10T09:00:00-03:00'},'end':{'dateTime':'2026-10-10T10:00:00-03:00'}},
                {'start':{'dateTime':'2026-10-10T09:45:00-03:00'},'end':{'dateTime':'2026-10-10T11:00:00-03:00'}}]
        with patch('backend.app.core.google_day.day_plan',return_value=self.local()):
            result=combine_day('owner','Bearer fixture',date(2026,10,10),'America/Sao_Paulo',events,False)
        self.assertEqual([{'title':'Study','time':'09:30'}],result['conflicts'])
        self.assertEqual([{'start':'08:00','end':'09:00'},{'start':'11:00','end':'22:00'}],result['windows'])
        self.assertEqual(self.local()['priorities'],result['priorities'])

    def test_all_day_blocks_frame_and_other_days_do_not(self):
        event={'start':{'date':'2026-10-10'},'end':{'date':'2026-10-11'}}
        with patch('backend.app.core.google_day.day_plan',return_value=self.local()):
            result=combine_day('owner','Bearer fixture',date(2026,10,10),'America/Sao_Paulo',[event],False)
            next_day=combine_day('owner','Bearer fixture',date(2026,10,11),'America/Sao_Paulo',[event],False)
        self.assertEqual([],result['windows']);self.assertEqual(2,len(result['conflicts']))
        self.assertEqual([{'start':'08:00','end':'22:00'}],next_day['windows'])

    def test_provider_and_local_partial_flags_are_preserved(self):
        with patch('backend.app.core.google_day.day_plan',return_value={**self.local(),'partial':True}):
            result=combine_day('owner','Bearer fixture',date(2026,10,10),'America/Sao_Paulo',[],False)
        self.assertTrue(result['partial']);self.assertIn('não garantem',result['note'])
