-- Bootstrap da estrutura do histórico da Koiwai.
-- Não inclui conversas pessoais nem credenciais.
begin;

create table public.chat_messages (
    user_id uuid not null references auth.users(id) on delete cascade,
    id uuid not null,
    server_sequence bigint generated always as identity unique,
    role text not null check (role in ('user', 'assistant')),
    content text not null check (length(content) between 1 and 20000),
    occurred_at timestamptz not null,
    received_at timestamptz not null default now(),
    timezone text not null default 'America/Sao_Paulo',
    local_date date generated always as ((occurred_at at time zone timezone)::date) stored,
    reply_to uuid,
    primary key (user_id, id),
    foreign key (user_id, reply_to) references public.chat_messages(user_id, id),
    constraint chat_messages_reply_role check (
        (role = 'user' and reply_to is null) or
        (role = 'assistant' and reply_to is not null)
    ),
    unique (user_id, reply_to)
);

create index chat_messages_user_sequence_idx
    on public.chat_messages (user_id, server_sequence);
create index chat_messages_user_date_idx
    on public.chat_messages (user_id, local_date, server_sequence);

alter table public.chat_messages enable row level security;

revoke all on public.chat_messages from anon, authenticated;
grant select, insert on public.chat_messages to authenticated;
grant usage on sequence public.chat_messages_server_sequence_seq to authenticated;

create policy chat_messages_select_owner on public.chat_messages
    for select to authenticated
    using ((select auth.uid()) = user_id);
create policy chat_messages_insert_owner on public.chat_messages
    for insert to authenticated
    with check ((select auth.uid()) = user_id);

comment on table public.chat_messages is
    'Histórico por usuário. UUIDs permitem reenvio sem duplicação. Sem update/delete nesta etapa.';

commit;

select tablename, rowsecurity
from pg_tables where schemaname = 'public' and tablename = 'chat_messages';
