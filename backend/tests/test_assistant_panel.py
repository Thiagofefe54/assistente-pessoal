import io
import json
import unittest
from datetime import date, datetime
from uuid import UUID
from unittest.mock import patch, MagicMock
from fastapi import HTTPException
from pydantic import SecretStr
from backend.app.core.assistant_panel import comparison, day_panel
from backend.app.core.direct_reads import direct_read, money
from backend.app.core.intent import Interpretation
from backend.app.core import poe_balance
from backend.app.core.config import settings
from backend.tests.test_auth import call, USER


def row(kind, day, amount=0, archived=None, title='Fictício', **extra):
    return dict(id=title, kind=kind, happened_on=day, amount_cents=amount,
                archived_at=archived, title=title, content='', details={}, **extra)


class PanelTests(unittest.TestCase):
    def test_month_compares_matching_elapsed_days_and_ignores_future_archived(self):
        rows = [row('expense', '2026-10-08', 1200), row('income','2026-10-01', 7000),
                row('expense', '2026-09-08', 2000), row('expense','2026-09-09',9999),
                row('expense','2026-10-09',9999), row('expense','2026-10-01',9999,'gone')]
        data = comparison(rows, date(2026,10,8))
        self.assertEqual({'income':7000,'expense':1200},data['current'])
        self.assertEqual(-800,data['difference']['expense'])
        self.assertEqual('2026-09-08',data['previous_end'])

    def test_previous_month_clamps_day_and_handles_year_boundary(self):
        self.assertEqual('2024-02-29',comparison([],date(2024,3,31))['previous_end'])
        self.assertEqual('2025-12-01',comparison([],date(2026,1,8))['previous_start'])

    def test_week_uses_same_weekdays_not_entire_previous_week(self):
        data=comparison([row('expense','2026-10-01',200),row('expense','2026-10-02',999)],date(2026,10,8),'week')
        self.assertEqual('2026-09-28',data['previous_start'])
        self.assertEqual('2026-10-01',data['previous_end'])
        self.assertEqual(200,data['previous']['expense'])

    def test_negative_money_is_not_floor_divided(self):
        self.assertEqual('-R$ 0,50',money(-50))

    def test_day_uses_callers_token_timezone_and_excludes_paid_archived_bills(self):
        owner=UUID(int=1)
        rows=[row('bill','2026-10-08',100,title='Paga'),row('bill','2026-10-09',200,title='Pendente'),
              row('bill','2026-10-09',999,'gone',title='Arquivada'),row('diary','2026-10-08',title='Estudo')]
        tasks={'tasks':[dict(title='Dormir',due_time='22:00:00',due_date='2026-10-08'),dict(title='Estudar',due_time='19:00:00',due_date='2026-10-08')],
               'focus_pending':2,'complete_list':True,'total':2}
        with patch('backend.app.core.assistant_panel.load_records',return_value=rows) as load, \
             patch('backend.app.core.assistant_panel.load_payments',return_value=[{'bill_id':'Paga','occurrence_on':'2026-10-08'}]), \
             patch('backend.app.core.assistant_panel.task_context',return_value=tasks) as task, \
             patch('backend.app.core.ai.generate') as model:
            data=day_panel(owner,'Bearer fixture','America/Sao_Paulo',datetime.fromisoformat('2026-10-09T01:00:00+00:00'))
        self.assertEqual('2026-10-08',data['date'])
        self.assertEqual('Estudar',data['tasks'][0]['title'])
        self.assertEqual(1,data['bill_count'])
        self.assertEqual('Estudo',data['diary_today'][0]['title'])
        self.assertFalse(data['records_partial'])
        load.assert_called_once_with(owner,'Bearer fixture')
        self.assertEqual(owner,task.call_args.args[0]); model.assert_not_called()

    def test_new_reads_never_execute_writes_or_wrong_domains(self):
        for query, domain in [('memory_all','memory'),('finance_compare_week','record'),('day_overview','conversation'),('diary_last_week','diary')]:
            semantic=Interpretation(domain=domain,operation='create',speech_act='request',evidence='x',device=None,read_query=query)
            with patch('backend.app.core.direct_reads.load_records') as read:
                self.assertIsNone(direct_read(semantic,UUID(int=1),'Bearer x','UTC','2026-10-08T00:00:00+00:00'))
                read.assert_not_called()

    def test_last_week_diary_excludes_current_and_archived_and_reports_limit(self):
        semantic=Interpretation(domain='diary',operation='read',speech_act='question',evidence='semana passada',device=None,read_query='diary_last_week')
        rows=[row('diary','2026-10-01',title=f'Passado {i}') for i in range(22)]
        rows += [row('diary','2026-10-08',title='Atual'),row('diary','2026-10-01',archived='gone',title='Arquivado')]
        with patch('backend.app.core.direct_reads.load_records',return_value=rows):
            answer=direct_read(semantic,UUID(int=1),'Bearer x','UTC','2026-10-08T00:00:00+00:00')['reply']
        self.assertIn('20 de 22',answer); self.assertNotIn('Atual',answer);self.assertNotIn('Arquivado',answer)

    def test_memory_reads_only_verified_facts_without_financial_loading(self):
        semantic=Interpretation(domain='memory',operation='read',speech_act='question',evidence='lembranças',device=None,read_query='memory_all')
        with patch('backend.app.core.memory.confirmed_facts',return_value=[{'content':'Gosto de roxo','category':'preference'}]) as read, \
             patch('backend.app.core.direct_reads.load_records') as records:
            answer=direct_read(semantic,UUID(int=1),'Bearer x','UTC','2026-10-08T00:00:00+00:00')['reply']
        self.assertIn('Gosto de roxo',answer);read.assert_called_once_with(UUID(int=1),'Bearer x');records.assert_not_called()


