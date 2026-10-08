import unittest
from datetime import date
from uuid import UUID
from unittest.mock import patch
from backend.app.core import life,personal,intent
import json

def row(kind='bill',day='2026-10-10',amount=5000,details=None):
    return dict(id=str(UUID(int=3)),kind=kind,title='Internet',content='',amount_cents=amount,progress=0,happened_on=day,archived_at=None,updated_at='2026-10-08T12:00:00Z',details=details or {})

class LifeTests(unittest.TestCase):
    def test_month_end_keeps_original_day(self):
        anchor=date(2027,1,31)
        self.assertEqual(date(2027,2,28),life.occurrence(anchor,'monthly',1))
        self.assertEqual(date(2027,3,31),life.occurrence(anchor,'monthly',2))
        self.assertEqual(date(2028,2,29),life.occurrence(date(2028,1,31),'monthly',1))

    def test_weekly_and_once_have_exact_occurrences(self):
        r=row(day='2026-10-02',details={'repeat':'weekly'})
        self.assertEqual(['2026-10-02','2026-10-09','2026-10-16','2026-10-23','2026-10-30'],life.bill_dates(r,date(2026,10,1),date(2026,11,1)))
        r['details']={};self.assertEqual(['2026-10-02'],life.bill_dates(r,date(2026,10,1),date(2026,11,1)))
        r['archived_at']='now';self.assertEqual([],life.bill_dates(r,date(2026,10,1),date(2026,11,1)))

    def test_long_lived_monthly_bills_jump_to_window(self):
        r=row(day='2000-01-31',details={'repeat':'monthly'})
        self.assertEqual(['2026-10-31'],life.bill_dates(r,date(2026,10,1),date(2026,11,1)))

    def test_overview_does_not_count_forecast_or_future_expense(self):
        bill=row();expense=row('expense','2026-10-02',1200);future=row('expense','2026-10-20',9000)
        budget=row('budget','2026-10-01',1000)
        result=life.overview([bill,expense,future,budget],[],date(2026,10,8))
        self.assertEqual(1200,result['recorded_cents']['expense']);self.assertEqual(5000,result['unpaid_cents'])
        self.assertTrue(result['budgets'][0]['exceeded'])
        self.assertEqual(0,life.overview([bill],[{'bill_id':bill['id'],'occurrence_on':'2026-10-10'}],date(2026,10,8))['unpaid_cents'])

    def test_sleep_needs_two_aware_times(self):
        self.assertIsNone(life.sleep_minutes({'category':'sleep'}))
        details={'category':'sleep','started_at':'2026-10-07T23:00:00-03:00','ended_at':'2026-10-08T07:30:00-03:00'}
        self.assertEqual(510,life.sleep_minutes(details))
        for d in ({'started_at':'2026-10-08T23:00:00'},dict(details,ended_at='2026-10-07T22:00:00-03:00'),dict(details,ended_at='2026-10-11T07:00:00-03:00')):
            with self.assertRaises(ValueError):life.validate_details('diary',d)

    def test_details_reject_cross_tool_fields(self):
        for kind,details in [('bill',{'category':'work'}),('expense',{'repeat':'monthly'}),('diary',{'category':'evil'}),('bill',{'repeat':'daily'})]:
            with self.assertRaises(ValueError):life.validate_details(kind,details)

    def test_current_capture_can_authorize_bill_payment_but_future_cannot(self):
        text='Paguei a conta de internet'
        meaning=intent.Interpretation(domain='record',operation='complete',speech_act='report',evidence=text,device=None)
        self.assertFalse(meaning.permits('record','update',text))
        self.assertTrue(meaning.permits('record','update',text,capture=True))
        meaning=meaning.model_copy(update={'evidence':'Vou pagar a conta'})
        self.assertFalse(meaning.permits('record','update','Vou pagar a conta',capture=True))

    def test_payment_uses_atomic_rpc_not_generic_update(self):
        text='Marque minha conta de internet como paga'
        meaning=intent.Interpretation(domain='record',operation='complete',speech_act='request',evidence=text,device=None)
        bill=row();plan=dict(reply='Certo',action='update',target_kind='record',record_id=bill['id'],fields={})
        result=dict(record=row('expense'),action='create',target_kind='record',request_id=str(UUID(int=2)))
        with patch.object(personal,'cloud',side_effect=[[],[],[bill],[],result]) as cloud,patch.object(personal,'generate',return_value=json.dumps(plan)):
            personal.personal_conversation(text,[],UUID(int=1),'Bearer fixture',UUID(int=2),'UTC','2026-10-08T12:00:00Z',meaning)
        self.assertEqual('rpc/pay_koi_bill',cloud.call_args.args[0]);self.assertEqual('2026-10-10',cloud.call_args.args[3]['occurrence_on'])

    def test_weekly_bill_with_multiple_unpaid_dates_requests_specific_occurrence(self):
        text='Marque internet como paga';meaning=intent.Interpretation(domain='record',operation='complete',speech_act='request',evidence=text,device=None)
        bill=row(day='2026-10-02',details={'repeat':'weekly'});plan=dict(reply='Certo',action='update',target_kind='record',record_id=bill['id'],fields={})
        with patch.object(personal,'cloud',side_effect=[[],[],[bill],[]]) as cloud,patch.object(personal,'generate',return_value=json.dumps(plan)):
            result=personal.personal_conversation(text,[],UUID(int=1),'Bearer fixture',UUID(int=2),'UTC','2026-10-08T12:00:00Z',meaning)
        self.assertIsNone(result['action_receipt']);self.assertEqual(4,cloud.call_count)
