import json
import unittest
from unittest.mock import patch
from uuid import UUID
from backend.app.core import actions, personal


class ReviewTests(unittest.TestCase):
    def test_vocative_questions_and_negated_now_are_not_authorization(self):
        for text in ('Koi, como criar uma tarefa?', 'Koiwai: como apagar tarefas?',
                     'Agora não crie uma tarefa', 'Mas não crie uma tarefa',
                     'Não quero de jeito nenhum que você crie uma tarefa hoje',
                     'Continue sem criar uma tarefa'):
            self.assertFalse(actions.explicit_intent(text,'archive' if 'apagar' in text else 'create'),text)
        self.assertTrue(actions.explicit_intent('Koi, crie uma tarefa estudar','create'))
        self.assertTrue(actions.explicit_intent('Não apague, mas crie uma tarefa estudar','create'))
        self.assertFalse(actions.explicit_intent('Não apague, mas crie uma tarefa estudar','archive'))

    def test_week_and_month_comparisons_respect_local_boundaries_and_archives(self):
        dates=('2026-09-27','2026-09-28','2026-10-04','2026-10-05','2026-10-11','2026-10-12')
        rows=[dict(id=str(UUID(int=i+10)),kind='income',title=f'Entrada {i}',content='',
                   amount_cents=100,progress=0,happened_on=d,archived_at=None,updated_at='v')
              for i,d in enumerate(dates)]
        rows.append(dict(rows[1],id=str(UUID(int=99)),amount_cents=99999,archived_at='v'))
        plan=dict(reply='Totais dos registros',action=None,target_kind=None,record_id=None,fields=None)
        with patch.object(personal,'cloud',side_effect=[[],[],rows]),patch.object(personal,'generate',return_value=json.dumps(plan)) as model:
            personal.personal_conversation('Compare minhas receitas',[],UUID(int=1),'Bearer fixture',UUID(int=2),
                                           'America/Sao_Paulo','2026-10-09T01:00:00+00:00')
        data=json.loads(model.call_args.args[0][1]['content'].split('\n',1)[1])
        self.assertEqual(200,data['financial_periods_cents']['last_week']['income'])
        self.assertEqual(100,data['financial_periods_cents']['this_week']['income'])
        self.assertEqual(200,data['financial_periods_cents']['this_month']['income'])
        self.assertEqual(200,data['financial_periods_cents']['last_month']['income'])
        self.assertEqual('2026-10-08T22:00:00-03:00',data['now'])

    def test_empty_personal_answer_is_rejected_without_mutation(self):
        plan=dict(reply='  ',action=None,target_kind=None,record_id=None,fields=None)
        with patch.object(personal,'cloud',side_effect=[[],[],[]]) as cloud,patch.object(personal,'generate',return_value=json.dumps(plan)):
            with self.assertRaises(ValueError):
                personal.personal_conversation('Consulte notas',[],UUID(int=1),'Bearer fixture',UUID(int=2),'UTC','2026-10-08T12:00:00+00:00')
        self.assertEqual(3,cloud.call_count)
