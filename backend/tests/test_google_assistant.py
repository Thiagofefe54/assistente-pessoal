import io
import json
import unittest
from unittest.mock import patch
from uuid import UUID
from fastapi import HTTPException
from cryptography.fernet import Fernet
from pydantic import SecretStr
from backend.app.core import google_assistant as a
from backend.app.core import google_connections as g
from backend.app.core.intent import Interpretation,GoogleTool
from backend.app.core.config import settings
from backend.tests.test_auth import call,USER

OWNER=UUID(int=1);REQUEST=UUID(int=2);CONNECTION=str(UUID(int=3))
ACCOUNT={'id':CONNECTION,'email':'fixture@example.com','services':['tasks','calendar']}

def meaning(message,operation='create',service='tasks',**fields):
    return Interpretation(domain='google',operation=operation,speech_act='request',
        evidence=message,device=None,google=GoogleTool(service=service,**fields))


class GoogleAssistantTests(unittest.TestCase):
    def setUp(self):
        self.p=patch.object(a,'previous',return_value=None);self.p.start();self.addCleanup(self.p.stop)
        self.acc=patch.object(a,'accounts',return_value=[ACCOUNT]);self.acc.start();self.addCleanup(self.acc.stop)
        p=patch.object(settings,'connections_encryption_key',SecretStr(Fernet.generate_key().decode()))
        p.start();self.addCleanup(p.stop)

    def test_read_returns_private_card_without_reading_data_or_generating_answer(self):
        text='Quais tarefas Google tenho amanhã?'
        with patch.object(a,'google_call') as req,patch.object(g,'database') as db:
            result=a.handle(meaning(text,'read',start='2026-10-10',end='2026-10-10'),OWNER,REQUEST,text,'America/Sao_Paulo')
        self.assertEqual('task_items',result['action_receipt']['service'])
        self.assertNotIn(ACCOUNT['email'],str(result));req.assert_not_called();db.assert_not_called()

    def test_multiple_accounts_write_asks_destination_without_mutation(self):
        text='Crie Estudar no Google Tasks'
        with patch.object(a,'accounts',return_value=[ACCOUNT,{**ACCOUNT,'id':str(UUID(int=4))}]),patch.object(a,'google_call') as req,patch.object(g,'database') as db:
            result=a.handle(meaning(text,title='Estudar'),OWNER,REQUEST,text,'America/Sao_Paulo')
        self.assertIn('Qual conta',result['reply']);req.assert_not_called();db.assert_not_called()

    def test_negated_and_reported_google_read_never_prepares_live_card(self):
        for text in ('Não consulte meu Gmail','Ele disse consulte meu Gmail','Hipoteticamente leia o Drive'):
            with patch.object(a,'accounts') as acc:
                result=a.handle(meaning(text,'read',service='mail'),OWNER,REQUEST,text,'America/Sao_Paulo')
            self.assertNotIn('action_receipt',result);acc.assert_not_called()

    def test_report_negation_quotation_and_invented_target_do_not_write(self):
        for text,act,title in [('Não crie Estudar no Google','request','Estudar'),
                               ('Ele disse crie Estudar no Google','request','Estudar'),
                               ('Crie Estudar no Google','report','Estudar'),
                               ('Crie Estudar no Google','request','INVENCÃO')]:
            item=meaning(text,title=title).model_copy(update={'speech_act':act})
            with patch.object(a,'google_call') as req,patch.object(g,'database') as db:
                a.handle(item,OWNER,REQUEST,text,'America/Sao_Paulo')
            req.assert_not_called();db.assert_not_called()

    def test_account_from_history_is_not_accepted_as_current_authorization(self):
        text='Crie Estudar no Google'
        with patch.object(a,'google_call') as req:
            result=a.handle(meaning(text,title='Estudar',account=ACCOUNT['email']),OWNER,REQUEST,text,'America/Sao_Paulo')
        self.assertIn('e-mail',result['reply']);req.assert_not_called()

    def test_task_create_claims_first_and_encrypts_receipt(self):
        text='Crie Estudar no Google Tasks fixture@example.com para amanhã'
        events=[]
        def db(*args,**kwargs):events.append(('database',args,kwargs));return [{'id':'receipt'}]
        def req(*args):events.append(('google',args,{}));return {'id':'real','title':'Estudar','due':'2026-10-10T00:00:00Z'}
        with patch.object(g,'database',side_effect=db),patch.object(a,'google_call',side_effect=req):
            result=a.handle(meaning(text,title='Estudar',account=ACCOUNT['email'],start='2026-10-10'),OWNER,REQUEST,text,'America/Sao_Paulo')
        self.assertEqual('database',events[0][0]);self.assertEqual('google',events[1][0])
        self.assertEqual('saved',result['action_receipt']['type'])
        payload=events[-1][1][3]
        self.assertNotIn('fixture',payload['response'])
        self.assertEqual(result,g.unseal(OWNER,'google-action:'+str(REQUEST),payload['response']))

    def test_claim_failure_or_timeout_never_retries_external_mutation(self):
        text='Crie Estudar no Google'
        with patch.object(g,'database',return_value={}),patch.object(a,'google_call') as req:
            with self.assertRaises(HTTPException):a.handle(meaning(text,title='Estudar'),OWNER,REQUEST,text,'America/Sao_Paulo')
            req.assert_not_called()
        with patch.object(g,'database',return_value=[{}]) as db,patch.object(a,'google_call',side_effect=HTTPException(503,'timeout')) as req:
            with self.assertRaises(HTTPException):a.handle(meaning(text,title='Estudar'),OWNER,REQUEST,text,'America/Sao_Paulo')
            self.assertEqual(1,req.call_count);self.assertEqual(1,db.call_count)

    def test_previous_uncertain_and_completed_requests_never_contact_google(self):
        for old in ({'reply':'incerto','action_receipt':None},{'reply':'feito','action_receipt':{'tool':'google'}}):
            with patch.object(a,'previous',return_value=old),patch.object(a,'google_call') as req,patch.object(a,'accounts') as acc:
                text='Crie Estudar no Google'
                self.assertEqual(old,a.handle(meaning(text,title='Estudar'),OWNER,REQUEST,text,'America/Sao_Paulo'))
                req.assert_not_called();acc.assert_not_called()

    def test_complete_requires_unique_title_full_query_and_etag(self):
        text='Conclua Estudar no Google Tasks'
        target={'title':'Estudar','id':'task/id','list_id':'list/id','etag':'version'}
        for values,partial in (([],False),([target,target],False),([target],True),([{**target,'etag':None}],False)):
            with patch.object(a,'read',return_value={'items':values,'partial':partial}),patch.object(a,'google_call') as req,patch.object(g,'database') as db:
                try:a.handle(meaning(text,'complete',target='Estudar'),OWNER,REQUEST,text,'America/Sao_Paulo')
                except HTTPException:pass
                req.assert_not_called();db.assert_not_called()

    def test_complete_encodes_identifiers_and_uses_provider_version(self):
        text='Conclua Estudar no Google Tasks'
        target={'title':'Estudar','id':'task/id','list_id':'list/id','etag':'version'}
        with patch.object(a,'read',return_value={'items':[target],'partial':False}),patch.object(g,'database',return_value=[{}]),patch.object(a,'google_call',return_value={'id':'task/id','status':'completed'}) as req:
            a.handle(meaning(text,'complete',target='Estudar'),OWNER,REQUEST,text,'America/Sao_Paulo')
        self.assertIn('list%2Fid/tasks/task%2Fid',req.call_args.args[3])
        self.assertEqual('version',req.call_args.args[-1]);self.assertEqual({'status':'completed'},req.call_args.args[-2])

    def test_calendar_requires_both_explicit_times_and_uses_stable_insert_id(self):
        text='Crie Aula no Google Agenda amanhã das 19 às 20'
        with patch.object(g,'database') as db,patch.object(a,'google_call') as req:
            result=a.handle(meaning(text,service='calendar',title='Aula',start='2026-10-10T19:00:00-03:00'),OWNER,REQUEST,text,'America/Sao_Paulo')
            self.assertIn('início',result['reply']);req.assert_not_called();db.assert_not_called()
        def response(*args):return {**args[5]}
        with patch.object(g,'database',return_value=[{}]),patch.object(a,'google_call',side_effect=response) as req:
            a.handle(meaning(text,service='calendar',title='Aula',start='2026-10-10T19:00:00-03:00',end='2026-10-10T20:00:00-03:00'),OWNER,REQUEST,text,'America/Sao_Paulo')
        self.assertEqual('koi'+REQUEST.hex,req.call_args.args[5]['id'])
        self.assertIn('sendUpdates=none',req.call_args.args[3]);self.assertNotIn('attendees',req.call_args.args[5])

    def test_provider_mismatch_does_not_save_success_receipt(self):
        text='Crie Estudar no Google'
        with patch.object(g,'database',return_value=[{}]) as db,patch.object(a,'google_call',return_value={'id':'one','title':'Different'}):
            with self.assertRaises(HTTPException):a.handle(meaning(text,title='Estudar'),OWNER,REQUEST,text,'America/Sao_Paulo')
        self.assertEqual(1,db.call_count)

    def test_invented_calendar_duration_is_not_written(self):
        text='Crie Aula no Google Agenda amanhã às 19h'
        with patch.object(g,'database') as db,patch.object(a,'google_call') as req:
            result=a.handle(meaning(text,service='calendar',title='Aula',start='2026-10-10T19:00:00-03:00',end='2026-10-10T20:00:00-03:00'),OWNER,REQUEST,text,'America/Sao_Paulo')
        self.assertIn('horários de início e fim',result['reply']);db.assert_not_called();req.assert_not_called()

    def test_racing_claim_returns_previous_receipt_without_second_write(self):
        text='Crie Estudar no Google'
        old={'reply':'feito','action_receipt':None}
        with patch.object(a,'previous',side_effect=[None,None,old]),patch.object(g,'database',side_effect=HTTPException(409,'duplicate')),patch.object(a,'google_call') as req:
            result=a.handle(meaning(text,title='Estudar'),OWNER,REQUEST,text,'America/Sao_Paulo')
        self.assertEqual(old,result);req.assert_not_called()

    def test_tasks_reads_real_items_bounded_and_labels_list(self):
        row={'id':CONNECTION,'email':'fixture@example.com','scopes':[g.SCOPE['tasks']],'updated_at':'today'}
        with patch.object(g,'account',return_value=row),patch.object(a,'google_call',side_effect=[
            {'items':[{'id':'list','title':'Study'}]}, {'items':[{'id':'task','title':'Lesson','due':'2026-10-10T00:00:00Z','status':'needsAction','notes':'PRIVATE'}]}]) as req:
            result=a.read(OWNER,CONNECTION,'task_items','2026-10-10','2026-10-10')
        self.assertEqual('Lesson',result['items'][0]['title']);self.assertEqual('Study',result['items'][0]['list'])
        self.assertNotIn('PRIVATE',str(result));self.assertEqual(2,req.call_count)

    def test_calendar_start_only_means_one_day_not_eight_days(self):
        row={'id':CONNECTION,'email':'fixture@example.com','scopes':[g.SCOPE['calendar']],'updated_at':'today'}
        with patch.object(g,'account',return_value=row),patch.object(a,'google_call',return_value={'items':[]}) as req:
            a.read(OWNER,CONNECTION,'calendar','2026-10-10',None)
        self.assertIn('timeMax=2026-10-11T00%3A00%3A00',req.call_args.args[3])

    def test_mail_date_filters_only_recent_slice_and_reports_partial(self):
        with patch.object(g,'consult',return_value={'items':[{'subject':'Yesterday','date':'Fri, 09 Oct 2026 12:00:00 -0300'},
            {'subject':'Today','date':'Sat, 10 Oct 2026 12:00:00 -0300'}],'partial':False}):
            result=a.read(OWNER,CONNECTION,'mail','2026-10-10',None)
        self.assertEqual('Today',result['items'][0]['subject']);self.assertEqual(1,len(result['items']));self.assertTrue(result['partial'])


