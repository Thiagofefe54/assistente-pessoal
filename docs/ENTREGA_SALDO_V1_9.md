# V1.9 — Consulta pessoal de saldo, 09/10/2026

Usuário delimitou: somente saldo, para futuras sugestões. Não buscar extratos,
transações, identidade, investimentos nem iniciar pagamentos. Não importar valores
como receitas/despesas. Consulta de saldo ainda não integrada ao Chat/IA.

## Implementado

Painel Bancos em Rotina → Ferramentas → Conexões: links oficiais Meu Pluggy/guia,
conferir configuração e consultar saldos. Entrar no site não conecta automaticamente
à Koi. Chaves não ficam no APK, no chat nem no Git.
Servidor: POST /assistant/bank-status e /assistant/bank-summary, com autenticação
Supabase existente, sem mudar auth/RLS. Conexão pessoal vinculada a UUID específico
no servidor; demais contas não podem consultar. IDs de conexões vêm exclusivamente
de configuração privada, nunca do cliente. Até3 conexões; conta BANK, moeda BRL.
Retorno minimizado: Conta1/2, saldo em centavos, data de atualização do registro no
provedor; nenhum número de conta, CPF, titular ou chave no retorno. Total só das
contas BRL consultadas; sinal de dados parciais. Não representa limite de crédito,
investimentos nem saldo em tempo real.
Cliente solicita leitura ao toque. Cache privado em RAM do servidor por60segundos,
com autorização antes da leitura e invalidação por alteração de configuração;
falhas também limitadas por60segundos. Respostas HTTP no-store. Nenhum snapshot
bancário salvo no app e nenhuma chamada a IA. Host fixoHTTPS, redirects recusados,
respostas limitadas262KB/8s, erros externos sanitizados. Nenhum pagamento/refresh
forçado/webhook de movimentação foi implementado.

## Estado real

Conexão DESATIVADA por padrão. Nenhuma credencial Pluggy configurada, nenhum saldo
real consultado. Usuário entrou no Meu Pluggy e chegou à autorização do Inter;
consentimento/conexão concluída ainda NÃO confirmados. Inter e Next aparecem na
seleção observada; Next não comprova compatibilidade com nextJoy.
177 testes backend PASS (8 novos, provedor simulado); após revisão de validação,
os8 bancários passaram novamente. 46 Android PASS, build/lint0erros104avisos.
APK1.9/code11 instalado por atualização no Poco14c3a88a. Nenhum dado apagado,
nenhuma IA paga, compra, segredo ou permissão nova aplicada.
APK android/build/releases/Koiwai-1.9.apk SHA256
8C5458F6FBB5077F96B1FB479DB0093F1997D768FC880843261C2C4192264405.
Publicação backend pendente neste checkpoint. Não declarar saldo funcionando.

## Ativação que falta

1. Usuário conclui pessoalmente consentimento no Meu Pluggy/Inter. Revisar dados
   pedidos pelo provedor; a API Koi usa saldo, mas o consentimento do provedor pode
   abranger mais dados. Não prometer consentimento granular só de saldo.
2. Acessar Dashboard Pluggy, aplicação pessoal/development e conectar o proxy
   Meu Pluggy conforme guia oficial. Validar condição gratuita pessoal, sem plano
   pago, produção comercial ou cartão. Mudanças nas condições precisam ser conferidas.
3. Criar credenciais com autorização específica e colocar somente no servidor
   privado: PLUGGY_CLIENT_ID, PLUGGY_CLIENT_SECRET, PLUGGY_ITEM_IDS,
   PLUGGY_OWNER_ID (UUID da conta Koi, não CPF). Nunca pedir esses valores no chat.
4. Somente então ativar PLUGGY_ENABLED=true e fazer consulta autenticada de saldo.
   O consentimento ainda pode expirar/revogar e o banco/provedor ficar indisponível.
5. Sugestões com saldo no Chat dependem de trabalho seguinte e controle de
   compartilhamento com IA. Não mandar saldo à IA sem tratar essa decisão.

## Pesquisa e fontes

Meu Pluggy divulga acesso pessoal por API e sincronização diária. Não é garantia de
avisos instantâneos; documentação Open Finance informa atraso de até24h em transações.
Só consulta manual de dados já existentes está preparada aqui. Inter apareceu no
widget, mas conexão real ainda não testada. nextJoy continua sem compatibilidade confirmada.

- [Acesso pessoal gratuito](https://www.pluggy.ai/precos)
- [Guia atual do Meu Pluggy](https://meu.pluggy.ai/en/api-guide)
- [Guia oficial do projeto](https://github.com/pluggyai/meu-pluggy)
- [Accounts API](https://docs.pluggy.ai/en/reference/account/accounts-list)
- [FAQ: atualizações e limites](https://docs.pluggy.ai/en/docs/get-started/faq)
- [Fluxo regulado de consentimento](https://docs.pluggy.ai/pt/docs/open-finance/creating-item)
- [Políticas de privacidade/segurança](https://www.pluggy.ai/legal)

Nenhuma garantia de risco zero foi dada. O usuário perguntou sobre confiança e
aceitou o recorte somente saldo; conclusão do consentimento não deve ser inferida.
