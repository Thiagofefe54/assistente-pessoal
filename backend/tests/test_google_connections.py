import unittest
from unittest.mock import patch
from uuid import UUID
from urllib.parse import urlsplit, parse_qs
from cryptography.fernet import Fernet
from pydantic import SecretStr
from fastapi import HTTPException
from backend.app.core import google_connections as g
from backend.app.core.config import settings
from backend.tests.test_auth import call

OWNER = UUID(int=1)
OTHER = UUID(int=2)


class GoogleTests(unittest.TestCase):
    def setUp(self):
        for name, value in dict(google_enabled=True, google_client_id='fixture-client',
            google_client_secret=SecretStr('fixture-client-secret'),
            connections_encryption_key=SecretStr(Fernet.generate_key().decode()),
            connections_supabase_secret_key=SecretStr('sb_secret_fixture'),
            google_redirect_uri='https://koi.example' + g.CALLBACK_PATH,
            supabase_url='https://fixture.supabase.co').items():
            p = patch.object(settings, name, value); p.start(); self.addCleanup(p.stop)

    def test_disabled_and_bad_redirect_do_not_read_database(self):
        with patch.object(settings, 'google_enabled', False), patch.object(g, 'request_json') as req:
            self.assertEqual({'configured': False, 'accounts': []}, g.list_accounts(OWNER))
            with self.assertRaises(HTTPException): g.start(OWNER)
            req.assert_not_called()
        for url in ('http://koi.example'+g.CALLBACK_PATH, 'https://u:p@koi.example'+g.CALLBACK_PATH,
                    'https://koi.example'+g.CALLBACK_PATH+'?token=bad'):
            with patch.object(settings, 'google_redirect_uri', url): self.assertFalse(g.ready())

    def test_cipher_binds_owner_and_purpose_and_detects_tampering(self):
        value = g.seal(OWNER, 'google:123', {'refresh':'PRIVATE'})
        self.assertNotIn('PRIVATE', value)
        self.assertEqual('PRIVATE', g.unseal(OWNER,'google:123',value)['refresh'])
        for owner, purpose, token in ((OTHER,'google:123',value),(OWNER,'google:456',value),(OWNER,'google:123',value[:-4]+'xxxx')):
            with self.assertRaises(HTTPException):g.unseal(owner,purpose,token)

    def test_browser_handoff_generates_pkce_and_distinct_cookie(self):
        row={'user_id':str(OWNER),'secret':g.seal(OWNER,'oauth',{})}
        with patch.object(g,'pending',return_value=row) as pending, patch.object(g,'create_pending',return_value='x'*43) as create:
            url,state,cookie=g.begin('ticket'*8)
        self.assertEqual('DELETE',pending.call_args.args[2])
        query=parse_qs(urlsplit(url).query)
        self.assertEqual(['S256'],query['code_challenge_method'])
        self.assertEqual(['select_account consent'],query['prompt'])
        self.assertEqual(g.digest(cookie),create.call_args.args[2]['cookie_hash'])
        self.assertNotEqual(g.cookie_name(state),g.cookie_name('y'*43))
        self.assertNotIn('fixture-client-secret',url)

    def flow_row(self):
        return {'user_id':str(OWNER),'secret':g.seal(OWNER,'oauth',{'verifier':'fixture-verifier','cookie_hash':g.digest('browser-cookie')})}

    def test_wrong_browser_never_consumes_or_exchanges_code(self):
        with patch.object(g,'pending',return_value=self.flow_row()) as pending, patch.object(g,'request_json') as req:
            with self.assertRaises(HTTPException):g.callback('x'*43,'wrong','code')
        self.assertEqual(1,pending.call_count); req.assert_not_called()

    def test_replay_denial_and_missing_offline_access_never_save_account(self):
        with patch.object(g,'pending',side_effect=HTTPException(400,'used')),patch.object(g,'request_json') as req:
            with self.assertRaises(HTTPException):g.callback('x'*43,'browser-cookie','code')
            req.assert_not_called()
        with patch.object(g,'pending',return_value=self.flow_row()),patch.object(g,'request_json') as req:
            with self.assertRaises(HTTPException):g.callback('x'*43,'browser-cookie',None,'access_denied')
            req.assert_not_called()
        with patch.object(g,'pending',return_value=self.flow_row()),patch.object(g,'request_json',return_value={'access_token':'fixture','token_type':'Bearer'}),patch.object(g,'database') as db:
            with self.assertRaises(HTTPException):g.callback('x'*43,'browser-cookie','code')
            db.assert_not_called()

    def test_callback_stable_subject_owner_encrypted_and_no_tokens_in_result(self):
        result={'id':str(UUID(int=3)),'email':'fixture@example.com','scopes':[g.SCOPE['tasks']],'updated_at':g.stamp()}
        with patch.object(g,'pending',return_value=self.flow_row()),patch.object(g,'request_json',side_effect=[
            {'access_token':'PRIVATE_ACCESS','refresh_token':'PRIVATE_REFRESH','token_type':'Bearer','scope':g.SCOPE['tasks']},
            {'sub':'stable-subject','email':'fixture@example.com','email_verified':True}]) as req,patch.object(g,'database',return_value=[result]) as db:
            output=g.callback('x'*43,'browser-cookie','fixture-code')
        payload=db.call_args.args[3]
        self.assertEqual(str(OWNER),payload['user_id']);self.assertEqual('stable-subject',payload['subject'])
        self.assertNotIn('PRIVATE',str(payload));self.assertNotIn('PRIVATE',str(output))
        self.assertEqual('PRIVATE_REFRESH',g.unseal(OWNER,'google:stable-subject',payload['secret'])['refresh'])
        self.assertTrue(db.call_args.kwargs['upsert'])
        self.assertEqual('fixture-verifier',req.call_args_list[0].kwargs['payload']['code_verifier'])

    def test_foreign_connection_cannot_read_or_disconnect(self):
        with patch.object(g,'database',return_value=[]) as db,patch.object(g,'request_json') as req:
            for fn,args in ((g.consult,(OTHER,UUID(int=3),'tasks')),(g.disconnect,(OTHER,UUID(int=3)))):
                with self.assertRaises(HTTPException) as exc:fn(*args)
                self.assertEqual(404,exc.exception.status_code)
            for c in db.call_args_list:self.assertEqual('eq.'+str(OTHER),c.args[1]['user_id'])
            req.assert_not_called()

    def test_missing_permission_does_not_refresh_or_contact_google(self):
        with patch.object(g,'account',return_value={'scopes':[]}),patch.object(g,'access_token') as token:
            with self.assertRaises(HTTPException):g.consult(OWNER,UUID(int=3),'mail')
            token.assert_not_called()

    def test_refresh_never_resurrects_disconnected_account(self):
        row={'id':str(UUID(int=3)),'subject':'subject','updated_at':'version',
             'secret':g.seal(OWNER,'google:subject',{'access':'old','refresh':'PRIVATE','expires_at':0})}
        with patch.object(g,'request_json',return_value={'access_token':'new','token_type':'Bearer'}),patch.object(g,'database',return_value=[]) as db:
            with self.assertRaises(HTTPException):g.access_token(OWNER,row)
        self.assertEqual('PATCH',db.call_args.args[2]);self.assertEqual('eq.version',db.call_args.args[1]['updated_at'])

    def test_read_minimizes_events_and_preserves_account_and_partial_flag(self):
        row={'id':str(UUID(int=3)),'email':'fixture@example.com','scopes':[g.SCOPE['calendar']],'updated_at':'today'}
        with patch.object(g,'account',return_value=row),patch.object(g,'access_token',return_value='private'),patch.object(g,'request_json',return_value={
            'items':[{'id':'one','summary':'Study','attendees':[{'email':'PRIVATE'}],'description':'PRIVATE'}], 'nextPageToken':'PRIVATE'}):
            result=g.consult(OWNER,UUID(int=3),'calendar')
        self.assertTrue(result['partial']);self.assertNotIn('PRIVATE',str(result));self.assertEqual(row['email'],result['account']['email'])

    def test_malformed_provider_tokens_fail_without_exposing_values(self):
        valid = {'access_token':'fixture','refresh_token':'fixture','token_type':'Bearer'}
        for bad in ([], {**valid,'expires_in':'PRIVATE'}, {**valid,'expires_in':False},
                    {**valid,'expires_in':-1}, {**valid,'access_token':['PRIVATE']},
                    {**valid,'access_token':'PRIVATE\r\n'}, {**valid,'scope':[]},
                    {**valid,'token_type':42}):
            with self.assertRaises(HTTPException) as exc:
                g.token_response(bad, require_refresh=True)
            self.assertNotIn('PRIVATE',str(exc.exception.detail))
        self.assertEqual(3600,g.token_response({**valid,'expires_in':'7200'})['expires_in'])

    def test_malformed_consultation_and_mail_metadata_fail_safely(self):
        row={'id':str(UUID(int=3)),'email':'fixture@example.com','scopes':[g.SCOPE['mail']],'updated_at':'today'}
        for bad in ([], {'messages':{}}, {'messages':[42]}):
            with patch.object(g,'account',return_value=row),patch.object(g,'access_token',return_value='fixture'),patch.object(g,'request_json',return_value=bad):
                with self.assertRaises(HTTPException):g.consult(OWNER,UUID(int=3),'mail')
        with patch.object(g,'account',return_value=row),patch.object(g,'access_token',return_value='fixture'),patch.object(g,'request_json',side_effect=[
                {'messages':[{'id':'one'}]}, {'payload':{'headers':{}}}]):
            with self.assertRaises(HTTPException):g.consult(OWNER,UUID(int=3),'mail')

    def test_mail_is_limited_to_five_metadata_reads_and_never_returns_bodies(self):
        row={'id':str(UUID(int=3)),'email':'fixture@example.com','scopes':[g.SCOPE['mail']],'updated_at':'today'}
        responses=[{'messages':[{'id':str(i)} for i in range(8)],'nextPageToken':'private'}]
        responses += [{'payload':{'headers':[{'name':'Subject','value':'Study'},{'name':'From','value':'fixture@example.com'}],
                                  'body':{'data':'PRIVATE_BODY'}},'snippet':'PRIVATE_SNIPPET'}]*5
        with patch.object(g,'account',return_value=row),patch.object(g,'access_token',return_value='fixture'),patch.object(g,'request_json',side_effect=responses) as req:
            result=g.consult(OWNER,UUID(int=3),'mail')
        self.assertEqual(5,len(result['items']));self.assertEqual(6,req.call_count)
        self.assertNotIn('PRIVATE',str(result));self.assertTrue(result['partial'])
        for request in req.call_args_list[1:]:
            self.assertIn('format=metadata',request.args[0])


