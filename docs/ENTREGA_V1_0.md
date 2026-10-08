# Koiwai — entrega móvel V1.0, 08/10/2026

Este relatório registra a ampliação preparada enquanto o Poco estava desconectado.
V1.0 é a numeração escolhida pelo usuário para este pacote; V1.1 será usada para
correções e V2.0 para a etapa considerada definitiva. A numeração não representa
garantia de ausência de erros. Instalação e validação física continuam separadas.

## O que já existia e foi preservado

- Identidade da Koi: feminina, próxima, alegre, natural; roxo, azul e vermelho,
  contraste escuro, personagem, fundos e transições animadas; redução de movimento.
- Cadastro/login Supabase, confirmação de e-mail e sessão protegida no Android.
- Histórico por conta, persistência local, leitura sem rede, sincronização e diário
  por dia. Mensagens que falham são reenviadas somente quando a pessoa pede.
- Lembranças confirmadas revisáveis, sugestões com revisão e resumos diários.
- Tarefas com data, horário, repetição diária/semanal/mensal e conclusão;
  ações claras no chat criam, editam, concluem, reabrem, arquivam e desfazem.
- Avisos locais opcionais, período silencioso, adiar e concluir pela notificação.
- Relatórios de semana, mês, semestre e ano com fontes e indicação de cobertura.
- Backend FastAPI no Render Free e dados no Supabase; chave Groq no servidor.
  O computador não hospeda o serviço de produção. O Render pode dormir e demorar
  no primeiro pedido; rede/VPN, cotas e indisponibilidade de provedores também afetam.

## O que este pacote acrescenta

| Área | Comportamento implementado |
| --- | --- |
| Prazos relativos | “Daqui 10 minutos” usa o horário original da mensagem, preservado ao reenviar; atravessa meia-noite corretamente. Arredondamento para cima até o próximo minuto evita aviso antecipado. Pedidos claros simples dispensam a IA para calcular/criar. |
| Agenda | Visualização das tarefas que têm data, com editor e ações reais. Não é sincronização bidirecional com Google Calendar. |
| Hábitos | Visualização das tarefas repetidas; criação começa com repetição diária. Conclusão avança a ocorrência e mostra a contagem existente. Não calcula sequências de dias consecutivos. |
| Notas | Criar, buscar, editar, arquivar, recuperar, compartilhar e desfazer a última ação. |
| Listas | Uma linha por item; caixas para marcar/desmarcar; edição, arquivo e compartilhamento. |
| Metas | Registro de objetivo e progresso de 0 a 100%, com barra visual e edição. |
| Treinos | Registros de treino com data e detalhes. Não é prescrição de exercícios nem integração com sensores. |
| Finanças | Receitas/despesas BRL em centavos exatos; filtros de mês/arquivados/busca, totais e diferença. Não acessa banco, não transfere dinheiro e não representa saldo bancário. |
| Chat e registros | Ferramentas estruturadas consultam dados atuais e executam uma ação explícita por mensagem. Resultado confirmado, botão para abrir a área e desfazer. Consultas não escrevem. Tarefas e registros pessoais têm roteamento separado. |
| Memória no chat | Pedidos explícitos permitem criar/editar/apagar lembranças; apagar pelo chat pode ser desfeito se não houver mudança posterior. Continua possível revisar na tela Memória. |
| Voz | Botão abre o reconhecimento de fala do Android; texto entra no campo para revisar/enviar. Ouvir, parar e preferência para ler novas respostas enquanto o chat está aberto. Não é microfone contínuo nem ativação por palavra-chave. |
| Fotos e imagens | Escolher imagem ou abrir a câmera do Android; prévia e remoção antes de enviar; análise/leitura de texto pela IA. A câmera envia uma miniatura. A IA não observa o celular continuamente e não executa ordens vindas da imagem. |
| Pesquisa na conversa | “Pesquise na internet…” utiliza browser_search do Groq e entrega fontes com botões para abrir. Só o pedido atual é enviado; histórico, tarefas e lembranças não são acrescentados à pesquisa. |
| Clima | Escolha explícita da cidade, temperatura, sensação, mínima/máxima e chance de chuva, com horário/fuso dos dados; cache local e atualização. Fonte Open-Meteo; sem GPS. |
| Calculadora | Decimal, operadores, parênteses e %. Funciona localmente e também reconhece expressões numéricas no chat. Sem execução de código; % significa dividir por 100. |
| Foco | Contador de 25 minutos/pausa de 5 minutos, com horário final salvo neste aparelho. Não envia notificação de término nesta versão. |
| Texto | Abrir TXT UTF-8 de até 16 KB, editar, exportar e compartilhar. Não lê PDF nesta versão. |
| Calendário externo | Abre o formulário de compromisso no aplicativo instalado. A pessoa escolhe data e confirma lá; abrir o formulário não significa evento salvo. |

