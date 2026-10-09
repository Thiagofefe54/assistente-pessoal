# V1.14 — Dia conectado e consultas Google

## Entregue no código

- Endpoint privado google-day reúne até três agendas principais, até vinte eventos por conta, com consultas paralelas limitadas. Conta selecionada deve pertencer ao usuário validado pelo Supabase; nenhum ID do cliente define o dono.
- Linha do tempo preserva conta de origem. Mostra até vinte sobreposições e contagem total no recorte. Eventos transparentes e convites recusados pelo próprio usuário não bloqueiam horários.
- Cruzamento com tarefas Koi: conflitos e janelas sem eventos das agendas consultadas entre 08h e 22h. Não estima duração nem altera tarefas. Falhas/parcialidade são explícitas; se todas as agendas falham, não indica janelas livres.
- Painel Meu dia conectado em Rotina → Ferramentas → Conexões, com navegação por dia. Chat usa o mesmo endpoint quando a interpretação pede planejamento Google. Com e-mail explícito, respeita a conta escolhida.
- Busca por assunto Gmail e nome Drive executada no provedor, não só filtrando os primeiros itens recentes. Datas Gmail usam segundos do fuso local; Drive usa última modificação. Até cinco cabeçalhos / vinte metadados; resultados fora desses limites são sinalizados.
- Painel de filtros por termo e data. Google Tasks mostra tarefas reais, inclusive ocultas após conclusão pelo app Google; exclui excluídas. Todas/Pendentes/Concluídas filtra o recorte na tela.
- Abrir resultado em destinos Google fixos. Gmail/Drive abrem item, Agenda/Tasks abrem serviço; conta enviada como seletor. Não executa URL arbitrária recebida do provedor.
- Config. → Consultas Google: permitir ou desligar a abertura automática de consultas de novos pedidos. Não desativa pedidos de alteração; cartões antigos continuam manuais. Nenhum resultado Google entra no cache, histórico ou prompt.
- Plano local converte horários do fuso de cada tarefa antes de comparar o dia. Pedido de outro dia usa a data solicitada; não ignora silenciosamente intervalo de vários dias.
- Middleware protege respostas da API, inclusive erros, com Cache-Control private/no-store, Pragma no-cache e nosniff. Cache explícito privado Android existente continua limitado aos quatro painéis permitidos e separado por dono.

- Leitor privado Google por toque: texto Gmail sem anexos, HTML convertido para texto sem scripts/imagens/links ativos; Google Docs exportado para texto, TXT/CSV/Markdown em prévia. Até 16 mil caracteres, 64KB por download Drive, respostas Gmail até 1MB; truncamento explícito. Sem leitura em massa, IA, histórico, cache ou marcação como lido. Formatos binários não são abertos pelo leitor.
- Voz: perfis Koi delicada (.94/1.10), animada (1.02/1.08) e tranquila (.88/1.02) com exemplo e aplicação explícita. Texto falado remove emoji/marcação/URL bruta. Usa a voz Android escolhida, não supõe gênero nem gera voz contínua própria.

- Amostra feminina suave de 5,2s gerada com o plugin Runway, preset Katie em português, 2 créditos gratuitos existentes (saldo retornado498). MP3 de84.471bytes embutido em res/raw/koi_voice_preview.mp3; botão Ouvir amostra da Koi, reprodução local com parar e liberação ao sair da tela. Não configura síntese contínua nem cria identidade vocal exclusiva.

## Verificação

- 253 testes backend PASS; 59 testes unitários Android PASS; build debug V1.14/code16 PASS.
- Consultas reais: busca fictícia Gmail/Drive nas três contas passou, sem resultados esperados. Tarefas reais lidas nas três contas com sucesso; apenas contagens impressas.
- Leitura conjunta das três agendas passou. Nesse teste, somente Google era real; a parte local do plano usou fixture, pois não utilizamos uma sessão do Poco.
- Sem escrita Google real, sem chamada Poe e sem contratação nesta entrega. Leitor com fixtures e consultas reais: três prévias Gmail e duas exportações Drive compatíveis passaram (documentos retornaram texto vazio); somente contagens/status impressos. Conferência visual de textos e voz no Poco pendente. Conteúdo pessoal e credenciais não impressos nem registrados no Git.
- APK preparado: android/build/releases/Koiwai-1.14.apk. SHA256 0EE2B14956BE8748D9B6DD57DE7239A439BA1B505A7071A74BB55762B495CA5C.

