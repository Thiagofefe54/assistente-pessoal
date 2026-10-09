import io
import json
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from uuid import UUID

from fastapi import HTTPException

from backend.app.core.config import settings
from backend.app.core.memory import confirmed_facts
from backend.app.core import ai


class MemoryTests(unittest.TestCase):
    def test_read_uses_verified_owner_and_caller_token_without_service_key(self):
        with patch.multiple(settings, supabase_url='https://project.supabase.co', supabase_publishable_key='public-test'), \
             patch('backend.app.core.memory.build_opener') as opener:
            opener.return_value.open.return_value = io.BytesIO(b'[{"content":"Gosto de azul","category":"preference"}]')
            rows = confirmed_facts(UUID('12345678-1234-4234-9234-123456789012'), 'Bearer private-token')
            request = opener.return_value.open.call_args.args[0]
            self.assertEqual(1, len(rows))
            self.assertIn('user_id=eq.12345678-1234-4234-9234-123456789012',request.full_url)
            self.assertIn('limit=20',request.full_url)
            self.assertEqual('Bearer private-token',request.get_header('Authorization'))
            self.assertEqual('public-test',request.get_header('Apikey'))

    def test_failure_or_invalid_data_stops_before_inference_without_leaking(self):
        bad = [b'{}', b'[{"content":"x","category":"admin"}]', b'[{"content":null,"category":"note"}]',
               json.dumps([{'content':'x','category':'note'}]*21).encode(), b'x'*50001]
        with patch('backend.app.core.memory.build_opener') as opener:
            for data in bad:
                opener.return_value.open.return_value = io.BytesIO(data)
                with self.assertRaises(HTTPException) as caught:
                    confirmed_facts(UUID(int=1),'Bearer private-token')
                self.assertEqual(503,caught.exception.status_code)
            opener.return_value.open.side_effect = HTTPError('https://example',302,'private-token',{},None)
            with self.assertRaises(HTTPException) as caught:
                confirmed_facts(UUID(int=1),'Bearer private-token')
            self.assertNotIn('private-token',caught.exception.detail)

    def test_confirmed_facts_are_reference_data_below_system_instructions(self):
        with patch('backend.app.core.ai.generate',return_value='Azul') as completion:
            ai.reply('Qual cor?',[{'role':'user','content':'oi'}],[{'content':'Gosto de azul','category':'preference'}])
            messages=completion.call_args.args[0]
            self.assertEqual(['system','user','user','user'],[row['role'] for row in messages])
            self.assertIn('Gosto de azul',messages[1]['content'])
            self.assertIn('dados de referência',messages[1]['content'])
            self.assertEqual('Qual cor?',messages[-1]['content'])
