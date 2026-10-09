# Organização V1.5 — 08/10/2026

## Implementado

- **Memória → Buscar lembranças:** busca por palavras e período (7, 30 ou 90 dias
  no app; até 366 dias na API/chat). Trechos originais, tipo de fonte e data,
  quando ela existe. Abrir conversa, lembranças ou a área do registro de origem.
  São até 12 resultados, 300 mensagens do usuário no período, 20 lembranças
  confirmadas e 2.000 registros. Lista parcial é avisada. Não é busca semântica
  em todo o passado. Fatos e notas sem data não recebem datas inventadas.
- **Home → Plano do dia:** horários reais de hoje, depois prioridades entre
  tarefas atrasadas e sem horário. Exclui concluídas, arquivadas e futuras.
  Até 12 horários e 8 prioridades, consulta até 500 tarefas. Não altera registros
  nem estima duração, deslocamento ou disponibilidade.
- **Finanças e Orçamento:** alimentação, transporte, casa, saúde, estudos,
  lazer, trabalho e outros. Editor e filtro por categoria; limite geral ou por
  categoria. Cálculo e aviso de 80% usam apenas os gastos da categoria escolhida.
  Despesas futuras/arquivadas não entram. Registros antigos sem categoria
  continuam Outros; orçamento antigo continua geral. Pagamentos de contas
  ainda criam despesas em Outros; podem ser classificados editando a despesa.
- **Config. → Laboratório da Koi:** criar 12 exemplos marcados Teste: nove
  registros (diário atual/anterior, despesas, receita, orçamentos, nota, conta),
  duas tarefas e uma lembrança explicitamente fictícia. Valores entram nos totais.
  Cada exemplo tem ID/recibo estável por conta e versão. Repetir retorna os
  recibos sem duplicar, sobrescrever edições ou restaurar algo desfeito. A data
  inicial é recuperada do recibo; outra data/fuso não reinicia os exemplos.
  Falha parcial é informada e permite continuar. Não é uma transação única
  para o conjunto; cada exemplo usa a RPC atômica já existente.
- **Chat:** interpretação pode selecionar busca por assunto/período, plano
  do dia e gastos por categoria. Depois lê dados sem segunda geração. Schema,
  datas e domínio/operação validados. História não autoriza novas escritas.
- Editor preserva detalhes existentes ao editar um registro ou lista.
- Dashboard interativo atualizado; checklist própria da V1.5.

## Custos, permissões e dados

Os quatro novos botões não geram IA nem gastam pontos Poe. No chat, a
interpretação ainda pode consumir pontos. Testes de servidor usam mocks,
sem chamadas pagas. Nenhum plano, segredo ou permissão Android foi alterado.

Endpoints autenticados `/assistant/search`, `/assistant/plan`, `/assistant/demo`
usam o dono verificado e seu token original, com RLS/RPC existentes.
App exige HTTPS e rejeita redirecionamentos; descarta resultados se a conta mudar.
Nenhum identificador de outra pessoa é aceito do formulário. A criação de Teste
é manual, nunca ocorre só por abrir Configurações. Não houve reset.

Migração `organization_finance_categories` aplicada no Supabase. Apenas amplia
o validador de `details`; preserva tabelas, concessões e regras por dono. Ensaio
real `organization_v15_verify.sql` passou criação/edição/desfazer, valores inválidos,
legado e isolamento entre duas contas; toda a transação foi revertida.
Ensaio real adicional `organization_v15_demo_verify.sql` confirmou os 12 exemplos
(nove registros, duas tarefas, uma lembrança) e repetição sem duplicação usando
as RPCs atuais. Também foi revertido.
Avisores: proteção de senha vazada continua desativada (aviso preexistente);
índice `life_owner_kind_date` ainda sem uso observado (informativo, preservado).

## Evidências e estado

Servidor publicado: commit `771a18914b604459baae3140374af40954b4b5d6`,
Render `dep-db45c4rbc2fs73am4n9g` LIVE em 08/10/2026 23:49:45 (São Paulo).
Saúde HTTPS 200/provider poe. Novos endpoints search/plan/demo recusaram pedidos
sem sessão com HTTP401. GitHub atualizado; publicação automática permanece desligada.
Processo Gradle encerrado ao finalizar para liberar memória do PC.
Dashboard: navegação, detalhes e novo exemplo de busca verificados no Brave;
checklist permanece 0/5 para a conferência manual das telas pelo usuário.

- 149 testes do servidor aprovados, incluindo 12 novos; zero geração paga.
- Compilação final Android 1.5/code7 aprovada: assembleDebug, assembleDebugAndroidTest,
  30 testes unitários (zero falhas/erros), lintDebug (zero erros, 81 avisos).
  APK: android/build/releases/Koiwai-1.5.apk. SHA256:
  D4F9A92D9102D95353ED67D5AD9B9C03957456C1B3003CC75D87788308E15573.
- Poco reconectado: APK atualizado com install -r, confirmado versionName1.5/code7.
- `OrganizationApiLiveTest` APROVADO no Poco: 2 testes em 20,74s. Conferiu
  cálculo por categoria; criou 12 exemplos com sessão existente; repetiu sem
  duplicação; pesquisou o lanche por ID original; consultou plano do dia.
  Sem geração IA, exportação de token, reset ou alteração de permissões.
- Exemplos Teste persistem na conta por autorização do usuário. Manifesto com
  IDs/recibos guardado no arquivo privado de preferências koi-demo do app,
  separado por conta. Não publicar esse manifesto nem apagá-lo na limpeza.
- Teste usa o mesmo cliente dos painéis; não equivale à avaliação visual de
  todas as telas. Conferência manual dos cartões ainda pendente.

## Próxima conferência no celular

Instalação e teste de API concluídos. Agora verificar Home → Plano do dia, Memória → Teste Lanche e Rotina →
Orçamento (alimentação e total). A limpeza final deve usar IDs do manifesto,
sem apagar registros reais ou edições indiscriminadamente.

## O que continua faltando

Busca semântica mais ampla e acompanhamento de mudanças; planejamento com
disponibilidade e ações em lote; conexão real Google/Inter/nextJoy/ChatGPT;
gatilhos e entrega proativa na nuvem; voz própria/assistente padrão; testes reais
de imagem/pesquisa no Poe; revisão visual definitiva e integração Windows.
Nenhum desses itens foi anunciado como concluído pela V1.5.