class BalanceTests(unittest.TestCase):
    def setUp(self):
        poe_balance._cached=None;poe_balance._deadline=0
        self.provider=patch.object(settings,'ai_provider','poe');self.provider.start()
        self.key=patch.object(settings,'poe_api_key',SecretStr('fixture'));self.key.start()
        self.addCleanup(self.provider.stop); self.addCleanup(self.key.stop)
        self.addCleanup(self.clear)

    def clear(self):
        poe_balance._cached=None;poe_balance._deadline=0

    def test_balance_caches_and_never_calls_generation_or_history(self):
        opener=MagicMock()
        opener.open.return_value.__enter__.return_value=io.BytesIO(b'{"current_point_balance":9000}')
        with patch('backend.app.core.poe_balance.build_opener',return_value=opener),patch('backend.app.core.ai.generate') as model:
            first=poe_balance.balance();second=poe_balance.balance()
        self.assertEqual(9000,first['available_points']); self.assertEqual(first,second)
        self.assertEqual('https://api.poe.com/usage/current_balance',opener.open.call_args.args[0].full_url)
        self.assertEqual(1,opener.open.call_count);model.assert_not_called()
        self.assertNotIn('fixture',json.dumps(first))

    def test_failed_balance_is_bounded_and_negative_cached(self):
        opener=MagicMock()
        opener.open.return_value.__enter__.return_value=io.BytesIO(b'{"current_point_balance":true}')
        with patch('backend.app.core.poe_balance.build_opener',return_value=opener):
            for _ in range(2):
                with self.assertRaises(HTTPException) as raised: poe_balance.balance()
                self.assertEqual(503,raised.exception.status_code)
        self.assertEqual(1,opener.open.call_count)

    def test_missing_key_never_calls_network(self):
        with patch.object(settings,'poe_api_key',SecretStr('')),patch('backend.app.core.poe_balance.build_opener') as network:
            with self.assertRaises(HTTPException): poe_balance.balance()
        network.assert_not_called()


class PanelAuthTests(unittest.IsolatedAsyncioTestCase):
    async def test_panels_require_auth_and_never_load_data_anonymously(self):
        with patch('backend.app.api.routes.assistant.day_panel') as data,patch('backend.app.api.routes.assistant.balance') as points:
            self.assertEqual(401,(await call(path='/api/v1/assistant/day',body={'timezone':'UTC'}))[0])
            self.assertEqual(401,(await call(path='/api/v1/assistant/usage',method='GET'))[0])
        data.assert_not_called();points.assert_not_called()

    async def test_verified_owner_not_client_id_and_invalid_timezone_rejected(self):
        opener=MagicMock()
        opener.open.return_value.__enter__.return_value.read.return_value=json.dumps({'id':USER}).encode()
        with patch('backend.app.core.auth.build_opener',return_value=opener), \
             patch('backend.app.api.routes.assistant.day_panel',return_value={'date':'2026-10-08'}) as panel:
            status,_=await call(path='/api/v1/assistant/day',token='Bearer fixture',body={'timezone':'UTC','owner':'someone-else'})
            self.assertEqual(422,status);panel.assert_not_called()
            status,_=await call(path='/api/v1/assistant/day',token='Bearer fixture',body={'timezone':'UTC'})
            self.assertEqual(200,status); self.assertEqual(UUID(USER),panel.call_args.args[0])
            self.assertEqual('Bearer fixture',panel.call_args.args[1])
            status,_=await call(path='/api/v1/assistant/day',token='Bearer fixture',body={'timezone':'invalid'})
            self.assertEqual(422,status)