## Dados, limites e confirmação das ações

- Banco novo `koi_personal_records`: até 200 registros por conta, considerando
  ativos e arquivados; conteúdo até 8.000 caracteres, título até 160.
- `koi_tool_receipts` guarda o resultado das ações e permite repetição segura e
  desfazer. Alterações usam a versão atual do registro; conflito não sobrescreve.
- RLS e funções com segurança do invocador limitam cada conta aos seus dados.
  Migração `personal_records_and_reversible_memory` aplicada no projeto Koiwai.
- Verificação SQL com duas contas fictícias passou e foi revertida: criação,
  repetição, edição, versão antiga, desfazer repetido, apagar/recuperar lembrança e
  isolamento entre contas. Nenhum registro pessoal foi usado no teste.
- Consulta pessoal lê até 200 registros; a IA recebe até 20, com conteúdo de cada
  um limitado a 350 caracteres e indicação de truncamento. Totais financeiros
  são calculados pela aplicação, com todos os registros lidos, também para
  hoje, ontem e mês atual. Um registro citado pelo nome pode fornecer até 8.000
  caracteres completos; alterações do conteúdo são bloqueadas se o contexto
  estiver truncado, evitando perda de trechos que a IA não viu.
- As novas telas pessoais precisam de rede para consultar/salvar. Não há fila
  offline durável para criação manual. No chat, a mensagem e a identidade do
  pedido ficam persistidas para reenvio explícito. Não fechar o editor e iniciar
  outro registro para repetir um envio incerto: atualizar primeiro.
- Room passa de 4 para 5 por migração que só acrescenta o campo de imagem; nada
  foi desinstalado, apagado ou resetado. Imagens ficam no histórico local e são
  enviadas à IA quando você toca Enviar; a sincronização Supabase mantém apenas
  o texto dessas mensagens. Não prometer cópia de fotos na nuvem.
- Texto usa Groq com os modelos existentes; visão usa `qwen/qwen3.8-27b`;
  pesquisa usa `openai/gpt-oss-20b` com browser_search. Cota 429 não é contornada.
  ChatGPT Plus não fornece créditos de API. Gemini ainda não foi integrado.
- Nenhum plano pago, cartão, novo serviço pago ou mecanismo para manter Render
  acordado artificialmente foi ativado.

## Revisão e evidências

- Testes automatizados do servidor: 76 passaram, incluindo relógio original,
  prazo cruzando meia-noite, proteção de pedidos citados/negados, centavos,
  versão atual, conflito, reenvio, seleção da ferramenta e autenticação.
- Testes unitários Android: 25 passaram na primeira verificação completa.
- Compilação do app e pacote de testes aprovadas; revisão Android inicial:
  zero erros e 65 avisos. Última execução após acabamento será registrada abaixo.
- Groq real com dados fictícios e escritas simuladas confirmou notas, listas,
  metas, despesa, lembrança e consulta de itens. A primeira tentativa revelou
  confusão entre nota e lembrança: corrigida com ferramenta/schema selecionados
  pela aplicação e conferida novamente. Não houve escrita em dados reais.
