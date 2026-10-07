alter table public.koi_daily_reports add column source_sequence bigint not null default 0;
grant insert(source_sequence),update(source_sequence) on public.koi_daily_reports to authenticated;
create table public.koi_period_reports (
 user_id uuid not null references auth.users(id) on delete cascade,
 kind text not null check(kind in ('week','month','halfyear','year')),
 start_date date not null,
 end_date date not null check(end_date>start_date and end_date-start_date<=366),
 source_hash text not null check(source_hash ~ '^[a-f0-9]{64}$'),
 source_count integer not null check(source_count>0),
 items jsonb not null check(jsonb_typeof(items)='array' and jsonb_array_length(items) between 1 and 16 and octet_length(items::text)<=24000),
 coverage jsonb not null check(octet_length(coverage::text)<=4000),
 updated_at timestamptz not null default clock_timestamp(),
 primary key(user_id,kind,start_date)
);
alter table public.koi_period_reports enable row level security;
revoke all on public.koi_period_reports from public,anon,authenticated;
grant select,delete on public.koi_period_reports to authenticated;
grant insert(user_id,kind,start_date,end_date,source_hash,source_count,items,coverage) on public.koi_period_reports to authenticated;
grant update(end_date,source_hash,source_count,items,coverage) on public.koi_period_reports to authenticated;
create policy period_reports_owner on public.koi_period_reports for all to authenticated
 using((select auth.uid())=user_id) with check((select auth.uid())=user_id);
create trigger koi_period_report_updated before update on public.koi_period_reports for each row execute function public.memory_fact_timestamp();
create function public.koi_diary_inventory(start_date date,end_date date) returns jsonb
 language plpgsql stable security invoker set search_path='' as $$
begin
 if end_date<=start_date or end_date-start_date>366 then raise exception 'Invalid period'; end if;
 return coalesce((select jsonb_agg(to_jsonb(d) order by d.local_date) from (
  select m.local_date,count(*)::integer as message_count,max(m.server_sequence) as last_sequence
  from public.chat_messages m where m.user_id=(select auth.uid()) and m.role='user'
   and m.local_date>=start_date and m.local_date<end_date group by m.local_date
 ) d),'[]'::jsonb);
end; $$;
revoke execute on function public.koi_diary_inventory(date,date) from public,anon;
grant execute on function public.koi_diary_inventory(date,date) to authenticated;
