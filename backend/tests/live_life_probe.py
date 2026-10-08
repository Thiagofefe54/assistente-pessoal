"""Explicit opt-in integration probe: fictional inputs, mocked DB, real Groq only."""
import sys
import json
from unittest.mock import patch
from uuid import UUID
from backend.app.core.intent import interpret
from backend.app.core import personal

def run():
    owner=UUID(int=1);now='2026-10-08T18:30:00-03:00'
    cases=[('Coi, crie uma conta de internet de 80 reais vencendo dia 10 de outubro de 2026 e repetindo todo mês','bill'),
        ('Defina meu limite de gastos de outubro de 2026 em 500 reais','budget'),
        ('Dormi ontem às 23 horas e acordei hoje às 7 horas','diary'),
        ('Paguei a conta de internet','payment')]
    for number,(message,expected) in enumerate(cases,1):
        if '--only' in sys.argv and expected!=sys.argv[sys.argv.index('--only')+1]:continue
        semantic=interpret(message,[],capture=True)
        last=[]
        original=personal.generate
        def model(*args,**kwargs):
            value=original(*args,**kwargs);last.append(value);return value
        def cloud(path,authorization,method='GET',payload=None):
            if method=='GET':
                if expected=='payment' and path.startswith('koi_personal_records?'):
                    return [dict(id=str(UUID(int=80)),kind='bill',title='Internet',content='',amount_cents=8000,progress=0,happened_on='2026-10-10',details={'repeat':'monthly'},archived_at=None,updated_at='2026-10-08T12:00:00Z')]
                return []
            if expected=='payment':
                assert path=='rpc/pay_koi_bill' and payload['occurrence_on']=='2026-10-10'
                return dict(request_id=str(UUID(int=number+10)),target_kind='record',action='create',record=dict(id=str(UUID(int=number+20)),title='Fixture',kind='expense'))
            assert path=='rpc/apply_koi_personal_action'
            assert payload['fields']['kind']==expected
            if expected=='bill':assert payload['fields']['details']['repeat']=='monthly' and payload['fields']['amount_cents']==8000
            if expected=='budget':assert payload['fields']['happened_on']=='2026-10-01' and payload['fields']['amount_cents']==50000
            if expected=='diary':assert payload['fields']['details']['category']=='sleep'
            return dict(request_id=str(UUID(int=number+10)),target_kind='record',action='create',record=dict(id=str(UUID(int=number+20)),title='Fixture',kind=expected))
        try:
            with patch.object(personal,'cloud',side_effect=cloud),patch.object(personal,'generate',side_effect=model):
                result=personal.personal_conversation(message,[],owner,'Bearer fixture',UUID(int=number+10),'America/Sao_Paulo',now,semantic,capture=True)
        except Exception:
            print('Fictional plan diagnostic:',semantic.model_dump(),last)
            raise
        assert result['action_receipt'] and result['action_receipt']['record_kind']==('expense' if expected=='payment' else expected)
        print('PASS fictional',expected,'no database writes')

if __name__=='__main__':
    if '--run' not in sys.argv:raise SystemExit('Use --run to opt into the real Groq probe.')
    run()
