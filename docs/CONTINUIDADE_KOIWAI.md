# Koiwai — leia primeiro ao retomar

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
  Último código publicado: commit 85aada2, personalidade mais alegre; Live e health 200.
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

1. Observar no uso real o ajuste de personalidade já publicado; memória e navegação
   verificadas no Poco. Próxima implementação: sugestões revisáveis e resumos diários.
2. Observar o tom no uso real e testar uso com PC realmente desligado quando conveniente.
3. Sugerir lembranças a partir de conversas, sempre com revisão antes de salvar.
4. Resumo diário com fontes, cobertura explícita e atualização após sincronização tardia.
5. Relatórios semanais, mensais, semestrais e anuais; primeiro ano parcial começa no
   primeiro registro. Não inventar meses anteriores nem duplicar períodos equivalentes.
6. Depois: tarefas/agenda/notas/treinos/finanças reais, Windows e voz por botão.
7. Proatividade/notificações, ações autorizadas de PC/Android e offline ampliado são futuras.

Rotina atualmente é visual: não executa cadastros/tarefas. Clima não configurado.
Não há voz, navegação web da Koi, notificações, relatórios automáticos ou controle
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
