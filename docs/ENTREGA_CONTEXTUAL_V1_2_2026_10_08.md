# Entrega contextual V1.2 — 08/10/2026

## Resultado

V1.2 (versionCode 4) instalada no Poco por atualização, sem reset. APK android/build/releases/Koiwai-1.2.apk.
SHA256 880bfe174cbe03f54b35ccb86657c3ec99ca2d15780c58613636de9611748d19.
Código publicado no GitHub: 48bec44e981c76d9286fc1989177acfe1d2b516d.
Deploy Render Free confirmado Live: dep-db40ij4s728c73fgdt50, concluído em 08/10/2026 às 21:22:35 UTC. Health 200, chat sem sessão 401 e demo 404.

## Implementado

- Classificação semântica com JSON validado: conversa, tarefa, registro, memória, diário, dispositivo e web. Modelo interpreta pedidos indiretos e histórico recente; validação e ferramentas continuam verificando dados/alvos.
- Clientes antigos mantêm caminho anterior; Android V1.2 habilita contextual_mode.
- Solicitações de tarefas e registros podem usar paráfrases. Operações correspondem ao protocolo existente; operações distintas do plano não são autorizadas.
- Recibos são consultados antes de reinterpretar um reenvio, evitando nova escrita de pedido já executado.
- Memória aceita guarde/salve, incluindo a frase guiada e nomes Koi/Coi/Koiwai/Coiwai.
- Guardar relatos do dia é opção desativada por padrão. Ativada, permite registrar relato claro como nota de diário, preferência como lembrança, dinheiro ocorrido como receita/despesa. Não cria tarefas a partir de todo relato.
- Diário desta entrega usa Notas existentes, data e conteúdo; não possui tabela de eventos própria, cálculo de sono ou recuperação ilimitada. Mantém limite de 200 registros pessoais / 20 fatos.
- Cartões de ações Android preparados por IA: abrir app, reprodução por busca em player compatível, abrir Wi-Fi/Bluetooth/configurações da Koi, alarme, timer, mapa e pesquisa. Execução pelo botão; sem disparo automático de cartões antigos e sem alegar sucesso antes de abrir. Abertura entregue ao aplicativo não prova reprodução nem salvamento de alarme.
- Microfone usa reconhecimento do Android dentro de uma janela da Koi, transcrição parcial e revisão antes de enviar. Permissão RECORD_AUDIO pedida ao usar. Reconhecimento cancelado ao sair para segundo plano; sem escuta contínua nem palavra de ativação.
- SET_ALARM declarada para abrir os fluxos de relógio; não desativadas proteções Android.
- Painel atualizado com V1.2, captura opcional e limites reais.

## Validação

93 testes backend aprovados; 25 unitários Android aprovados; build app/testes e lint aprovados. Foram corrigidos erro de compilação inicial e instruções conflitantes do diário antes da entrega final.
6 testes de persistência passaram no Poco atualizado; versão instalada confirmada via Android. Nenhuma limpeza nem desinstalação. Mais 1 teste real de saudação autenticada ao servidor publicado passou em 4,588 s, sem gravação no histórico. Esse teste usa o caminho direto anterior; não comprova todos os novos fluxos semânticos no celular.
Groq real, dados fictícios e banco simulado: seleção de música/pedido indireto, conversa sem ação, salvar memória, recuperar roxo, registrar despesa de R$12 e registrar chegada como nota, além de remarcar tarefa para amanhã. Nenhuma escrita real na conta nesses testes. Primeiro teste de classificação de memória e primeiro teste de diário revelaram problemas corrigidos; testes relevantes repetidos e aprovados.
Novos fluxos de microfone, abertura de player/alarme/app e captura com dados da conta ainda precisam de teste manual no Poco. Bloqueio de toques remotos/navegação automática conhecido; não mudar segurança para passar teste. Não afirmar que todos os fluxos físicos foram aprovados.

## Uso

Entrar na conta, caso necessário. Pedidos explícitos funcionam sem ativar captura. Para relatos naturais, Config. → Guardar relatos do dia. Conferir Notas, Memória e Finanças; usar Desfazer. No Chat, Voz pede microfone e coloca texto no campo para revisão. Ações de celular têm botão Abrir ação preparada.
Exemplos: “Coi, guarde como lembrança: minha cor favorita é roxo”; “qual minha cor favorita?”; “dá para passar aquela tarefa para amanhã?”; “quero ouvir música”; com captura ativada, “cheguei do trabalho agora”. Não usar dados bancários ou senhas nos testes.

## Bancos e escopo

Usuário confirmou bancos Inter e nextJoy. Itaú/iti foram apenas pesquisa. Itaú oferece compartilhamento entre instituições Open Finance; não foi confirmado acesso gratuito direto a saldo/extrato PF pela Koi. APIs públicas de produtos não são extratos pessoais. iti foi migrado para Superapp Itaú, conforme comunicado oficial de 2025.
Fontes: https://devportal.itau.com.br/nossas-apis/openfinance e https://www.itau.com.br/media/dam/m/4fd0dce5d5eb9938/original/08-07-2025_Itau_Unibanco_conclui_migracao_de_mais_de_10_milhoes_de_clientes_para_seu_Superapp.pdf.
Não houve conexão bancária, contratação, criação de credenciais ou transferência de dados financeiros reais. Inter/nextJoy continuam futuros; importação de extratos e notificações ainda não implementadas.

## Pendências

Automação de música por sessão/notificações, Google OAuth, bancos, contas recorrentes/orçamento, diário próprio/sono, agendador cloud/proatividade, assistente padrão/wakeword, fila offline/exportação e redesign completo. APIs e condições precisam de verificação ao implementar. Classificador pode errar ou atingir quota; modos estruturados podem gerar 502 e recusas conservadoras. Dois passos de IA podem aumentar latência e consumo. Não ativado serviço pago.
