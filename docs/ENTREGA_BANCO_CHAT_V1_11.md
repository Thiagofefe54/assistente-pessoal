# V1.11 — consulta do Inter dentro do chat

Instalada no Poco em09/10/2026, sem reset. Usuário confirmou que a nova pergunta
“Quanto eu tenho no meu banco?” exibiu cartão Saldo do Inter com total e data.

## Como funciona

Perguntas reconhecidas localmente, sem chamada de IA, geram resposta gentil e
descritor de leitura no chat. O cartão consulta os endpoints existentes, com login
normal e autorização por owner no servidor. Exemplos:

- Quanto eu tenho no meu banco? / Qual meu saldo? / Quanto dinheiro temos no Inter?
- Meu saldo cobre as próximas contas? / É suficiente para as contas?
- O que preciso reservar esta semana? / Quanto separar para as contas?

Modo saldo usa bank-summary; comparação usa bank-summary + day e as proteções da
V1.10. A consulta automática acontece só na mensagem nova mais recente, com o chat
aberto. Ao abrir histórico antigo, tocar Atualizar saldo ou Conferir saldo e contas.
Assim a Koi não consulta o banco a cada entrada antiga da conversa.

Perguntas sobre pontos/Poe e mensagens de registro (recebi, gastei, anote etc.)
preservam o fluxo anterior. Perguntas explicativas, como “O que é saldo bancário?”,
também seguem para conversa normal. O reconhecimento aceita famílias de frases;
não é garantia de interpretação de qualquer mensagem financeira ambígua.

## Privacidade e limites

O resultado monetário fica no estado transitório do cartão. Não é incluído no texto
da mensagem, receipt, cache Android ou contexto enviado à IA. A pergunta e resposta
genérica continuam no histórico habitual sincronizado. Receipt local só tem
tool=bank, type=read, query=balance/plan. Não há Desfazer em uma consulta.

O botão Ouvir/Compartilhar da mensagem usa a resposta genérica, não o saldo privado.
Os descritores atuais não são sincronizados pelo CloudSync: numa nova instalação
ou outro aparelho, a mensagem histórica pode aparecer sem cartão; perguntar de novo.
Não há saldo no raciocínio do Poe, escuta contínua, histórico de saldo, pagamentos,
extratos ou avisos instantâneos de depósito. Inter somente; Next/nextJoy excluído.

## Verificação

- 53 testes unitários aprovados (3 novos grupos de frases e preservação de fluxos).
- Build app/teste aprovado, instalação principal1.11/code13 e pacote de testes.
- Teste físico BankChatRepositoryDeviceTest PASS0,112s em banco isolado em memória:
  pergunta cria descritor/resultado enviado, tokenProvider que falharia se chamasse
  backend da IA não foi invocado; texto/contexto sem saldo, receipt só3campos.
- Tentativa inicial de teste Compose ficou travada, interrompida explicitamente
  pelo agente com force-stop; não foi considerada aprovada e teste foi substituído
  pelo teste isolado acima. Nenhum dado removido. Usuário confirmou visualmente o
  cartão com dados reais no chat. Comparação pelo chat ainda requer teste manual.
- Pequena exclusão de perguntas explicativas adicionada depois da confirmação;
  testes unitários rerodados53PASS, app reinstalado preservando os dados.
- Gradle parado e app reaberto.
- APKandroid/build/releases/Koiwai-1.11.apk SHA256:
  D7446CFFABFD485C14AB6FBAD7CC923DDFF50593A9A44C53C5F757035082AA4E.

Sem alterações de backend, Render, SQL, Supabase Auth, consentimentos ou credenciais.
