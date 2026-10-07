# Revisão do projeto e ligação entre conversa e tarefas

07/10/2026. Motivação: uma tarefa criada em Rotina não era conhecida pelo chat;
a Koi também não podia preparar tarefas a partir de pedidos na conversa.

## Revisão e correções implementadas

Revisados os fluxos do backend (conta, IA, memória, diário), Android (conta,
armazenamento, sincronização, navegação, tarefas e memória), configuração de
build/deploy, permissões e políticas do banco. Isto não equivale a uma auditoria
independente ou à garantia de ausência de defeitos.

- Chat consulta tarefas atuais com o token do dono verificado e RLS. Não usa uma
  cópia da lista no histórico nem uma chave privilegiada. Consulta indisponível
  bloqueia a resposta, evitando declarar uma lista vazia por falha de conexão.
- Data atual e fuso do aparelho são fornecidos à IA. Hoje/amanhã não dependem de
  uma data antiga na conversa. Datas, horários e repetição da proposta são validados.
- Resposta estruturada com texto e proposta opcional. O backend não grava tarefas.
  A proposta pede revisão; só Salvar tarefa usa o caminho de escrita já protegido.
- Propostas ficam na conversa local com migração Room 2→3, sem apagar histórico.
  Revisar permite editar antes de salvar; descartar não apaga a conversa. Metadados
  da proposta ainda não sincronizam entre dispositivos; o texto da conversa sim.
- Identidade estável por proposta e botão bloqueado durante o envio evitam duplicar
  uma criação após falha de conexão/repetição. Se o registro já existe, a tentativa
  não o sobrescreve e orienta consultar Rotina.
- Editor de tarefas fica aberto em caso de falha; só fecha após gravação confirmada.
  A criação manual também conserva a identidade durante as tentativas no editor.
- Filtro Hoje em Rotina respeita o fuso da tarefa. Tarefa sem data ou concluída
  não entra nesse filtro. Hora opcional continua valendo para identificar vencimento.
- Sucesso confirmado da escrita é separado de falha ao atualizar a lista depois.
- Memória encerra seu trabalho ao trocar de conta. Ativar a mesma conta novamente
  não recria sincronizadores/componentes desnecessariamente.
- Configurações deixou de afirmar que o chat é simulado e depende do PC. Explica
  nuvem, despertar do serviço gratuito e dados enviados ao provedor de IA.
- Resposta de autenticação tem limite de leitura; fusos funcionam também no
  ambiente Windows de desenvolvimento via dependência tzdata fixada.

## Limites reais

Leitura online de até 500 tarefas por conta, com consulta paginada. A IA recebe no
máximo 60 títulos/datas/horários/repetições/estados, priorizando hoje, amanhã ou uma
data AAAA-MM-DD explícita. Recebe contagens e indicação de lista incompleta. Não
recebe as notas longas da tarefa; em listas grandes, abrir Rotina para a lista toda.
Esse limite reduz consumo de tokens; não implica acesso a todo o diário.

Criar por chat significa preparar e revisar uma tarefa por vez. A IA pode interpretar
mal uma frase; conferir o editor é parte do fluxo. Ambiguidades devem virar pergunta.
Editar, concluir e apagar pela conversa ainda não estão implementados; funcionam em
Rotina. Proposta pendente não tem notificação nem vale como tarefa salva.

Lembretes/adiar/silêncio foram implementados após esta revisão, com validação
física pendente; ver REVISAO_FINAL_2026_10_07.md. Ainda faltam relatórios periódicos do Pack 2; voz do
Pack 3; agenda/notas/treinos/finanças reais e ações adicionais do Pack 4; recuperação
de conta, exportação/exclusão completa, cache/fila offline e acabamento do Pack 5.
Windows continua depois do celular. Sem contratação, cartão ou tráfego artificial.

## Verificação

36 testes backend aprovados: conta, limites, falhas, isolamento, memória, diário,
consulta de tarefas, paginação, dados incompletos e propostas inválidas.
Teste real no Groq com conteúdo fictício validou amanhã e horário 09:15, sem escrita.
RLS conferida ativa nas cinco tabelas e políticas restringindo o dono.
Backend publicado e Live no Render em 31197a6. APK dessa integração instalado
no Poco, sem apagar dados. Quatro testes de persistência/migração passaram e um
teste real de tarefas/chat validou consulta, proposta sem escrita, revisão e
repetição sem duplicação. Fixtures próprias removidas. Navegação passou isoladamente
após corrigir seletor ambíguo; uma execução agrupada posterior foi interrompida
durante outro teste. O teste novo do editor foi corrigido e recompilado, mas falta
executar sua versão final no aparelho. Atualizações adicionais e evidências estão
em REVISAO_FINAL_2026_10_07.md.
