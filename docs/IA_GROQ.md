# Primeira integração de IA — 07/10/2026

Integração local Groq validada com chamadas reais: GPT-OSS 120B como principal e
GPT-OSS 20B como alternativa. A chave privada foi configurada; o app autenticado
ainda depende de uma origem HTTPS. O simulador HTTP continua sendo uma demonstração.

## Configuração

Configure GROQ_API_KEY somente no .env da raiz ou nos segredos da hospedagem.
O arquivo é ignorado pelo Git; o APK não recebe essa chave. Não publique capturas
do segredo e não o envie em mensagens. A senha do banco Supabase não é necessária.

As opções estão em .env.example: modelo principal, reserva, timeout e orçamento
de saída. Nenhum plano pago é ativado pelo código. Antes de habilitar o servidor
externo, confira novamente os limites. O painel confirmou plano Free de $0 e,
para ambos os modelos, 30 pedidos/minuto, 1.000/dia, 8.000 tokens/minuto e 200.000/dia
na data desta entrega. O limite atingido primeiro prevalece; não são promessas de
1.000 mensagens longas diárias nem de cotas independentes entre todos os recursos.

## Conversa e contexto

- POST /api/v1/chat exige HTTPS e Supabase Auth antes de chamar o modelo.
- O Android busca até 20 mensagens concluídas no banco da conta atual, anteriores
  à mensagem enviada. Reenvios excluem a própria mensagem, falhas e mensagens futuras.
- O contexto tem até 12.000 caracteres e 4.000 por item; a mensagem atual tem até
  8.000 caracteres. Esse limite de caracteres não é um contador exato de tokens.
- Apenas os papéis user e assistant são aceitos no contexto. A personalidade é
  definida no servidor, separada dos textos da conversa.
- Este contexto enviado pelo cliente é informação não confiável, não uma fonte
  para autorizar ações ou consultar dados de outras contas.
- Fatos confirmados já são consultados pelo servidor; resumos e relatórios são futuros.
- O simulador /api/v1/chat/demo não chama Groq, e o Android não lhe envia histórico
  nem token. Não há nova rota de IA aberta sem autenticação.

## Falhas e limites

O código tenta a reserva uma vez apenas em HTTP 500, 502 ou 503 do modelo principal.
Não troca de modelo em 429, pois a organização pode compartilhar cotas. O servidor
preserva Retry-After numérico; o app avisa que é necessário aguardar. Não há fila
automática de reenvio, nem cooldown distribuído ou limite diário por usuário ainda.

Timeouts não são repetidos automaticamente; a resposta pode ter sido gerada antes
da interrupção. Respostas vazias ou cortadas pelo limite não são salvas como sucesso.
O envio fica recuperável pelo botão de tentar novamente existente. O backend ainda
não tem cache/idempotência entre solicitações; a continuidade local não promete
que reenvios consumirão zero tokens.

O tempo de leitura no Android passou para 90 segundos para tolerar uma futura
inicialização lenta na hospedagem gratuita. As mensagens de falha não exibem corpos
de erro do provedor nem credenciais. O modelo não recebe ferramentas nesta entrega.

## Validação e próxima etapa

19 testes de backend passaram com dados fictícios, incluindo contexto, identidade,
falhas, reserva, quotas, segredo e respostas incompletas. Compilação Android e sete
testes unitários passaram; lint terminou com zero erros e 27 avisos existentes.
Uma chamada real ao principal recuperou corretamente o nome Jardim Lunar de uma
conversa fictícia; a reserva respondeu a outra solicitação fictícia. Não foram
enviadas conversas reais do usuário nestes testes. O teste completo no celular
ainda depende do backend HTTPS; o APK instalado não foi trocado.

O painel Groq recusou a geração automatizada da chave com falha de verificação.
A criação manual e o preenchimento do .env foram feitos pelo usuário. A primeira
requisição recebeu 403 da camada de proteção; definir User-Agent Koiwai/0.1 e
Accept application/json resolveu o problema. O usuário foi orientado a substituir
a chave que acabou sendo compartilhada no chat; não houve revogação automática.

Atualização: hospedagem HTTPS publicada no Render Free. O usuário decidiu
substituir a chave compartilhada após os testes. Próximo:
validar login, contexto, persistência e sincronização no
celular antes de declarar M06–M09 concluídos.

Referências oficiais: [API](https://console.groq.com/docs/api-reference),
[modelos](https://console.groq.com/docs/models),
[limites](https://console.groq.com/docs/rate-limits) e
[tratamento dos dados](https://console.groq.com/docs/your-data).


## Incremento de lembranças confirmadas

O servidor foi atualizado no Render com consulta de fatos da conta antes da
resposta. Uma chamada real ao Groq recuperou Estação Lavanda de uma lembrança
fictícia, sem histórico recente. O teste completo da memória no Poco permanece
pendente após a desconexão do aparelho. A conversa online anterior já foi validada.