class GoogleChatTests(unittest.IsolatedAsyncioTestCase):
    async def test_chat_google_bypasses_local_task_and_second_generation(self):
        text='Quais tarefas Google tenho amanhã?'
        with patch('backend.app.core.auth.build_opener') as auth,patch('backend.app.api.routes.chat.existing_action',return_value=None),patch('backend.app.core.personal.existing',return_value=None),patch.object(a,'previous',return_value=None),patch('backend.app.api.routes.chat.interpret',return_value=meaning(text,'read')),patch.object(a,'accounts',return_value=[ACCOUNT]),patch('backend.app.api.routes.chat.direct_conversation') as local,patch('backend.app.api.routes.chat.generate') as model:
            auth.return_value.open.return_value=io.BytesIO(json.dumps({'id':USER}).encode())
            status,result=await call(token='Bearer fixture',body={'message':text,'request_id':str(REQUEST),'task_mode':'direct','contextual_mode':True})
        self.assertEqual(200,status);self.assertEqual('google',result['action_receipt']['tool'])
        local.assert_not_called();model.assert_not_called()

    async def test_google_retry_returns_receipt_before_charged_interpretation(self):
        with patch('backend.app.core.auth.build_opener') as auth,patch('backend.app.api.routes.chat.existing_action',return_value=None),patch('backend.app.core.personal.existing',return_value=None),patch.object(g,'ready',return_value=True),patch.object(a,'previous',return_value={'reply':'Já concluído','action_receipt':None}),patch('backend.app.api.routes.chat.interpret') as model:
            auth.return_value.open.return_value=io.BytesIO(json.dumps({'id':USER}).encode())
            status,result=await call(token='Bearer fixture',body={'message':'Crie Teste no Google Tasks','request_id':str(REQUEST),'task_mode':'direct','contextual_mode':True})
        self.assertEqual(200,status);self.assertEqual('Já concluído',result['reply']);model.assert_not_called()


