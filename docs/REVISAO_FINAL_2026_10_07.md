# Checkpoint: revisão geral, horários e lembretes

07/10/2026. Registro atual complementando os documentos históricos.

## O que temos e onde está

- Android com visual aprovado, conversa Groq, histórico local por conta, sincronização
  Supabase, lembranças revisáveis, diário e resumo diário com fontes, tarefas reais.
- Backend da integração Chat↔Tarefas publicado em 31197a6, Live no Render. Consulta
  atual com RLS e proposta validada; tarefa só é criada ao revisar e salvar no app.
- APK dessa integração instalado no Poco antes de o usuário sair. As melhorias
  abaixo estão preparadas em novo APK, ainda sem instalação/validação física.
- R$0 mantido. Lembretes locais não dependem de despertar o Render e não usam IA.
  A atualização periódica lê Supabase, sem tráfego artificial para manter servidor.

## Correções da revisão adicional

1. Horário destacado no editor, mesmo antes de escolher data. Escolher apenas hora
   preenche hoje, explicado na tela e revisável antes de salvar. Horários opcionais.
2. Editores de lembranças conservam o texto em caso de falha. Só fecham após confirmação.
   Sucesso ao salvar/apagar é separado de falha ao atualizar a lista depois.
3. Destino Supabase validado antes de acrescentar credenciais; HTTPS, mesma origem,
   sem redirecionamento. Respostas têm limites de leitura; paginação de histórico
   reduz respostas grandes. Propostas inválidas não são aceitas pelo cliente.
4. Descarte de proposta trata falha de armazenamento, evitando exceção sem aviso.
5. Lista de tarefas atualiza os lembretes antes de consultar conquistas: falha no
   histórico de conclusão não mantém um aviso de tarefa já removida/concluída.
6. Isolamento por conta e migrações preservados. Sessão continua cifrada via Keystore.
   Cache de lembretes é privado do app e excluído de backup/transferência Android.

## Primeira parte do Pack 2: implementada, teste no Poco pendente

Configurações oferece lembretes opcionais neste celular. Android 13+ pede permissão
somente ao ativar. Tarefas precisam de data e hora; só pendentes são programadas.
Alterar/apagar/concluir atualiza a programação após consulta confirmada. Tarefas
antigas descobertas depois de vencidas não causam uma sequência de avisos.

Notificação abre Rotina/Tarefas e oferece Adiar 15 min ou Concluir. Conclusão exige
internet e usa a mesma operação protegida por dono e versão. Nunca anuncia sucesso
sem confirmação; falha ambígua orienta atualizar. Repetição mantém regras do banco.
Conta mudou ou saiu: cancela trabalhos e avisos antigos. Desativar cancela lembretes.

Descanso padrão 22h–08h, configurável, usando horário do celular. Avisos durante o
descanso ficam para depois. Horários iguais desativam o intervalo. Conteúdo sensível
tem versão pública reduzida na tela bloqueada, sujeita às preferências do Android.

Entrega usa WorkManager, não um alarme exato. Android, economia de bateria, aparelho
desligado/forçado a parar ou permissão bloqueada podem atrasar/impedir o aviso.
Atualização remota é aproximadamente horária quando rede e sistema permitem;
uma edição feita em outro aparelho pode levar esse tempo para ser refletida.
Nenhum teste físico de precisão, reinício, descanso ou ações dessa nova entrega foi
concluído. Não usar como substituto de despertador/alarme crítico.

Referências: [permissão de notificações](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
e [agendamento do Android](https://developer.android.com/develop/background-work/services/alarms).

## Evidências

- Backend: 36 testes aprovados nesta revisão, incluindo conta/isolamento, memória,
  diário, paginação, tarefas e propostas. Groq real fictício validou amanhã/09:15.
- Android: 17 testes unitários aprovados, incluindo destino de credenciais, limite
  de resposta, validação de tarefa, horário de descanso, virada do dia e DST.
- APK principal e APK de testes compilados. Lint: zero erros, 45 avisos; 18 novos
  são sugestões SharedPreferences/KTX, incluindo gravação síncrona intencional
  do registro de lembretes. Não são prova de ausência de bugs.
- No Poco, antes da saída: 4 testes de persistência/migração e 1 teste real de
  Chat↔Tarefas passaram. Teste real confirmou que proposta não escreve e salvar
  duas vezes com mesma identidade não duplica. Fixtures próprias removidas.
- Teste de navegação teve seletor ambíguo corrigido. Teste do editor foi corrigido
  após bloqueio de atividade; a versão final compilou e ainda precisa rodar.
- APK final desta etapa não instalado, porque o celular foi desconectado pelo usuário.

## Próximos miniobjetivos

1. Atualizar Poco preservando dados e executar os testes finais do editor/navegação.
2. Ativar notificações com autorização na tela e testar missão fictícia: aviso,
   adiar, concluir, descanso, editar/apagar, permissão negada e troca de conta.
3. Testar retomada/reinício e atraso real com a economia de bateria do Poco.
4. Relatórios semanais/mensais/semestrais/anuais com cobertura parcial e fontes;
   definir entrega automática compatível com orçamento e limites de execução.
5. Packs seguintes: voz; notas/agenda/objetivos/treinos/finanças; offline ampliado,
   recuperação/exportação/exclusão e uso prolongado. Windows depois do celular.

Ainda faltam ações de editar/concluir/apagar tarefas pelo chat, relatórios periódicos,
voz e ferramentas pessoais além de tarefas. Gestão de keys pelo app não implementada.
Pack 2 e versão funcional completa não estão concluídos. Teste com PC realmente
desligado continua pendente, embora uso fora de casa tenha sido confirmado pelo usuário.

## Instalação e decisão de V1 — 07/10/2026

APK do commit e230382 instalado no Poco 14c3a88a via atualização, sem apagar conta
ou histórico. Testes finais TaskProposalEditorTest e PackOneNavigationTest passaram
isoladamente (1 + 1), após iniciar o app em primeiro plano. Uma tentativa anterior
de teste de tela ficou bloqueada ao iniciar em segundo plano e foi interrompida;
não representa aprovação. Usuário ativou lembretes e permitiu notificações na tela.
Teste físico TaskRemindersLiveTest aprovado (1 teste, 46,219 s). Confirmou entrega real pelo WorkManager, bloqueio no descanso, rejeição de dono/versão errados, deduplicação, programação de adiamento e conclusão protegida na nuvem. Reentrega foi antecipada pelo teste; não houve espera de 15 min. Apenas fixture própria removida e zero restante confirmado. Ainda faltam toque real nas ações, reinício e medição de atrasos prolongados.

Usuário quer um começo limpo/reset completo quando chegarmos à V1. Não é pedido
para apagar agora. Escopo de conversas, lembranças, tarefas e preferências deverá
ser combinado nessa etapa; não resetar contas/infraestrutura/credenciais por inferência.
