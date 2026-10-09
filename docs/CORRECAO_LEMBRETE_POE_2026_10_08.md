# Lembrete após migração Poe — 08/10/2026

Tela do Poco: conversa respondeu; "Me lembra de estudar amanhã às 19h." falhou
com aviso genérico 502. Render confirmou dois 502 às 21:34:31/21:34:46.
Poe atividade mostrou quatro chamadas API no minuto 21:34, custos 12,12,12,13:
49 pontos nesse minuto. Não há associação por request_id nessa interface; não
atribuir cada linha a uma etapa como fato. Geração pode ser cobrada mesmo quando
o servidor rejeita o formato. Logs antigos não guardaram categoria da validação;
não é possível afirmar o campo exato que falhou sem nova geração.

Correção:
- Pedido completo e explícito de um lembrete hoje/amanhã com horário pode ser
  resolvido localmente, sem classificação/geração paga. Usa instante original,
  fuso, autenticação, validação e o mesmo RPC/recibo idempotente existente.
- Frases compostas, negadas, citadas, condicionais ou ambíguas ficam no fluxo
  semântico. Não foi criado um parser permissivo para executar qualquer texto.
- Schema e regras enviados juntos em uma mensagem system. Alguns gateways só
  preservam uma; havia duas. Possível risco encontrado na revisão, não causa
  provada das duas falhas observadas.
- JSON em um bloco inteiro Markdown pode ser validado localmente. Não extrai
  fragmentos de prosa, não relaxa schema nem pede outra geração.
- Diagnóstico de validação registra só categoria/keyword do schema, sem valores.

124 testes backend simulados aprovados, incluindo o texto exato da falha, data
09/10/2026 às 19:00, zero chamadas IA e uma gravação autorizada simulada;
negações/hipóteses/citações/compostos, fuso/virada do ano e JSON cercado validado.
Zero chamadas pagas feitas durante esta correção. Nenhum dado do usuário apagado.
Publicado commit 77cf61f, Render dep-db4473rbc2fs73ai86pg LIVE, saúde 200/provider
poe. Verificação física solicitada ao usuário, ainda pendente. Não reenviar
automaticamente o pedido do usuário. Teste no celular após publicação pendente.
