# Koiwai — revisão funcional e direção aprovada

## Objetivo real

Companheira para organizar a vida por conversa: registrar relatos, acompanhar
rotina/sono/trabalho/academia, dinheiro informado e compromissos; relacionar períodos,
agir e lembrar com carinho. Personalidade delicada, tímida, prestativa, feminina,
natural, usa “mestre” sem repetição excessiva. Não inventar saldo, acontecimentos,
promessas de vigilância ou ações. Funcionalidades primeiro; redesign depois.
Usuário considera as telas recentes desorganizadas/feias; aprovação visual antiga
não representa aprovação da navegação atual. Preservar identidade e cores, não
tratar o layout atual como definitivo. Windows após o celular.

## Escopo da revisão

Leitura dos fluxos críticos de backend (auth, geração, ações, registros pessoais,
diário, relatórios e datas), Android (sessão, persistência, sincronização, tarefas,
registros, lembretes, relatórios, voz, imagem e controles externos), documentação,
SQL versionado e configuração remota. Testes das suítes completas disponíveis.
Isso não é garantia de ausência de todos os erros ou leitura/verificação física
de cada tela. Documento DOCX original indisponível no caminho Downloads informado;
documentação versionada e conversa atual usadas. Nenhum arquivo sincronizado alterado.

## Correções comprovadas

1. Autorização textual: vocativo Koi/Koiwai não deve esconder uma pergunta “como…”.
   “Agora” não deve anular “não”. Prefixo limitado a 35 caracteres podia perder
   uma negativa longa. Agora a análise usa a cláusula e mantém negação/“sem”.
   Casos com “mas” só permitem ação na cláusula afirmativa. Integração simulada
   confirma que perguntas e negativas não produzem RPC de escrita.
2. Financeiro: períodos exatos da semana corrente, semana anterior e mês anterior;
   centavos, fuso local e arquivados excluídos. Totais atuais até hoje; datas futuras
   não são valores recebidos/gastos hoje. Total geral continua soma dos registros,
   não saldo bancário. Sem previsão salarial automática.
3. Resposta pessoal só com espaços passa a ser recusada antes de qualquer escrita.
4. Android: entrada monetária brasileira com milhares e vírgula aceita (1.234,56);
   agrupamentos malformados/ambíguos recusados. Bloqueio do Android ao abrir outro
   app tratado com aviso, não SecurityException não capturada.
5. Personalidade reforça organização, acolhimento e relatos sem interrogatório;
   não transforma relato em tarefa, memória permanente ou finança automaticamente.

## Estado observado

- Backend: 79 testes passaram (76 anteriores + 3 novos). Android: 25 unitários;
  build/lint final e APK registrados no fechamento do checkpoint de continuidade.
- Render Free, auto-deploy desativado; último deploy anterior Live 1550f68.
  Nenhum log nível error retornado pela consulta; isso não prova ausência de falhas.
- Nove tabelas da Koi com RLS habilitado e políticas. Não recriados usuários/dados.
- Advisor de segurança: somente aviso antigo de proteção de senhas vazadas desativada.
  https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection
- Capacidade confirmada em SQL real: 200 registros pessoais no total por conta,
  incluindo arquivados, compartilhados pelas seis categorias; 500 slots de tarefas
  e 20 de lembranças no SQL versionado. Isso precisa crescer antes de uso prolongado.
- V1.0 é o APK instalado anteriormente. V1.1, versionCode 3, preparada nesta revisão;
  celular desconectado. Não instalar, resetar ou alegar testes físicos novos.
- Build V1.1 e lint aprovados: zero erros/69 avisos. APK
  android/build/releases/Koiwai-1.1.apk, SHA256
  502a74dea3280a7f7abd3fff26b3c703077b40bb6b0e127d8b68a93217833c8d.
- App não é assistente padrão, não tem wake word, não lê notificações nem controla
  outras telas. Google/ChatGPT são atalhos, bancos não estão conectados.

## Limitações essenciais para a visão aprovada

Conversas estão guardadas, mas o contexto do chat é recente/limitado; possuir diário
não significa consultar automaticamente toda a vida da pessoa. Resumos têm fontes,
mas não há eventos estruturados de sono, deslocamento, rotina modificada ou contas.
“Recebi…” pode selecionar finanças, porém salvar exige pedido explícito atual.
Não há detecção de depósitos, orçamento com alertas de excesso, contas recorrentes,
classificação de valores previstos/pagos ou acompanhamento autônomo de rotina.
Fotos são locais; registros pessoais requerem rede; fila de ações pessoais não é
durável em reinício; exportação integral, recuperação de conta e limites próprios
por usuário ainda precisam de desenvolvimento. Não listar isso como entregue.

## Próximos grandes pacotes

1. Diário estruturado + consulta contextual: eventos com fonte, data/fuso,
   intenção vs realização, rotina/sono, correção/desfazer e política de privacidade.
   Critério: lembrar relato de outro dia com prova, sem duplicação após reenvio.
2. Finanças de verdade + capacidade: recebidos/previstos, contas com vencimento,
   pago/pendente, recorrência, orçamento e comparações; histórico escalável.
   Critério: totais estruturados e contas alertadas uma vez, sem confundir saldo.
3. Proatividade configurável: revisão do dia e lembretes úteis, frequência limitada,
   quiet hours, recibos de entrega, rede/reinício/bateria e eventual push.
4. Voz e papel de assistente: definir viabilidade Android, wake word, voz alternativa
   e permissões, sem vigilância presumida. Integrações por ações concretas.
5. Redesign + uso prolongado: consolidar telas e botões, separar funcionalidades
   por intenção, manter identidade gamer/animada, então sete dias de uso e Windows.

## Dinheiro e Render: corrigir expectativa

Nenhuma assinatura autorizada/contratada nesta revisão. Orçamento gratuito continua
válido; usuário considera investimento mensal pequeno, contratação depende do preço
final e decisão específica. Starter remove suspensão por inatividade. Não ativa
funções, IA mais inteligente, memória autônoma ou notificações por si.
Lembretes e relatórios atuais são coordenados pelo Android, com atraso possível.
Não existe scheduler de acompanhamento pessoal ou push na nuvem. Servidor pago
pode apoiar isso no futuro; não é obrigatório pagar para continuarmos os módulos.
Fonte: https://render.com/docs/free

Dashboard atualizado: docs/dashboard/index.html. Ilustrativo, sem leitura de dados
pessoais ou pedidos reais à IA; exemplos e checkboxes não concedem permissões.

## Fechamento verificado

Código fa797b0 publicado no GitHub. Backend Render Free Live:
dep-db3un0nf3r2c73dlluq0 (08/10/2026). Health HTTP 200, chat sem sessão 401 e
demo produção 404. Consulta Groq real com registros fictícios e cloud substituída
por fixture respondeu sem ação/escrita; não usou informações pessoais.
Testes locais verificam os cálculos exatos; não afirmar que o texto da IA é
determinístico. Sem conversa autenticada no Poco nesta sessão.
Dashboard recarregado no navegador; exemplo “Recebi dinheiro” e filtro de próximos
módulos verificados. V1.1 segue preparada, não instalada. Nenhum reset realizado.
Limites de crescimento também a tratar: histórico local carrega mensagens/fotos
na lista e precisa avaliação de memória/paginação; resumo de um dia tem limite
500 mensagens/60 mil caracteres e síntese tem limite de entrada. Vida inteira
não cabe no contexto do modelo; seleção de fontes e retenção são parte do próximo pacote.
