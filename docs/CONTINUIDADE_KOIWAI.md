# Koiwai — leia primeiro ao retomar

## Checkpoint Google V1.12 — 09/10/2026 (substitui pendências antigas abaixo)

Fluxo OAuth e painel Android implementados. Servidor: sessão Koi validada, dono
extraído no servidor, state de uso único persistente, PKCE e cookie HttpOnly/Secure
por fluxo. Até três identidades Google distintas; reautorizar atualiza por subject.
Tokens cifrados Fernet, vinculados ao dono/finalidade, guardados somente em tabelas
Supabase com RLS e sem privilégios anon/authenticated. SQL google_connections.sql
APLICADO; bloqueio REST anônimo e acesso privado do servidor VERIFICADOS.
Chave Supabase koiwai_connections e chave de cifragem criadas após autorização
específica, salvas no .env ignorado e Render. Não gerar outra chave de cifragem
sem migração dos tokens. GOOGLE_ENABLED=true no servidor. Segredos fora do APK/Git.

Backend: 202 testes PASS. Android: 53 testes PASS, APK V1.12/code14 preparado em
android/build/releases/Koiwai-1.12.apk. INSTALADO no Poco por install -r, Success;
dumpsys confirmou code14/name1.12. Home aberta, conta conectada preservada.
Toques ADB bloqueados pelo Poco (INJECT_EVENTS); usuário confere painel manualmente.
PUBLICADO Render commit28efa33, dep-db4kh8rncjis73fsllrg live20:04:40Z.
Smoke HTTPS: health200, Google status/connect sem sessão401, callback inválido400.
Calendar, Tasks, Gmail e Drive habilitados e verificados após autorização dos termos.
Usuário informou que conectou tudo. Verificação SQL em 09/10 confirmou três contas,
um dono e tokens cifrados presentes. Não confundir autorização com consulta real
de cada serviço; estas consultas ainda precisam de validação.
Usuário CONFIRMOU painel Google e botão Adicionar disponível no Poco.
Próxima entrega: consultas Google pelo chat, tarefas dentro das listas e ações
de agenda/Tasks com conta de destino explícita e confirmação do resultado real.
Android: Rotina → Ferramentas → Conexões → Google com a Koi; conectar/reautorizar,
atualizar, consultar e desconectar individualmente. Consultas sem IA/cache:
20 eventos da agenda principal, 20 agendas, 20 LISTAS Tasks, cinco cabeçalhos Gmail
(sem corpo), 20 metadados Drive (sem conteúdo). Não chamar listas de tarefas reais.
Ainda faltam criação/edição Google, rascunhos, conteúdo de arquivos e ferramentas
Google no chat. Não afirmar que conexão de cliente equivale a autorização pessoal.
Modo Google Testing pode exigir reautorização em sete dias. Outlook cancelado.
Mais detalhes: ENTREGA_GOOGLE_V1_12.md e PLANO_CONEXOES_MULTICONTAS.md.

## Em andamento — consultas úteis e três contas Google — 09/10/2026

DECISÃO ATUAL: usuário desistiu da conta Outlook/Microsoft e do cadastro Azure.
Remover Microsoft do escopo ativo; não retomar formulário, criar conta/tenant ou
pedir cartão. Registros abaixo sobre Microsoft são históricos, não tarefas atuais.
Próxima entrega: fluxo OAuth Google no servidor e Android, três conexões distintas,
armazenamento cifrado persistente e APIs autorizadas de Calendar/Tasks/Gmail/Drive.
Cliente/test users/segredos preparados não equivalem a conexões funcionais.

Usuário mostrou resposta genérica a pedido de orientação sobre seu dinheiro; pediu
conectar três contas Google e uma Outlook. Prioridade real: autonomia, trabalho,
organização, metas e finanças utilizáveis. Inter continua somente saldo; não ampliar
pagamentos/Next. Separar salário e benefícios conforme regras, sem presumir dinheiro
disponível. Não gravar estimativas pessoais em Git/documentação pública.
finance_guidance adicionado ao schema semântico: modelo escolhe cartão bancário plan,
sem segunda geração nem dados bancários ao Poe. Recibo inclui request_id para
compatibilidade ChatBackend atual. Guardas read/domain/question/request/filtro e
evidência do pedido atual; nunca grava gasto nem executa pagamento. 186 testes PASS.
Uma interpretação real curta no Poe reconheceu a pergunta como record/read e
finance_guidance; não foi teste de interface. Publicado no Render commit196c039,
dep-db4jmpei0phs73culpf0 live19:08:15Z; /api/v1/health HTTP200/ok/poe.
App1.11 já aceita recibo bank/plan com request_id válido; nenhum APK novo necessário.
Pendente teste manual com nova mensagem; não editar/apagar a conversa anterior.
Google Cloud: projeto separado Koiwai criado, ID handy-geography-511119-h4,
sem billing/crédito/teste pago. Não havia cliente OAuth no projeto anterior; não
alteramos outros projetos. Cadastro OAuth/branding criado depois de confirmação
específica do usuário para política API Google; Externo/Testing, 3 usuários de teste
SALVOS e confirmados na tabela. Cliente OAuth WEB criado após confirmação específica;
Client ID, Client Secret e redirect no .env privado/ignorado e segredos do Render,
Save only (nenhum novo deploy). Autorizações e adapters ainda pendentes;
não chamar as quatro contas conectadas. Brave reconectado ID1, projeto aberto.
Google Tasks/Gmail/Drive não existem como adapters no código. Agenda atual lê apenas
calendários sincronizados do Android. Outlook ainda sem app/Graph OAuth.
Usuário forneceu três contas Google/uma Outlook no chat; não colocar endereços no Git.
Cliente WEB 'Koiwai - assistente pessoal' criado, flag AI-agent=true,
redirect configurado https://koiwai-backend.onrender.com/api/v1/connections/google/callback.
ATENÇÃO: callback ainda NÃO implementado; não iniciar autorização/ligação antes de
implementar/validar retorno, state/PKCE, sessão/owner e armazenamento cifrado.
Operações Google já aprovadas e concluídas: não pedir autorização novamente para
essas mesmas ações. APIs Calendar/Tasks/Gmail/Drive ainda não habilitadas/verificadas.
Proofs privados/ignorados: android/build/google-contas-teste.png e
android/build/google-render-configurado.png; nunca capturar segredos revelados.
Outlook: usuário concluiu passkey. Portal Azure Home abriu com conta pessoal, sem
assinatura. Entra pediu recuperar tokens; Entrar novamente + conta autenticada voltou
ao erro de conta ausente no locatário Microsoft Services. Não é senha incorreta.
Nenhum app Microsoft, segredo, assinatura ou plano criado. Precisa de diretório
próprio acessível para registro conforme documentação oficial; não iniciar contratação
ou repetir login indefinidamente. Proof privado microsoft-diretorio-pendente.png.
Diagnóstico oficial AADSTS50020 causa1 confirma conta pessoal no Microsoft Services
sem diretório vinculado. Caminho oficial é cadastro Azure com novo tenant. Cadastro
gratuito aberto no Brave (aba1253037202) e entregue ao usuário para revisar/concluir
pessoalmente; pergunta pendente se quer cadastrar ou manter Microsoft pendente.
Não submetidos telefone, cartão, termos, plano ou nova conta. Microsoft informa
possível autorização temporária US$1 no cartão. Proof microsoft-cadastro-azure.png.
Usuário reafirmou escopo amplo: todas as conexões/funcionalidades possíveis e depois
melhorar inteligência; contexto de uso não limita escopo apenas a finanças/trabalho.
Ler PLANO_CONEXOES_MULTICONTAS.md para dependências e ordem. Não confundir plugin
do Codex com autorização da Koi. Consentimentos/criação de credenciais via navegador
exigem confirmação específica no momento; login/senhas/códigos com o usuário.

