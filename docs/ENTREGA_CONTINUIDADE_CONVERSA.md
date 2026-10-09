# Conversa com contexto — 09/10/2026

## O que mudou

- “E sobre isso?” e “Como melhorar isso?” podem recuperar o assunto da última fala
  do usuário quando a pergunta não traz outro assunto explícito.
- A fala da Koi não vira evidência pessoal. Uma mudança de assunto explícita prevalece;
  uma saudação recente não é ignorada para puxar um assunto antigo.
- As lembranças consultadas trazem um trecho original próximo do assunto, em vez
  de sempre usar o início da anotação. Até 40 registros recentes e 3 fontes.
- A resposta recebe orientações para separar informação confirmada de sugestão,
  não inventar horários/valores e esclarecer referências ambíguas antes de agir.

## Testar no Chat

Use dados fictícios, com interpretação contextual habilitada. Salve uma nota
“Teste academia”, com uma introdução longa e um trecho sobre treino no final.
Converse sobre academia e depois pergunte “Como melhorar isso?”. Observe se ela
acompanha o assunto e usa o trecho, identificando que são dados de teste.
Depois mude para estudo: ela deve acompanhar a mudança, sem insistir na academia.
Pergunte algo sem contexto suficiente: ela deve esclarecer, não inventar um fato.

## Verificação e limites

181 testes do servidor passaram, incluindo referência à última fala do usuário,
mudança de assunto, proteção de dados bancários, saudação, fonte original e rejeição
de tipos/datas inválidos. Não houve consumo de pontos em testes: IA e banco simulados.
As orientações de tom dependem da resposta do modelo e ainda precisam de observação
no uso real. Referências complexas não são resolvidas universalmente; não há busca
vetorial, memória infinita ou raciocínio exposto. O saldo bancário continua no cartão
privado do Android, fora do contexto da IA.

Sem novo APK, dependência, permissão, migração ou aumento de chamadas ao modelo.
Publicação e verificação do serviço serão registradas no checkpoint de continuidade.
