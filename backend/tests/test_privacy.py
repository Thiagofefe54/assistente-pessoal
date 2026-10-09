import unittest
from backend.app.core.privacy import PrivateApiResponses

class PrivacyTests(unittest.IsolatedAsyncioTestCase):
    async def test_api_errors_and_successes_are_private_without_losing_other_headers(self):
        for status in (200,401,409,503):
            events=[]
            async def app(scope,receive,send):
                await send({'type':'http.response.start','status':status,'headers':[(b'Cache-Control',b'public'),(b'content-security-policy',b'default-src none')]})
                await send({'type':'http.response.body','body':b'fixture'})
            async def receive():return {'type':'http.request'}
            async def send(event):events.append(event)
            await PrivateApiResponses(app)({'type':'http','path':'/api/v1/assistant/bank-summary'},receive,send)
            headers=dict(events[0]['headers'])
            self.assertEqual(b'private, no-store',headers[b'cache-control']);self.assertEqual(b'nosniff',headers[b'x-content-type-options'])
            self.assertEqual(b'default-src none',headers[b'content-security-policy']);self.assertEqual(status,events[0]['status'])
    async def test_non_api_and_websocket_pass_through(self):
        for scope in ({'type':'http','path':'/'},{'type':'websocket','path':'/api/v1/socket'}):
            called=[]
            async def app(scope,receive,send):called.append(scope)
            await PrivateApiResponses(app)(scope,None,None)
            self.assertEqual([scope],called)
