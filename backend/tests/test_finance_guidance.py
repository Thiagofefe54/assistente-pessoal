"""Semantic selection prepares the private phone panel, not another paid answer."""
import unittest
from unittest.mock import patch
from uuid import UUID
from starlette.requests import Request
from fastapi import HTTPException
from backend.app.core.intent import Interpretation, interpret, FORMAT
from backend.app.core.direct_reads import direct_read
from backend.app.api.routes.chat import ChatRequest, chat


def decision(text):
    return Interpretation(domain='record',operation='read',speech_act='question',
        evidence=text,device=None,read_query='finance_guidance')


class FinanceGuidanceTests(unittest.TestCase):
    def test_schema_and_classifier_allow_semantic_planning(self):
        text='Como eu posso gastar meu dinheiro?'
        with patch('backend.app.core.intent.generate',return_value=decision(text).model_dump_json()) as generate:
            result=interpret(text,[])
        self.assertEqual('finance_guidance',result.read_query)
        self.assertIn('finance_guidance',str(FORMAT))
        self.assertIn('cartão privado',generate.call_args.args[0][0]['content'])
        self.assertEqual(512,generate.call_args.kwargs['max_tokens'])

    def test_phone_plan_has_no_bank_data_and_does_not_query_records(self):
        with patch('backend.app.core.direct_reads.load_records') as records, \
             patch('backend.app.core.banking.summary') as bank:
            result=direct_read(decision('Posso comprar algo para mim?'),UUID(int=1),'Bearer fixture','UTC','2026-10-09T12:00:00Z')
        records.assert_not_called();bank.assert_not_called()
        self.assertEqual({'tool':'bank','type':'read','query':'plan'},result['action_receipt'])
        self.assertNotIn('R$',result['reply'])
        self.assertIn('não significa dinheiro livre',result['reply'])

    def test_question_route_uses_one_interpretation_with_compatible_receipt(self):
        text='Como eu posso gastar meu dinheiro?'
        payload=ChatRequest(message=text,request_id=UUID(int=2),task_mode='direct',contextual_mode=True)
        request=Request({'type':'http','headers':[(b'authorization',b'Bearer fixture')]})
        with patch('backend.app.api.routes.chat.existing_action',return_value=None), \
             patch('backend.app.core.personal.existing',return_value=None), \
             patch('backend.app.api.routes.chat.interpret',return_value=decision(text)) as model, \
             patch('backend.app.api.routes.chat.reply') as answer, \
             patch('backend.app.api.routes.chat.personal_conversation') as writer:
            result=chat(payload,request,UUID(int=1))
        model.assert_called_once();answer.assert_not_called();writer.assert_not_called()
        self.assertEqual(str(UUID(int=2)),result.action_receipt['request_id'])
        self.assertEqual('bank',result.action_receipt['tool'])
        self.assertEqual('plan',result.action_receipt['query'])

    def test_write_report_wrong_domain_and_extra_filter_do_not_open_bank_panel(self):
        from backend.app.core.intent import ReadFilter
        base=decision('Fictício')
        variants=[base.model_copy(update=v) for v in (
            {'operation':'create'},{'speech_act':'report'},{'domain':'memory'},
            {'read_filter':ReadFilter(query='Academia')})]
        for value in variants:
            self.assertIsNone(direct_read(value,UUID(int=1),'fixture','UTC','2026-10-09T12:00:00Z'))

    def test_stale_history_evidence_cannot_prepare_panel(self):
        request=Request({'type':'http','headers':[(b'authorization',b'Bearer fixture')]})
        with patch('backend.app.api.routes.chat.existing_action',return_value=None), \
             patch('backend.app.core.personal.existing',return_value=None), \
             patch('backend.app.api.routes.chat.interpret',return_value=decision('Como gastar meu dinheiro?')):
            with self.assertRaises(HTTPException) as error:
                chat(ChatRequest(message='Oi',request_id=UUID(int=2),task_mode='direct',contextual_mode=True),request,UUID(int=1))
        self.assertEqual(502,error.exception.status_code)
