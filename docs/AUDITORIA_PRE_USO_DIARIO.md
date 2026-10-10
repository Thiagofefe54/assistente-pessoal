# Revisão antes do uso diário — 10/10/2026

Base: Android V1.15.0/code19. Esta entrega altera apenas o servidor, sem apagar
dados, comprar serviços, gerar respostas pagas de teste ou reformular o visual.

## Cobertura

Inventário dos fontes e configurações; análise estática do backend; revisão dos
fluxos críticos de autenticação, troca de conta, histórico e migrações, ações e
recibos, memória, tarefas e horários, finanças, Google, notificações, relatórios,
voz, cache e recuperação de rede. Revisão dos scripts SQL de isolamento por dono
e privilégios; não houve alteração nem nova inspeção do banco publicado.
Não é uma certificação de ausência de bugs nem uma leitura manual de cada linha.

## Problemas corrigidos

1. **Tarefas em outro fuso:** o resumo do dia contava e exibia a data original,
   diferente do planejador. Agora converte data/hora ao fuso solicitado para
   contagem, seleção e exibição. A data persistida e a versão usadas pelas ações
   ficam intactas. Regressão: tarefa UTC 09/10 01h aparece em São Paulo 08/10 22h.
2. **Agenda incompleta:** evento sem término ou com datas inválidas era ignorado;
   campos nulos podiam derrubar o planejamento. Agora são tratados como consulta
   incompleta, sem apresentar janelas livres como resultado confiável. O resumo
   de múltiplas contas também rejeita estruturas inválidas. Eventos válidos,
   livres/recusados e sobreposições mantêm seu comportamento.
3. **Relatórios simultâneos:** dois pedidos da mesma conta podiam chegar à IA
   antes de qualquer resultado ser salvo. Preparações agora são exclusivas por
   conta no processo; o segundo pedido recebe 409 antes da geração. Consultas e
   outras contas continuam disponíveis. Um período pode preparar seu capítulo
   interno, e erros liberam a proteção. Não há repetição automática de geração.

## Verificação

- 269 testes backend passaram, incluindo quatro novos testes de regressão e
  concorrência. Antes das correções, os testes de fuso e agenda reproduziram erros.
- Android: 61 testes existentes aprovados; compilação do APK aprovada.
- Lint aprovado sem erros impeditivos; 111 avisos permanecem: 67 UseKtx,
  17 ApplySharedPref, 9 sobre dependências/versões e 18 de recursos e estilo.
  Não trocar `commit()` indiscriminadamente: persistência imediata é necessária
  em sessões e proteções contra duplicação.
- Sem chamadas pagas de IA nesta auditoria. Nenhum dado financeiro pessoal usado
  como fixture; nenhum histórico ou conta apagado.

## Limites e próximos testes

- A exclusão de relatórios vale para o atual servidor de um processo. Antes de
  escalar para múltiplos processos/instâncias, precisa de coordenação persistente.
- O histórico é reconciliado desde o início para não perder commits atrasados;
  pode ficar custoso com históricos grandes. Não substituir por cursor simples.
- Notificações dependem das permissões e da economia de bateria do Android.
  WorkManager não promete entrega no segundo exato.
- Inter é uma fotografia do provedor, não atualização bancária instantânea.
- A voz expressiva escolhida continua sendo uma amostra; respostas faladas usam
  Android. Visual final e Windows permanecem adiados.
- Conferir no uso real: reinício do telefone, falta de internet no meio de uma
  ação, servidor acordando, autorizações expiradas, escritas Google nas três
  contas e conversa com frases variadas. Observar durante sete dias antes de V2.0.

O aparelho não apareceu por USB na conferência desta auditoria. As verificações
físicas da V1.15.0 estão registradas em ENTREGA_PACKS_1_4_V1_15.md; não foram
repetidas nem contadas como novas aqui.