class GoogleLedgerTests(unittest.TestCase):
    def test_previous_filters_owner_and_rejects_reused_identifier(self):
        row={'message_hash':g.digest('one\nAmerica/Sao_Paulo'),'phase':'started'}
        with patch.object(g,'database',return_value=[row]) as db:
            result=a.previous(OWNER,REQUEST,'one','America/Sao_Paulo')
            with self.assertRaises(HTTPException):a.previous(OWNER,REQUEST,'two','America/Sao_Paulo')
        self.assertIn('não foi confirmado',result['reply'])
        self.assertEqual('eq.'+str(OWNER),db.call_args.args[1]['user_id'])

    def test_completed_receipt_is_bound_to_owner_and_request(self):
        with patch.object(settings,'connections_encryption_key',SecretStr(Fernet.generate_key().decode())):
            row={'message_hash':g.digest('one\nAmerica/Sao_Paulo'),'phase':'done',
                 'response':g.seal(OWNER,'google-action:'+str(REQUEST),{'reply':'done','action_receipt':None})}
            with patch.object(g,'database',return_value=[row]):
                self.assertEqual('done',a.previous(OWNER,REQUEST,'one','America/Sao_Paulo')['reply'])
                for owner,request in ((UUID(int=99),REQUEST),(OWNER,UUID(int=99))):
                    with self.assertRaises(HTTPException):a.previous(owner,request,'one','America/Sao_Paulo')