## Checkpoint — continuidade de conversa — 09/10/2026

Usuário confirmou também a comparação saldo/contas no Chat da V1.11.
Melhoria do servidor: perguntas referenciais sem assunto explícito recuperam termos
da última fala do usuário, dentro do histórico limitado. Nunca usam fala da Koi como
fato/assunto pessoal, nem saltam uma saudação para um assunto antigo. Assunto explícito
atual prevalece. Termos de banco/saldo não alimentam essa recuperação. Mesma consulta
autenticada a até 40 registros diary/note/goal/workout, até 3 fontes; trecho original
próximo ao assunto, datas/tipos validados. Sem nova chamada de IA, migração ou APK.
Prompt distingue fato/sugestão, evita inventar lacunas, pede esclarecimento só quando
necessário e indica título/data de lembrança consultada naturalmente.
181 testes backend PASS. Publicado commit8f27b49 no Render:
dep-db4h99s9v7es73akog0g live09/10/2026 16:22:51Z. Health correto /api/v1/health
conferido; /health sem prefixo retorna404. Sem teste real novo de conversa/Poe:
tom e referências no uso real aguardam usuário; testes não gastaram pontos.
Não é compreensão universal: referências sem tópico claro ainda podem pedir detalhe;
busca continua por palavras/sinônimos limitados e apenas 40 registros recentes.
Dados financeiros privados continuam fora do Poe. Visual/Windows continuam adiados.

## Checkpoint V1.11 — banco no Chat instalado e confirmado — 09/10/2026

Ler ENTREGA_BANCO_CHAT_V1_11.md. Perguntas de saldo Inter/comparação reconhecidas
localmente pelo Android antes de qualquer chamada de IA. Resposta genérica e
receiptbankread sem valores, cartão BankConnectionPanel no Chat busca bank-summary
ou bank-summary+day (plan), autorização/sessão existentes, sem cache/saldo à IA.
AutoRead só mensagem nova mais recente com Chat aberto; histórico antigo botão
Atualizar. Sem undo para consultas. Perguntas de Poe/registro/explicativas preservam
fluxos normais. Famílias de frases, não interpretação universal nem saldo no Poe.
53unitários PASS; build/instalação1.11/code13 app+testes aprovados. Tentativa Compose
travou; agente interrompeu o teste, substituído por teste físico isolado repository
PASS0,112s sem backend IA: tokenProvider com erro se usado nunca chamado, apenas
3campos no receipt e texto/contexto sem saldo. Usuário confirmou cartão real ao
perguntar quanto tem no banco. Comparação no Chat confirmada manualmente pelo usuário.
APKandroid/build/releases/Koiwai-1.11.apk SHA256
D7446CFFABFD485C14AB6FBAD7CC923DDFF50593A9A44C53C5F757035082AA4E.
Gradle parado, app aberto. Sem backend/deploy/SQL/credenciais/permissões novas.
Histórico sincroniza pergunta/resposta genérica; receipt só local, outro aparelho
pode restaurar texto sem cartão. Ouvir/Compartilhar não incluem resultado privado.
Pendente: ampliar compreensão com privacidade, sugestões mais
amplas, monitoramento sem Android. Não mandar saldo à IA/abrir extratos sem tratar
novo escopo. Inter somente; Next excluído; visual definitivo/Windows depois.


## Checkpoint V1.10 — saldo e contas instalado — 09/10/2026

Ler ENTREGA_SALDO_CONTAS_V1_10.md. Tela Poco V1.9 conferida visualmente e correta.
Agora painel compara bank-summary + day via leitura autenticada, allowCached=false;
saldo não enviado à IA e não persistido no cacheAndroid. Nenhum backend/deploy/SQL
novo. Inter somente; OAuth anterior mais amplo autorizado, adapter somente saldo.
Comparação local de vencimentos cadastrados hoje..hoje+7 inclusive; lista8, conta
real de total/registros e flags parciais impedem alegação de cobertura. Saldo antigo
>24h/futuro>5min e mudança de dia sinalizados. Diferença não chamada dinheiro livre.
Pendências do mês separadas, sem dupla subtração; alerta budgets_exceeded existente.
Datas locais e detalhes de ativação recolhíveis, visual geral preservado.
50unitários Android PASS4novos; buildapp/teste PASS; install-r1.10/code12 ambos.
Teste físico balanceAndRegisteredBillsCanBeComparedWithoutWrites PASS4,948s com
saldo e contas autenticados contra Render, sem valores privados no log nem escrita.
APKandroid/build/releases/Koiwai-1.10.apk SHA256
BB1E92021D648DD5BDFBFDE6FCAA2D0B1D6F8F366C2EFA094C86384101DACB8E.
Gradle parado; app aberto. Novo cartão aguarda conferência visual manual. Dashboard
atualizado. Ainda sem banco no Chat, monitoramento de depósitos ou dados bancários
para IA. Próximo: consulta natural de saldo/contas no Chat, com limites explícitos.

## Checkpoint V1.9 — Inter vinculado, publicado, instalado e leitura validada — 09/10/2026

Ler ENTREGA_SALDO_V1_9.md. Usuário quer somente saldo Inter; Next/nextJoy excluído.
Credenciais existentes Pluggy Demo App guardadas somente no .env ignorado e Render.
Consentimento OAuth MeuPluggy concluído; products=['ACCOUNTS'] no widget não reduz
as cinco permissões OAuth. Usuário autorizou explicitamente o acesso adicional após
explicação de privacidade. Adapter Koi segue apenas GET/accounts typeBANK e BRL;
sem extratos/identity/investimentos/cartões/empréstimos/pagamentos nem envio à IA.
PLUGGY_ENABLED=true e owner/itemUUID privados configurados. Owner do Poco capturado
por teste opt-in sem exportar token; arquivo no_backup temporário removido.
Render dep-db4g9kbbc2fs73bmvtpg live15:15:16Z, commit backend53ca84c; atualização das
variáveis iniciou deploy automaticamente. AutoDeploy de código permanece desligado.
Consulta local real retornou1conta, partialfalse, nenhum valor impresso. Teste físico
BankBalanceLiveDeviceTest#balanceReadReturnsOnlySummary PASS3,899s com login normal
contra Render; confirmou retorno sem CPF/número/id/extrato. Primeiro teste encontrou
whitelist incompleta no Android; corrigida incluindo bank-status/bank-summary.
App1.9/code11 atualizado install-r, sem reset. Texto explica somente Inter. Build
app/teste PASS. APKandroid/build/releases/Koiwai-1.9.apk SHA256
B7DAEA3F5F20ED4CD25893FAA039529783F839992A50F7C59FECC19713D189E4.
177backend e46Android aprovados na preparação; nesta retomada teste real novo e build
aprovados, sem nova bateria completa. Gradle parado após uso. Servidor temporário de
setup127.0.0.1:8770 parado; nenhum segredo/UUID privado no Git. Dashboard atualizado.
Limites: leitura manual, dados conforme data do provedor/sincronização diária; cache
RAM60s com owner antes do cache e no-storeHTTP. Sem cache Android ou consumo de IA.
Falta confirmação visual manual dos cartões; ainda sem sugestões com saldo no Chat,
avisos instantâneos de depósitos, próximos bancos ou uso desses dados pela IA.

