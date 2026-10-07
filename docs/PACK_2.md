# Pack 2 — uma Koi que ajuda a agir

Checkpoint de 07/10/2026. Android primeiro, orçamento R$0, identidade aprovada preservada.
Código implementado e APK preparado. Backend 06c003c publicado no Render Free,
Live no deploy dep-db3akiflk1mc73a03phg. Health HTTPS 200; rotas de relatórios e
desfazer sem sessão 401; demo em produção 404; logs recentes sem erros de aplicação.
APK instalado no Poco por atualização, código 1537df2, dados preservados.
Cinco testes de persistência passaram; novas ações/telas/relatórios ainda exigem
validação funcional no aparelho. O acabamento posterior à publicação mudou
somente Android/documentação.

## Entrega

| Necessidade | Implementação |
| --- | --- |
| Consultar a rotina | Chat consulta tarefas atuais da conta, datas, horários, repetições e estado. Até 60 tarefas no contexto; procura por título prioriza o alvo, mesmo antigo. |
| Agir sem perguntas repetidas | Um pedido claro cria, conclui, reabre, altera título/notas/data/horário/repetição ou arquiva uma tarefa. Pergunta somente quando precisar esclarecer. |
| Corrigir um engano | Botão Desfazer na resposta ou pedido pelo chat. Só restaura se a tarefa não mudou depois. Arquivar é reversível; apagar definitivamente continua na Rotina com confirmação. |
| Evitar duplicações | UUID do envio e recibo atômico na nuvem. Repetir o envio recupera o resultado; não cria outra tarefa ou avança outra ocorrência. Desfazer pelo chat também tem identidade própria. |
| Lembrar na hora | Lembretes locais opcionais, permissão Android, descanso, Adiar 15 min e Concluir. Mudança confirmada no chat invalida a programação antiga e atualiza a Rotina. |
| Reunir a história | Memória → Relatórios: semana, mês, semestre e ano; cobertura parcial, tópicos e fontes clicáveis. Dias alimentam semanas/mês, meses alimentam semestre/ano. |
| Preparar aos poucos | Trabalho persistente no Android, internet necessária, três passos por execução, continuação após cerca de 15 min. Pode acompanhar/interromper, sair da tela e continuar depois. |
| Entregar quando pronto | Preparação automática opt-in em Configurações. Verificação aproximadamente diária, até três períodos por rodada, notificação privada e descanso respeitado. |

Exemplos de pedidos: “Crie estudar amanhã às 9h”, “Já terminei estudar”,
“Mude estudar para amanhã às 10h”, “Reabra estudar”, “Arquive estudar”,
“Desfaça a última ação”. Um pedido por mensagem; dois alvos iguais pedem
esclarecimento. Datas ISO e DD/MM/AAAA e hoje/amanhã/ontem orientam a consulta.
O modelo interpreta linguagem; não há garantia de compreender toda formulação.
Relatórios são preparados na área Memória; geração de relatório pelo chat ainda
não é uma ferramenta disponível.

## Fontes e calendário

Semana de segunda a domingo; meses civis; janeiro–junho/julho–dezembro; ano civil.
O ano inicial inclui apenas registros reais, mesmo começando em outubro.
Se o semestre final tiver exatamente os mesmos dados do primeiro ano parcial,
a entrega automática prefere o anual; o semestral permanece disponível sob demanda.

Somente mensagens do usuário já sincronizadas alimentam os capítulos. Mensagens
que chegam tarde invalidam a síntese anterior por contagem/última sequência/hash.
Ao encerrar um período, uma síntese parcial anterior deve ser atualizada. Datas
futuras não entram na cobertura. Fontes do ano levam ao mês, do mês ao dia, e do
dia à conversa original. Resumos são interpretações da IA dos relatos, não prova
de atividades realizadas fora do app.

Cada chamada prepara no máximo uma síntese. Cache atual evita nova chamada à IA.
Dia: até 500 mensagens/60 mil caracteres e oito tópicos. Período: até 16 tópicos,
500 caracteres por tópico, 1–5 capítulos por fonte e 120 mil caracteres de entrada.
Nenhuma síntese truncada é salva como completa. Recuperação automática cobre ano
atual e anterior; períodos mais antigos podem ser preparados manualmente.
Apagar um relatório não apaga fontes. Este celular suspende sua recriação
automática até novo preparo manual; essa preferência não é sincronizada entre celulares.

## Dados e autorização

Room 4 adiciona o recibo da ação sem apagar conversas/propostas. Metadados do
botão Desfazer ficam no histórico deste celular; recibos reais ficam na nuvem.
Conta validada no backend; SQL SECURITY INVOKER, RLS por proprietário, nenhuma
chave privilegiada. Versões do servidor protegem contra alteração concorrente.
Recibos guardam o estado antes/depois para recuperação e desfazer; atualmente
não há retenção automática. Pedidos citados, hipóteses e negações não são ordens.

