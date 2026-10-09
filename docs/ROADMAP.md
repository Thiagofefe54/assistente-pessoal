# Roadmap da Koiwai

## Direção atual — revisão de 08/10/2026

Leia o checkpoint de continuidade e a [entrega V1.4](ENTREGA_MEUDIA_V1_4_2026_10_08.md)
antes do planejamento histórico abaixo. V1.3 instalada; V1.4 implementada com
Meu dia, saldo Poe e leituras ampliadas. Diário, contas, orçamento e acompanhamento
opt-in já existem. Próximos pacotes: busca mais ampla, categorias/importação,
Google autorizado, voz/assistente, então redesign e Windows.
O layout atual foi considerado desorganizado pelo usuário; revisão visual adiada.
Poe pago contratado pelo usuário: R$24,90/mês, 10.000 pontos/dia compartilhados.
Render e Supabase gratuitos. Nenhuma compra adicional autorizada nesta entrega.

Documento de evolução, atualizado em 08/10/2026. Sem datas de entrega prometidas.
Numeração atual definida pelo usuário: **V1.0** para o pacote móvel amplo,
**V1.1** para correções, **V2.0** para a etapa definitiva após validação.
[Entrega V1.0 e próximos miniobjetivos](ENTREGA_V1_0.md). O APK ainda aguarda
instalação/testes físicos; Windows continua posterior. A tabela histórica abaixo
é um planejamento anterior e não substitui essa numeração.

Checkpoint atual: [Pack 2](PACK_2.md) implementa ações diretas e reversíveis de
tarefas no chat, relatórios por período e preparação/notificação opcionais no
Android. Banco aplicado e testes locais aprovados. APK instalado; cinco testes de
persistência passaram. Validação funcional dessa atualização pendente.
Não marcar Pack 2 ou V1 concluídos.
Critérios de reinício, entrega prolongada e integridade continuam exigidos.

## Ponto de partida

Identidade e experiência Android refinadas e aprovadas. Histórico Room por conta,
login Supabase, sessão criptografada, sincronização e consulta por dia funcionando.
Backend publicado no Render Free; conversa Groq autenticada com contexto recente
validada no Poco e confirmada pelo usuário. Primeiro incremento de lembranças
confirmadas instalado e testado no Poco. Pack 1 implementa sugestões revisáveis,
resumo diário sob demanda e tarefas reais. Pack 2 amplia relatórios/notificações
e ações, aguardando a validação do novo APK. Outros cadastros de Rotina e Windows
continuam pendentes.

## Etapas e critérios

| Versão | Entrega | Critério para concluir |
| --- | --- | --- |
| 0.0.1 | Fundação: especificação e identidade. | Arquitetura e limites iniciais definidos. |
| 0.1.0 | Nascimento: texto, conta, histórico, IA e nuvem. | Conversar com IA em HTTPS com o PC desligado; recuperação e isolamento entre contas verificados; limites de uso ativos. |
| 0.2.0 | Windows e continuidade. | Mesma conta nos dois clientes, histórico reconciliado sem duplicação. |
| 0.3.0 | Memória: diário, fatos e resumos. | Fontes consultáveis e correção de fatos; recuperar dados tardios sem duplicar relatórios. |
| 0.4.0 | Voz por botão. | Transcrever, falar e interromper com permissões visíveis; palavra “Koi” avaliada separadamente. |
| 0.5.0 | Organização. | Criar, consultar, editar e concluir registros; cálculos financeiros feitos com dados estruturados. |
| 0.6.0 | Proatividade configurável. | Lembretes e relatórios entregues sem duplicação após reinício ou retorno de conexão. |
| 0.7.0 | Agente de PC limitado. | Status e ações autorizadas com permissões explícitas e expiração. |
| 0.8.0 | Ações pela tela do PC. | Localizar, agir, verificar resultado e parar; confirmar operações sensíveis. |
| 0.9.0 | Ações Android selecionadas. | Integrações úteis sem permissões excessivas e com resultado verificável. |
| 0.10.0 | Offline ampliado. | Fila, conflitos, reconexão e reenvios tratados com identidade estável. |
| 1.0.0 | Uso pessoal confiável. | Sete dias de uso essencial sem perda de dados ou exceder o orçamento definido. |

## Ordem imediata

1. Concluído: Groq, publicação gratuita e conversa real autenticada no Poco.
2. Concluído: lembranças confirmadas, criação/edição/exclusão e uso no contexto.
3. Ajuste de personalidade alegre e natural publicado; observar no uso real.
4. Pack 1: sugestões revisáveis, resumo diário com fontes e tarefas na conta.
5. Pendências da fundação: recuperação de conta, limites próprios por usuário e
   observação do uso com o computador fisicamente desligado.

A meta inicial de custo é R$0. Isso não é promessa de servidor gratuito sempre
disponível: limites, suspensão por inatividade e condições dos provedores precisam
ser verificados na escolha. Nenhum serviço pago foi contratado nesta etapa.
Chaves de IA ficam no servidor. Não é necessário rodar um modelo grande no PC.

## Prioridade atual por packs

Completar primeiro o Android. Pack 1: memória/diário/tarefas; Pack 2: lembretes e
relatórios periódicos; Pack 3: voz e personalidade; Pack 4: ferramentas pessoais;
Pack 5: offline, privacidade e acabamento. Windows vem depois, mesmo que a tabela
histórica de versões o apresente antes. Não confundir packs entregues com versões
inteiras concluídas: os critérios da tabela continuam exigidos.

O orçamento confirmado é R$0. Balanço atual e pequenos objetivos M01–M10 em [REVISAO_2026_10_07.md](REVISAO_2026_10_07.md).

## Memória periódica aprovada como direção

- Registros por dia, mantendo instante UTC e fuso do usuário.
- Relatório semanal, mensal, semestral e anual, com cobertura explícita.
- Proposta de calendário: segunda a domingo; meses civis; janeiro–junho e
  julho–dezembro; retrospectiva até 31 de dezembro. Implementado no Pack 2,
  validação física pendente.
- Primeiro ano parcial começa no primeiro registro real: reunir até dezembro,
  sem esperar seis meses e sem inventar informações anteriores.
- Se uma visão semestral parcial repetir exatamente a retrospectiva anual inicial,
  evitar uma segunda entrega redundante.
- Manter referências às fontes originais; resumos anteriores podem orientar a
  leitura, mas não substituir a verificação dos registros.
- Dados offline que chegarem tarde devem atualizar a cobertura com uma chave
  estável por conta, tipo e período, evitando relatórios duplicados.
- Entrega inicial no app; notificações opcionais. Envio externo exige definição
  explícita do canal e autorização.

A consulta diária já existe. Preparação automática e agendamento opcionais foram
implementados no Pack 2 pelo Android, sujeitos a rede, cotas e atrasos do sistema.
Política de retenção é futura e configurável; não há
exclusão automática de conversas nesta entrega.