## Checkpoint V1.8 Planejamento — instalada, 09/10/2026

Ler ENTREGA_PLANEJAMENTO_V1_8.md. Somente Android; sem backend/Render/migração.
Agenda→tarefas via loadTasks existente por owner/token, cálculo local sem IA.
Até200eventos/7dias (201º sinaliza truncamento), grupos visuais preservamorigens.
Roteiro/conflitos/janelas15min, dia/período8–22, estimativa15/30/60min por tarefa.
Recorrências diárias/semanais/mensais projetadas e rotuladas, sem escritas.
Perguntas hoje/amanhã/manhã/tarde/noite neste painel, não no Chat.
Tarefas concluídas/arquivadas excluídas; instantes/fusos/noite/dia-inteiroUTC.
Feriados não bloqueiam automaticamente. Incompleto não afirma janela livre.
46unitários Android PASS9novos. Build/lint aprovados. Poco14c3a88a install-r
aprovado app+testes,1.8/code10. CalendarPlanningLiveDeviceTest1PASS1,055s.
APKbuild/releases/Koiwai-1.8.apk SHA256
C1393157E8C790DCAADD54F44C1226498471ED399E841759D1F5A2BD73D86009.
Gradle parado; lint0erros102avisos. Dashboard V1.8 recarregado no Brave.
Usuário confirmou roteiro e janelas corretos após consultar agenda/cruzar tarefas.
Casos visuais de perguntas/duração/conflitos e estabilidade prolongada não completos.
Servidor segueV1.7 sem necessidade de deploy.
Teste físico opt-in CalendarPlanningLiveDeviceTest, calendar-read-test=true:
leitura da agenda permitida + tarefas da conta; sem fixtures/títulos privados/IA.
Não declarar hábitos com horário, GoogleOAuth, bancos, Chat com agenda, teste de
assistente padrão/gesto/offline nem estabilidade prolongada concluídos.
Sem reset/novos segredos/permissões/plano/visual definitivo/Windows.

## Checkpoint V1.7 Expansão — publicada e instalada, 09/10/2026

Ler ENTREGA_EXPANSAO_V1_7.md. Usuário pediu todas as frentes exceto visual/Windows.
Implementado contexto lexical de até3trechos/40registros ativos recentes na conversa,
busca com sinônimos limitados/pequenas variações, snapshots privados no-backup
Meu dia/Meu ritmo/Plano/Busca com aviso de data/limite7dias/20consultas por conta.
Nenhuma escrita/saldo/aviso usa cache; 401/404/409/422 não são mascarados.
Voz: escolha instalada+tom/velocidade, reconhecimento localAndroid12+se disponível.
ACTION_ASSIST→Chat e botão oficial de escolha padrão; não é hotword/escuta contínua.
Agenda local sincronizada: READ_CALENDAR manual, até20eventos/7dias, sem enviar àIA.
Relatórios auto: limite de pedidos por celular/conta/dia padrão3/opções1/3/5;
pedidos falhos contam, manualseparado, pode gastar pontos. Não é quota globalPoe.
169backend +37unitáriosAndroid passaram; buildslint0erros102avisos; daemonparado.
APK1.7/code9 build/releases/Koiwai-1.7.apk SHA256
6D0843D81E474BD51BD5718263E12B1ED9E53AF7D8605C5060B2E1D83C418C25.
Nenhuma chamada paga/reset/migração/novo segredo/plano. Render LIVE
09/10/2026 11:29:06 São Paulo, dep-db4fjurl550s73bjgk30, runtime b104bf568e09cf488f1ae7dee5268e0278db1fcc.
Saúde200/providerpoe; chat/review sem sessão401; logs de erro vazios nesta conferência.
Poco14c3a88a install-r aprovado (app+testes),1.7/code9 confirmado.
ExpansionDeviceTest:2PASS0,023s, cache privado isolado/read-only e declaraçãoASSIST.
ACTION_ASSIST abriu Chat; screenshot carregada com conta conectada/histórico preservado.
Isso não verifica o gesto físico nem a escolha padrão. Voz/agenda aguardam usuário.
Gmail/Tasks/DriveOAuth e Inter/nextJoy não conectados. Interdocs públicasPJ;
sem APIgratuitaPF confirmada. Nunca declarar bancos/Googlecompleto funcionando.
Faltam testes físicos de voz/agenda/padrão/snapshots e estabilidade prolongada.

## Checkpoint V1.6 Meu ritmo — publicado e instalado, 09/10/2026

Ler ENTREGA_COMPANHEIRA_V1_6.md. Check-ins início/fim, revisão do dia, hábitos
últimos7dias, reagendar amanhã preservando horário e Desfazer. Config. amplia
Cuidados com seu dia: tipos, horários, 1–3/dia, mínimo4h e carinho6h opt-in.
Descanso compartilhado respeitado; entrega local pelo Android sujeita a atraso.
Endpoints review/checkin/task-action/undo reutilizam RPC/RLS sem migração.
163 backend +35 Android unitários passaram, sem geração paga; builds e lint
0erros97avisos aprovados. APK1.6/code8 build/releases/Koiwai-1.6.apk SHA256
5A13A9957C0765A96E3D47C6D7D9BCA328DBC7F98261545B82DC3EA932267EA3.
DaemonGradle parado. Servidor commit6c384dde45818428f635c11de4c9ee695c865b42,
Renderdep-db4ehs3ncjis73coaung LIVE09/10 10:16:30 SãoPaulo; saúde200/providerpoe,
endpoints novos sem sessão401; logs de erro após deploy vazios na conferência.
Poco14c3a88a install-r aprovado,1.6/code8 confirmado. CompanionApiLiveTest2tests
PASS34,344s:checkin início/retry/fim/undo,taskreschedule preservahora/repetição,
completeavança/undo. Zero geração paga/tokenexport/reset. Duas fixtures próprias
removidas; recibos ficam. ScreenshotMeu ritmo carregado, sem crashobservado.
Usuário confirmou Check-ins e Hábitos: “As duas abas abriram bem”. Ainda falta
entrega real de avisosV1.6 ao longo dos horários/descanso/reinício; não declarar
essa bateria concluída.
Usuário confirmou V1.5 perfeita e autorizou todo este pacote; Poco reconectado.
Testes físicos preparados criam/removem só fixtures próprias; 12 TesteV1.5 ficam.
Dashboard atualizado/reaberto no Brave, checklist manual própria V1.6 0/5.
Usuário perguntou sobre primeiro print vazio: captura antes de renderizar; segundo
mostrou tela normal. Não havia erroAndroidRuntime na conferência.
Próximo: acompanhar avisos reais e retorno do usuário; depois ampliar inteligência,
integrações autorizadas/voz/visual. V2definitiva não concluída.

## Checkpoint V1.5 Organização — instalada no Poco, 08/10/2026

Ler ENTREGA_ORGANIZACAO_V1_5.md. Busca por palavras/período com fontes, plano
do dia só leitura, categorias/limites/avisos e laboratório12 exemplos Teste.
149 testes backend, 30 unitários Android, assembleDebug/AndroidTest e lint
aprovados (0 erros,81 avisos), sem geração paga. Migração categorias aplicada;
ensaios SQL de categoria/edição/desfazer/legado/RLS e dos12 exemplos/repetição
passaram e foram revertidos (zero contas fictícias remanescentes).
Servidor commit771a18914b604459baae3140374af40954b4b5d6, Render
 dep-db45c4rbc2fs73am4n9g LIVE08/10/2026 23:49:45 SãoPaulo; saúde200/providerpoe.
