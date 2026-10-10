-- Referência da migration remota chat_conversation_identity, aplicada pelo MCP.
-- Alteração aditiva; RLS/grants por usuário permanecem os existentes.
alter table public.chat_messages add column if not exists conversation_id uuid not null default '00000000-0000-0000-0000-000000000000'::uuid;
create index if not exists chat_messages_owner_conversation on public.chat_messages(user_id,conversation_id,server_sequence);
