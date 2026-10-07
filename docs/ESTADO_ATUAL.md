# Estado atual da Koiwai

## Dia 02 — Histórico local do chat

O Android usa Kotlin e Compose; o backend FastAPI ainda responde com saudações e eco.
O histórico passou a usar Room, com mensagens em ordem de inserção, IDs UUID,
instante em UTC, fuso e data local preservados. A tela agrupa por dia e mostra horário.
Envios têm estado de envio, sucesso ou falha; uma tentativa manual reutiliza a mensagem.
Ao reiniciar o processo, envios interrompidos ficam como falha, sem reenvio automático.
A resposta e a confirmação do envio são gravadas na mesma transação.

Esta etapa não conclui a v0.1.0: faltam login, nuvem, sincronização e uso com PC desligado.
O backend local continua em http://IP_DO_PC:8000; precisa estar acessível pelo Poco.
Não existe memória resumida ou integração com um modelo de IA nesta entrega.
Mensagens da versão anterior estavam somente na RAM e não podem ser recuperadas.

## Validação no Poco

1. Iniciar o backend e instalar a atualização do app sem desinstalar a versão anterior.
2. Enviar três mensagens, incluindo aspas, acentos, emoji e quebra de linha.
3. Conferir ordem, respostas, horários, rolagem e campo acima do teclado.
4. Voltar à Home e entrar de novo; fechar o app e reabrir; conferir o mesmo histórico.
5. Desligar o backend e enviar uma mensagem; conferir falha e texto salvo.
6. Religar o backend e tocar Tentar novamente; conferir apenas uma mensagem e uma resposta.
7. Encerrar o processo durante envio; reabrir e conferir opção de tentar novamente.
8. Testar troca de orientação e virada do dia; o histórico deve manter os horários originais.

Os testes de banco usam dados fictícios e arquivo separado, removido ao terminar.
Não versionar banco local ou conversas pessoais.

## Verificação desta entrega

Em 04/10/2026, o APK de debug e o APK de testes compilaram com sucesso.
O teste automático de virada do dia passou. A análise lint terminou com zero erros;
os avisos incluem dependências com versões mais recentes e recursos do template não usados.
Os testes instrumentados de persistência foram compilados, mas não executados:
nenhum aparelho apareceu conectado por USB. A validação visual e funcional no Poco está pendente.
As alterações permanecem locais, sem commit ou publicação no GitHub nesta etapa.

## Validação no Poco informada pelo usuário

Em 04/10/2026, o usuário confirmou que o histórico funcionou e, após as instruções
para fechar/reabrir o app e testar falha seguida de nova tentativa, informou:
"funcionou, tá certinho". A validação manual relatada passou; o teste instrumentado
continua sem resultado de execução registrado.

## Próxima etapa iniciada

Login e sincronização com Supabase. O plugin está instalado e habilitado, mas suas
ferramentas de projeto/banco não foram expostas nesta sessão. Foi possível conferir
o painel pelo navegador autenticado. Não há projeto chamado Koiwai na lista atual;
a escolha do projeto está pendente do usuário. Nenhum banco remoto foi alterado.


## Supabase e Android atualizados

O usuário criou um projeto Supabase separado para a Koiwai. A tabela
chat_messages foi criada e os testes SQL de isolamento, acesso anônimo,
idempotência e data local passaram, com rollback de todos os dados fictícios.
Login/cadastro, sessão criptografada e sincronização estão implementados no Android.
O Poco recebeu a atualização por instalação normal mantendo os dados.
Três testes instrumentados passaram no aparelho: migração Room 1→2 preservando
o histórico, reabertura/recuperação e reconciliação sem duplicações.
O teste unitário de virada do dia passou. A primeira conta e a primeira
sincronização com credenciais reais ainda precisam ser testadas pelo usuário.

O Security Advisor aponta dois avisos na função automática public.rls_auto_enable().
A retirada de EXECUTE dos papéis PUBLIC, anon e authenticated foi rejeitada pela
revisão automática por exigir autorização específica. Esse ajuste não foi aplicado.
A tabela de conversas tem RLS e só permite SELECT/INSERT pelo dono.
Nenhuma senha de banco, service_role ou token privado foi incluído no código.
Nenhum commit/push foi feito nesta etapa.

## Navegação Android — 04/10/2026

O usuário confirmou login e sincronização funcionando. As cinco abas agora navegam;
Memória abre o histórico real por dia; Rotina apresenta categorias em preparação;
Configurações gerencia conta e salva o tratamento da saudação localmente.
A Home mantém personagem/identidade e usa data/hora reais e estado da conta.
A atualização foi instalada no Poco preservando os dados. Compilação passou,
lint terminou com zero erros e 23 avisos e NavigationTest passou no aparelho.
Detalhes e pendências estão em ENTREGA_NAVEGACAO.md. A v0.1.0 continua em andamento:
o backend ainda é local e simulado. Backup dos fontes anterior à mudança é local;
nenhum commit/push foi feito nesta entrega.

## Refinamento visual — 04/10/2026

As telas atuais receberam a direção gamer/expressiva escolhida pelo usuário:
fundos animados, transições, resposta ao toque, identidade própria por área,
linha do tempo e busca na Memória, painel de missões na Rotina e central de
personalização. Reduzir movimento é uma preferência persistida. A Home preserva
a personagem. Chat, conta, histórico e sincronização existentes foram mantidos.
O APK foi atualizado no Poco. Build e teste unitário passaram; lint da aplicação
terminou com zero erros e 27 avisos; três testes de interface passaram no aparelho.
Capturas finais foram conferidas e a Home foi reaberta no uso normal.
Detalhes: REFINAMENTO_VISUAL.md. Cadastros de Rotina, resumos e IA continuam pendentes.
Backup anterior preservado localmente; nenhum commit/push foi feito nesta entrega.