Poco reconectado e APK atualizado com install -r: confirmado1.5/code7.
APK android/build/releases/Koiwai-1.5.apk SHA256
D4F9A92D9102D95353ED67D5AD9B9C03957456C1B3003CC75D87788308E15573.
OrganizationApiLiveTest APROVADO no Poco: 2 testes,20,74s. Categorias, criação
12 exemplos autorizados, repetição sem duplicação, busca do lanche por fonte
ID e plano do dia. Zero geração IA/token exportado/reset/permissões alteradas.
Exemplos PERSISTEM na conta, claramente Teste/fictícios; não são vida real.
Manifesto privado guardado no app em preferências koi-demo, chave por dono:
IDs/recibos necessários à futura limpeza, não publicar nem perder.
Próximo: usuário conferir visualmente Home→Plano do dia, Memória→Teste Lanche,
Rotina→Orçamento/categoria. API testada não equivale à bateria visual concluída.
Checklist dashboard0/5 é manual. Não afirmar confirmação do usuário ainda.
DaemonGradle encerrado após build (PC pouca RAM). Não recompilar sem mudanças;
não desinstalar app ou mudar proteções/plano. Dashboard atualizado.

## Checkpoint atual: Meu dia V1.4 — 08/10/2026

Ler ENTREGA_MEUDIA_V1_4_2026_10_08.md. Pacote implementado: cartões de resumo
do dia/finanças/diário na Home; saldo do Poe em Config.; leituras naturais ampliadas
para lembranças, diário anterior e comparação de períodos. Cartões sem geração IA;
chat usa interpretação antes de ler, salvo lembrete explícito já resolvido sem IA.
137 testes backend, testes unitários Android, assembleDebug e lintDebug passaram.
Servidor publicado: commit 9571a2646fb4cd52c0448cbbd242c6031d8c1bf2, Render
dep-db44jn7lk1mc73eqdd0g LIVE em 08/10/2026 22:57:40 (São Paulo).
Saúde HTTP 200/provider poe. V1.4/code6 instalada por atualização no Poco.
Usuário conferiu Meu dia e Consultar saldo no Poco: “Os dois funcionaram”.
Bateria automática de telas não concluída (abertura de atividades no Poco);
teste adicional do cliente autenticado APROVADO no Poco (1 teste, 3,217s):
mesmo cliente dos cartões leu Meu dia e pontos com sessão existente, sem geração
de IA, exportação de token ou mudanças nos registros. Sem reset ou mudanças
de banco/segredos/plano. APK final: android/build/releases/Koiwai-1.4.apk,
SHA256 98D886B3C6F0A4449CE6257E01368F99BAEDB994DFC0767ADE4DEA34B0DAB76F.
Usuário confirmou que os cinco testes anteriores de conversa, criação, consulta,
conclusão e finanças passaram. Essas são evidências do usuário, não teste automático.
Poe é o provedor ativo, plano já contratado R$24,90; Render/Supabase continuam grátis.
Cabeçalhos antigos abaixo preservam história e NÃO descrevem o estado atual.

## Checkpoint: falha no lembrete Poe — 08/10/2026

Ler CORRECAO_LEMBRETE_POE_2026_10_08.md. Celular conectado; tela mostrou pedido
Me lembra de estudar amanhã às 19h. falhando. Dois 502 no servidor às 21:34;
Poe cobrou quatro chamadas naquele minuto (49 pontos), sem dados para atribuir
campo de schema que falhou. Correção sem chamadas pagas: lembrete explícito
hoje/amanhã/horário resolve localmente, zero IA, preservando RPC/recibo/owner;
regras+schema em system único, JSON fence completo validado, diagnóstico seguro.
124 testes simulados passaram. Commit 77cf61f publicado; Render
dep-db4473rbc2fs73ai86pg LIVE em 08/10/2026 22:30:42 (São Paulo).
Saúde HTTP 200/provider poe após publicação. Pedi ao usuário tocar uma vez em
Tentar novamente no lembrete exato, agora sem IA; usuário confirmou:
"Sim, criou a tarefa". Fluxo físico confirmado pelo usuário, sem inspeção do banco.
Não reenviar pedido automaticamente nem prometer estorno do Poe.

## Checkpoint anterior: integração Poe — 08/10/2026

Ler IA_POE.md. Usuário autorizou chave Koiwai no .env privado/segredos Render e
testes até 200 pontos. Chave criada/guardada, nenhuma compra nova. Consumo real
total inicial medido: 50 pontos. Revisão final teve uma chamada adicional (6 pontos)
porque teste antigo mockava só Groq. Mock corrigido para generate de qualquer
provedor; 120 testes repetidos sem chamadas reais. Total final 56, saldo 9.944.
Conversa, intenção de contas, plano validado
de concluir tarefa fictícia e negação passaram; nenhuma escrita pessoal real.
120 testes simulados aprovados. Texto GPT-OSS-120B via Chat Completions + schema
nas instruções/validação local JSON Schema/Pydantic. Responses nesse modelo deu
400 sem custo; não afirmar strict garantido pelo Poe. GPT-4.1-mini respondeu
schema via Responses; imagem/pesquisa só testadas com mocks, reais pendentes.
AI_PROVIDER=poe impede qualquer chamada Groq ou fallback automático.
Commit 09713ef publicado; Render dep-db439i67bikc73e4oul0 LIVE às 21:27:40 de
08/10/2026. Saúde HTTP 200, ai_provider=poe. API de configuração disparou deploy;
trigger manual também criou dep-db439jeiej4c73cplop0 do mesmo commit (sem custo
de IA). Segundo deploy confirmado LIVE às 21:28:32, saúde 200/provider poe.
Correção de mock e documentação publicadas depois, sem nova alteração de runtime.
Celular desconectado pelo usuário; avisar
quando for necessário teste físico. Android sem alteração nesta integração.

## Checkpoint anterior: economia antes do Poe pago — 08/10/2026

Usuário pagou Poe mensal R$24,90, ativo 10.000 pontos/dia. Pediu otimizar antes
da integração. Ler OTIMIZACAO_IA_2026_10_08.md. Implementado: consultas simples
financeiras/diário 2→1 chamadas; contexto limitado completo; planejadores com tom
resumido e regras preservadas; JSON compacto; telemetria só contagens; Retry-After.
112 testes backend simulados aprovados, zero chamadas/pontos Poe.
Publicado no GitHub: 296b7b571b9766b16075eb70e3860a44cd5280ed.
Render dep-db432p7lk1mc73ekpmlg LIVE em 08/10/2026 21:13:20 (São Paulo).
GET /api/v1/health retornou 200/status ok após publicação, sem geração IA.
Android V1.3 instalado sem mudança; não precisou reinstalar. Conversa real com
as novas consultas ainda não testada para evitar consumo durante a otimização.
Poe NÃO integrado; Groq atual. Próximo: adaptador Responses/schema e credencial
privada Poe, com confirmação específica se necessária no navegador.

## Diagnóstico anterior — limite Groq, 08/10/2026 à noite

Leia DIAGNOSTICO_COTA_GROQ_2026_10_08.md. Usuário confirmou V1.3/code5 no Poco;
conta Internet recebeu confirmação, mas consulta seguinte falhou com 429.
Painel Groq: 7.663 tokens em cerca de 18 s antes da recusa; limite organização
8.000/min, 200.000/dia, modelo gpt-oss-120b. Evidências apontam para limite por
minuto; diário não confirmado. Fluxo contextual faz interpretação + geração de
consulta. Diagnóstico apenas: nenhuma correção funcional/publicação/instalação
ou chamada de teste Groq neste trabalho. Próximo: reduzir chamadas/contexto,
consultas com dados verificados e mensagem de espera respeitando Retry-After.

