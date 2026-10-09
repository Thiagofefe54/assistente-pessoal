# Poe na Koiwai — 08/10/2026

Usuário autorizou criar a chave Koiwai, guardá-la somente no .env privado e
nos segredos do Render, e testar até 200 pontos. Plano mensal já pago pelo
usuário: R$24,90, 10.000 pontos/dia. Não houve compra de pontos ou plano novo.

## Integração e verificações

AI_PROVIDER=poe seleciona exclusivamente Poe. Chave Groq não é usada nesse modo,
nem como fallback. Código Groq permanece disponível para reversão explícita.
Nenhuma chave entra no APK ou GitHub. Provedor é consultável em /api/v1/health;
status ok indica serviço disponível, não teste da IA em cada chamada.

Modelo texto: GPT-OSS-120B. Teste Responses foi recusado com HTTP 400:
"Model does not support responses method", sem desconto. Documentação geral
diz todos os modelos, mas observação real mostra essa limitação. Texto usa
Chat Completions com schema explícito nas instruções, validação local JSON Schema
e validações existentes Pydantic/autorização/recibos. Não há garantia de schema
pelo provedor nesse fluxo; resposta inválida falha antes de executar ferramentas,
sem tentar outra geração automaticamente. Isso pode exigir reformular um pedido.

Imagem e pesquisa usam Responses/GPT-4.1-mini, por compatibilidade. Conversão,
validação de conclusão e exigência de pesquisa executada/fontes são testadas com
mocks; modalidades reais ainda não testadas. Não declarar migração completa dessas
modalidades. Podem gastar mais pontos que texto; somente pedidos explícitos as usam.
GPT-4.1-mini respondeu ao teste real de schema, sem escrita de dados.

120 testes backend simulados passaram. Testes reais: consulta de contas interpretada,
conversa recebida, conclusão de tarefa fictícia interpretada/validada, negação protegida.
Não criaram nem modificaram registros reais. O terminal não imprimiu a conversa
por incompatibilidade de codificação com emoji; a geração completou e não foi repetida.
Saldo inicial 10.000; final 9.950: 50 pontos no total, dentro da autorização de 200.
Não promete número fixo de mensagens/dia. Pontos também são usados no site Poe.

## Operação

Máximo de saída 1.536 tokens, classificação 512, histórico otimizado. Sem retry
automático, troca de modelo após erro ou compras automáticas. HTTP 402 vira aviso
de saldo insuficiente; 429 respeita Retry-After válido. Android pode substituir
o aviso por mensagem genérica de limite. Sem chave Poe, falha sem acionar Groq.

Publicação: registrar resultado em CONTINUIDADE_KOIWAI.md. Alteração só no servidor,
Android V1.3 não precisa reinstalação. Teste físico após publicação pendente:
usuário desconectou celular e pediu aviso quando necessário.

Fontes:
- https://creator.poe.com/docs/external-applications/openai-compatible-api
- https://creator.poe.com/api-reference/createResponse
- https://creator.poe.com/docs/resources/usage-api
