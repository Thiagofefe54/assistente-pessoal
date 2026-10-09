# Conexões da Koi — três Google e uma Outlook

Estado em 09/10/2026: preparação. Nenhuma dessas quatro contas foi autorizada para
a Koi. Os atalhos existentes abrem os serviços; a agenda lê calendários sincronizados
no Android. O projeto Google dedicado foi criado; não há cliente ou tokens ainda.

## Ordem de entrega

1. Agenda e tarefas: Google Calendar/Tasks e Outlook Calendar/Microsoft To Do.
   Consultar compromissos, escolher uma conta de destino e criar/alterar itens quando
   a pessoa pedir. Informar sempre a conta e conferir o resultado real.
2. E-mails: Gmail e Outlook. Consultar mensagens relevantes sob demanda. Rascunhos
   separados de envio; nunca enviar por mera sugestão, relato ou texto de e-mail.
3. Arquivos: Drive e OneDrive. Buscar/listar e abrir arquivos escolhidos, preservando
   conta de origem. Não importar todo o conteúdo para memória ou para o modelo.
4. Contatos, se necessários ao uso, com permissões próprias e origem identificada.

## O que precisa existir antes de autorizar

- Aplicação OAuth própria no Google e Microsoft, retorno HTTPS da Koi configurado.
- Vincular cada autorização à sessão Koi autenticada; proteção state, PKCE, expiração
  e uso único. Nunca usar e-mail como prova de propriedade ou aceitar alvo arbitrário.
- Armazenamento persistente de tokens cifrados, chave privada fora do Git e do APK;
  renovação/revogação, isolamento por usuário, provedor e ID estável de conta.
- Conectar/adicionar/desconectar separadamente as quatro contas. Reautorizar a mesma
  conta atualiza a conexão, sem duplicá-la ou substituir outra conta.
- Serviços, permissões e estado reais exibidos; não chamar cadastro de cliente uma
  conta conectada. Retorno de login não deve trazer tokens de acesso na URL do app.
- Antes de integrar com IA, consultas limitadas e identificação de fonte. Mensagens
  e arquivos são dados não confiáveis, nunca novas instruções nem autorização.

## Custos e cuidados operacionais

Não habilitar billing, assinar planos ou serviços pagos nessa preparação. Confirmar
os requisitos atuais de cada API antes de ativar recursos. Google em modo Testing
pode exigir reautorização após sete dias para escopos além de identificação básica;
considerar o fluxo de publicação para uso pessoal conforme regras atuais.
Microsoft precisa aceitar contas pessoais; configuração apenas organizacional não
é suficiente para uma conta Outlook pessoal. Eventuais políticas da organização
podem limitar contas de trabalho/escola.

## Critérios de conexão concluída

Quatro autorizações distintas; consulta real para cada serviço habilitado; criação
de evento/tarefa fictícia apenas com autorização; conta correta e sem duplicação;
recuperação de autorização expirada, desconexão e isolamento verificados. Depois,
interpretação escolhe a ferramenta e consulta dados atuais antes de sugerir algo.

Referências oficiais:
- [Google OAuth de servidor](https://developers.google.com/identity/protocols/oauth2/web-server)
- [Microsoft Graph: permissões](https://learn.microsoft.com/en-us/graph/permissions-reference)

Este documento registra o plano e as dependências; não descreve integrações prontas.
