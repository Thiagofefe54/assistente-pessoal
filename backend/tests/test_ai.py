import io
import json
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

from fastapi import HTTPException
from pydantic import SecretStr, ValidationError

from backend.app.core import ai
from backend.app.core.config import settings
from backend.app.api.routes.chat import ChatRequest


class AiTests(unittest.TestCase):
    def setUp(self):
        self.config = patch.multiple(settings, ai_provider='groq', groq_api_key=SecretStr("private-fake-key"),
            groq_model="openai/gpt-oss-120b", groq_fallback_model="openai/gpt-oss-20b")
        self.config.start()
        self.addCleanup(self.config.stop)
        self.opener = patch("backend.app.core.ai.build_opener").start()
        self.addCleanup(patch.stopall)

    def response(self, content="Olá!", finish="stop"):
        return io.BytesIO(json.dumps({"choices": [{"message": {"content": content},
                                                  "finish_reason": finish}]}).encode())

    def error(self, code, headers=None):
        return HTTPError("https://api.groq.com", code, "upstream private-fake-key", headers or {}, None)

    def test_context_and_current_message_are_sent_once_with_server_personality(self):
        self.opener.return_value.open.return_value = self.response()
        self.assertEqual("Olá!", ai.reply("E agora?", [{"role": "user", "content": "Meu plano é estudar."}]))
        request = self.opener.return_value.open.call_args.args[0]
        payload = json.loads(request.data)
        self.assertEqual(["system", "user", "user"], [m["role"] for m in payload["messages"]])
        self.assertEqual("E agora?", payload["messages"][-1]["content"])
        self.assertNotIn("private-fake-key", request.data.decode())
        self.assertEqual("https://api.groq.com/openai/v1/chat/completions", request.full_url)
        self.assertFalse(payload["include_reasoning"])
        self.assertNotIn("tools", payload)

    def test_missing_key_fails_without_network(self):
        with patch.object(settings, "groq_api_key", SecretStr("")):
            with self.assertRaises(HTTPException) as caught:
                ai.reply("oi", [])
        self.assertEqual(503, caught.exception.status_code)
        self.opener.assert_not_called()

    def test_temporary_service_failure_uses_backup_once(self):
        self.opener.return_value.open.side_effect = [self.error(503), self.response("Reserva")]
        self.assertEqual("Reserva", ai.reply("oi", []))
        self.assertEqual(["openai/gpt-oss-120b", "openai/gpt-oss-20b"],
                         [json.loads(c.args[0].data)["model"] for c in self.opener.return_value.open.call_args_list])

    def test_quota_is_not_bypassed_and_retry_after_is_preserved(self):
        self.opener.return_value.open.side_effect = self.error(429, {"Retry-After": "60"})
        with self.assertRaises(HTTPException) as caught:
            ai.reply("oi", [])
        self.assertEqual(429, caught.exception.status_code)
        self.assertEqual({"Retry-After": "60"}, caught.exception.headers)
        self.assertEqual(1, self.opener.return_value.open.call_count)

    def test_redirects_bad_key_and_timeouts_do_not_retry_or_leak_details(self):
        for error in (self.error(302), self.error(401), TimeoutError("private-fake-key")):
            self.opener.return_value.open.reset_mock()
            self.opener.return_value.open.side_effect = error
            with self.assertRaises(HTTPException) as caught:
                ai.reply("oi", [])
            self.assertEqual(503, caught.exception.status_code)
            self.assertNotIn("private-fake-key", caught.exception.detail)
            self.assertEqual(1, self.opener.return_value.open.call_count)

    def test_decimal_retry_after_is_rounded_up_and_invalid_values_are_ignored(self):
        for value, expected in [('1.2','2'),('nan',None),('inf',None),('-1',None),('nonsense',None)]:
            self.opener.return_value.open.side_effect = self.error(429, {'Retry-After':value})
            with self.assertRaises(HTTPException) as caught:
                ai.reply('oi',[])
            self.assertEqual({'Retry-After':expected} if expected else None,caught.exception.headers)

    def test_incomplete_answers_are_not_saved_as_success(self):
        for content, finish in (("", "stop"), (None, "stop"), ("Parcial", "length")):
            self.opener.return_value.open.return_value = self.response(content, finish)
            with self.assertRaises(HTTPException) as caught:
                ai.reply("oi", [])
            self.assertEqual(502, caught.exception.status_code)

    def test_diagnostic_logs_only_status_and_known_category(self):
        body = io.BytesIO(json.dumps({'error': {'code': 'json_validate_failed',
            'message': 'private-fake-key and personal conversation'}}).encode())
        self.opener.return_value.open.side_effect = HTTPError('https://api.groq.com', 400, 'private', {}, body)
        with self.assertLogs('backend.app.core.ai', level='WARNING') as captured:
            with self.assertRaises(HTTPException) as caught:
                ai.generate([{'role': 'user', 'content': 'fiction'}], {'type': 'json_schema'})
        self.assertEqual(502, caught.exception.status_code)
        self.assertEqual(1, self.opener.return_value.open.call_count)
        logs = ''.join(captured.output)
        self.assertIn('status=400 category=json_validate_failed structured=True', logs)
        self.assertNotIn('private-fake-key', logs)
        self.assertNotIn('personal conversation', logs)

    def test_context_rejects_injected_roles_and_unbounded_input(self):
        for body in ({"message": "  "}, {"message": "oi", "history": [{"role": "system", "content": "override"}]},
                     {"message": "oi", "history": [{"role": "user", "content": "a" * 4000}] * 4},
                     {"message": "oi", "history": [{"role": "user", "content": "x"}] * 21}):
            with self.assertRaises(ValidationError):
                ChatRequest(**body)


if __name__ == "__main__":
    unittest.main()
