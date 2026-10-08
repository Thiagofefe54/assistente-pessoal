# Pesquisa de expansão da Koiwai — 08/10/2026

Pesquisa de viabilidade, não entrega de funcionalidades. Estado do app: V1.1 instalada. Usuário relata sucesso nos testes guiados exceto memória, cuja resposta foi estranha. Esse relato é evidência de uso, não aprovação de toda a matriz de testes. Não houve instalação, mudança de permissões, novo provedor, conexão de contas ou contratação nesta pesquisa.

## Decisão principal

Construir uma assistente que compreende pedidos variados e relatos, consulta dados, escolhe ferramentas e verifica resultados. Um catálogo de funções por si só não produz inteligência. A próxima implementação deve unificar interpretação, contexto, execução e retorno, preservando conversa normal.

O Groq documenta chamadas de ferramentas locais: o modelo propõe função e argumentos; a aplicação executa e devolve o resultado ao modelo. Esta é a base recomendada para a Koi, com limites de chamadas e tratamento de erros. [Documentação](https://console.groq.com/docs/tool-use/local-tool-calling).

Fluxo proposto: mensagem atual + contexto relevante → decisão de conversar/consultar/registrar/agir → validação da ferramenta e dos dados → execução no servidor ou no Android → resultado real → resposta natural. Recusa de permissões, recurso ausente e falha devem produzir resultado honesto, sem declarar execução.

A IA pode compreender paráfrases, mas não garante acerto em todas as frases. Pedido implícito precisa ser distinguido de relato, hipótese, citação e negação. “Coloca música para tocar” é um pedido; “estou animado, música rolando” não é autorização para interromper reprodução.

## Matriz de possibilidades

| Área | O que pode entrar | Situação e dependências | Caminho / fonte |
|---|---|---|---|
| Interpretação | Paráfrases, referências a mensagens anteriores, escolha de ferramentas, perguntas só quando faltar dado | Próxima implementação; atual roteamento depende de palavras/verbos | [Groq tools](https://console.groq.com/docs/tool-use/local-tool-calling) |
| Memória | Preferências duradouras, correções, busca e origem; diário separado de fatos | Existe memória confirmada; falha do pedido guiado precisa de correção | Código personal.py/actions.py; limite atual 20 fatos |
| Diário | Chegadas, trabalho, academia, acontecimentos, mudanças de rotina e sono | Planejado; associar data/fuso/origem e distinguir acontecido de planejado | Implementação própria sobre histórico e registros |
| Organização | Tarefas, subtarefas, projetos, compromissos, hábitos, revisão do dia | Tarefas e cadastros existem; ampliar interpretação, modelos e capacidade | Dados próprios e ferramentas estruturadas |
| Finanças pessoais | Entradas/despesas, contas recorrentes, orçamento, vencimentos e comparações | Registros existem; contas/orçamento ainda faltam. Relato recebido não equivale a renda futura | Cálculos estruturados, correção e desfazer; hoje 200 registros totais |
| Voz na Koi | Microfone dentro da própria tela, transcrição parcial, interrupção e resposta falada | Viável com permissão de microfone; reconhecimento local depende do aparelho | [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer) |
| Assistente padrão | Abrir Koi pelo gesto/botão de assistente e sessão própria | Viável como integração Android; usuário precisa escolhê-la; comportamento depende do sistema | [VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService) |
| Chamar “Koi” | Detecção do nome sem abrir manualmente | Pesquisa específica; tornar-se assistente não garante palavra personalizada em todo aparelho | VoiceInteractionService; SpeechRecognizer não foi projetado para escuta contínua |
| Abrir apps | “Quero ouvir música”, “abre meu aplicativo de treino”, resolução local de app | Abertura por botões existe; ligar a decisões do chat e preferências do usuário | [Visibilidade de apps](https://developer.android.com/training/package-visibility/declaring) |
| Música | Tocar, pausar, próxima faixa, consultar faixa ativa | Sessão de mídia e ações suportadas pelo player; acesso às sessões pode exigir listener de notificações autorizado | [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager) |
| Buscar música | Pedir música/artista em um aplicativo compatível | Android oferece intent de reprodução por busca; cada player decide suporte | [Intents comuns](https://developer.android.com/guide/components/intents-common) |
| Spotify completo | Biblioteca, playlists e controle por API | Não tratar como gratuito universal: modo desenvolvimento exige Premium do dono; há quotas e restrições de endpoints | [Mudanças de 2026](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide) |
| Alarmes e timer | Criar alarme/timer no relógio do aparelho | Intents oficiais; permissão SET_ALARM para ações relevantes e suporte do aplicativo | [Intents comuns](https://developer.android.com/guide/components/intents-common) |
| Contatos e comunicação | Escolher contato, abrir discador, preparar mensagem ou compartilhar | Contatos precisam de acesso quando lidos; abrir discador/compositor pode usar apps existentes | Intents comuns; envio e destinatário devem estar definidos |
| SMS e chamadas | Consulta de mensagens/histórico e ações mais profundas | Restrições de papéis e distribuição; não presumir que qualquer app pode ler tudo | [Default handlers](https://developer.android.com/guide/topics/permissions/default-handlers) |
| Notificações | Avisar sobre eventos de aplicativos selecionados, resumir avisos, sugerir registro de dinheiro | Listener autorizado, filtros locais e deduplicação; notificações podem omitir/ocultar dados | [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) |
| Agenda local | Ler/escrever compromissos do calendário Android | Atual app abre formulário; leitura/escrita direta exige permissões e tratamento de conflitos | Intents comuns; Calendar Provider a verificar antes de implementação |
| Google Agenda | Consultar disponibilidade, criar/editar eventos e sincronizar | OAuth e API; uso padrão da Calendar API sem custo adicional, sujeito a quotas | [Quotas](https://developers.google.com/workspace/calendar/api/guides/quota), [escopos](https://developers.google.com/workspace/calendar/api/auth) |
| Google Tasks | Listar, criar, editar e concluir tarefas | OAuth; definir qual sistema é fonte e evitar duplicatas entre Koi e Google | [Google Tasks](https://developers.google.com/workspace/tasks) |
| Gmail | Resumir e-mails, identificar compromissos, preparar respostas | OAuth; escopos restritos podem exigir verificação e avaliação conforme acesso/distribuição | [Escopos Gmail](https://developers.google.com/workspace/gmail/api/auth/scopes) |
| Google Drive | Buscar/usar documentos escolhidos, criar e organizar arquivos | OAuth; drive.file dá acesso a arquivos selecionados/criados, não ao Drive inteiro | [Escopos Drive](https://developers.google.com/workspace/drive/api/guides/api-specific-auth) |
| Arquivos e visão | PDF/TXT/documentos, OCR, comprovantes, imagens selecionadas e compartilhadas | TXT e imagens existem; ampliar formatos e revisão de extração. Texto de documento não vira ordem | Processamento próprio e APIs de visão; limites de arquivo/modelo |
| Ler a tela | Analisar tela compartilhada pelo usuário e ajudar em uma tarefa | MediaProjection exige consentimento em cada sessão; não é acesso permanente invisível | [MediaProjection](https://developer.android.com/media/grow/media-projection) |
| Agir pela tela | Interações em interfaces de outros apps | Viabilidade depende de acessibilidade, interface e políticas; falhas com mudanças de layout. Pesquisa/distribuição separadas | [AccessibilityService](https://developer.android.com/guide/topics/ui/accessibility/views/service) |
| Localização | Rotas, lembretes por lugar, chegada/saída de casa ou trabalho | Permissões, localização em segundo plano e bateria; frequência não é instantânea garantida | [Background location](https://developer.android.com/develop/sensors-and-location/location/background) |
| Saúde e treino | Sono, passos, exercícios e resumos | Health Connect + permissões por tipo + fonte que realmente grave esses dados. O celular não inventa sono | [Health Connect](https://developer.android.com/health-and-fitness/health-connect/data-types) |
| Proatividade | Revisão do dia, contas a vencer, avisos úteis, silêncio e frequência | Jobs locais já existem; ampliar gatilhos/deduplicação. Push precisa de backend que gere os eventos | [Background work](https://developer.android.com/develop/background-work/background-tasks), [FCM pricing](https://firebase.google.com/pricing) |
| Relatórios | Diário/semanal/mensal/semestral/anual com cobertura e fontes | Parte existe; faltam diário estruturado e agendamento cloud independente | Implementação própria; mostrar período parcial, não preencher lacunas |
| Plugins | Módulos de agenda, mídia, notas, finanças, arquivos, serviços | Catálogo tipado, permissões, status, execução verificável e opção de desligar. MCP é uma possibilidade | [Groq tools/MCP](https://console.groq.com/docs/tool-use/overview) |
| IA alternativa | Provedor reserva quando o principal falhar ou atingir quota | Gemini tem acesso gratuito a certos modelos com limites; requer chave/configuração própria | [Gemini billing](https://ai.google.dev/gemini-api/docs/billing/), [Groq limits](https://console.groq.com/docs/rate-limits) |
| OpenAI | Modelo dentro da Koi | API tem cobrança separada do Plus. Atalho ChatGPT atual não conecta esta sessão ao app | [OpenAI billing](https://help.openai.com/en/articles/9039756-managing-billing-settings-on-chatgpt-web-and-platform) |
| Banco Inter | Saldo/extrato por integração real | Portal oficial é Inter Empresas; ajuda diz APIs indisponíveis para PF e MEI. Não prometer acesso direto para a conta pessoal | [Ajuda Inter](https://ajuda.inter.co/conta-digital-pessoa-juridica/o-que-e-uma-api/) |
| nextJoy / Open Finance | Consultar dados bancários por caminho autorizado | API pública apropriada para nextJoy não foi confirmada. Open Finance envolve instituições participantes e consentimento | [Dados transacionais](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/17369300/Dados%2BCadastrais%2Be%2BTransacionais) |
| Importar finanças | Extrato fornecido/compartilhado, revisão, deduplicação | Alternativa independente de API bancária; formatos reais precisam ser avaliados | Importação própria; notificação é indício, não extrato completo nem saldo |
| Windows | Abrir programas, estado do computador, arquivos escolhidos e automações | Depois do celular; agente local autenticado, ferramentas limitadas e resultado. PC desligado não executa suas ações | Arquitetura própria; reavaliar repositório Mark-LV antes de reutilizar |
| Casa conectada | Lâmpadas, tomadas e cenas | Depende de dispositivos/serviço existentes; Home Assistant pode ser integração futura | [Home Assistant](https://developers.home-assistant.io/docs/creating_integration_manifest/) |
| Continuidade | Fila offline, exportar/restaurar, recuperação de conta, reconciliação | Pendências essenciais antes de uso prolongado; prioridade superior a atalhos extras | Implementação própria; não apagar dados para testar |

## Custos e acesso

Possibilidade técnica não significa recurso ativado nem gratuito. Recursos locais não exigem pagamento ao Android por uso dessas APIs, mas dependem do dispositivo e dos apps. IA, hospedagem, serviços externos e alguns requisitos de integração têm limites ou custo. Nenhuma contratação foi autorizada por esta pesquisa.

FCM é oferecido sem custo, mas transporte push não fornece processamento/agendador gratuito ilimitado. Calendar API tem uso padrão sem custo adicional; isso não implica que todo serviço Google ou processo de verificação seja gratuito. Premium Spotify para API não significa requisito para simplesmente abrir o aplicativo pelo Android.

Um acesso Android não concede automaticamente outro. Assistente padrão não libera todo o celular; notificações não dão extrato bancário completo; análise de imagem não equivale a controlar tela; OAuth de agenda não dá Gmail inteiro. O catálogo deve mostrar disponível, não configurado, permissão pendente, indisponível ou falha real.

## Ordem para construir

1. Corrigir memória e aceitar Koi/Coi/Koiwai/Coiwai. Testar o exemplo exatamente fornecido no painel.
2. Unificar interpretação semântica e ferramentas de consulta/ação já existentes. Reutilizar transações, recibos e desfazer; substituir decisões por listas de palavras sem remover validações de dados.
3. Diário estruturado e contexto recuperável por data/assunto; salvar relatos conforme preferências e distinguir planos de acontecimentos.
4. Ações locais Android conectadas ao chat: abrir apps, alarmes, música suportada. Resolução de apps local e sem enviar inventário completo ao provedor por padrão.
5. Contas recorrentes, orçamento, maiores limites e deduplicação de importações.
6. Proatividade configurável, avisos e relatórios, com horário de silêncio e cobertura visível.
7. Agenda/Tasks Google, documentos; depois e-mail e integrações que precisam de acesso mais amplo.
8. Voz dentro da Koi, assistente padrão e pesquisa de palavra de ativação no Poco.
9. Redesign completo, estabilidade/offline/exportação e uso prolongado; Windows depois do celular.

Os recursos podem ser desenvolvidos em um pacote amplo, mas devem ser validados por módulo. Não chamar tudo de pronto apenas porque os botões existem. Conversa normal não deve sempre produzir uma ação.

## Critérios da próxima entrega

- Mesma intenção em paráfrases: pedido direto, indireto e referência a mensagem anterior.
- Negação/hipótese/citação não executam ações indevidas.
- Memória salva e recuperada de verdade, correções prevalecem e têm origem.
- Ausência de permissão ou app produz explicação real, sem dizer que executou.
- Identidade do pedido evita duplicação ao repetir tentativa.
- Relatos diários não viram automaticamente tarefa nem renda recebida futura.
- Consultas financeiras calculadas com dados estruturados.
- Estado documentado separadamente: implementado, publicado, instalado, verificado.

## Limite da pesquisa

Este documento cobre áreas relevantes à visão da Koi, não todas as APIs ou aplicativos existentes. Compatibilidade no Poco, políticas de distribuição, quotas, OAuth e termos de cada serviço precisam ser reconfirmados ao implementar. Não houve prova prática das novas integrações nesta etapa.
