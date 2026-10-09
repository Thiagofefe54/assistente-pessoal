import asyncio
import unittest
from decimal import Decimal
from uuid import UUID
from unittest.mock import patch
from pydantic import SecretStr
from fastapi import HTTPException
from backend.app.core import banking
from backend.app.core.config import settings
from backend.tests.test_auth import call

OWNER=UUID(int=1)
ITEM=str(UUID(int=2))

class BankingTests(unittest.TestCase):
    def setUp(self):
        banking._cache=None;banking._cache_key=None;banking._deadline=0
        for name,value in dict(pluggy_enabled=True,pluggy_owner_id=str(OWNER),
            pluggy_client_id=SecretStr('fixture-client'),pluggy_client_secret=SecretStr('fixture-secret'),
            pluggy_item_ids=SecretStr(ITEM)).items():
            p=patch.object(settings,name,value);p.start();self.addCleanup(p.stop)

    def row(self,**extra):
        return dict(id=str(UUID(int=3)),itemId=ITEM,type='BANK',currencyCode='BRL',
            balance=Decimal('10.25'),updatedAt='2026-10-09T12:00:00Z',number='PRIVATE',**extra)

    def test_disabled_or_foreign_owner_never_contacts_provider(self):
        with patch.object(settings,'pluggy_enabled',False),patch.object(banking,'_request') as request:
            self.assertFalse(banking.status(OWNER)['configured'])
            with self.assertRaises(HTTPException) as e:banking.summary(OWNER)
            self.assertEqual(503,e.exception.status_code);request.assert_not_called()
        with patch.object(banking,'_request') as request:
            with self.assertRaises(HTTPException) as e:banking.summary(UUID(int=999))
            self.assertEqual(403,e.exception.status_code);request.assert_not_called()

    def test_summary_is_read_only_minimized_and_cached_after_owner_check(self):
        with patch.object(banking,'_request',side_effect=[{'apiKey':'fixture'}, {'results':[self.row()], 'totalPages':1}]) as request:
            data=banking.summary(OWNER);self.assertEqual(1025,data['total_cents'])
            self.assertFalse(data['partial']);self.assertNotIn('PRIVATE',str(data));self.assertNotIn(ITEM,str(data))
            self.assertEqual('/auth',request.call_args_list[0].args[0])
            self.assertEqual('/accounts?itemId='+ITEM+'&type=BANK',request.call_args_list[1].args[0])
            data['accounts'].clear();self.assertEqual(1,len(banking.summary(OWNER)['accounts']))
            with self.assertRaises(HTTPException):banking.summary(UUID(int=999))
            self.assertEqual(2,request.call_count)

    def test_partial_currency_and_multiple_pages_never_look_complete(self):
        foreign=self.row();foreign['currencyCode']='USD'
        with patch.object(banking,'_request',side_effect=[{'apiKey':'fixture'}, {'results':[foreign], 'totalPages':2}]):
            data=banking.summary(OWNER);self.assertTrue(data['partial']);self.assertEqual([],data['accounts'])

    def test_foreign_item_and_malformed_money_are_rejected_without_leaking(self):
        for change in ({'itemId':str(UUID(int=99))},{'id':123},{'itemId':{}},{'balance':True},{'balance':Decimal('1.001')},{'updatedAt':'bad'}):
            banking._deadline=0;row=self.row();row.update(change)
            with patch.object(banking,'_request',side_effect=[{'apiKey':'fixture'}, {'results':[row]}]):
                with self.assertRaises(HTTPException) as e:banking.summary(OWNER)
                self.assertEqual(503,e.exception.status_code);self.assertNotIn('fixture',str(e.exception.detail))

    def test_money_is_exact_including_negative_balance_and_zero(self):
        self.assertEqual(-123,banking._cents(Decimal('-1.23')));self.assertEqual(0,banking._cents(0))
        for value in (True,'1.00',Decimal('NaN'),Decimal('Infinity'),Decimal('.001')):
            with self.assertRaises((ValueError,ArithmeticError)):banking._cents(value)

    def test_failed_provider_is_throttled_and_configuration_changes_invalidate_cache(self):
        with patch.object(banking,'_request',side_effect=OSError('private fixture')) as request:
            for _ in range(2):
                with self.assertRaises(HTTPException):banking.summary(OWNER)
            self.assertEqual(1,request.call_count)
            with patch.object(settings,'pluggy_client_secret',SecretStr('changed')):
                with self.assertRaises(HTTPException):banking.summary(OWNER)
                self.assertEqual(2,request.call_count)

    def test_endpoints_require_verified_login(self):
        for path in ('bank-status','bank-summary'):
            with patch.object(banking,'_request') as request:
                code,_=asyncio.run(call('/api/v1/assistant/'+path,body={}))
                self.assertEqual(401,code);request.assert_not_called()

    def test_incomplete_configuration_never_accepts_client_selected_items(self):
        with patch.object(settings,'pluggy_item_ids',SecretStr('bad')):
            self.assertFalse(banking.status(OWNER)['configured'])
            with patch.object(banking,'_request') as request:
                with self.assertRaises(HTTPException):banking.summary(OWNER)
                request.assert_not_called()
