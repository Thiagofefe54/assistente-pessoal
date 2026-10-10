# Conversas e avisos · prévia V2.2

- Conversas separadas por ID UUID; histórico recente do modelo não cruza chats.
- Lembranças/rotina do dono seguem compartilhadas; não são apagadas ao mudar de chat.
- Seletor com busca por título/última mensagem, prévia, fixados e pastas Pessoal/Estudos/Trabalho/Outros.
- Rascunhos de texto e imagens ficam associados ao chat durante essa tela; anexos não enviados são temporários e não sobrevivem ao encerramento do processo.
- Avisos da Koi: recados de tarefas, acompanhamento, carinho e importantes do Gmail. Não lidos, ler todos e abrir área correspondente.
- Retenção SOMENTE avisos, corte estrito de mais de 60 dias, até 50 por execução. Não apaga chat, memórias, conta nem e-mails.
- Toque de carinho: preferência solicitada 24h, máximo um/24h, descanso respeitado, desativável. Verificação periódica pelo Android a cada 6h; não garante entrega no instante exato.
- Mensagens + IDs de conversa sincronizam. Organização personalizada (títulos/pastas/fixados), chats vazios e avisos permanecem locais nesta prévia.
- Room 5→6 preserva dados antigos no chat “Conversa anterior”; Supabase migration `chat_conversation_identity` aditiva, RLS por dono preservada.
- Avisos existentes no Android antes da atualização não são importados; prévias de mensagens de outros apps permanecem em RAM e não entram nesse arquivo.

## Validação

Backend 283 testes PASS; Android 62 unitários PASS. Build/lint PASS (0 erros/131 avisos). Dez testes físicos PASS: quatro novos de separação/recuperação/retensão/lidos e seis de persistência/migração/recibos/imagens. Prévia2/code21 instalada e usuário confirmou seletor/pastas. Aviso de ausência após 24h e novos e-mails reais ainda não avaliados em espera real.

## Banco

Verificada coluna conversation_id UUID/default/não nula e RLS ativa. Advisors: tabelas Google sem políticas são privadas do servidor por desenho; alerta pré-existente de proteção de senha vazada sem relação com esta alteração. Referência: https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection
