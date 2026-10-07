# Pack 1 — memória, diário e missões

## Como usar

**Rotina → Tarefas:** criar, editar, concluir, reabrir e apagar. Data e horário
opcionais; repetição diária, semanal ou mensal exige data. A conclusão de uma
tarefa repetida registra a ocorrência e avança uma data. Se estiver atrasada,
cada conclusão corresponde a uma ocorrência, sem pular dias automaticamente.
Na mensal, o dia é ajustado ao fim do mês quando necessário (31/01 → 28 ou
29/02 → 28 ou 29/03). As últimas dez conclusões aparecem no painel.
Sem notificações neste pack; elas serão implementadas no Pack 2.

**Memória → escolher um dia:** gerar resumo, conferir fontes e pedir sugestões
de lembranças. Há também um atalho no Chat para abrir o capítulo da conversa.
A geração é solicitada pela pessoa, não automática a cada mensagem.

O resumo considera somente mensagens do usuário sincronizadas no dia local
registrado em cada conversa. Não representa atividades fora do chat, não usa
as respostas da IA como evidência e precisa ser revisado. Cada tópico oferece
as fontes originais. O capítulo de hoje é parcial até o dia terminar.
Mensagens recebidas depois tornam o resumo desatualizado; a pessoa pode gerar
a versão atual. Se nada mudou, o servidor devolve a versão salva sem gastar
uma nova chamada ao modelo. Apagar resumo preserva histórico e lembranças.

Sugestões são rascunhos, limitadas a três por pedido. Revisar abre o editor;
Confirmar e salvar usa a memória existente. Descartar não cria nenhum fato.
Rascunhos não são persistentes e um novo pedido pode sugerir novamente o mesmo
assunto. Se houver várias fontes, a revisão mostra todas; a lembrança salva
fica vinculada à primeira fonte, conforme o modelo atual de memória.
Evitar dados sensíveis, senhas e chaves. O texto do dia é enviado ao Groq
quando a pessoa pede resumo ou sugestões; a interface explica isso.

## Limites e integridade

- Até 500 tarefas por conta, título de 160 caracteres e descrição de 2 mil.
- Até 500 mensagens/60 mil caracteres do usuário por capítulo. Se exceder,
  recusar a geração inteira em vez de salvar um resumo silenciosamente incompleto.
- Até oito tópicos no resumo, cada um com uma a cinco fontes existentes.
- Datas, horários e fusos guardados com as tarefas; sem alarme neste pack.
- Tarefas e resumos são online. Cache persistente e fila offline ficam no Pack 5.
- Falha de escrita pode ser ambígua: atualizar para conferir, sem repetição automática.
- Edição/exclusão compara updated_at. Conclusão usa bloqueio e versão observada
  na função complete_koi_task, impedindo concluir duas vezes com a mesma versão.
- A exclusão de uma tarefa apaga seus registros de conclusão; o diálogo informa.
- Separação por conta e RLS em todas as tabelas; nenhum service_role no app/backend.
- Resultados da IA são dados. Não podem criar tarefas, fatos ou autorizar ações.
- Validação de fontes evita referências inventadas; não garante que toda
  interpretação do modelo seja correta. A revisão humana continua necessária.

## Implementação

docs/sql/pack1_schema.sql é a referência da migração remota.
Tabelas koi_tasks, koi_task_completions e koi_daily_reports. Reports têm chave
(user_id,local_date), hash de fontes e updated_at para revisão em vez de duplicação.
Endpoint autenticado /api/v1/journal: GET/DELETE /{dia}, POST /summary e /suggestions.
Geração verifica de novo as fontes após a IA; sincronização concorrente e edição
em outro dispositivo impedem sobrescrever silenciosamente uma versão nova.

## Verificação nesta entrega

Registro provisório: banco aplicado e teste SQL com rollback aprovado; 30 testes
backend (incluindo acesso autenticado, fonte inválida, limite, cache e atualização
tardia). Compilação, testes Android, publicação e teste físico serão registrados
ao encerrar. Não interpretar este registro provisório como tudo publicado/testado.

PackOneLiveTest usa somente fixtures fictícias. O teste de diário reserva
1901-01-02 e recusa execução se já houver dados. Histórico remoto é imutável para
o cliente: limpar os dois UUIDs f34fc19d-df0b-4ec9-a3ee-1d240e02b011 e
f34fc19d-df0b-4ec9-a3ee-1d240e02b012 via conector do banco, restrito ao dia
reservado e conteúdo fictício. Depois executar cleanupLocalJournalFixture com
koiCleanupFixture=true para retirar apenas esses UUIDs do Room caso sincronizados.
Nunca ampliar DELETE do histórico para acomodar testes.
