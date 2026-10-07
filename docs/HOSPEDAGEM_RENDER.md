# Servidor gratuito no Render

Configuração preparada em 07/10/2026; publicação e teste no aparelho são
registrados em ESTADO_ATUAL.md quando concluídos.

O serviço Python usa render.yaml, .python-version e requirements.txt. Plano Free,
uma instância e deploy manual. O banco de memória continua no Supabase; não é
criado um banco Render com expiração e não há conversas em disco no servidor.

## Configuração privada

No ambiente do serviço, informe ENVIRONMENT=production, SUPABASE_URL,
SUPABASE_PUBLISHABLE_KEY e GROQ_API_KEY. Não envie o arquivo .env ao GitHub.
Não são necessárias senha do banco, chave secret/service_role ou senha do app.
A chave Groq permite ao servidor usar a cota dessa conta e deve ficar nos segredos
do serviço. Configurar uma chave por uma tela do app é uma etapa futura; isso exige
HTTPS, controle de dono e armazenamento próprio no servidor. Uma tela sozinha
não protege um segredo salvo sem criptografia.

## HTTPS e proxy

O Render termina TLS no balanceador e redireciona HTTP para HTTPS. Sua porta
interna não é acessível diretamente pela internet. Uvicorn aceita cabeçalhos
encaminhados apenas de loopback e redes privadas RFC1918, não de qualquer IP.
O acesso autenticado deve ser validado no endereço HTTPS final, incluindo a
recusa de token ausente/inválido e 404 na rota de demonstração em produção.
Se o provedor alterar as redes internas, confira a origem do proxy antes de
adaptar a lista; não remova a exigência HTTPS da autenticação.

## Limitações reais

O Free dorme após 15 minutos sem tráfego e costuma precisar de cerca de um minuto
para voltar. Há cotas de horas, tráfego, builds e requisições externas. Sem método
de pagamento, esgotamento das cotas pode suspender serviço/builds. Não adicionamos
cartão nem ativamos plano pago; não simulamos tráfego para impedir suspensão.

O Android tolera até 90 segundos de leitura. Conversas ficam preservadas no
celular após uma falha; reenvio é manual. Groq tem cotas próprias, contexto/saída
limitados e reserva apenas em falhas temporárias. O backend ainda não tem cotas
persistentes por usuário nem cache de respostas entre tentativas.

## Critério de conclusão

- Health HTTPS responde; produção desabilita demonstração.
- Sem sessão válida não há chamada ao modelo.
- Conta existente continua conectada após atualizar APK sem desinstalar.
- Teste fictício no aparelho usa IA e contexto, sem enviar o diário pessoal.
- Verificar histórico/sincronização e depois testar com o servidor local desligado.

Referências: [Free](https://render.com/docs/free),
[FastAPI](https://render.com/docs/deploy-fastapi),
[porta e TLS](https://render.com/docs/web-services),
[BluePrint](https://render.com/docs/blueprint-spec).
