# Koiwai V1.3 — contas, orçamento e diário

## Estado em 08/10/2026

Backend 843cd93af23705977b01938f0ee55e1ac7cb6146 publicado e Render Free Live,
deploy dep-db411ejl550s73c7i5ag concluído às 21:53:38 UTC (18:53:38 São Paulo).
Health HTTPS 200. Saudação autenticada do Poco passou em 4,881 s; esse teste
usa o protocolo direto anterior, não comprova sozinho os novos fluxos semânticos.
Aplicativo V1.3 / versionCode 5 instalado por atualização, sem reset/desinstalação.
Revisão final de navegação isolada por tela e identificadores de abertura incluída
no APK final e instalada. SHA256 do android/build/releases/Koiwai-1.3.apk:
83D113A4DF053A12B257E54BFA24594A6DC15C2BFC23E952797A2D5E769A6169.
Banco recebeu migração aditiva koi_life_pack.

## O que funciona neste pack

- Rotina → Contas: primeiro vencimento, valor, repetição única/semanal/mensal,
  navegação entre meses, editar e arquivar/recuperar. Mensal mantém o dia original:
  31 de janeiro vira último dia de fevereiro e volta a 31 em março.
- Já paguei registra a parcela e a despesa numa transação. Não é transferência
  nem pagamento no banco. Reenvios usam recibo; uma mesma parcela não é registrada
  duas vezes. Desfazer a criação da despesa remove também o marcador de pagamento.
  Alterações posteriores impedem desfazer uma versão antiga por segurança de dados.
- Chat interpreta criação de conta, orçamento e pagamento. Com captura ativada,
  “paguei a conta de internet” pode registrar pagamento. Alvo ou parcela ambígua
  pede esclarecimento. Valor do pagamento é o cadastrado na conta; pagamentos
  parciais/valores diferentes ainda não são suportados por esse fluxo.
- Rotina → Orçamento: limites do total de despesas por mês. Gastos futuros,
  receitas e contas previstas não são despesas efetivamente registradas até hoje.
  Limite não representa saldo bancário; vários limites não viram categorias.
- Rotina → Diário: acontecimentos por dia e categoria (trabalho, academia, sono,
  casa, estudo, outros), filtros da semana/sono, editar, arquivar e desfazer.
  Captura é opcional. Notas antigas permanecem em Notas; não foram convertidas.
- Sono: dois horários completos com fuso permitem calcular duração, inclusive
  atravessando a madrugada. Não inferir horário ausente. Intervalos máximos 48h.
- Balanço semanal no Diário: acontecimentos, gastos, tarefas concluídas na
  semana, pendências totais e média de intervalos de sono completos registrados.
  Relatórios narrativos anteriores continuam em Memória → Relatórios.
- Config. → Cuidados com seu dia: opt-in separado, desligado por padrão.
  Até um aviso por dia; contas dos próximos 3 dias/recém-vencidas até 2 dias,
  gasto ≥80% de um limite mensal (inclui limite zero quando houver gasto),
  e balanço do diário no domingo se houve acontecimentos. Privado na tela bloqueada.
  Horário de silêncio compartilhado com os lembretes. Android/WorkManager e internet;
  verificação periódica a cada 6h, conferência manual disponível, entrega pode atrasar.
  Nenhum agendador cloud independente nem garantia de horário exato.
- Capacidade ampliada de 200 para 2.000 registros incluindo arquivados, com leitura
  em páginas. 20 lembranças e 500 tarefas permanecem limites separados.
- Painel interativo atualizado, com exemplos fictícios e instruções de ativação.

## Validação

102 testes backend; 30 testes unitários Android; build/lint aprovados (há avisos).
6 testes de persistência no Poco e 1 saudação autenticada passaram após atualização.
SQL real, em transação revertida com usuários fictícios: conta mensal no dia 31,
pagamento, idempotência, parcela duplicada recusada, desfazer pagamento/despesa,
editar/desfazer recorrência, diário com sono, orçamento e isolamento entre donos.
Nenhum dado fictício dessa verificação ficou no banco. Funções são security invoker,
nova tabela de pagamentos tem RLS por auth.uid() e acesso de anônimo revogado.
Advisors não apontaram problema novo de RLS; permanece aviso preexistente de
[proteção contra senhas vazadas](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).

Groq real com banco simulado, zero gravações na conta: conta mensal, limite mensal,
sono e pagamento pelo relato passaram. Primeiro planejamento de conta omitiu valor
e foi recusado; repetição passou. Uma rodada atingiu 429; quota foi respeitada,
testes restantes executados após intervalo normal, sem trocar modelo para contornar.
Não garantir que toda paráfrase será interpretada corretamente.

Contas, balanço do Diário e Orçamento conferidos visualmente no Poco. APK final
instalado e 6 testes de persistência repetidos e aprovados. Entrega real de notificações e
formulários pelo toque ainda exigem teste manual; não foram ativados sem a pessoa.

## O que ativar e testar

1. Config. → Guardar relatos do dia para captura espontânea. Pedidos explícitos
   continuam funcionando sem ela.
2. Config. → Cuidados com seu dia e notificações Android para acompanhamento.
3. Criar conta fictícia com vencimento, marcar Já paguei, conferir Finanças e
   Desfazer. Não registrar como pagamento novamente se a parcela já está paga.
4. Definir limite mensal e conferir gastos; registrar sono com ambos horários.

## Pendências

Inter/nextJoy não conectados; não foram usadas credenciais bancárias, novas contas
ou serviços pagos. Google OAuth/ChatGPT, extratos, categorias de orçamento,
pagamento parcial, escuta por Koi/assistente padrão, voz própria, busca ampla no
passado, sugestões adaptativas e agendador cloud continuam etapas futuras.
O redesign completo continua adiado conforme a preferência do usuário.