## Checkpoint e autenticação do backend — 04/10/2026

README público criado com identidade, capacidades atuais, arquitetura, execução
e metas; ROADMAP.md consolida etapas e memória por períodos. A v0.1.0 continua
em andamento. Todos os fontes e testes das entregas anteriores entram neste
checkpoint, sem bancos pessoais, sessões, capturas privadas ou credenciais.

A rota /api/v1/chat agora exige HTTPS e token validado com Supabase Auth;
/api/v1/chat/me retorna a identidade verificada. /api/v1/chat/demo mantém as
respostas simuladas sem credenciais e existe apenas em desenvolvimento.
Produção exige configuração de Auth. O app tem origem de backend configurável,
obtém o token apenas em HTTPS e bloqueia HTTP em release e redirecionamentos.

Doze testes de backend passaram com dados fictícios, incluindo falhas, transporte,
configuração e identidade. Não houve uso de credenciais reais pelo agente.
Hospedagem, teste autenticado real de ponta a ponta e IA continuam pendentes.
Esta entrega atualiza fontes e APK compilado; a instalação em uso no celular e
o processo de servidor local não foram substituídos neste checkpoint.

Verificação final: assembleDebug e cinco testes unitários Android passaram; lint
terminou com 0 erros e 27 avisos. Configuração da instalação Android foi
separada em koiwai.local.properties, ignorado pelo Git. O conteúdo público usa
apenas exemplos, sem IP local, identificador do projeto ou chave da instalação.

## Revisão e pequenos objetivos — 07/10/2026

Código e documentação revisados; 12 testes do backend passaram novamente.
Metadados remotos confirmam projeto ativo e tabela de histórico com RLS e registros.
Não foram lidas conversas privadas nem alterados usuários, senhas ou dados.
Orçamento confirmado R$0. Balanço completo e metas M01–M10 em REVISAO_2026_10_07.md.
A revisão registra a separação entre senhas de painel, banco e conta do app;
o app atual não depende da senha de conexão direta ao banco.
Hospedagem, IA, contexto e recuperação de conta no app continuam pendentes.

## Primeira integração Groq — 07/10/2026

Servidor preparado para GPT-OSS 120B, com 20B como reserva em falhas temporárias;
429 e timeout não causam troca automática. O app prepara contexto recente da conta,
limitado a mensagens concluídas e anteriores ao envio, e envia apenas em HTTPS.
Chave Groq somente no .env privado. A demonstração HTTP continua simulada.

19 testes de backend, sete testes unitários Android e assembleDebug passaram.
A chave foi configurada pelo usuário após falha de verificação na criação automática.
Testes reais com conversa fictícia confirmaram resposta e contexto no principal,
além de disponibilidade da reserva. Plano Free e cotas conferidos no painel.
Lint: zero erros e 27 avisos. Sem publicação, troca de APK instalado ou plano pago.
Detalhes e limitações em IA_GROQ.md. Hospedagem HTTPS e fluxo completo pendentes.

## Publicação gratuita e atualização do Poco — 07/10/2026

Backend publicado em https://koiwai-backend.onrender.com no Render Free,
com configuração de produção e credenciais nos segredos do serviço.
Validação externa: health HTTPS 200; conversa sem token ou com token inválido 401;
rota de demonstração 404. Nenhum plano pago foi ativado.

APK atualizado no Poco sem desinstalar; endereço privado do app passou a usar
HTTPS na nuvem. Pacote de testes instalado. Os três testes de persistência,
migração e reconciliação passaram no aparelho, usando bancos de teste isolados.
Após login feito pelo usuário, o teste real autenticado passou no Poco: a IA
recuperou o nome Tucano Violeta do contexto fictício via HTTPS no Render.
Não foram exportados senha ou tokens nem enviadas conversas pessoais. O teste
usou diretamente o endereço online, sem depender do processo local; o computador
não foi desligado durante a execução. Os quatro testes no aparelho passaram
(três de persistência e um de integração real), em execuções separadas.
## Lembranças confirmadas — desenvolvimento em 07/10/2026

Primeiro incremento de memória útil: até 20 fatos curtos confirmados pela pessoa,
com categorias, edição, exclusão e fonte opcional no diário. A tabela nova no
Supabase tem RLS de dono, fonte da mesma conta e limite de posições por usuário.
App preparado com nova tela, confirmação e operações online; nenhuma alteração
nas conversas existentes nem na versão do banco Room.

O servidor foi implementado para consultar as lembranças usando a sessão da
pessoa, sem service_role, e fornecê-las como dados ao modelo. 23 testes de backend
e sete testes Android passaram; APK e pacote de testes compilados; lint sem erros
com 27 avisos existentes. Teste SQL com dados fictícios e rollback confirmou
isolamento entre contas, fonte, limite e recusa de edição antiga.

A revisão de segurança encontrou permissões públicas numa função de inicialização
RLS já existente. A execução direta dessa função foi revogada para os clientes;
o mecanismo de inicialização de tabelas permaneceu preservado. O único aviso
restante foi a proteção de senhas vazadas, já desativada no projeto; não foi
alterada a configuração de Auth nem contratado plano pago.

Poco desconectado pelo usuário antes dos novos testes de aparelho. A nova tela
não foi instalada nem validada no telefone nesta etapa. Publicação e verificação
externa são registradas abaixo quando concluídas. Detalhes em MEMORIA_CONFIRMADA.md.