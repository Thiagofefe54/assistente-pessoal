-- Confirmed memories only. No inferred facts or personal seed data.
create table public.memory_facts (
  user_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null default gen_random_uuid(),
  slot smallint not null check (slot between 1 and 20),
  content text not null check (length(btrim(content)) between 1 and 500),
  category text not null check (category in ('preference','goal','routine','note')),
  source_message_id uuid,
  created_at timestamptz not null default clock_timestamp(),
  updated_at timestamptz not null default clock_timestamp(),
  primary key (user_id, id),
  unique (user_id, slot),
  foreign key (user_id, source_message_id) references public.chat_messages(user_id, id)
);
create index memory_facts_source on public.memory_facts(user_id, source_message_id);
alter table public.memory_facts enable row level security;
revoke all on public.memory_facts from public, anon, authenticated;
grant select, delete on public.memory_facts to authenticated;
grant insert (user_id,id,slot,content,category,source_message_id) on public.memory_facts to authenticated;
grant update (content,category) on public.memory_facts to authenticated;
create policy memory_read on public.memory_facts for select to authenticated
  using ((select auth.uid()) = user_id);
create policy memory_create on public.memory_facts for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy memory_edit on public.memory_facts for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy memory_delete on public.memory_facts for delete to authenticated
  using ((select auth.uid()) = user_id);
create function public.memory_fact_timestamp() returns trigger language plpgsql
  security invoker set search_path = '' as $$
begin
  new.updated_at := clock_timestamp();
  return new;
end;
$$;
revoke execute on function public.memory_fact_timestamp() from public, anon, authenticated;
create trigger memory_fact_updated before update on public.memory_facts
  for each row execute function public.memory_fact_timestamp();