## Entrega atual — V1.3 vida organizada, 08/10/2026

Leia ENTREGA_VIDA_V1_3_2026_10_08.md. Contas recorrentes, pagamento/despesa atômicos,
orçamento total mensal, diário estruturado/categorias/sono e balanço semanal local.
Acompanhamento opt-in até 1 aviso/dia, descanso, internet/Android, sem scheduler cloud.
Migração koi_life_pack aplicada: details, novos tipos, RLS pagamentos e capacidade
2.000 registros; dados anteriores preservados. Notas antigas continuam Notas.
Backend 843cd93 publicado, Render Free Live dep-db411ejl550s73c7i5ag, health 200.
V1.3/code5 instalada no Poco. 102 backend +30 unitários +6 persistência +1 saudação
real passaram; SQL transação revertida e 4 cenários Groq/banco simulado passaram.
Painel atualizado e verificado no Brave. Revisão final de navegação instalada;
Contas, Diário e Orçamento conferidos no Poco. APK final SHA256 começa 83D113A4;
hash completo no relatório. Avisos reais/formulários por toque ainda precisam de teste manual.
Não prometer banco conectado, voz própria, toda paráfrase perfeita ou push exato.
Nenhum plano pago, reset ou permissões ativadas. Usuário mantém Inter e nextJoy.

## Entrega atual — V1.2 contextual, 08/10/2026

Leia ENTREGA_CONTEXTUAL_V1_2_2026_10_08.md. V1.2 instalada no Poco (code 4), sem
reset. Classificação semântica, Coi, memória corrigida, captura opcional de relatos
(em Notas/fatos/finanças), cartões Android e microfone dentro da Koi. 93 backend,
25 unitários e 6 persistência no Poco passaram; testes Groq fictícios passaram.
Novos fluxos físicos precisam de teste manual. Usuário usa Inter e nextJoy;
Itaú foi apenas pesquisa. Código 48bec44 publicado; deploy dep-db40ij4s728c73fgdt50
confirmado Live; health 200, chat sem sessão 401 e demo 404. Plano Free.
Saudação autenticada enviada pelo Poco ao servidor publicado passou (1 teste,
4,588 s), sem gravação no histórico. Esse teste usa o caminho direto anterior;
não comprova todos os novos fluxos semânticos ou físicos no celular.

## Pesquisa mais recente — expansão contextual, 08/10/2026

Leia PESQUISA_EXPANSAO_KOIWAI_2026_10_08.md: matriz de possibilidades, fontes,
dependências, custos e critérios. Usuário relata testes guiados aprovados exceto
memória. Prioridade: corrigir memória, aliases Coi e interpretação semântica com
ferramentas reais; diário, ações Android, contas/proatividade e integrações depois.
Pesquisa NÃO implementou nem instalou novos recursos, concedeu permissões ou pagou
serviços. Inter API oficial é PJ; Spotify API dev exige Premium; não prometer
integrações bancárias/ChatGPT ou controle irrestrito. Visual completo posterior.

## Entrega mais recente — V1.1 instalada no Poco, 08/10/2026

Leia ENTREGA_V1_1_2026_10_08.md. Atualização preservou os dados; versionCode 3,
versionName 1.1 confirmados. Voz agrupada, atalhos duplicados removidos, ferramentas
separadas em Ferramentas/Celular/Conexões. Dashboard compacto interativo com filtros,
detalhes, checklist persistente, pedidos para copiar e mapa. Painel local no PC.
25 unitários Android + 6 persistência no Poco passaram; build/lint passaram.
Navegação automática bloqueada ao lançar atividade; toques remotos negados pelo
Android. Abertura manual e Home conferidas; novas abas precisam de teste manual.
Home mostra conta Local: usuário precisa entrar novamente para chat online.
Sem novo reset, nenhum pagamento, nenhum deploy backend nesta entrega.
APK SHA256 db4d69524f3c5cd3d4b7b8de095b454e9cc4321f978728d881ff8de58990dad2.

## Checkpoint prioritário — revisão funcional V1.1, 08/10/2026

Leia REVISAO_FUNCIONAL_2026_10_08.md. Direção aprovada: companheira que organiza a
vida por conversa; diário/rotina/sono/dinheiro/contas/lembretes conectados. Não
priorizar plugins indiscriminados. Usuário não gostou da organização visual atual;
redesign vem DEPOIS das funcionalidades, mantendo identidade e animações.
Correções: vocativo Koi e negativas não autorizam escrita; totais semanais/mês
anterior e exclusão de datas futuras dos períodos atuais; resposta vazia recusada;
Android aceita 1.234,56 e trata bloqueio de abertura externa. Personalidade ajustada.
79 testes backend, 25 unitários Android, build/lint: zero erros/69 avisos.
APK preparado na revisão anterior foi substituído pela entrega V1.1 acima.
Estado histórico: celular estava desconectado naquela revisão; agora foi instalado.
Supabase: 9 tabelas RLS habilitado, aviso antigo de senhas vazadas; limite real
200 registros pessoais compartilhados/arquivados, precisa crescer para uso diário.
Render continua Free. Pagar não cria proatividade: os jobs atuais são Android;
não existe agendador de acompanhamento/push em nuvem. Nenhum plano pago ativado.
Dashboard ampliado em docs/dashboard/index.html: exemplos ilustrativos, limites,
correções, pacotes, autorizações e estado de testes. Não é monitor ao vivo.
Próximo: diário estruturado e consulta com fontes; contas/orçamento/histórico;
proatividade opt-in; voz/assistente; redesign e uso prolongado, depois Windows.
Backend revisão publicado: fa797b0, Render Free Live dep-db3un0nf3r2c73dlluq0.
Health 200; chat sem sessão 401; demo produção 404. Consulta Groq real fictícia
passou, sem escrita. Dashboard exemplos/filtros verificados no navegador.

## Checkpoint mais recente — instalada e zerada, 08/10/2026

Leia ATUALIZACAO_CELULAR_2026_10_08.md antes dos registros históricos abaixo.
V1.0 instalada no Poco, versionCode 2. Usuário autorizou explicitamente reset;
nove tabelas da conta na nuvem e armazenamento local foram zerados. Conta Auth
preservada. Sete testes físicos de persistência/estado vazio passaram. App abriu.
Novos controles em Config.: voz, ferramentas, lembranças, notificações, permissões
e Celular e conexões. Ferramentas abre apps, discador, contatos, alarmes e Wi-Fi;
atalhos Google/ChatGPT abrem serviços, não conectam dados. Sem acesso irrestrito.
Build/lint aprovado: zero erros, 69 avisos. APK SHA256
6617b5e23113a2e3a0cdcd7c00e73d4260d8a700932de73adffd457061cd3e4d.
Navegação/configurações automatizadas pendentes: ActivityScenario travou no
lançamento, interrompido para reset. Não afirmar aprovação visual desses fluxos.
Próximo: login e testes manuais; módulos Google OAuth, notificações/contatos,
outras telas, ChatGPT e bancos Inter/nextJoy. Usuário pediu tudo, mas credenciais,
concessões sensíveis e pagamentos precisam de autorização específica no momento.
Google Cloud aberto: nenhum cliente OAuth configurado. Sem novos segredos/deploy.
Dashboard ilustrativo local em docs/dashboard/index.html. Não é monitor ao vivo.
Usuário desconectará o celular; APK final já instalado. Não limpar de novo.