- Pesquisa Groq real abriu documentação Android e retornou fontes.
- Visão Groq real leu “KOI TEST 42” de imagem gerada para teste. A resposta também
  continha repetição parcial: leitura de texto por IA pode errar e precisa revisão.
- Open-Meteo real respondeu à consulta pública de São Paulo e à previsão.
- Supabase: verificação de segurança sem novos alertas de schema; permanece o
  aviso anterior de proteção contra senhas vazadas desativada no Auth.
- Novos testes físicos preparados: navegação das áreas e ferramentas e
  persistência de imagem após sincronização só de texto. Não executados sem Poco.

## Miniobjetivos para a volta do celular

1. Instalar o APK por atualização, preservando login, histórico e avisos.
2. Executar persistência/migração até Room 5 e navegar por todas as novas áreas.
3. Criar tarefa fictícia “daqui 10 minutos”, conferir data/hora e concluir no chat.
4. Criar/editar/consultar lista fictícia; marcar item, arquivar, recuperar/desfazer.
5. Conferir centavos e totais financeiros com registros fictícios; limpar apenas
   esses registros quando concluído, sem mexer nos dados da pessoa.
6. Conferir voz do Android, interrupção ao sair do app, seleção de imagem/câmera,
   pesquisa com links e clima; ajustar layout ao tamanho e teclado do Poco.
7. Conferir notificações reais e relatórios do Pack 2, ainda parcialmente validados.
8. Usar sem USB, em outra rede e com PC desligado; investigar VPN/DNS só se houver
   falha reproduzível. Hospedagem remota não elimina problemas na conexão local.

## O que ainda falta para a assistente completa

Validação física e ajustes V1.1 vêm primeiro. Depois: importação de PDF, exportação
completa dos dados da conta, offline para registros pessoais, busca semântica e
melhor recuperação de notas longas; tarefas com prioridades/subtarefas; calendário
externo integrado; alertas do timer; modelos adicionais com limites explícitos;
voz contínua/palavra-chave se viável no Android; lembretes proativos mais ricos.

Integrações com WhatsApp, e-mail, contatos, ligações, aplicativos de terceiros,
GPS/sensores e Windows exigem implementação e permissões próprias. Não estão
prontas nesta entrega. Nenhum projeto consegue “fazer tudo” só com texto da IA:
cada ação precisa de ferramenta real, resultado verificado e teste do aparelho.
Não houve reset: a combinação anterior só se aplica no futuro, após definir escopo.

## Referências técnicas consultadas

- [Groq: visão](https://console.groq.com/docs/vision)
- [Groq: browser_search](https://console.groq.com/docs/tool-use/built-in-tools/browser-search)
- [Android: TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
- [Open-Meteo: previsão](https://open-meteo.com/en/docs)
- [Open-Meteo: busca de cidade](https://open-meteo.com/en/docs/geocoding-api)
- [Supabase: RLS](https://supabase.com/docs/guides/database/postgres/row-level-security)

## Artefato final e publicação

Build final: testes unitários Android 25/25, APK e pacote de testes compilados;
lint **zero erros e 66 avisos**, em sua maioria sugestões de estilo/dependências,
além da recomendação de usar ExifInterface AndroidX. Voz/câmera precisam de teste
físico. Nenhum teste do novo APK foi executado no Poco nesta sessão.

APK: `android/build/releases/Koiwai-1.0.apk`, versão 1.0, versionCode 2, Room 5.
SHA256: `f8090d34cef01c9d619c438f3b525579bf7ca0d874b96ad761a3f7ce33577b4e`.
Não foi instalado. Código principal publicado no GitHub: `489f6d22885fccaf6fc0ec246ca1223e7065a2c5`.
Render Free confirmou Live no deploy `dep-db3sor2j9qps738tgv7g`. Ajuste final
de proteção de conteúdo longo e totais por período será publicado em seguida. O GitHub não recebe APKs, dados ou chaves.
