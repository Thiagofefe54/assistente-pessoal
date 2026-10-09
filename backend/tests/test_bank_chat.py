import unittest
from uuid import UUID
from unittest.mock import patch
from starlette.requests import Request
from backend.app.core.bank_chat import bank_reply
from backend.app.api.routes.chat import chat, ChatRequest


class BankChatTests(unittest.TestCase):
    def test_balance_request_uses_balance_not_spending_plan(self):
        for text in ('quanto dinheiro eu tenho?', 'qual é meu saldo no inter?', 'Quanto eu tenho no meu banco?'):
            result=bank_reply(text,[])
            self.assertEqual('balance',result['action_receipt']['query'])
            self.assertIn('data',result['reply'])

    def test_discrepancy_acknowledged_without_writing_or_reopening_panel(self):
        result=bank_reply('eu tenho 12,85 não tenho esse dinheiro todo', [{'role':'assistant','content':'Saldo do Inter'}])
        self.assertIsNone(result['action_receipt'])
        self.assertIn('não atualiza o banco na hora',result['reply'])
        self.assertIn('Não vou tratar',result['reply'])

    def test_unrelated_reports_payments_and_negated_reads_not_intercepted(self):
        for text in ('Pague 12,85', 'Não consulte meu saldo', 'Eu tenho 12,85 de receita hoje', 'Tenho 12,85 anos', 'quanto dinheiro eu tenho e transfira tudo'):
            self.assertIsNone(bank_reply(text,[{'content':'Saldo do Inter'}]))
        self.assertIsNone(bank_reply('eu tenho 12,85',[]))

    def test_known_discrepancy_does_not_spend_points_or_query_private_data(self):
        request=Request({'type':'http','headers':[(b'authorization',b'Bearer fixture')]})
        with patch('backend.app.api.routes.chat.existing_action',return_value=None), patch('backend.app.core.personal.existing',return_value=None), patch('backend.app.api.routes.chat.interpret') as model, patch('backend.app.api.routes.chat.personal_conversation') as writer:
            result=chat(ChatRequest(message='eu tenho 12,85 não tenho esse dinheiro todo',request_id=UUID(int=2),task_mode='direct',contextual_mode=True),request,UUID(int=1))
        model.assert_not_called();writer.assert_not_called()
        self.assertIsNone(result.action_receipt)
