# Backend: demonstração e autenticação

Checkpoint de 04/10/2026. As respostas continuam simuladas; esta etapa prepara a
identidade verificada para a futura integração com IA. Não há escrita de conversas
pelo backend nem chamada a modelos nesta implementação.

## Rotas

| Rota | Acesso | Resultado |
| --- | --- | --- |
| `POST /api/v1/chat/demo` | Desenvolvimento, sem Authorization | Saudação/eco; 404 em produção. |
| `POST /api/v1/chat` | HTTPS + Bearer válido | Saudação/eco após verificar usuário no Supabase. |
| `GET /api/v1/chat/me` | HTTPS + Bearer válido | UUID verificado da própria conta. |
| `/api/v1/health`, `/` | Público | Disponibilidade e versão do serviço. |

O corpo de chat aceita apenas `message`, entre 1 e 8000 caracteres. `user_id`
fornecido pelo cliente é rejeitado. O backend consulta `GET /auth/v1/user` com o
token da solicitação e a chave publishable do projeto: não decodifica claims sem
verificação e não confia em `user_metadata`. Usuários anônimos são recusados.

Token ausente/inválido: 401; transporte HTTP na rota protegida: 400; falha de
verificação: 503. A consulta tem timeout e não segue redirecionamentos. Tokens e
respostas privadas da autenticação não são incluídos em mensagens de erro.

## Configuração

Instale `requirements.txt`, copie `.env.example` para `.env` e configure:

```dotenv
ENVIRONMENT=production
SUPABASE_URL=https://SEU-PROJETO.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_SUBSTITUA
```

Produção recusa iniciar sem as duas configurações. O URL precisa ser uma origem
HTTPS, sem credenciais embutidas. Não use uma chave secret/service_role.
Em desenvolvimento a demonstração funciona sem acessar Supabase.

Para hospedagem, exponha somente HTTPS. Quando o provedor termina TLS num proxy,
configure Uvicorn para confiar nos cabeçalhos encaminhados **somente dos proxies
do provedor** e restrinja o acesso direto ao processo. Não habilite confiança
global em `X-Forwarded-Proto` numa porta publicamente acessível. O backend verifica
o esquema que o servidor fornece à aplicação.

No Android, `-PkoiBackendUrl=https://SEU-SERVIDOR` seleciona chat autenticado.
O token é obtido da sessão renovável da conta atual. Em debug, uma origem HTTP
seleciona `/chat/demo` e o app nem recupera o token para essa chamada.
Release bloqueia HTTP tanto no código como no manifesto. A origem deve ser
configurada antes de usar release. Não coloque caminho, token ou senha no URL.

Cada instalação usa android/koiwai.local.properties, ignorado pelo Git,
ou propriedades Gradle. O fallback de debug aponta para o emulador Android. Atualizar o backend exige reiniciar o processo
Uvicorn, e atualizar o destino no app exige recompilar/reinstalar sem desinstalar.

## Verificação

`python -m unittest discover -s backend/tests -v` executa testes de integração
ASGI com respostas fictícias do serviço de autenticação. Não usa contas, tokens
ou conversas reais. Cobre autenticação, UUID verificado, recusa de usuário enviado
pelo cliente, falhas de rede/serviço, configuração e separação da demonstração.

Os testes Android verificam a escolha de rota, a exigência de HTTPS em release
e a recusa de origens com credenciais ou parâmetros.

A validação de ponta a ponta com uma conta real no servidor hospedado permanece
pendente: não há implantação HTTPS nem provedor de IA configurado neste checkpoint.
O login e a sincronização Android/Supabase já foram confirmados pelo usuário.

Referência oficial: [getUser](https://supabase.com/docs/reference/javascript/auth-getuser)
e [JWTs e validação](https://supabase.com/docs/guides/auth/jwts).
