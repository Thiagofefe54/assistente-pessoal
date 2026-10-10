# Packs 1–4 — atualização integrada V1.15.0

10/10/2026. APK code19. Incremento de estabilização para a futura V2.0;
não representa conclusão dos critérios físicos ou dos sete dias de uso.

## O que mudou

| Pack | Entrega desta atualização | O que já existia e foi preservado |
| --- | --- | --- |
| Inteligência e conversa | Conversa consulta tarefas relacionadas ao assunto e pendentes de hoje antes de sugerir algo. Até seis tarefas, com origem, horário, fuso e estado. Um agendamento não é tratado como acontecimento concluído. | Interpretação por IA, referências à fala recente da pessoa, ações reais com recibos, correções de saldo sem repetir cartão, identificação Koi/Coi. |
| Organização e memória | Busca de contexto ampliada de 40 para até 200 registros em cinco páginas, mantendo no máximo três trechos no prompt. Evita perder uma lembrança relevante após muitas edições recentes. Exclui arquivos e acontecimentos futuros; não executa instruções contidas nas fontes. | Lembranças editáveis, diário, busca com fontes, tarefas, hábitos, check-ins, sono, metas, finanças e relatórios. Os ciclos e permissões existentes permanecem cobertos pela suíte de regressão. |
| Conexões e voz | Agenda e tarefas Google consultam até três páginas: 60 eventos ou 60 tarefas por lista. Segue continuação inclusive após página vazia; interrompe cursor repetido e sinaliza parcialidade. Conexões permite escolher uma agenda secundária ou lista específica. Botão do sistema identifica explicitamente a voz Android. APK inclui amostra expressiva Pip escolhida. | Até três contas, criação/edição de Agenda, criação/edição/conclusão/reabertura de Tasks, leitor privado Gmail/Drive, voz Android e integração como assistente. |
| Confiabilidade | Consultas de lembretes, períodos de relatórios e acompanhamento repetem falhas transitórias até três vezes, com espera progressiva do WorkManager. Autorização expirada, conflito, pedido inválido e TLS exigem intervenção. Preserva notificações previamente programadas. | Histórico local, cache privado de painéis permitidos, isolamento por conta, recibos, pedido estável ao tentar novamente, limites automáticos de relatórios e consulta de consumo Poe. |

As consultas novas não fazem chamada adicional ao modelo. O contexto enviado
continua limitado a três trechos e seis tarefas; há pequeno acréscimo de contexto
em conversas sobre organização. Resultados Google continuam privados, sem prompt,
histórico ou cache. Saldo Inter permanece fotografia do provedor, não saldo ao vivo.

As tentativas automáticas novas são para consultas. Escritas no Google, criação de
tarefas, mensagens e geração paga de relatórios não foram colocadas em repetição
automática após timeout. Um resultado incerto não é anunciado como concluído.

## Evidências

- Backend: 265 testes aprovados, usando respostas fictícias de provedores.
- Android: 61 testes unitários aprovados e assembleDebug aprovado.
- APK: `android/build/releases/Koiwai-1.15.0.apk`.
- SHA256: `B56E29882DC3777121E4E10443C7881FFF37ADA598AC84EEE22CECE41542D24F`.
- MP3 embutido: 88.233 bytes, idêntico à amostra expressiva escolhida.
- Nenhuma chamada Poe real, compra, assinatura, alteração de consentimento,
  migração de banco ou apagamento de dados nesta entrega.
- Ambiente de compilação: Java25 falhava ao abrir pipe no diretório temporário;
  build aprovado com diretório temporário curto, só para esse processo.

## Voz: limite importante

A amostra expressiva é reprodução local de cinco segundos. Respostas variáveis
continuam usando o mecanismo Android escolhido. Não foi instalada uma voz
contínua do Runway nem autorizada cobrança nova. Para considerar esse item fechado
na V2.0, escolher entre manter a voz Android ou financiar/validar uma solução
de síntese expressiva com limite de uso. Não afirmar que a amostra fala qualquer frase.

## Conferência no Poco e critérios para fechar os packs

USB estava ausente em 10/10. A versão confirmada no aparelho antes desta entrega
era V1.14.1/code17. Instalar a V1.15.0 com atualização que preserve dados;
nunca desinstalar ou apagar histórico para passar em testes.

1. Criar dado fictício: “Me lembra de estudar amanhã às 19h”. Consultar com
   frase diferente, concluir, reabrir, editar horário e verificar a lista.
2. Registrar lembrança fictícia; pedir recuperação com outra frase, corrigir
   e consultar novamente. Conferir fontes e que a correção substituiu o dado.
3. Conversar sobre treino/estudo e “como organizar isso?”; conferir se a sugestão
   usa dados realmente presentes, sem afirmar que planos aconteceram.
4. Em cada uma das três contas Google, consultar sua agenda e Tasks.
   Usar Minhas agendas → Consultar esta agenda e Listas → Consultar esta lista.
   Criar/editar/concluir/reabrir um item Teste na conta escolhida explicitamente.
   Conferir no Google antes de considerar a alteração confirmada.
5. Ouvir amostra expressiva e testar leitura de resposta; distinguir a amostra
   da síntese Android. Conferir microfone e assistente padrão.
6. Programar lembrete, apagar tela e verificar entrega; repetir após reiniciar.
   Conferir horário de silêncio, revogação de notificações e permissão concedida.
7. Interromper rede durante consulta e durante ação fictícia. Restaurar conexão:
   leitura deve recuperar; ação deve usar recibo/pedido estável, sem duplicação.
8. Após servidor dormir, enviar mensagem e conferir espera/recuperação.
   Reautorizar Google quando necessário; erros de autorização não entram em loop.
9. Acompanhar consumo Poe e sete dias de uso. Anotar falhas reais e corrigi-las
   antes de marcar os quatro packs concluídos e avançar para visual final V2.0.

Esses testes físicos não foram executados nesta sessão. Não automatizar revogação
de contas nem mexer em dados reais para simular uma falha. Windows e reformulação
visual final continuam fora desta atualização.

## Publicação

Preparada para publicação; substituir esta linha pelo resultado real do Render.

## Referências de implementação

- [Google Calendar: eventos e paginação](https://developers.google.com/workspace/calendar/api/v3/reference/events/list).
- [Google Tasks: consulta, concluídas e paginação](https://developers.google.com/workspace/tasks/reference/rest/v1/tasks/list).
- [Supabase: changelog consultado](https://supabase.com/changelog). Sem migração
  ou mudança de autenticação nesta atualização.
