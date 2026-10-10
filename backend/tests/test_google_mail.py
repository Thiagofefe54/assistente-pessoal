import asyncio
import base64
import unittest
from email import message_from_bytes
from unittest.mock import patch
from uuid import UUID, uuid4
from cryptography.fernet import Fernet
from pydantic import SecretStr, ValidationError
from fastapi import HTTPException
from backend.app.api.routes.connections import MailSendRequest
from backend.app.core import google_connections as g, google_mail as mail
from backend.app.core.config import settings
from backend.tests.test_auth import call


class GoogleMailTests(unittest.TestCase):
    def setUp(self):
        self.owner=UUID(int=1);self.connection=UUID(int=2);self.request=uuid4();self.rows={}
        for target,name,value in ((settings,'connections_encryption_key',SecretStr(Fernet.generate_key().decode())),
            (g,'database',self.database),(g,'account',lambda owner,connection:{'email':'sender@example.test','scopes':[g.SCOPE['mail_send']]}),
            (g,'access_token',lambda *args:'fixture-token')):
            p=patch.object(target,name,value);p.start();self.addCleanup(p.stop)
        p=patch.object(g,'request_json',return_value={'id':'fixture-id'});self.network=p.start();self.addCleanup(p.stop)

    def database(self,table,query=None,method='GET',payload=None,**kwargs):
        if method=='POST':
            key=(payload['user_id'],payload['request_id'])
            if key in self.rows:raise HTTPException(409,'Already claimed')
            self.rows[key]=dict(payload);return [self.rows[key]]
        key=(query['user_id'][3:],query['request_id'][3:])
        if key not in self.rows:return []
        if method=='PATCH':self.rows[key].update(payload)
        return [self.rows[key]]

    def send(self,**changes):
        values=dict(owner=self.owner,connection_id=self.connection,request_id=self.request,recipient='friend@example.test',subject='Olá 💜',body='Texto fictício\nlinha dois',reviewed=True)
        values.update(changes);return mail.send(**values)

    def test_send_mime_receipt_and_identical_replay_only_once(self):
        answer=self.send();self.assertTrue(answer['sent']);self.assertEqual(answer,self.send());self.network.assert_called_once()
        self.assertEqual('https://gmail.googleapis.com/gmail/v1/users/me/messages/send',self.network.call_args.args[0])
        raw=self.network.call_args.kwargs['payload']['raw']
        message=message_from_bytes(base64.urlsafe_b64decode(raw))
        self.assertEqual('friend@example.test',message['To']);self.assertIsNone(message['Bcc'])
        self.assertIn('Texto fictício',message.get_payload(decode=True).decode())
        stored=self.rows[(str(self.owner),str(self.request))]
        self.assertNotIn('Texto fictício',str(stored));self.assertEqual('done',stored['phase'])

    def test_uncertain_send_cannot_repeat_after_timeout(self):
        self.network.side_effect=HTTPException(503,'Timeout')
        with self.assertRaises(HTTPException):self.send()
        self.network.side_effect=None
        answer=self.send();self.assertTrue(answer['uncertain']);self.assertFalse(answer['sent']);self.network.assert_called_once()

    def test_reused_id_with_other_body_rejected_before_network(self):
        self.send()
        with self.assertRaises(HTTPException) as error:self.send(body='Outra mensagem')
        self.assertEqual(409,error.exception.status_code);self.network.assert_called_once()

    def test_missing_review_or_scope_never_claims_or_sends(self):
        with self.assertRaises(HTTPException):self.send(reviewed=False)
        with patch.object(g,'account',return_value={'email':'sender@example.test','scopes':[g.SCOPE['mail']]}):
            with self.assertRaises(HTTPException) as error:self.send()
            self.assertEqual(403,error.exception.status_code)
        self.network.assert_not_called();self.assertFalse(self.rows)

    def test_invalid_confirmation_headers_addresses_and_empty_text(self):
        base=dict(request_id=str(self.request),connection_id=str(self.connection),recipient='friend@example.test',subject='Teste',body='Texto',reviewed=True)
        for changed in ({'reviewed':False},{'subject':'Oi\r\nBcc: outsider@example.test'},
            {'recipient':'a@example.test,other@example.test'},{'recipient':'a@example.test\nBcc:b@example.test'},
            {'body':'  '},{'body':'Texto\x00'}):
            with self.subTest(changed=changed),self.assertRaises(ValidationError):MailSendRequest(**(base|changed))

    def test_receipts_are_owner_bound(self):
        self.send();self.assertIsNone(mail.receipt(UUID(int=9),self.request))
        self.assertTrue(mail.receipt(self.owner,self.request)['sent'])

    def test_invalid_provider_success_stays_uncertain(self):
        self.network.return_value={'not_id':'bad'}
        with self.assertRaises(HTTPException):self.send()
        self.assertTrue(self.send()['uncertain']);self.network.assert_called_once()

    def test_receipt_storage_failure_does_not_allow_resending(self):
        original=self.database
        def failed(*args,**kwargs):
            if kwargs.get('method')=='PATCH':raise HTTPException(503,'Persistence failed')
            return original(*args,**kwargs)
        with patch.object(g,'database',side_effect=failed):
            with self.assertRaises(HTTPException):self.send()
        self.assertTrue(self.send()['uncertain']);self.network.assert_called_once()

    def test_new_endpoints_require_auth(self):
        for endpoint in ('google-mail-send','google-mail-status','google-mail-important'):
            status,_=asyncio.run(call(path='/api/v1/assistant/'+endpoint))
            self.assertEqual(401,status)
        self.network.assert_not_called()

    def test_important_mail_only_reads_bounded_ids_no_bodies(self):
        account={'id':str(self.connection),'services':['mail']}
        self.network.return_value={'messages':[{'id':'fixture-id'}],'nextPageToken':'more'}
        with patch.object(g,'list_accounts',return_value={'accounts':[account]}):
            result=mail.important(self.owner)
        self.assertEqual(['fixture-id'],result['accounts'][0]['ids']);self.assertTrue(result['accounts'][0]['partial'])
        self.assertIn('maxResults=20',self.network.call_args.args[0]);self.assertIn('is%3Aimportant',self.network.call_args.args[0])
        self.network.assert_called_once()

    def test_partial_failed_account_not_reported_as_empty_inbox(self):
        self.network.side_effect=HTTPException(401,'Expired')
        with patch.object(g,'list_accounts',return_value={'accounts':[{'id':str(self.connection),'services':['mail']}]}):
            result=mail.important(self.owner)
        self.assertTrue(result['partial']);self.assertEqual([],result['accounts'])
