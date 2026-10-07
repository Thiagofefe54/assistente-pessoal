# Login e histórico na nuvem

Projeto Supabase dedicado à Koiwai; configuração fora do código versionado.
A tabela chat_messages foi criada pelo SQL Editor. O bootstrap e os testes estão
em docs/sql. Os testes SQL passaram usando dados fictícios revertidos ao final:
isolamento entre contas, acesso anônimo negado, reenvio sem duplicação e fuso.

O Android usa Auth por e-mail/senha e REST por HTTPS diretamente no Supabase.
A chave publishable é pública; nenhuma chave administrativa ou senha de banco
está no app. Tokens são criptografados com uma chave do Android Keystore,
renovados antes de expirar e excluídos do backup/transferência do Android.

Cada conta tem seu próprio banco Room; sair retorna ao histórico local anterior.
Copiar histórico local para a conta é uma ação explícita na tela Conta Koiwai.
A atualização de Room 1 para 2 adiciona apenas o estado de sincronização.
Mensagens concluídas vão para uma fila local e WorkManager tenta sincronizar
quando houver rede. Uma tentativa de sincronização não reenvia mensagens com
falha ao backend. Sincronizar agora também baixa mensagens de outros dispositivos.
A leitura é paginada e reconcilia todo o histórico por UUID a cada sincronização.
Isso evita perder inserções simultâneas que terminam fora da ordem de seus IDs
sequenciais. Um cursor incremental confiável será adicionado numa etapa futura.

O FastAPI continua respondendo com saudação/eco. O checkpoint de autenticação
separa a demonstração HTTP de desenvolvimento (sem tokens) da rota protegida
HTTPS, que verifica a conta no Supabase. Hospedagem e integração com IA continuam
pendentes: a instalação local ainda precisa do PC para receber respostas.
Detalhes e configuração: BACKEND.md.
Exclusão/edição remota, resumos e atualização em tempo real não estão nesta entrega.

## Teste no Poco

1. Atualizar sem desinstalar e conferir o histórico local anterior.
2. No chat, Entrar / criar conta; criar conta com uma senha própria.
3. Confirmar o e-mail quando solicitado; voltar e entrar.
4. Copiar histórico local para minha conta somente se desejar enviar as conversas.
5. Enviar mensagens e tocar Sincronizar agora; verificar sucesso.
6. Fechar/reabrir e conferir conta e conversa; desligar a internet e ler o histórico.
7. Sair e entrar com outra conta; verificar que não vê o histórico da primeira.
8. Entrar com a mesma conta em outra instalação e sincronizar para baixar a conversa.

O usuário confirmou cadastro/login e sincronização funcionando na instalação
Android original. O agente não utilizou a senha ou a sessão pessoal nesses testes.


Os bancos de conversa e a sessão ficam fora do backup/transferência automática
do Android. A recuperação em outro aparelho é feita por login e sincronização
no Supabase; conversas exclusivamente locais não têm backup externo nesta etapa.

### Revisão de permissões em 07/10/2026

EXECUTE de public.rls_auto_enable() foi revogado de PUBLIC, anon e authenticated.
O mecanismo de inicialização via event trigger foi preservado. O Advisor não
reportou mais esses dois avisos. A tabela memory_facts tem RLS e quatro políticas
de dono; isolamento, fonte e alterações concorrentes passaram em teste fictício
com rollback. Nenhuma conversa ou senha existente foi alterada.

Permanece o aviso existente de [proteção contra senhas vazadas desativada](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).
Não foi alterado o plano gratuito ou a configuração de Auth nesta etapa.
