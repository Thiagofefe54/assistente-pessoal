# Koiwai — navegação Android

Entrega em 04/10/2026, dentro do desenvolvimento da v0.1.0; não representa o fechamento dessa versão.

## Implementado

- Navegação compartilhada Home, Chat, Memória, Rotina e Configurações.
- Retorno do sistema: detalhe de Memória para dias, categoria para Rotina, conta para a tela de origem e aba para Home.
- Estado salvo das telas por conta, preservando rascunhos ao trocar de aba.
- Memória consulta o histórico real da conta ativa, agrupa por data e abre as mensagens de cada dia, com estados de carregamento e vazio. Resumos e memórias importantes ainda não são gerados.
- Rotina abre Tarefas, Agenda, Notas, Treinos e Finanças, identificadas como em preparação. Não há cadastro nessas categorias nesta entrega.
- Configurações abre Conta e sincronização e salva localmente um tratamento de até 30 caracteres para a saudação da Home.
- Tema escuro comum com as cores da identidade aprovada. Personagem e composição da Home preservadas.
- Saudação, horário e data reais na Home. Conta ativa/histórico local substituem o estado Online fixo, sem afirmar disponibilidade do servidor.
- Home pode rolar em telas menores. Nome exibido do aplicativo atualizado de Assistente para Koiwai.

## Validação

- APK compilado e instalado por atualização no Poco aparelho físico, sem desinstalar nem limpar os dados.
- Teste unitário existente passou na primeira compilação da entrega.
- Lint da versão final: zero erros, 23 avisos.
- Teste instrumentado NavigationTest passou no Poco: abas, categoria de rotina, retorno pelo sistema, conta a partir de Configurações e retorno do Chat para Home. Não envia mensagens nem altera preferências.
- O teste precisou do aparelho desbloqueado. A chamada de teste Espresso ao botão Voltar encontrou a restrição INJECT_EVENTS do fabricante; o teste passou usando o dispatcher de retorno da Activity, sem modificar permissões do aparelho.
- Home conferida visualmente no aparelho. git diff --check passou.
- Antes das mudanças, foi preservada uma cópia local dos fontes Android e documentação em `koiwai-antes-navegacao.zip`, no diretório do projeto ChatGPT. O arquivo não contém bancos de conversas nem sessões do dispositivo.

## Pendências

- Cadastros de Rotina, resumo diário, memórias importantes e busca.
- Provedor de IA, backend autenticado e hospedagem HTTPS para conversar com o PC desligado.
- Retorno do e-mail de confirmação ao app e recuperação de senha.
- Revisão dos avisos do Supabase já registrados em ESTADO_ATUAL.md.
- Commit/push e organização formal das versões ainda pendentes; o backup desta entrega é local.

O usuário confirmou nesta conversa que login e sincronização já funcionaram. Essa confirmação substitui a pendência anterior de testar a primeira conta descrita em ESTADO_ATUAL.md.
