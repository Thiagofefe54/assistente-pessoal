# V2 · reformulação e acessos pessoais · 10/10/2026

## Conversas separadas — prévia 2

Novos pedidos do usuário: múltiplos chats com pastas Pessoal/Estudos/Trabalho/Outros,
fixar/renomear/buscar; área Avisos da Koi com não lidos; retenção somente de avisos
por 60 dias em lotes de até 50; toque após 1 dia sem interação, limitado a 1/dia,
com silêncio e opção de desligar. Room migra 5→6 preservando mensagens anteriores,
respostas e desfazer usam a mesma conversa; IA só recebe contexto recente do chat
selecionado. Supabase recebe conversation_id UUID aditivo, sem mudar RLS por dono.
Mensagens sincronizadas reconstroem chats. Organização personalizada/avisos ficam
locais nesta prévia. Não importamos avisos antigos de apps externos.

Inteligência publicada LIVE2799d23, dep-db58qqqd0e5s73ejj1ag; 283 testes backend.
Prévia2/code21 instalada; build/lint PASS, 62 Android unitários e 10 testes físicos
PASS (4 novos + 6 de persistência). Usuário confirmou seletor e quatro pastas.
SHA256 APK: 4F313B19D3ED8DB6A2662D10B0E168CFF35818ECE74434D3801BFAC528B09B68.
Prompt adicional de carinho PUBLICADO: Render LIVE35cefd2, deploy
dep-db594ffavr4c73fst930. Health200; rotas Gmail anônimas401/private-no-store.

## Implementado

- Todas as cinco abas principais reorganizadas: Home com personagem e próximos passos; Chat com ações no menu +; Memória em capítulos; Rotina em grupos e missões; Configurações em áreas separadas.
- Barra flutuante, ícone próprio, cabeçalhos ilustrados, roxo/azul/vermelho, cartões com entrada e resposta ao toque. Movimento respeita preferência e pausa com o app em segundo plano.
- Áreas internas de tarefas, agenda, hábitos, listas, notas, diário, metas, treinos, finanças, contas, orçamento, ritmo, lembranças, relatórios, conta e ferramentas recebem a nova linguagem visual. Formulários e operações existentes preservados.
- Painel compacto ACTION_ASSIST separado do app, voz/texto e abertura da conversa completa. Não foi selecionado como assistente padrão pelo agente.
- Busca de contatos por permissão e nome, sem envio à IA. WhatsApp/SMS abrem rascunho revisável no aplicativo; não são enviados automaticamente.
- Notificações de aplicativos escolhidos: ativação por opt-in + autorização Android. Só até 40 avisos atuais, em memória; sem banco de mensagens, histórico antigo ou encaminhamento à IA. Agrupamentos e avisos contínuos excluídos.
- Captura única de tela: novo consentimento Android a cada sessão, serviço visível, 7 segundos para escolher tela, encerramento após captura/cancelamento/timeout. JPEG no rascunho; envio ao modelo só após revisão e botão Enviar. Telas protegidas podem ficar vazias.
- Gmail: formulário com seleção de conta, destinatário único, assunto e corpo. Confirmação explícita de revisão, autorização gmail.send, recibo cifrado por dono e pedido, claim antes da escrita. Envio incerto não é repetido; verificação de recibo disponível após reabrir o app. Sem chamada à IA.
- Avisos Gmail importantes: consulta somente IDs não lidos/importantes nas três contas, até 20 por conta/último dia; WorkManager aproximadamente 15min, primeira consulta define baseline, silêncio 22h–08h. Não analisa automaticamente corpos com IA.
- Guia de início: [COMECE_AQUI_V2.md](COMECE_AQUI_V2.md).

- Conversa: contexto de organização inclui atrasadas/hoje, referências ao assunto anterior e horários no fuso local. Orientação para corrigir respostas, esclarecer ambiguidades e propor um próximo passo concreto; sem chamadas adicionais de IA.

## Conferência e limites

Backend 283 testes PASS, incluindo MIME, escopos, destinatário/header injection, envio incerto, repetição, isolamento por usuário, falha ao guardar recibo e consultas importantes parciais. Todas as chamadas de envio testadas com dados fictícios e mocks; nenhum e-mail real enviado e nenhuma geração Poe feita para os testes.

Android 61 testes unitários PASS; APK/testAPK e lint após aprofundar o visual PASS sem erro impeditivo. Manutenção no Poco para cancelar jobs/notificações antes do reset: 1 teste PASS. Testes Compose de navegação travaram antes de completar no aparelho; não contar como aprovados. Captura real da Home confirmou a primeira composição; avaliação das outras abas e dos novos consentimentos ainda necessária.

Reset autorizado explicitamente pelo usuário: registros Koi do dono atual excluídos em transação, auth e três conexões Google preservadas. Banco/cache local zerados com o app fechado; sessão cifrada mantida, jobs cancelados. Um relatório em trânsito apareceu após a primeira exclusão e foi removido novamente. SQL final confirmou zero em todas as 12 tabelas de dados. DeviceAccessV2Test: 3 testes PASS no Poco, incluindo login preservado, três conexões e chat local vazio, serviço de notificações protegido, captura não exportada e remoção/limite dos avisos fictícios em RAM.

Prévia `2.0.0-preview.1`/code20; não anunciar V2 definitiva nem acesso irrestrito ao Android. Voz expressiva escolhida continua como amostra local; síntese contínua é Android. Não há root, automação de telas de outros apps, pagamento bancário, Windows, hotword contínua ou acesso ao histórico integral WhatsApp/SMS.

## Antes do uso

APK final instalado por atualização: 2.0.0-preview.1/code20. SHA256 `20A4022143855D28082114EEE15B577481826798ED1CDE4FE5041000EEE21551`. Login e histórico vazio conferidos. Painel compacto também confirmado por captura real; demais abas ainda exigem avaliação manual. Selecionar Koi como assistente padrão pessoalmente. Conceder contatos/notificações apenas pelas telas do Android. Reautorizar contas Google para envio; testar um envio real somente com destinatário/texto concretos aprovados pelo usuário. Conferir captura escolhida pelo usuário e custo da interpretação; não disparar IA paga em testes de navegação.