Migrações remotas aplicadas: pack2_reversible_actions, pack2_period_reports,
pack2_completion_guard, pack2_undo_request e pack2_completion_index. SQL em
docs/sql/ é documentação dessas migrações, não script para reaplicar em produção.

## Evidências deste checkpoint

- Backend: **58 testes aprovados**.
- Android local: **19 testes aprovados**, APK e APK de testes compilados.
- Poco: **ChatPersistenceTest, 5 testes aprovados em 0,278 s**, depois da atualização.
- Lint: **0 erros / 55 avisos**, incluindo recomendações de estilo/KTX; não afirmar zero avisos.
- SHA256 do APK: `60cb5fe8dd335996cb8764c634fbe3e67b42beda7a0e6846fc57581d5095385f`.
- SQL fictício com rollback aprovado: dono, criação/conclusão repetidas,
  avanço mensal no ano bissexto, desfazer repetido, versão antiga recusada,
  arquivar/recuperar, tarefa arquivada impedida de concluir, inventário e relatórios
  isolados por conta. Nenhuma fixture dessa verificação ficou persistida.
- Groq real, somente dados fictícios e escritas simuladas: criação, conclusão e
  mudança de data/horário aprovadas na última sequência. Identificado/corrigido
  HH:mm:00 sem mudar minutos. A cota 429 interrompeu a primeira sequência.
  Checagem posterior: arquivar e relatório com fontes fictícias também aprovados.
  Não houve troca automática de modelo para contornar cota. Escritas simuladas
  não substituem teste de ação real no Poco.
- Advisor segurança: apenas aviso já existente de proteção contra senhas vazadas
  desativada. [Explicação do Supabase](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).
- Advisor performance: índice da FK de conclusões acrescentado; pode aparecer
  inicialmente como não usado. [Explicação do advisor](https://supabase.com/docs/guides/database/database-linter?lint=0005_unused_index).

## O que falta validar no Poco

Atualização já instalada com adb install -r, mantendo conta e histórico.
Usuário reconectou brevemente e depois desconectou para ir à escola.
O teste de persistência passou; os demais não foram executados. APK de testes
mais recente contém PackTwoLiveTest/PackTwoNavigationTest, ainda não reinstalado.

1. Concluído: persistência/migração Room 4, recibo e desfazer sem perder conversa
   no banco fictício do teste. Não equivale a concluir/desfazer na nuvem pelo chat.
2. Criar uma tarefa fictícia pelo chat sem Revisar; concluir, alterar horário,
   reabrir, arquivar e desfazer; conferir Rotina depois de cada resultado.
3. Reconexão/repetição do mesmo envio; repetição de tarefa avança uma vez.
4. Títulos iguais: esclarecer sem alterar a tarefa errada.
5. Preparar relatório com mensagens fictícias, ler fontes, cache e atualização.
6. Notificação do relatório abre o período certo; silêncio, exclusão e logout
   não deixam aviso antigo. Desativar automático cancela trabalhos automáticos.
7. Tocar nas ações de lembrete, reiniciar e observar atrasos reais do Android.

Teste físico anterior TaskRemindersLiveTest aprovou entrega/descanso/deduplicação/
programação de adiamento/conclusão; a espera de 15 min foi antecipada pelo teste.
Isso não aprova automaticamente estas novas telas e ações nem confiabilidade prolongada.

## Próximos objetivos

- P2.1: validar os fluxos acima no APK já instalado; corrigir problemas encontrados.
- P2.2: observar uso real, recuperação após reinício e cotas. Pack 2 permanece
  pendente de validação física, sem declarar versões inteiras concluídas.
- P3: voz por botão, interrupção e personalidade contextual.
- P4: notas/agenda/objetivos/treinos/finanças reais e ferramentas verificáveis.
- P5: cache/fila offline, recuperação de conta, privacidade, retenção e uma semana
  de uso confiável. Windows depois dos packs Android.

Render Free ainda dorme; nenhum cron pago, modelo local ou keepalive artificial.
WorkManager depende do Android e do celular ligado, pode atrasar e não é alarme
exato. Falhas/cotas exigem retomar; não há execução contínua garantida 24/7.
V1: usuário pediu começo limpo nessa etapa futura. Combinar escopo antes; não
apagar contas, conversas, lembranças, tarefas ou configurações agora.

Testes preparados: executar PackTwoLiveTest com koiLiveBackend=true, um método
por vez; não chamar todos os testes reais em lote e estourar cotas. Ações usam
fixture PACK2_TEST_B301, dois recibos protegidos por guarda e tarefa removida
no finally. Relatório usa dia fictício reservado 1901-02-03, guardado contra dados
existentes; não gera lembranças. Depois executar docs/sql/pack2_fixture_cleanup.sql
e cleanupLocalReportFixture com koiCleanupFixture=true, conferindo zero restos.
Não ampliar DELETE público de mensagens para limpar fixture. Nenhuma dessas
fixtures funcionais foi criada neste checkpoint, pois o usuário desconectou.
