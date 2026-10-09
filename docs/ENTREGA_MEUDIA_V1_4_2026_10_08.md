# Koiwai V1.4 — Meu dia e consumo do Poe

## O que entra neste pacote

- Home → Meu dia: tarefas pendentes de hoje com horário, receitas/despesas do mês,
  contas pendentes do mês e próximos sete dias. Detalhes recolhíveis mostram o diário
  de hoje, média dos intervalos de sono completos da semana e comparação de despesas.
- Config. → Consumo da IA: consulta do saldo do Poe com data/hora. Esse saldo é
  compartilhado com a conta Poe do servidor, inclusive o uso no site/app.
- Consultas de chat ampliadas: lembranças confirmadas, diário da semana passada
  ou do mês, comparação financeira semanal/mensal e resumo do dia.
- Comparações usam datas explícitas: período atual até hoje versus o mesmo trecho
  da semana/mês anterior. Dia 31 é limitado ao último dia do mês anterior.
- Mensagens de falha avisam que uma resposta inválida pode consumir pontos e
  orientam conferir saldo antes de reenviar repetidamente.

## Consumo e privacidade

Os cartões não chamam um modelo de IA. A consulta do saldo usa a
[API de uso do Poe](https://creator.poe.com/docs/resources/usage-api), sem leitura
do histórico de conversas/consumo. Cache de 60 segundos também em falhas evita
consultas repetidas ao provedor. Nenhuma chave sai do servidor.

As novas consultas naturais de chat normalmente usam uma interpretação de IA,
depois dados exatos, sem uma segunda geração. Não são todas gratuitas. O lembrete
explícito hoje/amanhã às HH:mm continua com zero gerações.

Todas as rotas novas exigem conta autenticada. O servidor verifica a sessão e usa
o token original ao ler Supabase; nenhum ID de dono enviado pelo cliente é aceito.
Não houve alteração de tabela, permissões, senhas, plano ou preço.

Valores financeiros são registros da Koiwai, não saldo de Inter/nextJoy. Listas
dos cartões são resumidas e sinalizam limites; até 500 tarefas, 2.000 registros
e 2.000 pagamentos carregados. Cartões atualizam ao tocar, com data visível;
alterações posteriores exigem nova consulta. Não apagam nem editam dados.

## Verificação desta entrega

- 137 testes backend passaram, incluindo comparação de datas/centavos,
  exclusão de arquivados/futuros/pagos, isolamento da sessão e cache de saldo.
- Compilação do APK e APK de testes, 30 testes unitários Android e lint passaram.
  Lint: zero erros, 80 avisos preexistentes/gerais; não afirmar código sem avisos.
- Backend publicado no Render: dep-db44jn7lk1mc73eqdd0g LIVE em 08/10/2026
  22:57:40 (São Paulo), commit 9571a26. Saúde 200/provider poe. V1.4/code6
  instalada no Poco com atualização, sem limpar dados.
- O primeiro teste de tela leu Meu dia e abriu a comparação; falhou ao buscar um
  botão ainda não composto na lista das configurações. A rolagem do teste foi
  corrigida. Rodadas seguintes ficaram presas na abertura de atividades no Poco;
  não foram aprovadas. Toques remotos recusados com INJECT_EVENTS; não foram
  alteradas proteções. Usuário abriu os dois cartões e confirmou: “Os dois
  funcionaram”. Isso valida o teste manual, não aprova a bateria de navegação.
- Teste direto do mesmo cliente Android dos cartões APROVADO no Poco: Meu dia
  autenticado e saldo Poe (1 teste, 3,217s). Sessão ficou dentro do app; nenhum
  token/dado pessoal foi exportado, nenhum registro foi criado ou alterado.
- APK final Koiwai-1.4.apk instalado: SHA256
  98D886B3C6F0A4449CE6257E01368F99BAEDB994DFC0767ADE4DEA34B0DAB76F.
- Nenhuma geração paga de IA foi usada nos testes deste pacote.
- As cinco frases anteriores foram aprovadas pelo usuário: conversa, criar tarefa,
  consultar, concluir e contas pendentes. Isso não prova toda paráfrase nova.

## Como conferir

1. Home → Meu dia → Conferir meu dia. Veja a data e compare com Rotina.
2. Abra Diário e comparação; confira as datas e os registros.
3. Config. → Consumo da IA → Consultar saldo. O horário indica quando foi medido.
4. No chat: “O que você lembra sobre mim?”, “O que registrei na semana passada?”,
   “Compare meus gastos deste mês com o anterior” ou “Como está meu dia?”.
   Essas frases podem consumir pontos na interpretação.

## O que ainda fica para os próximos pacotes

Google OAuth e leitura de dados, conexão bancária autorizada, categorias financeiras,
importação de extratos, pagamentos parciais, busca livre em todo o passado,
voz própria, chamada por Koi, assistente padrão e automações independentes do Android.
Imagens e pesquisa via Poe ainda precisam de validação real. Redesign completo e
Windows continuam posteriores. V1.4 é uma entrega incremental, não V2 definitiva.
