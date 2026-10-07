# Roadmap da Koiwai

Documento de evolução, atualizado em 07/10/2026. Sem datas de entrega prometidas.
A versão da especificação e o versionName do template Android não são releases.

## Ponto de partida

Identidade e experiência Android refinadas e aprovadas. Histórico Room por conta,
login Supabase, sessão criptografada, sincronização e consulta por dia funcionando.
O backend tem uma demonstração local e uma rota preparada para autenticação HTTPS.
Integração Groq com contexto recente implementada localmente; ativação real e
hospedagem HTTPS ainda pendentes. Relatórios, cadastros de Rotina e Windows pendentes.

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

1. Concluído: checkpoint recuperável publicado e autenticação do backend preparada.
2. Render Free escolhido e configuração de publicação preparada; Groq Free validado.
3. Validar a comunicação autenticada Android/backend com conta real.
4. Groq escolhido; testar configuração privada e limites reais antes de ativar no app.
5. Testar fluxo completo e recuperação de falhas com o computador desligado.

A meta inicial de custo é R$0. Isso não é promessa de servidor gratuito sempre
disponível: limites, suspensão por inatividade e condições dos provedores precisam
ser verificados na escolha. Nenhum serviço pago foi contratado nesta etapa.
Chaves de IA ficam no servidor. Não é necessário rodar um modelo grande no PC.

O orçamento confirmado é R$0. Balanço atual e pequenos objetivos M01–M10 em [REVISAO_2026_10_07.md](REVISAO_2026_10_07.md).

## Memória periódica aprovada como direção

- Registros por dia, mantendo instante UTC e fuso do usuário.
- Relatório semanal, mensal, semestral e anual, com cobertura explícita.
- Proposta de calendário: segunda a domingo; meses civis; janeiro–junho e
  julho–dezembro; retrospectiva até 31 de dezembro. Ainda requer implementação.
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

A consulta diária já existe. Geração automática e agendamento pertencem às etapas
de Memória e Proatividade. Política de retenção é futura e configurável; não há
exclusão automática de conversas nesta entrega.
