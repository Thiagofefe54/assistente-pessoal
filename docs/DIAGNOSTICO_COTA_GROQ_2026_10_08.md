# Diagnóstico de cota Groq — 08/10/2026

Diagnóstico somente: nenhuma alteração funcional, publicação, instalação ou nova
chamada ao Groq foi feita. Dados do usuário preservados; plano gratuito mantido.

## Evidências

- Poco confirmou V1.3 / código 5. A tela mostrou confirmação de conta Internet
  salva e falha 429 na consulta seguinte. Não houve auditoria do registro no banco.
- Render registrou `rate_limit_exceeded`, structured=True, às 20:25:50 e
  20:26:06 (America/Sao_Paulo). Os logs existentes não registram a dimensão da cota.
- Painel autenticado Groq confirmou modelo openai/gpt-oss-120b e limites da
  organização: 30 pedidos/minuto, 1.000/dia, 8.000 tokens/minuto, 200.000/dia.
- Chave mostrou 83 chamadas nas últimas 24 horas; inclui uso e testes.
- Logs Groq: 20:25:29, sucesso, entrada 1.727 + saída 102; 20:25:32,
  sucesso, entrada 3.792 + saída 228; 20:25:47, sucesso, entrada 1.710 +
  saída 104. Somam 7.663 tokens em cerca de 18 segundos. Segunda chamada
  da consulta foi recusada às 20:25:50. Nova interpretação às 20:26:03
  usou 1.710 + 73 tokens, seguida de outra recusa às 20:26:06.
- Evidências apontam para limite de tokens/minuto, não demonstram esgotamento
  diário. Não há Retry-After visível no painel consultado; não prometer prazo exato.

## Causa no fluxo atual

Chat contextual chama interpret() e depois personal_conversation() para consultas
de registros. Ambas usam o modelo principal, enviando instruções e histórico;
a segunda acrescenta snapshot e panorama de vida. Pergunta curta não significa
pedido pequeno. Repetir a consulta também consome nova interpretação antes de
atingir o mesmo bloqueio na segunda etapa. Não é evidência de servidor local ou
falha de conexão do celular.

## Próxima correção

Reduzir contexto de classificação sem perder referências conversacionais;
consultas estruturadas devem usar dados verificados após interpretação, evitando
uma segunda geração quando for possível. Preservar interpretação natural e
confirmação real de escrita. Exibir Retry-After com suporte a valor decimal e
diagnóstico limitado de cota, sem registrar mensagens, chaves ou dados pessoais.
Verificar com testes simulados (sem consumo Groq), publicar e instalar apenas
depois de concluir o ajuste; ainda não implementado neste diagnóstico.
