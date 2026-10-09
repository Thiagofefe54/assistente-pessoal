# V1.8 — Encaixar meu dia, 09/10/2026

Este pacote liga a agenda sincronizada do Android às tarefas da conta no painel
Rotina → Ferramentas → Conexões. Não altera o servidor nem contrata serviços.
Visual definitivo e Windows seguem adiados. Não significa que todo o roadmap terminou.

## O que entrou

- Consulta manual de agenda (até200 ocorrências/7dias, com sinal de truncamento)
  e tarefas da própria conta (loader existente, até500, token/RLS preservados).
- Roteiro do dia com horários, fontes e possíveis sobreposições. Tarefas concluídas
  ou arquivadas não ocupam horários.
- Escolha de hoje/amanhã e próximos dias, manhã8–12/tarde12–18/noite18–22,
  janelas de pelo menos15min e sugestão de encaixe. Hoje não sugere tempo passado.
- Duração estimada das tarefas selecionável15/30/60min. Não há duração real
  cadastrada; não contar isso como duração medida.
- Repetições diárias/semanais/mensais projetadas a partir da próxima data armazenada,
  com indicação explícita. Mensal31 usa o último dia disponível de meses menores.
  Nenhuma ocorrência é criada, reagendada ou concluída.
- Campo local aceita perguntas sobre horários hoje/amanhã, com período opcional,
  por exemplo “Koi, estou livre amanhã à tarde?”. Não é modelo de IA nem integração
  da agenda ao Chat; perguntas fora desse recorte pedem escolher o dia/período.
- Eventos iguais (título/início/fim/dia inteiro) agrupados para apresentação,
  mantendo origens e quantidade. Instância exata repetida não duplica bloqueios.
  Nenhum evento é removido no calendário. Grupos iguais de agendas diferentes
  aparecem uma vez no roteiro, com indicação de múltiplas ocorrências.
- Horários usam instantes/fusos das tarefas, eventos noturnos atravessam dias;
  datas de eventos de dia inteiro seguem UTC do CalendarContract.
  Feriados/dia inteiro são informativos, não bloqueiam o dia automaticamente.
- Agenda e tarefas têm horário da consulta. Falha de tarefas, dados inválidos,
  calendário truncado ou consulta de outro dia não confirmam janelas livres.
  Provider indisponível gera erro, não uma lista vazia que pareça disponibilidade.

## Limites e privacidade

Sem envio de eventos à IA, sem geração paga, sem gravação de tarefas/calendário,
sem nova permissão além da leitura já autorizada na V1.7. Consulta de tarefas
precisa de conta/conexão; não há novo cache deste cruzamento.
Hábitos sem horário continuam em Meu ritmo, sem inventar blocos para eles.
Não calcula deslocamento, duração real ou compromissos ainda não registrados.
Sem registro não significa disponibilidade garantida; revise antes de se comprometer.
Roteiro limitado visualmente20itens, conflitos8 e pendências sem horário10.
GoogleOAuth/Tasks/Gmail/Drive, bancos, hotword e automação independente do Android
permanecem pendentes. Assistente padrão e teste real offline da V1.7 ainda precisam
de conferência; este pacote não declara essas baterias concluídas.

## Verificação

46 testes unitários Android passaram (9 novos). Build final/lint aprovados; instalado no Poco por atualização, versão1.8/code10.
Teste físico CalendarPlanningLiveDeviceTest PASS1teste/1,055s: leitura de agenda
permitida + tarefas da conta + cálculo dos7dias. Não foi teste de interações visuais. Teste físico preparado exclusivamente de
leitura: agenda permitida + tarefas da sessão atual + cálculo dos7dias; não imprime
dados privados, não cria fixtures, não consome pontos. Sem alteração backend,
migração, segredo, plano ou reset.

Referência: [CalendarContract.Instances](https://developer.android.com/reference/android/provider/CalendarContract.Instances).

APK android/build/releases/Koiwai-1.8.apk SHA256
C1393157E8C790DCAADD54F44C1226498471ED399E841759D1F5A2BD73D86009.
Gradle encerrado para liberar RAM. Nenhuma geração paga/reset.

Lint final:0erros102avisos. Painel V1.8 recarregado no Brave com novos cartões
e checklist. Usuário confirmou consulta visual: “Sim, apareceu corretamente” para roteiro
e janelas após consultar agenda e cruzar tarefas. Não confirma todos os casos
de pergunta/duração/conflito nem estabilidade prolongada.
Servidor permanece V1.7; pacote não requer deploy backend.
