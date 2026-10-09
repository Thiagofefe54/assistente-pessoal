# Google na Koi — V1.12

Primeira entrega: conectar até três contas Google separadas e consultar dados
limitados, identificando a conta de origem. Não usa pontos Poe.

## Estado verificável

- Implementado: OAuth WEB, sessão Koi, state/PKCE, cookie vinculado ao navegador,
  expiração de dez minutos e consumo único persistente. Renovação de tokens.
- Supabase: migração aplicada, RLS ligado, privilégios públicos removidos.
  REST anônimo bloqueado; chave privada do servidor consegue consultar as tabelas.
- Segredos: .env ignorado e Render, valores ocultos. Chave de cifragem deve ser
  preservada; gerar outra sem migração torna tokens existentes ilegíveis.
- Backend: 202 testes aprovados, incluindo contas alheias, replay, autorização
  recusada, proteção criptográfica, respostas malformadas e minimização Gmail.
- Android: 53 testes aprovados, build V1.12/code14 concluído. APK preparado;
  instalação e conferência no Poco pendentes, pois USB não estava disponível.
- Publicação em andamento; registrar commit/deploy confirmado no checkpoint.
- APIs Calendar/Tasks/Gmail/Drive ativadas e verificadas, após aprovação dos termos.
- Autorizações pessoais e consultas reais pendentes. Não há tokens pessoais
  Google gravados nem uso de dados reais Google nestes testes.

## Usar depois de instalar

1. Entre na conta Koi e abra Rotina → Ferramentas → Conexões.
2. Em Google com a Koi, toque Adicionar / reautorizar conta Google.
3. Escolha uma conta e revise os acessos pessoalmente no Google.
4. Volte à Koi e atualize a lista. Repita nas outras contas desejadas.
5. Consulte cada serviço e confira a conta indicada no resultado.

O modo Testing do Google pode exigir nova autorização após sete dias. Desconectar
na Koi remove seu vínculo e tokens; revogar a concessão Google é uma ação separada
nas configurações da conta. Não apaga arquivos, mensagens ou eventos Google.

## Limites desta entrega

Até 20 eventos futuros da agenda principal, 20 agendas, 20 **listas** Tasks,
cinco mensagens com assunto/remetente/data e 20 metadados de arquivos recentes.
Resultados indicam quando existem mais itens. Não lê corpos Gmail, não importa
Drive completo, não envia conteúdo ao modelo nem guarda cache Android.

Faltam: tarefas dentro das listas, criação/edição de eventos e tarefas Google,
rascunhos de e-mail, arquivos selecionados e uso dessas ferramentas pelo chat.
Outlook foi retirado do plano pelo usuário. Inter continua apenas consulta de saldo.

## Operação e segurança

O servidor filtra sempre por dono derivado da sessão Supabase validada; cliente
não escolhe dono. Identidades Google usam subject estável, não e-mail como prova.
Tokens Fernet incluem dono e finalidade. Atualização de token não recria vínculos
desconectados. HTTP externo tem timeout, limite de tamanho e recusa redirects.
Callback não imprime códigos/tokens, HTML estático, no-store/no-referrer/CSP.
Comando Render ajustado para evitar códigos OAuth nos logs HTTP do Uvicorn.

Auditoria Supabase indicou INFO de RLS sem políticas nas duas tabelas: proposital,
pois só o servidor privilegiado tem acesso. Aviso anterior de proteção de senhas
vazadas desativada não foi alterado nesta entrega. Não declarar auditoria sem avisos.