## Checkpoint prioritário — pacote móvel V1.0, 08/10/2026

LEIA ENTREGA_V1_0.md antes dos checkpoints históricos. Usuário pediu ampliação
móvel completa e revisão enquanto o Poco fica desconectado. Não instalar nem
alegar validação física nesta sessão. V1.0 é o nome escolhido; V1.1 para correções
posteriores e V2.0 definitiva quando validada. Não resetar dados.

Código: prazo relativo usa o instante original persistido; Rotina com agenda,
hábitos, notas/listas/metas/treinos/finanças; registros pessoais e memória pelo
chat com recibos/CAS/desfazer; voz do Android, imagem escolhida/câmera miniatura,
pesquisa explícita Groq com fontes, clima Open-Meteo, calculadora, foco/TXT e
formulário externo de calendário. Histórico migra Room 4→5; fotos locais, texto
sincronizado. Sem vigilância contínua, pagamentos, WhatsApp ou controle Windows.
Novos registros precisam de internet; não prometer fila offline durável.

Supabase: migração personal_records_and_reversible_memory já aplicada. SQL com
duas contas fictícias/rollback aprovado; nenhum dado pessoal alterado. RLS e CAS.
Segurança sem novos alertas; aviso Auth de senhas vazadas desativado permanece.
Groq real com referências fictícias/escritas simuladas confirmou nota/lista/meta/
despesa/lembrança/consulta; ferramenta/schema separados corrigiram confusão de
nota com memória. Pesquisa, visão de figura fictícia e clima públicos passaram.
Backend: 76 testes. Android: 25 testes unitários, compilação e lint inicial sem
erros (65 avisos). Nova navegação e persistência de imagem preparadas, NÃO rodadas.

Build final aprovado: 25 testes Android; lint zero erros/66 avisos. APK de instalação
android/build/releases/Koiwai-1.0.apk; hash 5a0b32f69ba2ac920d3fd05af3876ee0b6b5b0ac305da5a0e26b17394c06ea55.
Código principal GitHub 489f6d2; correção final backend 1550f68 publicada:
Render Free Live dep-db3sqqid0e5s73f014ng. Health 200, chat/desfazer pessoal
sem sessão 401, demo produção 404; logs recentes de aplicação sem nível error.
Durante o deploy houve duas sondagens 502, resolvidas após Live.
Acabamento de voz lê primeira resposta nova, preserva silêncio no histórico
antigo e não inicia fala automática em segundo plano. Conferir no Poco.
Último APK instalado ainda é o checkpoint de conexão abaixo, não esta V1.0.
Próximo com Poco: instalar preservando dados, conferir migração, áreas, comandos,
voz/fotos/layout, notificações e relatórios. Só marcar o que foi executado.

## Correção mais recente — chat real, 08/10/2026

O checkpoint de conexão abaixo foi insuficiente: a tela mostrou envio novo com
503. Reproduzido com histórico real; log seguro identificou Groq 400
json_validate_failed. Correção 90d5a9f publicada, Render Free Live
(dep-db3qpu4s728c73fstf7g): formato JSON explícito e respostas históricas
apresentadas no protocolo atual; preserva história, validações e RLS. App
atualizado distingue erro de formato de indisponibilidade. Sem retry automático.
60 testes backend/22 Android; build e lint aprovados. Reenvio REAL da saudação
pendente pelo ChatRepository passou em 4,673 s e salvou SENT após o deploy.
APK SHA256 6e54d882e29a5f87833ca92b765f3a38079d678c3f727d1384b1c983ef76adbe.
Detalhes e limites em CONEXAO_2026_10_08.md, seção posterior. Não atribuir esta
falha à VPN nem tratar toda recusa como ausência de chave. Pack 2 físico segue
parcial; escola/VPN e teste físico PC desligado ainda pendentes.

## Checkpoint prioritário — conexão, 08/10/2026

Leia CONEXAO_2026_10_08.md. Erro relatado era resolução DNS do servidor Render,
também observado com PC ligado. Causa na escola ainda não reproduzida. No Poco
conectado agora, resolução do domínio e conversa real fictícia passaram antes e
depois da atualização. Não houve mudança de DNS/VPN/rede ou backend.
Novo APK instalado preservando dados: mensagens claras para falhas de conexão,
inclusive erro DNS antigo salvo. 22 testes Android; lint 0 erros/55 avisos;
LiveBackendTest passou após instalação (1 teste, 2,833 s).
SHA256 dcea49ae43a538ac705eea2d565b9fe3aa86c598688d341406a9bb7e0bc396b4.
Envio antigo só é reenviado manualmente; teste com PC desligado/VPN da escola
continua pendente. Isso não aprova os novos fluxos funcionais do Pack 2 abaixo.

## Checkpoint prioritário — Pack 2, 07/10/2026

**Leia este bloco e PACK_2.md antes dos registros históricos abaixo.** Usuário
pediu uma assistente capaz de agir: concluir tarefas no chat e eliminar revisão
rotineira de pedidos claros. Implementado: criar/concluir/reabrir/editar/arquivar/
desfazer por conversa, recibos atômicos e proteção contra repetição/versão antiga;
Room 4 preserva histórico; Rotina filtra arquivadas e lembretes invalidam versão antiga.
Relatórios semanais/mensais/semestrais/anuais com fontes, cobertura parcial,
cache, capítulos e preparação/entrega opcionais pelo Android.

Banco remoto: cinco migrações Pack 2 aplicadas, RLS e verificação fictícia com
rollback aprovadas. Backend 58 testes; Android 19; APK/testes compilados; lint
0 erros/55 avisos. Groq real com dados fictícios/escritas simuladas confirmou criar,
concluir, editar data/hora e arquivar; síntese com fontes fictícias também aprovada.
Cota 429 interrompeu a primeira sequência; checagem posterior dos dois fluxos
restantes passou, sem contornar a cota ou trocar de modelo. Segurança: aviso existente
de proteção contra senhas vazadas; performance: índice FK acrescentado.

**APK atualizado no Poco:** usuário reconectou, app e pacote de testes instalados
via atualização, sem desinstalar. ChatPersistenceTest passou **5 testes** em
0,278 s: migração até Room 4, reconciliação, persistência/proposta/recibo e desfazer.
Usuário desconectou novamente para ir à escola antes dos novos testes funcionais.
PackTwoLiveTest e PackTwoNavigationTest preparados/compilados, **não executados**.
Próximo é validar novas ações, relatório/fontes e avisos reais. Código do APK
instalado corresponde a 1537df2; backend continua 06c003c. Testes/docs posteriores
não mudam o aplicativo instalado. Não repetir instalação sem mudança de app.
APK android/app/build/outputs/apk/debug/app-debug.apk;
SHA256 60cb5fe8dd335996cb8764c634fbe3e67b42beda7a0e6846fc57581d5095385f.
Backend **06c003c** publicado no Render Free como **Live**;
deploy dep-db3akiflk1mc73a03phg. Health HTTPS 200, relatórios/desfazer sem sessão
401, demonstração em produção 404; logs recentes sem erro de aplicação.
O acabamento posterior é somente Android/documentação, sem mudança de backend.
Pack 2 tem validação física parcial; V1 não concluída. Reset só na futura V1,
com escopo combinado. Orçamento R$0 e visual aprovado mantidos.

