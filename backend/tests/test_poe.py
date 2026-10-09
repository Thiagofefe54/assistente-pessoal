import io
import json
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from pydantic import SecretStr
from fastapi import HTTPException
from backend.app.core import ai
from backend.app.core.config import settings
from backend.app.core.intent import FORMAT


class PoeTests(unittest.TestCase):
    def setUp(self):
        self.config=patch.multiple(settings,ai_provider='poe',poe_api_key=SecretStr('fake-private'),
                                   groq_api_key=SecretStr(''),poe_model='GPT-OSS-120B')
        self.config.start();self.addCleanup(self.config.stop)
        self.network=patch('backend.app.core.poe.build_opener').start()
        self.groq=patch('backend.app.core.ai._completion').start()
        self.addCleanup(patch.stopall)

    def result(self,text='Olá 💜',status='completed',extra=None):
        data={'choices':[{'message':{'content':text},'finish_reason':'stop' if status=='completed' else 'length'}],
            'status':status,'output':[{'type':'message','role':'assistant',
            'content':[{'type':'output_text','text':text}]}]}
        if extra: data.update(extra)
        return io.BytesIO(json.dumps(data).encode())

    def test_reply_uses_poe_only_without_groq_key(self):
        self.network.return_value.open.return_value=self.result()
        self.assertEqual('Olá 💜',ai.reply('oi',[]))
        request=self.network.return_value.open.call_args.args[0]
        payload=json.loads(request.data)
        self.assertEqual('https://api.poe.com/v1/chat/completions',request.full_url)
        self.assertEqual('GPT-OSS-120B',payload['model'])
        self.assertNotIn('previous_response_id',payload)
        self.assertEqual({'reasoning_effort':'low'},payload['extra_body'])
        self.assertNotIn('fake-private',request.data.decode())
        self.groq.assert_not_called()

    def test_schema_and_output_bound_sent_and_validated_locally(self):
        value={'domain':'conversation','operation':'none','speech_act':'conversation','evidence':'fictício','device':None,'read_query':None,'read_filter':None}
        self.network.return_value.open.return_value=self.result(json.dumps(value))
        ai.generate([{'role':'user','content':'fictício'}],FORMAT,max_tokens=512)
        data=json.loads(self.network.return_value.open.call_args.args[0].data)
        self.assertEqual(512,data['max_completion_tokens'])
        self.assertIn('read_query',data['messages'][0]['content'])
        self.assertNotIn('response_format',data)

    def test_system_rules_and_schema_share_one_message_and_json_fence_is_validated(self):
        value={'domain':'conversation','operation':'none','speech_act':'conversation','evidence':'oi','device':None,'read_query':None,'read_filter':None}
        self.network.return_value.open.return_value=self.result('```json\n'+json.dumps(value)+'\n```')
        answer=ai.generate([{'role':'system','content':'Regra A'},
                            {'role':'system','content':'Regra B'},
                            {'role':'user','content':'oi'}],FORMAT)
        self.assertEqual(value,json.loads(answer))
        data=json.loads(self.network.return_value.open.call_args.args[0].data)
        systems=[m for m in data['messages'] if m['role']=='system']
        self.assertEqual(1,len(systems))
        self.assertIn('Regra A',systems[0]['content'])
        self.assertIn('Regra B',systems[0]['content'])
        self.assertIn('read_query',systems[0]['content'])

    def test_invalid_schema_does_not_execute_or_retry(self):
        for value in ('{}','{"domain":"unknown"}','```json\n{}\n```'):
            self.network.return_value.open.return_value=self.result(value)
            with self.assertRaises(HTTPException) as caught:
                ai.generate([{'role':'user','content':'fictício'}],FORMAT)
            self.assertEqual(502,caught.exception.status_code)
        self.assertEqual(3,self.network.return_value.open.call_count)

    def test_image_maps_to_configured_vision_model(self):
        self.network.return_value.open.return_value=self.result('Imagem fictícia')
        ai.generate([{'role':'user','content':[{'type':'text','text':'O que vê?'},
            {'type':'image_url','image_url':{'url':'data:image/jpeg;base64,FAKE'}}]}],model='old-groq-vision')
        data=json.loads(self.network.return_value.open.call_args.args[0].data)
        self.assertEqual(settings.poe_vision_model,data['model'])
        self.assertEqual('input_image',data['input'][0]['content'][1]['type'])
        self.groq.assert_not_called()

    def test_errors_and_timeout_never_retry_or_fallback(self):
        for code in (402,429,401,503):
            self.network.return_value.open.reset_mock()
            self.network.return_value.open.side_effect=HTTPError('https://api.poe.com',code,'fake-private',{},None)
            with self.assertRaises(HTTPException) as caught: ai.reply('oi',[])
            self.assertEqual(429 if code in (402,429) else 503,caught.exception.status_code)
            self.assertNotIn('fake-private',caught.exception.detail)
            self.assertEqual(1,self.network.return_value.open.call_count)
        self.network.return_value.open.side_effect=TimeoutError()
        with self.assertRaises(HTTPException): ai.reply('oi',[])
        self.groq.assert_not_called()

    def test_incomplete_or_refused_responses_are_not_success(self):
        for status,text in [('incomplete','Parcial'),('failed','Erro'),('completed','')]:
            self.network.return_value.open.return_value=self.result(text,status)
            with self.assertRaises(HTTPException) as caught: ai.reply('oi',[])
            self.assertEqual(502,caught.exception.status_code)

    def test_web_requires_executed_search_and_verified_sources(self):
        self.network.return_value.open.return_value=self.result('Sem pesquisa')
        with self.assertRaises(HTTPException): ai.generate([{'role':'user','content':'fictício'}],web=True)
        data={'output':[{'type':'web_search_call','status':'completed','action':{'sources':[{'url':'https://example.com/source'}]}},
            {'type':'message','role':'assistant','content':[{'type':'output_text','text':'Resultado'}]}]}
        self.network.return_value.open.return_value=self.result(extra=data)
        answer=ai.generate([{'role':'user','content':'fictício'}],web=True)
        self.assertIn('https://example.com/source',answer)
        payload=json.loads(self.network.return_value.open.call_args.args[0].data)
        self.assertEqual('required',payload['tool_choice'])

    def test_missing_poe_key_fails_even_if_groq_available(self):
        with patch.multiple(settings,poe_api_key=SecretStr(''),groq_api_key=SecretStr('fake-groq')):
            with self.assertRaises(HTTPException) as caught: ai.reply('oi',[])
        self.assertEqual(503,caught.exception.status_code)
        self.network.assert_not_called();self.groq.assert_not_called()
