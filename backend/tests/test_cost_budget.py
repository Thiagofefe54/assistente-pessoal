import json
import unittest
from unittest.mock import patch
from uuid import UUID
from starlette.requests import Request
from backend.app.core.context_budget import compact_json, recent_history, TOOL_TONE
from backend.app.core.intent import Interpretation
from backend.app.core.direct_reads import direct_read, money
from backend.app.api.routes.chat import chat, ChatRequest


class CostBudgetTests(unittest.TestCase):
    def test_history_keeps_whole_recent_messages_without_mutating_storage(self):
        history=[{'role':'user','content':'old'*2000}, {'role':'assistant','content':'Conta Internet'},
                 {'role':'user','content':'Não pague essa conta.'}]
        before=json.dumps(history)
        self.assertEqual(history[-2:],recent_history(history))
        self.assertEqual(before,json.dumps(history))
        self.assertEqual(history[:1],recent_history(history[:1]))

    def test_compaction_preserves_unicode_and_literal_content(self):
        data={'message':'Não salve "isso" 💜', 'records':[{'amount':8000}]}
        self.assertEqual(data,json.loads(compact_json(data)))
        self.assertLess(len(compact_json(data)),len(json.dumps(data,ensure_ascii=False)))
        from backend.app.core.ai import KOI_INSTRUCTIONS
        self.assertLess(len(TOOL_TONE),len(KOI_INSTRUCTIONS)//4)

    def test_bill_query_route_uses_one_interpretation_and_no_second_generation(self):
        decision=Interpretation(domain='record',operation='read',speech_act='question',
            evidence='Quais contas tenho para pagar?',device=None,read_query='unpaid_bills_this_month')
        rows=[dict(id='bill',kind='bill',title='Internet',amount_cents=8000,happened_on='2026-10-15',
                   archived_at=None,details={'repeat':'monthly'})]
        request=Request({'type':'http','headers':[(b'authorization',b'Bearer fixture')]})
        payload=ChatRequest(message=decision.evidence,task_mode='direct',contextual_mode=True,
            request_id=UUID(int=2),requested_at='2026-10-08T12:00:00-03:00')
        with patch('backend.app.api.routes.chat.existing_action',return_value=None), \
             patch('backend.app.core.personal.existing',return_value=None), \
             patch('backend.app.api.routes.chat.interpret',return_value=decision) as model, \
             patch('backend.app.core.direct_reads.load_records',return_value=rows), \
             patch('backend.app.core.direct_reads.load_payments',return_value=[]), \
             patch('backend.app.api.routes.chat.personal_conversation') as planner:
            result=chat(payload,request,UUID(int=1))
        model.assert_called_once();planner.assert_not_called()
        self.assertIn('Internet',result.reply);self.assertIn('R$ 80,00',result.reply)
        self.assertIsNone(result.action_receipt)

    def test_expenses_respect_timezone_archive_and_future_dates(self):
        decision=Interpretation(domain='record',operation='read',speech_act='question',
            evidence='Quanto gastei este mês?',device=None,read_query='expenses_this_month')
        rows=[dict(id=str(i),kind='expense',title='Fictícia',amount_cents=amount,
                   happened_on=day,archived_at=archived)
              for i,amount,day,archived in [(1,1250,'2026-10-08',None),
              (2,9900,'2026-10-09',None),(3,1000,'2026-10-08','archived'),(4,500,'2026-09-30',None)]]
        with patch('backend.app.core.direct_reads.load_records',return_value=rows), \
             patch('backend.app.core.direct_reads.load_payments') as payments:
            result=direct_read(decision,UUID(int=1),'Bearer fixture','America/Sao_Paulo','2026-10-09T01:00:00+00:00')
        self.assertIn('R$ 12,50',result['reply']);payments.assert_not_called()

    def test_shortcut_never_handles_writes_reports_or_wrong_domains(self):
        with patch('backend.app.core.direct_reads.load_records') as read:
            for domain,operation,act in [('record','create','request'),('record','read','report'),('memory','read','question')]:
                value=Interpretation(domain=domain,operation=operation,speech_act=act,
                    evidence='fictício',device=None,read_query='expenses_this_month')
                self.assertIsNone(direct_read(value,UUID(int=1),'Bearer fixture','UTC','2026-10-08T00:00:00+00:00'))
            read.assert_not_called()

    def test_paid_rows_cannot_hide_unpaid_bills_in_limited_snapshot(self):
        value=Interpretation(domain='record',operation='read',speech_act='question',
            evidence='contas',device=None,read_query='unpaid_bills_this_month')
        rows=[dict(id=str(i),kind='bill',title=f'Conta {i}',amount_cents=100,
            happened_on='2026-10-15',archived_at=None,details={}) for i in range(82)]
        payments=[dict(bill_id=str(i),occurrence_on='2026-10-15') for i in range(81)]
        with patch('backend.app.core.direct_reads.load_records',return_value=rows), \
             patch('backend.app.core.direct_reads.load_payments',return_value=payments):
            answer=direct_read(value,UUID(int=1),'Bearer fixture','UTC','2026-10-08T00:00:00+00:00')['reply']
        self.assertIn('Conta 81',answer);self.assertIn('Total pendente: R$ 1,00',answer)

    def test_currency_large_values(self):
        self.assertEqual('R$ 1.000.000,50',money(100000050))

    def test_budget_and_diary_reads_use_recorded_data_only(self):
        rows=[dict(id='budget',kind='budget',title='Meu mês',amount_cents=1000,
                   happened_on='2026-10-01',archived_at=None),
              dict(id='expense',kind='expense',title='Compra',amount_cents=1200,
                   happened_on='2026-10-08',archived_at=None),
              dict(id='diary',kind='diary',title='Fui estudar',content='Fictício',
                   happened_on='2026-10-07',archived_at=None,details={})]
        for query,domain,expected in [('budget_this_month','record','ultrapassado'),
                                     ('diary_this_week','diary','07/10 — Fui estudar')]:
            value=Interpretation(domain=domain,operation='read',speech_act='question',
                                 evidence='fictício',device=None,read_query=query)
            with patch('backend.app.core.direct_reads.load_records',return_value=rows):
                result=direct_read(value,UUID(int=1),'Bearer fixture','UTC','2026-10-08T00:00:00+00:00')
            self.assertIn(expected,result['reply'])

    def test_task_planner_keeps_current_request_and_tool_rules(self):
        from backend.app.core.actions import direct_conversation
        history=[{'role':'user','content':'antigo'*1500},
                 {'role':'assistant','content':'Sua tarefa atual é Estudar.'}]
        with patch('backend.app.core.actions.cloud',return_value=[]), \
             patch('backend.app.core.actions.generate',return_value='{"reply":"Qual tarefa, mestre?","action":null}') as model:
            direct_conversation('Mude o horário dela',history,[],{},UUID(int=1),
                                'Bearer fixture',UUID(int=2),'UTC')
        messages=model.call_args.args[0]
        self.assertEqual('Mude o horário dela',messages[-1]['content'])
        self.assertIn('Somente o pedido atual autoriza ação',messages[0]['content'])
        self.assertIn('Se faltar título indispensável',messages[0]['content'])
        self.assertNotIn(history[0],messages)
        self.assertIn('Sua tarefa atual é Estudar.',messages[-2]['content'])
