# Meu ritmo com a Koi — V1.6, 09/10/2026

## O que foi implementado

Home e Rotina → **Meu ritmo**, em três abas:

- **Meu dia:** conclusões de hoje, pendências até hoje ou sem data, acontecimentos,
  sono de intervalos encerrados hoje, contas próximas e limites de gastos.
- **Hábitos:** dias com conclusão nos últimos 7 dias, próxima ocorrência e botão
  Concluir esta etapa. Repetição diária/semanal/mensal avança uma ocorrência.
  Uma etapa futura não pode ser concluída aqui. Não define frequência ideal.
- **Check-ins:** categorias estudo, trabalho, academia, casa, sono e outros.
  Comecei/Terminei, Vou dormir/Acordei e registro avulso. Horário vem do servidor.
  Inícios abertos até 48h aparecem; intervalos maiores precisam ser corrigidos no
  Diário. Sono é relato, não medição. Marcar como Teste inicia ligado nesta fase;
  registros fictícios entram nas contagens até serem removidos/arquivados.

Até três sugestões para pendências atrasadas: levar para amanhã, mantendo horário,
fuso e repetição. Só escreve ao tocar. O motivo usa acontecimentos registrados,
sem concluir que você está ocupado ou inventar duração/disponibilidade.
Há **Desfazer última ação**, confirmação por recibo e repetição do mesmo pedido
após falha para evitar duplicação. Conflitos pedem atualizar a lista.

Config. → **Cuidados com seu dia**:

- Ativação manual e permissão de notificações; configuração antiga é preservada.
- Tipos independentes: começo do dia, revisão, pendências/contas/orçamento e carinho.
- Até 1, 2 ou 3 avisos no conjunto por dia (padrão 2). Intervalo mínimo 4h;
  carinho exige 6h e inicia desligado. Cada tipo é enviado no máximo uma vez/dia.
- Começo 09h e revisão 20h inicialmente, horários ajustáveis. O limite pode impedir
  a revisão se avisos anteriores já o atingiram. Na noite, revisão tem prioridade.
- Respeita o descanso compartilhado dos lembretes (padrão 22h–08h), conta conectada
  e permissão tanto antes de consultar quanto antes de entregar.
- Conferir agora também respeita frequência/descanso; mostra o motivo de não enviar.

Lembretes pontuais de tarefas continuam separados. Desativá-los agora cancela
somente suas notificações, preservando avisos do acompanhamento.

## Como funciona e limites

Quatro endpoints autenticados: review, checkin, task-action e undo. Reutilizam as
RPCs atômicas, versões, recibos e isolamento por conta existentes no Supabase.
Nenhuma nova tabela/migração, segredo, plano ou permissão foi acrescentado.
IDs de pedido persistem no celular antes de enviar. Resposta de outra conta é descartada.

Painel, botões e avisos usam **zero geração de IA**. No chat, a interpretação pode
selecionar revisão do dia e consumir pontos; depois consulta sem segunda geração.
O pacote não conecta bancos, Gmail, Drive ou esta conversa do ChatGPT.

Consulta até 2.000 registros/pagamentos, 500 tarefas e 500 conclusões em 7 dias.
Mostra até 12 hábitos/inícios, 10 conclusões e 3 sugestões. Resumos limitados são
identificados. Reabrir preserva o evento anterior; Desfazer conclusão reverte a ação.

Os avisos usam WorkManager aproximadamente a cada hora e após atualizações,
com limitação de conferências extras. Dependem de celular ligado, sessão, internet
 e liberdade de execução no Android. Podem atrasar. Não são alarmes exatos nem
um serviço autônomo 24h em nuvem. O PC pode estar desligado.

## Verificação e entrega

163 testes backend passaram, incluindo 14 do pacote, com IA simulada.
35 testes unitários Android aprovados; assembleDebug/AndroidTest e lintDebug
aprovados (0 erros, 97 avisos). Daemon Gradle encerrado para liberar RAM.
APK android/build/releases/Koiwai-1.6.apk; SHA256
5A13A9957C0765A96E3D47C6D7D9BCA328DBC7F98261545B82DC3EA932267EA3.
Servidor publicado: commit 6c384dde45818428f635c11de4c9ee695c865b42,
Render dep-db4ehs3ncjis73coaung LIVE09/10/2026 10:16:30 SãoPaulo.
Saúde HTTPS200/providerpoe; quatro endpoints novos anônimos recusados com401.
Logs de erro após publicação não mostraram erros na consulta de conferência.

Poco14c3a88a atualizado com install-r; confirmado1.6/code8. Sem apagar dados.
CompanionApiLiveTest: **2 testes aprovados em34,344s** usando a sessão dentro
 do aparelho, sem exportar token ou gerar IA. Check-in início/repetição/fim/Desfazer;
reagendamento preservando19h/repetição, retrysemduplicação, conclusão avançando
hábito e Desfazer. Apenas as duas fixtures próprias criadas foram removidas;
recibos das ações continuam na conta. As12 TesteV1.5 permanecem.

Tela Meu ritmo aberta e capturada com dados no Poco; primeiro print saiu vazio
por captura imediata após abertura, segundo mostrou revisão e sugestões carregadas.
LogAndroidRuntime não mostrou erro nessa conferência. Isso não comprova todas as
abas visualmente nem a entrega de notificações ao longo do dia.
Novas regras de frequência/prioridade têm5 testes unitários. Teste físico de
notificações V1.6, descanso prolongado e reinício ainda pendentes.
Dashboard revisado no Brave; checklist manual0/5, não preenchida pelo agente.
Os testes físicos preparados usam só fixtures fictícias próprias e removem apenas
elas. A base Teste V1.5 autorizada permanece. Não houve reset.

## Próximo passeio

1. Abrir Meu ritmo → Check-ins com Marcar como Teste ligado.
2. Registrar Comecei, depois Terminei e conferir o Diário.
3. Conferir Hábitos e revisão; aplicar uma sugestão e testar Desfazer.
4. Escolher os avisos e descanso nas configurações.
5. Observar notificações reais ao longo dos horários; testes rápidos não comprovam
   pontualidade durante dias ou após reiniciar o aparelho.