class GoogleAuthTests(unittest.IsolatedAsyncioTestCase):
    async def browser_call(self, path, query, cookie=None):
        from backend.app.main import app
        headers=[] if cookie is None else [(b'cookie',cookie.encode())]
        scope=dict(type='http',asgi={'version':'3.0'},http_version='1.1',method='GET',
                   scheme='https',path=path,raw_path=path.encode(),query_string=query.encode(),
                   root_path='',headers=headers,server=('koi.example',443),client=('127.0.0.1',2345))
        output=[]
        async def receive():return {'type':'http.request','body':b'','more_body':False}
        async def send(event):output.append(event)
        await app(scope,receive,send)
        start=next(e for e in output if e['type']=='http.response.start')
        return start, b''.join(e.get('body',b'') for e in output)

    async def test_begin_sets_secure_per_flow_cookie_and_privacy_headers(self):
        state='s'*43
        with patch.object(g,'begin',return_value=('https://accounts.google.com/fixture',state,'PRIVATE_COOKIE')):
            start,body=await self.browser_call('/api/v1/connections/google/begin','ticket='+'t'*43)
        headers=dict(start['headers']);cookie=headers[b'set-cookie'].decode()
        self.assertEqual(303,start['status'])
        for required in ('HttpOnly','Secure','SameSite=lax',g.CALLBACK_PATH,g.cookie_name(state)):
            self.assertIn(required,cookie)
        self.assertEqual(b'no-store',headers[b'cache-control'])
        self.assertEqual(b'no-referrer',headers[b'referrer-policy'])
        self.assertNotIn(b'PRIVATE',body)

    async def test_callback_failure_is_static_and_clears_cookie_without_secrets(self):
        state='s'*43
        with patch.object(g,'callback',side_effect=HTTPException(409,'PRIVATE_UPSTREAM')):
            start,body=await self.browser_call(g.CALLBACK_PATH,'state='+state+'&code=PRIVATE_CODE')
        self.assertEqual(409,start['status']);self.assertNotIn(b'PRIVATE',body)
        headers=dict(start['headers'])
        self.assertIn(b'Max-Age=0',headers[b'set-cookie'])
        self.assertEqual(b'no-store',headers[b'cache-control'])
        self.assertIn(b"frame-ancestors 'none'",headers[b'content-security-policy'])

    async def test_all_android_routes_require_login_before_tools(self):
        with patch.object(g,'list_accounts') as read,patch.object(g,'start') as start:
            for path in ('google-status','google-connect','google-read','google-disconnect'):
                status,_=await call(path='/api/v1/assistant/'+path,body={})
                self.assertEqual(401,status)
            read.assert_not_called();start.assert_not_called()
