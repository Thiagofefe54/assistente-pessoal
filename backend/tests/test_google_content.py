import base64
import io
import unittest
from unittest.mock import patch
from uuid import UUID
from fastapi import HTTPException
from backend.app.core import google_content as c
from backend.app.core import google_connections as g
OWNER=UUID(int=1);CONNECTION=UUID(int=2)
def body(text,mime='text/plain',**extra):return {'mimeType':mime,'body':{'data':base64.urlsafe_b64encode(text.encode()).decode().rstrip('=')},**extra}
class ContentTests(unittest.TestCase):
    def test_mail_prefers_plain_text_and_never_renders_attachments(self):
        payload={'mimeType':'multipart/alternative','parts':[body('<p>HTML</p>','text/html'),body('Olá! 12 reais.'),body('PRIVATE attachment',filename='secret.txt')]}
        text,partial=c.mail_text(payload)
        self.assertEqual('Olá! 12 reais.',text);self.assertFalse(partial);self.assertNotIn('PRIVATE',text)
    def test_html_is_plain_inert_text_without_scripts_images_or_links(self):
        text,_=c.mail_text(body('<head>PRIVATE</head><script>PRIVATE</script><style>PRIVATE</style><p>Olá &amp; oi<img src="https://evil.example"><a href="https://evil.example">link</a></p>','text/html'))
        self.assertEqual('Olá & oilink',text);self.assertNotIn('PRIVATE',text);self.assertNotIn('evil',text)
    def test_text_limits_and_nested_parts_are_bounded(self):
        text,partial=c.mail_text(body('x'*20000));self.assertEqual(16000,len(text));self.assertTrue(partial)
        payload=body('nested')
        for _ in range(10):payload={'parts':[payload]}
        self.assertTrue(c.mail_text(payload)[1])
    def test_foreign_connection_and_missing_scope_fail_before_content_request(self):
        with patch.object(g,'account',side_effect=HTTPException(404,'foreign')),patch.object(g,'request_json') as req:
            with self.assertRaises(HTTPException):c.preview(OWNER,CONNECTION,'mail','abcd')
        req.assert_not_called()
        with patch.object(g,'account',return_value={'scopes':[]}),patch.object(g,'access_token') as token:
            with self.assertRaises(HTTPException):c.preview(OWNER,CONNECTION,'mail','abcd')
        token.assert_not_called()
    def test_arbitrary_id_never_becomes_a_request_url(self):
        with patch.object(g,'account') as account:
            for value in ('../secret','https://evil.example','a?token=b'):
                with self.assertRaises(HTTPException):c.preview(OWNER,CONNECTION,'drive',value)
        account.assert_not_called()
    def row(self,service):return {'id':str(CONNECTION),'email':'fixture@example.com','scopes':[g.SCOPE[service]],'updated_at':'today'}
    def test_google_doc_export_is_fixed_origin_and_does_not_mutate(self):
        with patch.object(g,'account',return_value=self.row('drive')),patch.object(g,'access_token',return_value='fixture'),patch.object(g,'request_json',return_value={'name':'Doc','mimeType':'application/vnd.google-apps.document','capabilities':{'canDownload':True}}),patch.object(c,'text_request',return_value=('Fictional text',False)) as text:
            result=c.preview(OWNER,CONNECTION,'drive','abcd')
        self.assertEqual('Fictional text',result['text']);self.assertEqual('https://www.googleapis.com/drive/v3/files/abcd/export?mimeType=text%2Fplain',text.call_args.args[0])
    def test_download_permission_and_unsupported_binary_are_not_bypassed(self):
        for info in ({'capabilities':{'canDownload':False}},{'mimeType':'application/pdf','capabilities':{'canDownload':True}}):
            with patch.object(g,'account',return_value=self.row('drive')),patch.object(g,'access_token',return_value='fixture'),patch.object(g,'request_json',return_value=info),patch.object(c,'text_request') as read:
                with self.assertRaises(HTTPException):c.preview(OWNER,CONNECTION,'drive','abcd')
            read.assert_not_called()
    def test_plain_download_is_bounded(self):
        with patch.object(c,'build_opener') as opener:
            opener.return_value.open.return_value=io.BytesIO(b'x'*100000)
            text,partial=c.text_request('https://www.googleapis.com/drive/v3/files/abcd?alt=media','fixture')
        self.assertEqual(16000,len(text));self.assertTrue(partial)
