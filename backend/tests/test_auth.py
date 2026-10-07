import asyncio
import io
import json
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError

from backend.app.main import app
from backend.app.core.config import Settings, settings
from backend.app.core.auth import NoRedirect

USER = '12345678-1234-4234-9234-123456789012'


async def call(path='/api/v1/chat', scheme='https', token=None, body=None, method='POST'):
    headers = [(b'content-type', b'application/json')]
    if token is not None:
        headers.append((b'authorization', token.encode()))
    scope = dict(type='http', asgi={'version': '3.0'}, http_version='1.1', method=method,
                 scheme=scheme, path=path, raw_path=path.encode(), query_string=b'',
                 root_path='', headers=headers, server=('koi.example', 443),
                 client=('127.0.0.1', 2345))
    data = json.dumps(body if body is not None else {'message': 'oi'}).encode()
    output = []

    async def receive():
        return {'type': 'http.request', 'body': data, 'more_body': False}

    async def send(event):
        output.append(event)

    await app(scope, receive, send)
    status = next(item['status'] for item in output if item['type'] == 'http.response.start')
    payload = b''.join(item.get('body', b'') for item in output if item['type'] == 'http.response.body')
    return status, json.loads(payload)


class AuthTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.config = patch.multiple(settings, environment='development',
            supabase_url='https://project.supabase.co', supabase_publishable_key='sb_publishable_test')
        self.config.start()
        self.addCleanup(self.config.stop)
        self.opener = patch('backend.app.core.auth.build_opener').start()
        self.addCleanup(patch.stopall)
        self.ai = patch('backend.app.api.routes.chat.reply', return_value='Olá, Mestre. Estou aqui.').start()
        self.memory = patch('backend.app.api.routes.chat.confirmed_facts', return_value=[]).start()

    def upstream(self, user):
        self.opener.return_value.open.return_value = io.BytesIO(json.dumps(user).encode())

    async def test_missing_or_wrong_scheme_rejected(self):
        for token in (None, 'Basic abc'):
            status, _ = await call(token=token)
            self.assertEqual(401, status)
        self.opener.assert_not_called()

    async def test_http_never_forwards_token(self):
        status, _ = await call(scheme='http', token='Bearer private-test-token')
        self.assertEqual(400, status)
        self.opener.assert_not_called()

    async def test_valid_token_and_verified_identity(self):
        self.upstream({'id': USER, 'user_metadata': {'user_id': 'attacker'}})
        status, payload = await call('/api/v1/chat/me', token='Bearer private-test-token', method='GET')
        self.assertEqual((200, {'user_id': USER}), (status, payload))
        request = self.opener.return_value.open.call_args.args[0]
        self.assertEqual('https://project.supabase.co/auth/v1/user', request.full_url)
        self.assertEqual('Bearer private-test-token', request.get_header('Authorization'))
        self.upstream({'id': USER})
        status, payload = await call(token='Bearer private-test-token')
        self.assertEqual((200, {'reply': 'Olá, Mestre. Estou aqui.'}), (status, payload))
        self.assertEqual(USER, str(self.memory.call_args.args[0]))
        self.assertEqual('Bearer private-test-token', self.memory.call_args.args[1])

    async def test_client_cannot_choose_owner(self):
        self.upstream({'id': USER})
        status, _ = await call(token='Bearer private-test-token', body={'message': 'oi', 'user_id': 'other'})
        self.assertEqual(422, status)

    async def test_memory_failure_prevents_inference(self):
        from fastapi import HTTPException
        self.upstream({'id': USER})
        self.memory.side_effect = HTTPException(503, 'Não consegui consultar suas lembranças.')
        status, _ = await call(token='Bearer private-test-token')
        self.assertEqual(503,status)
        self.ai.assert_not_called()

    async def test_rejected_token(self):
        for code in (401, 403):
            self.opener.return_value.open.side_effect = HTTPError('https://project.supabase.co', code, '', {}, None)
            status, _ = await call(token='Bearer private-test-token')
            self.assertEqual(401, status)
        self.memory.assert_not_called()

    async def test_service_failure_fails_closed(self):
        for error in (URLError('offline'), TimeoutError(),
                      HTTPError('https://project.supabase.co', 302, '', {}, None),
                      HTTPError('https://project.supabase.co', 500, '', {}, None)):
            self.opener.return_value.open.side_effect = error
            status, payload = await call(token='Bearer private-test-token')
            self.assertEqual(503, status)
            self.assertNotIn('private-test-token', json.dumps(payload))

    async def test_malformed_or_anonymous_user_rejected(self):
        for user, expected in (({'id': 'invalid'}, 503), ({}, 503),
                               ({'id': USER, 'is_anonymous': True}, 401)):
            self.upstream(user)
            status, _ = await call(token='Bearer private-test-token')
            self.assertEqual(expected, status)

    async def test_demo_and_production_separation(self):
        status, _ = await call('/api/v1/chat/demo', scheme='http')
        self.assertEqual(200, status)
        status, _ = await call('/api/v1/chat/demo', scheme='http', token='Bearer private-test-token')
        self.assertEqual(400, status)
        with patch.object(settings, 'environment', 'production'):
            status, _ = await call('/api/v1/chat/demo')
            self.assertEqual(404, status)
        self.opener.assert_not_called()

    async def test_unconfigured_server_rejected(self):
        with patch.object(settings, 'supabase_url', ''):
            status, _ = await call(token='Bearer private-test-token')
            self.assertEqual(503, status)
        self.opener.assert_not_called()

    async def test_payload_limits(self):
        for message in ('', 'a' * 8001):
            status, _ = await call('/api/v1/chat/demo', body={'message': message})
            self.assertEqual(422, status)

    def test_production_config_requires_auth(self):
        with self.assertRaises(ValueError):
            Settings(_env_file=None, environment='production', supabase_url='', supabase_publishable_key='')
        for url in ('http://project.supabase.co', 'https://user:password@project.supabase.co',
                    'https://project.supabase.co?x=1'):
            with self.assertRaises(ValueError):
                Settings(_env_file=None, supabase_url=url)

    def test_redirects_are_not_followed(self):
        self.assertIsNone(NoRedirect().redirect_request(None, None, 302, '', {}, 'https://other.example'))


if __name__ == '__main__':
    unittest.main()
