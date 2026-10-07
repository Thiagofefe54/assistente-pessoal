# Koiwai — leia primeiro ao retomar

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
concluir e editar data/hora. Cota 429 interrompeu arquivar e síntese: não alegar
aprovação desses dois fluxos com IA real nesta sequência. Segurança: aviso existente
de proteção contra senhas vazadas; performance: índice FK acrescentado.

**APK pronto, ainda NÃO instalado:** usuário saiu/desconectou Poco. Último APK
instalado continua e230382, com lembretes já testados. Próximo é atualizar sem
apagar dados e validar as novas ações, relatório/fontes, notificação e migração.
APK android/app/build/outputs/apk/debug/app-debug.apk;
SHA256 2a022c591eb225626bdce8dae0f885e2040e757b76cf4a15061e22ed39bba18f.
Publicação do backend será registrada após verificação; não inferir pelo APK.
Pack 2 não está fisicamente validado; V1 não concluída. Reset só na futura V1,
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
