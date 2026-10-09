# V1.14.1 — Correção da consulta de saldo

O usuário mostrou divergência entre o banco e a Koi. A captura tinha consulta
recente, mas atualização do provedor várias horas antes. O código consulta os
dados existentes da Pluggy; não sincroniza o Inter ao tocar no botão.

Documentação oficial: https://docs.pluggy.ai/en/docs/connections/item
O original Meu Pluggy atualiza diariamente; proxy acompanha o original e não
aceita atualização manual por API. Não adicionamos PATCH, novo consentimento,
extratos, pagamentos ou contratação. Não prometemos saldo em tempo real.

## Correções

- Pedidos simples de saldo abrem cartão balance, sem planejamento de gastos.
- Divergência explícita recebe reconhecimento e explicação, sem repetir cartão,
  consultar banco, gastar pontos ou salvar valor manual.
- Intérprete orientado a tratar correção de saldo como conversa.
- Cartão mostra último saldo informado, data do provedor e aviso permanente de
  fotografia; botão Consultar dados do provedor evita promessa de sincronização.

## Verificação

257 testes backend PASS, incluindo regressão de pedido de saldo, divergência,
negação, pagamento e relato de receita. 59 testes Android PASS; assembleDebug PASS.
Nenhuma chamada Poe ou alteração bancária nos testes desta correção.
APK android/build/releases/Koiwai-1.14.1.apk, code17, inclui V1.14.
SHA256 27520D63555A38AE93BF8B0127738F46F160C7413E783BF8FF81D5B0E19D9718.
NÃO instalado. Teste real no Poco pendente. Ao instalar, preservar os dados.
Servidor LIVE commit476ac79f1d4da25bc4d3ad604688d9d6655fdbcc,
deploy dep-db4m1gflot8c73bi7aog em 09/10/2026 às18:47 São Paulo.
Smoke HTTPS: health200, endpoints Google privados401, callback inválido400.

## Teste no retorno

1. Nova mensagem Quanto dinheiro eu tenho? deve mostrar Saldo do Inter.
2. Nova correção O saldo está errado deve explicar a fotografia, sem novo cartão.
3. Cartão deve distinguir atualização do provedor e horário da consulta da Koi.
4. Se banco e fotografia divergem, conferir banco. A correção não atualiza a
   fotografia da Pluggy nem modifica os registros financeiros da Koi.