## Publicação / aparelho

Servidor aguardando publicação final. APK NÃO instalado. Usuário está fora e conectará o Poco depois.
V1.14 inclui a V1.13, que também ainda aguardava instalação. No retorno, instalar somente a mais nova com install -r, preservando dados.

## Revisão das áreas solicitadas

| Área | Situação desta entrega |
|---|---|
| Inteligência | Novas ferramentas Google e orientação ao intérprete; seleção por IA já existente. Sem teste pago novo. |
| Memória | Captura, busca com fontes, correção e contexto existente preservados; suíte de regressão passou. Não foi adicionada memória ilimitada. |
| Organização | Plano de outro dia e fusos corrigidos; hábitos, check-ins, metas e lembretes existentes preservados. |
| Finanças | Receitas/despesas/contas/orçamento e Inter somente saldo preservados; testes passaram. Não há extrato automático ou pagamentos. |
| Google | Consultas e planejamento ampliados; criação/edição Agenda/Tasks da V1.13 mantidas. |
| Configurações | Novo controle de consultas Google; voz, silêncio, captura, notificações e limite de relatórios existentes preservados. |
| Celular | Ferramentas existentes preservadas; nenhum novo privilégio Android concedido. |
| Windows / visual final | Excluídos do pacote por decisão do usuário. |

## Verificar no Poco ao voltar

1. Conexões → Meu dia conectado: consultar hoje e amanhã, conferir contas e eventuais falhas.
2. Buscar por termo/data: consultar Gmail ou Drive; abrir um resultado legítimo.
3. Tarefas nas listas: alternar Todas/Pendentes/Concluídas.
4. Config. → Consultas Google: desligar automático, mandar um pedido novo e conferir que consulta só ao tocar.
5. Chat: Organize meu dia de amanhã com a agenda Google.
6. Chat: Planeje minhas tarefas para amanhã (conferir data no plano local).
7. Config. → Ajustar a voz → Ouvir amostra da Koi e Aplicar e ouvir Koi delicada; comparar e escolher voz instalada.
8. Ler texto na Koi em um resultado Gmail/Drive compatível; fechar e conferir que texto não ficou no histórico.
9. Testes fictícios de criação/conclusão/reabertura Google da ENTREGA_GOOGLE_V1_13.md, sempre com conta/título explícitos.

## Pendências reais — não declarar prontas

- Paginação completa, agendas secundárias, escolha de lista/calendar, busca sem limite e ações fora do recorte.
- Rascunhos/envio Gmail, anexos e edição Drive: não implementados; leitor limitado de texto entregue, sem ampliar escopos.
- Eventos Google recorrentes, convidados, apagar/undo: não implementados. Google Tasks API continua com vencimento por dia, sem alarme.
- Palavra de ativação Koi em segundo plano, voz contínua própria, execução autônoma de novas integrações: não implementadas. Perfis Android entregues; amostra curta de voz entregue, aguardando usuário ouvir. Escolha como assistente/voz precisa conferência real no aparelho.
- Comunicação automática com ChatGPT/Codex: não implementada. Assinatura pessoal não foi convertida em credencial de integração.
- Proatividade independente do Android e do despertar do Render, conclusão dos resumos longos e revisão final visual: permanecem no roadmap.
- Nenhuma nova conta foi necessária. Outlook/Microsoft e Next continuam cancelados.

## Referências técnicas verificadas

- https://developers.google.com/workspace/calendar/api/v3/reference/events
- https://developers.google.com/workspace/tasks/reference/rest/v1/tasks/list
- https://developers.google.com/workspace/gmail/api/guides/filtering
- https://developers.google.com/workspace/drive/api/guides/search-files

- Leitura: https://developers.google.com/workspace/drive/api/guides/manage-downloads
- Gmail: https://developers.google.com/workspace/gmail/api/reference/rest/v1/Format
