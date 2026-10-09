# V1.13 — Google no chat

## Implementado

- O intérprete escolhe Google para pedidos nessa integração, preservando tarefas
  locais da Koi quando Google não é mencionado. Datas relativas usam relógio/fuso.
- Chat com cartão privado: eventos, tarefas dentro de até três listas, cabeçalhos
  Gmail e metadados Drive, separados por conta. Conteúdo fica em memória da tela,
  não no histórico, no cache ou no prompt da IA. Cartões antigos só atualizam por toque.
- Google Tasks: criar na lista padrão, alterar título/vencimento por dia, concluir
  e reabrir tarefa identificada pelo título exato, sem duplicá-la na Rotina da Koi.
- Agenda principal: criar evento com início/fim explícitos e ajustar evento de
  título único encontrado nos próximos sete dias. Horários numéricos conferidos
  no pedido atual; não assumir uma hora de duração. Sem convidados/convites.
- Escrita identifica conta: com três contas, inclua o e-mail no pedido atual.
  Título também deve constar no pedido atual. Dúvidas, relatos, hipóteses, negações
  e dados externos não autorizam alteração. Gmail/Drive permanecem leitura.
- Organizar dia com Google: compara uma agenda principal com tarefas da Koi,
  aponta sobreposição e janelas sem evento entre 08h e 22h. Não muda tarefas nem
  estima sua duração. Outros calendários/atividades podem estar fora do recorte.
- Proteção de repetição: recibos privados/cifrados por dono + request_id. Claim
  único antes da escrita; resultado incerto não se repete automaticamente.
  Repetir o mesmo pedido recupera recibo antes de cobrar outra interpretação.
  PATCH exige ETag; consulta parcial/duplicidade não escolhe item arbitrariamente.

Datas solicitadas filtram o recorte: início sem fim significa somente aquele dia.
Gmail/Drive filtram resultados recentes disponíveis, sem busca completa.

## Verificado

- 226 testes backend aprovados; 53 testes unitários Android aprovados; build debug
  V1.13/code15 concluído. APK: android/build/releases/Koiwai-1.13.apk.
- SHA256: 901191FB965FACBB43444A527713903B30E4A1DC6E295736B6E198EDB570B7A3.
- Consultas reais Calendar/Tasks/Gmail/Drive nas três contas: todas bem-sucedidas.
  Registradas apenas contagens e status, sem conteúdo pessoal nos logs/Git.
- Dois testes reais de interpretação Poe, só com frases fictícias, escolheram
  Google/read/Tasks e Google/create/Tasks corretamente. Diferença observada no
  saldo: 35 pontos; não é medição isolada caso a conta tivesse uso concorrente.
  Nenhuma dessas interpretações executou escrita Google real.
- Tabela koi_google_actions aplicada no Supabase. RLS ligado; SELECT anon e
  authenticated negados, servidor com INSERT autorizado.
- Auditoria: INFO RLS sem políticas é intencional para três tabelas privadas com
  grants públicos removidos. Aviso de senha vazada anterior permanece:
  [documentação de proteção de senhas](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).

## Publicação e aparelho

PUBLICADO: commit 8bfbfc97c2b204372604a4fc55ab719860089a2c, Render
dep-db4kudid0e5s73coj2f0 LIVE em 09/10/2026 20:32:44Z (17:32:44 local).
Smoke HTTPS: health200, status/connect sem sessão401, callback inválido400.
APK preparado, NÃO instalado: usuário saiu e avisará quando reconectar o Poco.
Depois de instalar, conferir cartões e uma escrita fictícia escolhida pelo usuário.
Não afirmar validação real de escrita ou visual no Poco antes dessa etapa.

## Passeio de teste no app atualizado

- “Quais tarefas tenho no Google amanhã?”
- “Mostre minha agenda Google amanhã.”
- “Organize meu dia de amanhã com a agenda Google.”
- “Mostre meus e-mails recentes no Gmail.”
- “Crie Teste da Koi no Google Tasks meuemail@example.com para amanhã.”
- “Conclua Teste da Koi no Google Tasks meuemail@example.com.”
- “Reabra Teste da Koi no Google Tasks meuemail@example.com.”
- “Crie Teste da Koi no Google Agenda meuemail@example.com amanhã das 19:00 às 20:00.”

Troque o e-mail de exemplo pela conta desejada. Estes pedidos fazem escrita real
na conta escolhida. Não executados pelo agente neste trabalho.

## Ainda falta

Busca completa/paginação, escolha de lista/calendar específico, ações em itens
fora do recorte, eliminação/undo Google, rascunhos, corpo Gmail, conteúdo Drive,
conversa conectada ao ChatGPT e planejamento agregado de todas as agendas com
duração de tarefas. Google Tasks usa vencimento por dia, sem horário/alarme:
[recurso Task oficial](https://developers.google.com/workspace/tasks/reference/rest/v1/tasks).
Modo OAuth Testing pode requerer reautorização em sete dias. Outlook cancelado.