Os relatos abaixo documentam entregas anteriores e podem conter pendências já
implementadas nesta etapa. Limites e próximos testes em PACK_2.md.

Resumo de continuidade atualizado em **07/10/2026**. Este é o ponto de partida
atual; ESTADO_ATUAL.md e REVISAO_2026_10_07.md contêm registros históricos que
podem dizer “pendente” sobre entregas já concluídas. Confira código e último
registro antes de mudar algo. Não confunda implementação com teste aprovado.

## Projeto, pessoa e identidade

Koiwai, ou Koi, é uma assistente pessoal com identidade própria, continuidade e
memória revisável. Android é o primeiro cliente; Windows vem depois. Orçamento
confirmado: **R$0**. Não contratar planos, adicionar cartão ou criar serviços pagos.

O usuário aprovou a personagem e as telas refinadas: estilo gamer expressivo,
roxo/azul/vermelho, contraste com preto/branco, fundos animados, transições suaves
e botões reagindo. Preservar esse visual. Há redução de movimento persistente.

Pedido mais recente: Koi **fofinha, alegre, carinhosa e natural**, com humor e
emojis moderados; nada de tom técnico ou burocrático sem necessidade. A identidade
é feminina, próxima e competente. Adaptar o tom a assuntos sérios; não exagerar
apelidos/elogios, inventar fatos ou fingir ações. Isso é configuração de instruções,
não treinamento de um modelo do zero. Prompt em backend/app/core/ai.py.

A especificação original DOCX e o chat Antigo orientaram o plano em entregas
anteriores. Não foram reabertos neste checkpoint; este resumo deriva do código,
da documentação existente, das decisões do usuário e dos testes registrados.

## Onde está o trabalho

- Repositório real: `C:/Thiago/Projetos/assistente-pessoal`.
- GitHub: https://github.com/Thiagofefe54/assistente-pessoal, branch main.
- Backend: https://koiwai-backend.onrender.com, Render Free, deploy manual.
  Último backend publicado: commit 31197a6, Chat↔Tarefas; Live e health 200.
  Commits posteriores de documentação podem ser mais recentes sem novo deploy.
- Android: Kotlin/Compose, Room e WorkManager; backend: Python/FastAPI.
- Supabase: projeto Koiwai, Auth e Postgres com RLS por dono.
- A pasta do projeto ChatGPT é um espelho de referências; `sources/` é somente leitura.
- Documentação detalhada: ROADMAP.md, BACKEND.md, SUPABASE.md, IA_GROQ.md,
  HOSPEDAGEM_RENDER.md e MEMORIA_CONFIRMADA.md nesta pasta.

## O que funciona e foi confirmado

- Home, Chat, Memória, Rotina e Configurações navegáveis; saudação personalizável.
- Cadastro/login, confirmação de e-mail, sessão criptografada no Android Keystore
  e renovação. Histórico local separado por conta, importação explícita e sincronização.
- Chat real por HTTPS, contexto recente, histórico persistente, dias/horários/busca,
  estados de envio/falha e nova tentativa manual. Diário legível offline.
- Supabase reconcilia mensagens por UUID; mensagens interrompidas não são reenviadas
  automaticamente. Rascunho e histórico não dependem de ficar na tela do chat.
- Backend valida a conta no Supabase. Sem token/invalidado: 401. Demo: 404 em produção.
- IA Groq: GPT-OSS 120B principal e 20B reserva para falhas temporárias 500/502/503.
  Não trocar automaticamente em 429 ou timeout. Plus não fornece essa API ao app.
- Contexto: até 20 mensagens concluídas anteriores ao envio, 12 mil caracteres ao
  todo, 4 mil por item; mensagem atual até 8 mil. Não é o diário inteiro.
- Testes anteriores no Poco: três de persistência e conversa autenticada com
  contexto fictício aprovados. Usuário confirmou conversa dentro e fora de casa.
- Usuário observou despertar em aproximadamente 50 segundos após ficar de 12:03
  até 12:40 sem usar, seguido de respostas rápidas. Não cravar medição exata.
- **PC fisicamente desligado ainda não foi testado**: o usuário corrigiu esse relato.
  O endereço usado é o Render e a arquitetura não depende do processo local.

## Memória confirmada — entrega atual

Até 20 lembranças de 500 caracteres por conta, em Preferência, Objetivo, Rotina
ou Sobre mim. Pessoa confirma, edita e apaga; pode guardar uma mensagem enviada
e sincronizada do diário, mantendo a fonte original. Nenhum fato pessoal foi
criado pelo agente como exemplo.

Tabela memory_facts no Supabase com RLS, fonte da mesma conta, limite de posições
e timestamps do servidor. App protege edição/exclusão contra versão antiga.
O backend lê fatos com a sessão do usuário, sem service_role, e os fornece ao
modelo como dados. A interface informa o envio dessas lembranças ao provedor de IA.

Alterações exigem internet; não há cache persistente offline de fatos nem fila de
edições. Apagar uma lembrança não apaga o diário nem respostas anteriores; o
histórico recente ainda pode conter a informação. IA ainda não salva fatos sozinha.

O novo APK foi instalado no Poco em 07/10/2026. **MemoryLiveTest passou**: criação,
correção, recusa de edição antiga, recuperação de Farol de Rubi sem histórico
recente e exclusão da fixture. **MemoryNavigationTest também passou**: abertura da área,
editor, validação do botão, cancelamento sem salvar e retorno ao diário.
O teste da interface só terminou após abrir a Koiwai em primeiro plano; não
confundir a primeira execução interrompida com falha da memória.

## Validação e limites

- 23 testes backend e sete testes unitários Android aprovados; APK e testes compilados.
- Lint: zero erros, 27 avisos existentes. SQL fictício com rollback verificou dono,
  fonte, limite e concorrência; não ficaram contas/lembranças fictícias persistentes.
- Função antiga rls_auto_enable teve EXECUTE público revogado; event trigger preservado.
  Advisor mantém aviso existente de proteção contra senhas vazadas desativada.
- Render dorme após 15 minutos sem tráfego e desperta sob demanda. Há 750 horas
  mensais compartilhadas no workspace, cotas de tráfego/build, possíveis reinícios
  e suspensão por cotas/tráfego externo excessivo. Não há garantia de 24/7.
- Disco Render é temporário; histórico e fatos duradouros ficam no Supabase.
  Groq e Supabase têm limites próprios. Não gerar tráfego artificial para manter acordado.

## Segurança e decisões que não devem se perder

Segredos somente em `.env` privado e variáveis do Render; nunca no GitHub, resumo,
APK ou chat. Android usa URL e chave publishable do Supabase, não a chave Groq.
Configuração privada: android/koiwai.local.properties, ignorada pelo Git.

Usuário perdeu apenas a senha do banco Supabase; ainda acessa painel e tem a senha
da Koiwai. App não depende da senha direta do banco: não resetar por iniciativa própria.
Uma chave Groq foi compartilhada no chat. Usuário decidiu substituí-la depois dos
testes; registrar pendência sem bloquear trabalho ou repetir o valor.
Ideia de configurar keys pelo app foi anotada; tela sozinha não torna um segredo
seguro. Gestão de chaves pelo app ainda não implementada.

## O que falta e próximos pequenos objetivos

### Revisão e integração Chat ↔ Tarefas — 07/10/2026

