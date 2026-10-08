# Koiwai — instalação, reset e novos acessos

## Entrega real

V1.0 (versionCode 2) instalada no Poco via atualização, sem desinstalação.
APK: android/build/releases/Koiwai-1.0.apk.
SHA256: 6617b5e23113a2e3a0cdcd7c00e73d4260d8a700932de73adffd457061cd3e4d.
Configurações: testar/configurar/parar voz, ferramentas, lembranças,
notificações, permissões do app e Celular e conexões.
Ferramentas: lista local de apps com busca e abertura, discador (não liga sozinho),
contatos em app externo, alarmes e Wi-Fi. Consulta somente apps com atividade launcher,
sem QUERY_ALL_PACKAGES. Não transmite a lista ao backend ou provedor de IA.
Google Agenda/Tasks/Gmail/Drive e ChatGPT são atalhos externos, não conectores OAuth.
Os novos controles de celular são manuais, ainda não são ferramentas do chat.

## Reset explicitamente solicitado pelo usuário

O usuário pediu apagar tudo para testar do zero, supersedendo a restrição anterior.
Identidade da sessão foi verificada no dispositivo contra /auth/v1/user antes da
limpeza, sem expor token ou email. Conta Auth preservada.
Somente dados do proprietário conectado foram apagados, em transação:
chat_messages, memory_facts, koi_tasks, koi_task_completions, koi_personal_records,
koi_daily_reports, koi_period_reports, koi_action_receipts e koi_tool_receipts.
Verificação posterior: nove tabelas sem dados do proprietário; conta Auth existente.

Android recusou pm clear por falta de CLEAR_APP_USER_DATA do shell. App foi parado;
o caminho /data/user/0/com.thiago.assistentepessoal foi verificado por run-as. Somente
diretórios relativos de dados dessa sandbox foram removidos pelo usuário do app:
databases, shared_prefs, files, no_backup, cache, code_cache, app_dxmaker_cache.
Sandbox vazia verificada antes do novo lançamento. Permissões do SO não foram
revogadas. Sessão e preferências locais zeradas; login precisa ser repetido.

## Verificação

Build assembleDebug/assembleDebugAndroidTest/lintDebug aprovado: zero erros, 69 avisos.
Instalação do app e pacote de testes aprovadas. No Poco, ChatPersistenceTest (6) e
FreshStartTest (1) passaram após instalar o APK final: 7 testes, 0,314 s.
FreshStartTest é leitura do estado real, somente com verifyFreshStart=true; nenhuma
limpeza embutida no teste. APK abriu manualmente COLD em 1744 ms após testes.
Automação MobileV1SettingsTest ficou presa no lançamento da Activity (resultado
102); interrompida deliberadamente para atender o reset. Não foi aprovada nem é
evidência de crash espontâneo do app. Navegação/voz/fotos/novos controles visuais
e conversa autenticada após reset seguem pendentes de verificação.
76 testes backend e 25 unitários Android são resultados do checkpoint anterior.
Backend sem mudanças nesta entrega: nenhum novo deploy Render necessário.

## Pedido seguinte: celular e plugins completos

Usuário quer todos os acessos possíveis; Google completo, abrir suas conversas
ChatGPT e comunicação da Koi com o assistente, bancos Inter e nextJoy. Não representa
aprovação específica para pagamentos, mensagens externas, planos pagos ou concessão
automática de permissões sensíveis. Android não fornece acesso total irrestrito.

Próximos módulos com critérios de conclusão:
1. Google: configurar cliente OAuth, APIs, callback e tokens protegidos por conta;
   Agenda/Tasks primeiro, depois Drive/Gmail. Consentimento oficial e desconexão.
   Google Cloud acessível no Brave, projeto padrão sem cliente OAuth. Nenhuma
   credencial ou permissão Google foi criada nesta entrega.
2. ChatGPT: atalho externo entregue. Não existe ponte implementada para esta sessão;
   não automatizar envio de conversas pessoais como se fosse conector. Modelo OpenAI
   no app é outra integração por API, separada do Plus e sujeita a custos.
3. Contatos e notificações: leitura local com seletor/consentimento por função,
   revogação e controles de privacidade; não enviar conteúdo integral por padrão.
4. Outras telas: definir ações suportadas, serviço Android e autorização explícita;
   nunca executar comandos arbitrários vindos de notificações/páginas.
5. Bancos: verificar acesso autorizado compatível com Inter/nextJoy e orçamento R$0.
   Open Finance entre instituições não significa API aberta para qualquer app pessoal.
   Consulta/importação primeiro; pagamentos não implementados.

Dashboard local: docs/dashboard/index.html, responsivo, filtros de status e checklist
visual. Checkboxes não concedem permissões e não persistem. Não é monitor ao vivo.
Fontes: documentação Google sobre scopes; ajuda Inter Open Finance; FAQ next.
