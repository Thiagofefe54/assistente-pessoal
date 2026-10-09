# V1.10 — saldo do Inter e contas cadastradas

Em 09/10/2026 a tela real do Poco foi conferida: consulta do Inter exibiu conta,
total e datas sem erro. Nenhum valor privado foi incluído neste relatório.

## Entrega instalada

Rotina → Ferramentas → Conexões → **Conferir saldo e contas** consulta saldo e
Meu dia com login normal, sem usar IA. A comparação acontece no Android:

- Vencimentos cadastrados de hoje até hoje +7 dias, inclusive, com datas explícitas.
- Total dessas contas e diferença em relação ao saldo consultado.
- Sugestão de separar o valor dos vencimentos; diferença nunca tratada como dinheiro livre.
- Aviso quando o saldo não cobre os valores cadastrados.
- Contas não marcadas como pagas no mês, separadamente, sem somar novamente às próximas.
- Aviso de orçamento ultrapassado nos registros existentes.
- Datas de consulta/provedor no horário local e instruções de ativação recolhíveis.

Consulta parcial, lista truncada, registros incompletos ou mudança de dia impedem
afirmar cobertura. Saldo com atualização há mais de24h ou futuro acima de5min
recebe aviso de data não confiável. Sem contas cadastradas não significa dinheiro
livre. Somas exatas em centavos, valores inválidos/overflow recusados.

Nenhuma alteração no backend, Render, Supabase, consentimentos ou credenciais.
Saldo não vai à IA, não é importado para receitas/despesas nem salvo no cache
Android. Dados do dia já têm seu cache habitual, mas esta comparação exige consulta
online nova e nunca usa fallback offline. Falha na consulta de contas deixa apenas
saldo visível e aviso; sem comparação com dados antigos.

## Verificações

- 50 testes unitários Android aprovados, incluindo4 novos casos de comparação:
  centavos/saldo negativo, parcial/truncamento, datas antigas/futuras e overflow.
- Build app/testes aprovado. Instalação de ambos com atualização, sem reset.
- Teste físico opt-in balanceAndRegisteredBillsCanBeComparedWithoutWrites aprovado
  em4,948s: saldo Inter + dados atuais da conta Koi contra Render, sem escrita,
  sem imprimir valores/títulos privados e sem exportar sessão.
- Versão1.10/code12. Gradle parado após uso.
- APKandroid/build/releases/Koiwai-1.10.apk SHA256:
  BB1E92021D648DD5BDFBFDE6FCAA2D0B1D6F8F366C2EFA094C86384101DACB8E.

Confirmação visual manual do novo cartão ainda pendente. O teste físico valida os
dados/cálculo, não toda a navegação. App aberto novamente após teste.

## Limites e próximo passo

Somente Inter/saldo e compromissos informados na Koi, inclusive fictícios. Não
detecta automaticamente pagamento no banco nem entradas futuras. Contas vencidas
antes de hoje não aparecem na janela, mas podem integrar as pendências do mês.
Os dados do provedor podem mudar entre consultas; não há monitoramento instantâneo.
Faltam consulta/sugestões com saldo no Chat, avisos de depósito e integração Google
por OAuth. Next/nextJoy continua excluído. Visual definitivo/Windows seguem depois.
