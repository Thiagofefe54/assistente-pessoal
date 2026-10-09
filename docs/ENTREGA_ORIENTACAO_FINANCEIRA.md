# Consultar antes de orientar — 09/10/2026

Uma pergunta como “como eu posso gastar meu dinheiro?” recebia dicas gerais, sem
abrir a consulta de saldo e contas já disponível no celular. O saldo não pode ser
presumido pelo modelo: os valores bancários não entram no contexto da IA.

Agora o intérprete semântico pode escolher finance_guidance para orientação pessoal
sobre organização de dinheiro ou possibilidade de gastos. A rota devolve uma frase
curta e o cartão privado existente, em vez de pedir à IA uma segunda resposta genérica.
O Android consulta saldo/contas com a sessão normal e calcula localmente. O cartão
mostra vencimentos, diferença e alertas de orçamento; diferença não é dinheiro livre.

186 testes backend aprovados. Uma chamada real curta ao Poe classificou a pergunta
mostrada pelo usuário como record/read/finance_guidance. Não é prova universal de
entendimento: perguntas gerais, relatos, investimentos e pedidos de pagamento não
devem abrir esse fluxo. Guardas de domínio, operação, fala, filtro e evidência atual
cobertas. Recibo contém metadados e request_id válido, sem dados de banco.

Publicado commit196c039, Render live19:08:15Z, health200. Não precisa de novo APK:
o app1.11 já mostra esse cartão. Falta teste manual no app com nova mensagem.
Essa etapa não liga Google/Outlook e não amplia acesso bancário.
