# Expansão V1.7 — 09/10/2026

O usuário pediu avançar em inteligência, memória, proatividade, voz, integrações e
confiabilidade; reformulação visual e Windows ficam fora deste pacote.
Não significa que todas as possibilidades de uma assistente estão concluídas.

## Implementado neste pacote

### Inteligência e memória

Conversa contextual pode consultar até40 registros recentes ativos da própria conta
(diário/notas/metas/treinos) e incluir até3 trechos relevantes, com ID de fonte,
data e indicação de cobertura parcial. Seleção é uma busca lexical flexível;
não são embeddings ou compreensão infalível. Uma saudação simples não busca.
Não consulta finanças por fora das ferramentas já existentes nem todo o passado.
Registros com data futura são excluídos deste contexto de acontecimentos.
Teste/fictício permanece identificado; textos das fontes não autorizam ações.
A busca ganha grupos limitados de sinônimos (academia/treino, estudo/escola,
trabalho/emprego, sono/dormir, dinheiro/finanças), além de pequenas variações em
palavras longas. Números, valores e palavras curtas não recebem correção aproximada.
Trechos permanecem originais. Não é treinamento/fine-tuning do modelo.

A recuperação não faz nova geração de IA. Conversa ainda usa interpretação e
resposta, portanto consome pontos; contexto adicional pode aumentar a entrada.
Histórico de resposta é limitado a12 mensagens/6.000caracteres completos e
lembranças são serializadas de forma compacta, sem apagar o histórico armazenado.

### Confiabilidade

Meu dia, Meu ritmo, Plano do dia e Busca guardam a última resposta consultada para
usar após falha temporária de conexão/servidor. Exigem a mesma conta e sessão
utilizável. Cópia é marcada com data/hora e aviso de desatualização, vence em7dias;
até20 consultas/conta, em armazenamento privado excluído de backup.
Não guarda saldoPoe nem simula respostas de escrita, exemplos ou Desfazer.
401/404/409/422 não são mascarados pelo cache. Notificações exigem dados online;
Meu ritmo desativa ações sobre a cópia. Não é fila universal de edições offline.
Ações do próprio painel invalidam suas cópias. Alterações em outras telas ou
aparelhos podem deixar cópias antigas; o aviso deixa isso explícito.
Corrigida a lista da Rotina para manter Tarefas em destaque e também mostrar Meu ritmo.

### Proatividade e relatórios

Relatórios diários/semanais/mensais/semestrais/anuais já existiam com fontes,
cobertura e preparação por capítulos. Novo limite de pedidos automáticos por dia
neste celular/conta: padrão3, opções1/3/5. Consome o limite antes de enviar;
falhas de rede também contam. Não mede pontos ou tokens nem limita preparo manual.
Pedidos de capítulos podem gerar IA; avisos de rotina continuam sem geração.
A preparação pausada continua disponível no próximo ciclo do Android ou manualmente.
Não é um cron independente do telefone nem garante horário exato.

### Voz e assistente Android

Config. → Ajustar a voz: velocidade0,7–1,3, tom0,8–1,2 e até12 opções pt-BR do
motor instalado, identificadas como locais ou dependentes de rede. Teste de fala.
Qualidade depende do motor; não cria uma voz de personagem nem contrata serviço.
Captura prioriza reconhecimento local nativo no Android12+ quando disponível;
caso contrário pede preferência offline ao serviço usual, que pode usar rede.
Fala continua no campo para revisar; não envia sozinha nem escuta continuamente.
Config. → Escolher assistente padrão abre a escolha oficial do Android. A Koi
passa a tratar ACTION_ASSIST e abrir o Chat pelo gesto compatível. Disponibilidade
e escolha final dependem do aparelho; não é ativação automática ou hotword “Koi”.

### Integração de agenda

Rotina → Ferramentas → Conexões → Consultar agenda deste celular.
Nova permissão READ_CALENDAR, solicitada somente ao tocar; recusá-la não impede
usar a Koi. Até20 ocorrências nos próximos7dias, incluindo agendas Google que já
estejam sincronizadas no Android. Toque abre o evento original para revisar.
Dados ficam no painel local: não são enviados à IA, copiados ao Supabase ou
convertidos automaticamente em tarefas. Não pede WRITE_CALENDAR; o formulário
de criação existente continua sendo confirmado no aplicativo de calendário.

## Dependências ainda abertas

- Gmail, Tasks e Drive completos precisam de registroOAuth próprio da Koiwai,
  autorização e configuração das APIs. Conector do Codex não transfere acesso ao app.
  A agenda local acima não é loginOAuth Google nem consulta ao Gmail/Drive.
- Inter: documentação pública consultada descreve APIs para clientesPJ. Não foi
  identificada nesta pesquisa uma API gratuita pronta para a contaPF do usuário.
- nextJoy: fonte oficial descreve a conta, sem uma integração pública pessoal
  confirmada nesta pesquisa. Não afirmar indisponibilidade absoluta.
- Nenhum banco está conectado, nenhum saldo/extrato é lido ou pagamento efetuado.
  Registros financeiros manuais, contas, orçamento e comparações continuam funcionando.
- Escuta por “Koi”, voz de personagem, busca semântica em todo passado, fila completa
  offline, automação em nuvem e estabilidade prolongada seguem pendentes.
- Visual final e Windows permanecem adiados expressamente pelo usuário.

## Fontes primárias consultadas

- [Agenda Android](https://developer.android.com/reference/android/provider/CalendarContract.Instances)
- [Papel de assistente](https://developer.android.com/reference/androidx/core/role/RoleManagerCompat)
- [Reconhecimento de fala](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Autorização Google](https://developers.google.com/identity/authorization)
- [Escopos Google](https://developers.google.com/identity/protocols/oauth2/scopes)
- [APIs Inter PJ](https://ajuda.inter.co/conta-digital-pessoa-juridica/o-que-e-uma-api/)
- [nextJoy oficial](https://banco.bradesco/nextjoy/)

## Estado e validação

169 testes backend aprovados (6 novos) com IA simulada. Nenhuma chamada paga nesta
entrega até este checkpoint. 37 testes unitáriosAndroid aprovados; assembleDebug/AndroidTest e lintDebug
aprovados (0erros102avisos). Gradle encerrado para liberar RAM.
APK android/build/releases/Koiwai-1.7.apk SHA256
6D0843D81E474BD51BD5718263E12B1ED9E53AF7D8605C5060B2E1D83C418C25.
Publicado no Render em 09/10/2026 11:29:06 (São Paulo), deploy
dep-db4fjurl550s73bjgk30, commit b104bf568e09cf488f1ae7dee5268e0278db1fcc.
Saúde200/providerpoe; chat/review sem sessão401; nenhum log de erro na conferência.
Instalado por atualização no Poco14c3a88a; versão1.7/code9 confirmada.
ExpansionDeviceTest:2 testes físicos PASS0,023s (cache privado/isolamento/read-only
e declaraçãoACTION_ASSIST). AçãoASSIST abriu Chat, conta e histórico preservados.
Não é teste do gesto físico, seleção como padrão ou consulta real sem rede.
Reconhecimento local, qualidade da voz, escolha do assistente, leitura do calendário
e notificações prolongadas dependem de verificação no Poco e permissões do usuário.
Sem migração ou alteração de segredos/plano; não houve reset.

Painel local V1.7 recarregado no Brave e cartão interativo de voz conferido.
Checklist manual de voz/agenda enviada; ainda sem resposta neste checkpoint.
