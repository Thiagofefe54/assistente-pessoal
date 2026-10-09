create table public.koi_google_actions (
 user_id uuid not null references auth.users(id) on delete cascade,
 request_id uuid not null,
 message_hash text not null check(length(message_hash)=64),
 phase text not null check(phase in ('started','done')),
 response text check(length(response)<=32768),
 created_at timestamptz not null default now(),
 primary key(user_id,request_id),
 check(phase!='done' or response is not null)
);
alter table public.koi_google_actions enable row level security;
revoke all on public.koi_google_actions from public, anon, authenticated;
grant select, insert, update, delete on public.koi_google_actions to service_role;