Após o usuário constatar que o chat não conhecia tarefas criadas em Rotina,
a entrega foi ampliada: consulta atual protegida por dono/RLS e propostas de
criação na conversa, editáveis antes de salvar. Data/fuso atuais, validação de
agenda, identidade estável para impedir duplicações, Room 3 com migração sem
apagar histórico e descarte de proposta sem apagar conversa. Metadados das
propostas ficam somente no celular; tarefas salvas continuam sincronizadas.
Até 60 tarefas no contexto da IA; lista incompleta é explicitada. Editar/concluir/
apagar por chat ainda falta. Filtro Hoje adicionado à Rotina.
Corrigidos texto antigo sobre PC/chat simulado, vida útil da memória ao trocar
de conta, ativação repetida de componentes e resposta de sucesso após escrita
confirmada mesmo se atualizar a lista falhar. Editor não fecha após falha de
salvamento. Dependência tzdata fixada para datas no Windows.
36 testes backend e teste Groq fictício aprovados. RLS/políticas conferidas.
Backend publicado/Live em 31197a6, APK instalado no Poco e testes reais de consulta/proposta/persistência aprovados;
ver AUDITORIA_CHAT_TAREFAS.md. Pack 2 não está entregue.

### Pack 1 em 07/10/2026 — registro atual

Usuário decidiu completar o celular antes de Windows, em cinco packs grandes.
Pack 1 implementado e publicado no Render como Live no commit 04853d7:
sugestões revisáveis (sob demanda no capítulo/atalho Chat), resumo diário com
fontes e tarefas reais com CRUD, datas/horários/repetição e registro de conclusão.
Banco remoto aplicado com RLS; teste SQL com rollback aprovado. 30 testes backend,
nove testes unitários Android, build APK/testes e lint (0 erros/27 avisos) aprovados.
APK atualizado no Poco sem apagar dados. Teste real de tarefas e teste de navegação
do editor aprovados. Teste real dos resumos/sugestões também passou: fontes válidas,
cache sem nova IA, detecção de mensagem tardia, atualização e nenhuma memória
salva automaticamente. Fixtures fictícias removidas da nuvem e Room; contagens
remotas conferidas como zero. Mais detalhes e limites em PACK_1.md.

Os resumos cobrem somente mensagens sincronizadas do usuário no dia registrado,
até 500/60 mil caracteres; oito tópicos com fontes. Não há geração automática.
Novas mensagens marcam o resumo antigo como desatualizado. Mesmo hash devolve
cache, sem nova IA. Mudança durante geração e versão concorrente recusam gravação.
Sugestões não persistem nem viram fatos sem confirmação. Recusa não é permanente:
outro pedido pode sugerir novamente. Lembrança aceita guarda a primeira fonte.

Tarefas online, até 500 por conta. Concluir repetição registra e avança uma
ocorrência; mensal ajusta ao fim do mês e usa essa nova data no ciclo seguinte.
Lembretes locais implementados em APK posterior, teste físico pendente; sem cache/fila offline. Agenda/notas/treinos/finanças
continuam visuais. Próximo: Pack 2, lembretes e relatórios periódicos, depois voz,
ferramentas pessoais e offline/acabamento. O APK recebeu o ajuste final de horário
de vencimento e identificação explícita de resumo parcial do dia atual.

1. Usar o Pack 1 no dia a dia; depois implementar o Pack 2.
2. Observar o tom no uso real e testar uso com PC realmente desligado quando conveniente.
3. Sugestões revisáveis e resumo diário implementados, publicados e testados.
4. Ampliar mais tarde: cache offline de fatos/tarefas, fila de edições e conflitos.
5. Relatórios semanais, mensais, semestrais e anuais; primeiro ano parcial começa no
   primeiro registro. Não inventar meses anteriores nem duplicar períodos equivalentes.
6. Depois: voz, agenda/notas/treinos/finanças reais. Windows após os packs do celular.
7. Proatividade/notificações, ações autorizadas de PC/Android e offline ampliado são futuras.

Tarefas funcionam; demais categorias de Rotina ainda são visuais. Clima não configurado.
Não há voz, navegação web da Koi, relatórios automáticos ou controle
do computador pelo app. Recuperação de conta no app, cotas próprias por usuário,
retenção/exclusão completa e confiabilidade prolongada também faltam.
Versões 0.0.1 a 1.0.0 são etapas planejadas, não releases já concluídas; versionName
do template Android não representa estágio real. Consultar ROADMAP.md.

## Retomada técnica sem expor dados

Começar por git status, este resumo e o último registro de ESTADO_ATUAL.md.
No Windows, git pode exigir `-c safe.directory=C:/Thiago/Projetos/assistente-pessoal`.
Gradle usa o JBR do Android Studio; o contorno da falha de loopback usa TEMP/TMP
em android/build/jvm-tmp e JAVA_TOOL_OPTIONS com jdk.net.unixdomain.tmpdir, apenas
no processo. Não alterar variáveis globais do sistema.

Instalar APK com atualização (`adb install -r`), sem desinstalar ou apagar dados.
Não usar `-g`: Poco recusou concessão automática de permissões. Usuário precisa
aceitar o diálogo; não desativar proteções. Testes reais usam a sessão dentro do
aparelho e apenas dados fictícios, sem exportar tokens ou ler o diário pessoal.
MemoryLiveTest é opt-in com koiLiveBackend=true. Se uma abordagem travar, interromper,
isolar o teste e verificar o motivo; não declarar sucesso só pelo código de saída.

Fonte dos limites: https://render.com/docs/free. Atualizar este arquivo ao encerrar
uma entrega, distinguindo feito, testado, publicado e instalado.

## Checkpoint adicional: revisão geral e lembretes — 07/10/2026

Leia docs/REVISAO_FINAL_2026_10_07.md antes de retomar. Novo APK preparado,
ainda não instalado porque usuário saiu/desconectou celular. Editor com horário
em destaque, memória sem perder texto após falha, transporte limitado e validado,
lembretes opcionais locais com Adiar 15 min/Concluir e descanso padrão 22h–08h.
Android pode atrasar os avisos; não é alarme exato. Sincronização de programação
aproximadamente horária pelo Supabase, sem keepalive/IA/serviço pago.
17 testes Android + 36 backend aprovados; APK/test APK compilados; lint 0 erros,
45 avisos (18 novos de preferência/KTX). Validação física dessa parte pendente.
Antes de sair, Poco aprovou 4 testes de persistência e 1 real Chat↔Tarefas; fixtures
removidas. Teste final do editor/navegação ainda precisa ser repetido no aparelho.
Próximo: atualizar celular preservando dados e testar notificações/descanso/ações;
depois relatórios periódicos. Pack 2 permanece parcial; demais packs não concluídos.

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

Painel local V1.7 recarregado no Brave e cartão interativo de voz conferido.
Checklist manual de voz/agenda enviada; ainda sem resposta neste checkpoint.

Confirmação do usuário após pedido de teste de voz/agenda: “funcionou sim” (09/10/2026).
Screenshot no Poco confirma agenda consultada com eventos carregados, sem erro visível.
Evento de feriado aparece repetido; origem das duplicatas ainda não investigada
(podem existir várias agendas sincronizadas). Não afirmar deduplicação concluída.
Voz confirmada pelo relato do usuário, sem aferição independente de áudio.
Escolha padrão/gesto físico e consulta real offline ainda não confirmados.

Atualização do usuário: confirmou Inter conectado no Meu Pluggy e pediu excluir
Next/nextJoy. Somente Inter e somente saldo. Dashboard Pluggy acessado pelo
usuário; proxy/credenciais ainda não configurados na Koi. Banco no provedor
conectado não significa integração Koi ativa.
