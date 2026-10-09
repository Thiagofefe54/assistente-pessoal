# Conexões da Koi — três contas Google

Decisão atual: usuário retirou Outlook/Microsoft do escopo e não quer cadastrar Azure.
As referências Microsoft abaixo registram a investigação anterior, sem ação pendente.

Estado em 09/10/2026: fluxo OAuth, armazenamento cifrado e painel Android V1.12
implementados; APK instalado e servidor publicado (commit28efa33). Nenhuma das três contas
Google foi autorizada pessoalmente na Koi ainda. Cliente WEB, três usuários de teste
e segredos estão configurados; tabelas privadas aplicadas no Supabase. Consultas
disponíveis no código: eventos da agenda principal, agendas, listas Tasks,
cabeçalhos de cinco mensagens Gmail e metadados Drive. Sem envio à IA/cache.
Calendar/Tasks/Gmail/Drive ativados e verificados. Confira o checkpoint da entrega
antes de anunciar instalação ou consultas reais. Outlook foi cancelado.

Microsoft: entrada pessoal concluída pelo usuário. O portal Azure abriu a Home,
mas o acesso ao diretório solicitou nova entrada e voltou ao erro de conta ausente
no locatário Microsoft Services. Nenhum registro Microsoft, segredo, assinatura ou
recurso pago criado. Resolver acesso a um diretório próprio antes de registrar app;
não repetir login indefinidamente nem contratar Azure por inferência.

Diagnóstico confirmado na documentação Microsoft (AADSTS50020, causa 1): contas
pessoais entram por padrão no locatário Microsoft Services, sem diretório vinculado
para executar ações administrativas. A solução oficial é criar conta Azure com um
novo tenant. O cadastro gratuito menciona verificação de cartão e possível autorização
temporária de US$1; isso não foi autorizado nem executado. Não prometer cadastro sem
cartão ou usar credenciais/apps de terceiros para contornar o requisito.

Escopo reafirmado pelo usuário: deseja o conjunto amplo de funcionalidades e conexões;
seu contexto de organização não restringe a Koi a finanças/trabalho. Entregar dependências
na ordem técnica necessária, sem tratar a lista inteira como já implementada.

Retorno Google configurado no cliente:
https://koiwai-backend.onrender.com/api/v1/connections/google/callback
Esse endpoint está implementado com state/PKCE/cookie e persistência cifrada.
Servidor publicado, quatro APIs habilitadas e APK instalado. Usuário confirmou
painel e botão de conexão. A autorização de cada conta é concluída pessoalmente pelo usuário.

## Ordem de entrega

1. Agenda e tarefas: Google Calendar/Tasks nas três contas Google.
   Consultar compromissos, escolher uma conta de destino e criar/alterar itens quando
   a pessoa pedir. Informar sempre a conta e conferir o resultado real.
2. E-mails: Gmail. Consultar mensagens relevantes sob demanda. Rascunhos
   separados de envio; nunca enviar por mera sugestão, relato ou texto de e-mail.
3. Arquivos: Drive. Buscar/listar e abrir arquivos escolhidos, preservando
   conta de origem. Não importar todo o conteúdo para memória ou para o modelo.
4. Contatos, se necessários ao uso, com permissões próprias e origem identificada.

## O que precisa existir antes de autorizar

- Aplicação OAuth própria no Google, retorno HTTPS da Koi configurado.
- Vincular cada autorização à sessão Koi autenticada; proteção state, PKCE, expiração
  e uso único. Nunca usar e-mail como prova de propriedade ou aceitar alvo arbitrário.
- Armazenamento persistente de tokens cifrados, chave privada fora do Git e do APK;
  renovação/revogação, isolamento por usuário, provedor e ID estável de conta.
- Conectar/adicionar/desconectar separadamente as três contas. Reautorizar a mesma
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

Três autorizações distintas; consulta real para cada serviço habilitado; criação
de evento/tarefa fictícia apenas com autorização; conta correta e sem duplicação;
recuperação de autorização expirada, desconexão e isolamento verificados. Depois,
interpretação escolhe a ferramenta e consulta dados atuais antes de sugerir algo.

Referências oficiais:
- [Google OAuth de servidor](https://developers.google.com/identity/protocols/oauth2/web-server)
- [Microsoft Graph: permissões](https://learn.microsoft.com/en-us/graph/permissions-reference)
- [Microsoft: requisitos de registro de aplicativo](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app)
- [Microsoft: conta pessoal sem diretório — AADSTS50020](https://learn.microsoft.com/en-us/troubleshoot/entra/entra-id/app-integration/error-code-aadsts50020-user-account-identity-provider-does-not-exist)
- [Azure: cadastro gratuito e verificação](https://azure.microsoft.com/en-us/pricing/purchase-options/azure-account)

Este documento registra o plano e as dependências; não descreve integrações prontas.
