# Otimização antes do Poe pago — 08/10/2026

Implementado e validado localmente:

- Consultas simples de contas pendentes do mês, despesas, receitas, orçamento e
  acontecimentos da semana fazem uma interpretação por IA e consultam dados
  salvos diretamente. Duas chamadas passam a uma nesses casos; isso não significa
  redução de 50% dos tokens de todo o chat.
- Perguntas complexas, outros períodos, comparações e alterações continuam no
  fluxo de ferramentas. Interpretação natural permanece.
- Histórico da interpretação: dez mensagens completas recentes, orçamento de
  4.000 caracteres. Planejadores: 6.000. A última mensagem completa é preservada
  mesmo quando maior; pedido atual inteiro. Nenhum histórico salvo foi apagado.
- Personalidade resumida nos planejadores; regras de autorização, ambiguidade,
  validação e recibos preservadas. Conversa geral mantém personalidade completa.
- JSON compacto. Telemetria somente contagens válidas de tokens, sem mensagens
  ou credenciais. Economia real de tokens ainda não medida.
- Retry-After válido, inclusive frações, respeitado no aviso do servidor. Sem
  repetição automática ou contorno de cota. Android pode exibir aviso genérico.

112 testes backend simulados aprovados: uma chamada em consulta de contas,
cálculos em reais, fuso, arquivados/futuros, contas pagas antes do recorte,
diário/orçamento e contexto de tarefas. Nenhuma geração real nem ponto Poe.
Limite atual da leitura: 2.000 registros. Referências distantes podem precisar
de esclarecimento; histórico persistido permanece. Qualidade da seleção das
consultas ainda precisa de teste real com o provedor escolhido.

Poe mensal pago pelo usuário, ativo 10.000 pontos/dia. Ainda não integrado:
faltam credencial privada, adaptador Responses/JSON Schema e teste controlado.
Groq permanece até substituição testada. Nenhuma cobrança nova iniciada.
Publicado: commit 296b7b5, deploy dep-db432p7lk1mc73ekpmlg LIVE, saúde HTTP 200.
Android V1.3 sem alteração/reinstalação. Nenhuma conversa real de teste foi
enviada após deploy; integração Poe e medição de uso real continuam pendentes.
Texto de tom nas ferramentas: 2.816→288 caracteres; não equivale à redução
total do pedido, que ainda inclui regras, schema e dados.
