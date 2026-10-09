-- Server-only encrypted credentials. No direct Android/Data API client access.
create table public.koi_google_connections (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    subject text not null check (length(subject) between 1 and 255),
    email text not null check (length(email) between 1 and 320),
    scopes text[] not null,
    secret text not null check (length(secret) <= 32768),
    updated_at timestamptz not null default now(),
    unique(user_id, subject)
);
create table public.koi_google_pending (
    state_hash text primary key check (state_hash ~ '^[0-9a-f]{64}$'),
    user_id uuid not null references auth.users(id) on delete cascade,
    phase text not null check (phase in ('begin','callback')),
    secret text not null check (length(secret) <= 32768),
    expires_at timestamptz not null
);
alter table public.koi_google_connections enable row level security;
alter table public.koi_google_pending enable row level security;
revoke all on public.koi_google_connections, public.koi_google_pending from public, anon, authenticated;
grant select, insert, update, delete on public.koi_google_connections, public.koi_google_pending to service_role;
create index koi_google_pending_expiry on public.koi_google_pending(expires_at);

-- Serialize account count per owner; reauthorization of the same subject is allowed.
create function public.koi_google_limit_accounts() returns trigger
language plpgsql security invoker set search_path = '' as $$
begin
  perform pg_advisory_xact_lock(hashtextextended(new.user_id::text, 731));
  if not exists (select 1 from public.koi_google_connections
                 where user_id = new.user_id and subject = new.subject)
     and (select count(*) from public.koi_google_connections where user_id = new.user_id) >= 3 then
    raise exception 'Google account limit reached';
  end if;
  return new;
end $$;
revoke all on function public.koi_google_limit_accounts() from public, anon, authenticated;
create trigger koi_google_limit_accounts before insert on public.koi_google_connections
for each row execute function public.koi_google_limit_accounts();
