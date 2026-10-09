# Avaliação Poe — 08/10/2026

Pesquisa inicial sem compra, chave criada ou chamadas à API.
Atualização: usuário pagou R$24,90 mensal; plano ativo e 10.000 pontos/dia
confirmados na interface. Nenhuma chave ou chamada API Poe.
Não há integração Poe implementada, publicada ou instalada.

## Plano confirmado na interface brasileira

https://poe.com/subscription_plans, opção Cobrado mensalmente:
R$24,90/mês, 10.000 pontos/dia. Plano seguinte R$99,90/mês, fora do orçamento
de R$50. Não confundir equivalente mensal do anual com cobrança mensal.

## Modelo e estimativa

https://poe.com/GPT-OSS-120B, bot oficial, botão Taxas:
entrada 5 pontos/1.000 tokens; saída 20 pontos/1.000 tokens.
Usando 6.000 tokens de entrada +500 de saída no total das duas chamadas de uma
interação, estimativa 40 pontos/interação, ou 250 interações para 10.000 pontos.
É simulação, não teste ou garantia: histórico, arredondamento, raciocínio, outras
modalidades e modelos podem aumentar o custo. Pontos são compartilhados com uso
no próprio Poe e não equivalem a mensagens ilimitadas.

## Integração

Documentação oficial https://creator.poe.com/docs/external-applications/openai-compatible-api:
assinantes podem usar pontos da assinatura na API sem contratação separada.
Chat Completions ignora response_format; nossa geração atual depende desse
campo. Responses documenta JSON Schema via text.format, exigindo adaptador próprio,
validação e testes por modelo. Não basta substituir base_url e chave. Validação
e regras atuais de escrita/idempotência devem ser preservadas.

Sem conta autenticada/chave Poe, não foi possível confirmar chamadas reais,
qualidade, pontos efetivamente descontados ou suporte a nosso schema pelo modelo.
Parecer: plano mensal é candidato viável pelo orçamento/previsibilidade; teste
de integração ainda pendente. Não apresentar como IA ilimitada ou melhoria de
inteligência automática: candidato mantém o modelo atual.
