-- Pack 1: online tasks and source-backed daily reports. No personal seed data.
create table public.koi_tasks (
 user_id uuid not null references auth.users(id) on delete cascade,
 id uuid not null default gen_random_uuid(),
 slot smallint not null check(slot between 1 and 500),
 title text not null check(length(btrim(title)) between 1 and 160),
 notes text not null default '' check(length(notes)<=2000),
 due_date date,
 due_time time,
 timezone text not null default 'America/Sao_Paulo' check(length(timezone) between 1 and 100),
 recurrence text not null default 'none' check(recurrence in ('none','daily','weekly','monthly')),
 completed_at timestamptz,
 completed_count integer not null default 0 check(completed_count>=0),
 created_at timestamptz not null default clock_timestamp(),
 updated_at timestamptz not null default clock_timestamp(),
 primary key(user_id,id), unique(user_id,slot),
 check(due_time is null or due_date is not null),
 check(recurrence='none' or due_date is not null),
 check(recurrence='none' or completed_at is null)
);
create index koi_tasks_due on public.koi_tasks(user_id,due_date) where completed_at is null;
create table public.koi_task_completions (
 user_id uuid not null,
 id uuid not null default gen_random_uuid(),
 task_id uuid not null,
 title text not null check(length(title) between 1 and 160),
 scheduled_date date,
 completed_at timestamptz not null default clock_timestamp(),
 primary key(user_id,id),
 foreign key(user_id,task_id) references public.koi_tasks(user_id,id) on delete cascade
);
create index koi_task_completions_owner_time on public.koi_task_completions(user_id,completed_at desc);
create table public.koi_daily_reports (
 user_id uuid not null references auth.users(id) on delete cascade,
 local_date date not null,
 source_hash text not null check(source_hash ~ '^[a-f0-9]{64}$'),
 source_count integer not null check(source_count between 1 and 500),
 items jsonb not null check(jsonb_typeof(items)='array' and jsonb_array_length(items) between 1 and 8 and octet_length(items::text)<=16000),
 updated_at timestamptz not null default clock_timestamp(),
 primary key(user_id,local_date)
);
alter table public.koi_tasks enable row level security;
alter table public.koi_task_completions enable row level security;
alter table public.koi_daily_reports enable row level security;
revoke all on public.koi_tasks,public.koi_task_completions,public.koi_daily_reports from public,anon,authenticated;
grant select,delete on public.koi_tasks to authenticated;
grant insert(user_id,id,slot,title,notes,due_date,due_time,timezone,recurrence) on public.koi_tasks to authenticated;
grant update(title,notes,due_date,due_time,timezone,recurrence,completed_at,completed_count) on public.koi_tasks to authenticated;
grant select on public.koi_task_completions to authenticated;
grant insert(user_id,task_id,title,scheduled_date) on public.koi_task_completions to authenticated;
grant select,delete on public.koi_daily_reports to authenticated;
grant insert(user_id,local_date,source_hash,source_count,items) on public.koi_daily_reports to authenticated;
grant update(source_hash,source_count,items) on public.koi_daily_reports to authenticated;
create policy tasks_read on public.koi_tasks for select to authenticated using((select auth.uid())=user_id);
create policy tasks_create on public.koi_tasks for insert to authenticated with check((select auth.uid())=user_id);
create policy tasks_edit on public.koi_tasks for update to authenticated using((select auth.uid())=user_id) with check((select auth.uid())=user_id);
create policy tasks_delete on public.koi_tasks for delete to authenticated using((select auth.uid())=user_id);
create policy completions_read on public.koi_task_completions for select to authenticated using((select auth.uid())=user_id);
create policy completions_create on public.koi_task_completions for insert to authenticated with check((select auth.uid())=user_id);
create policy reports_read on public.koi_daily_reports for select to authenticated using((select auth.uid())=user_id);
create policy reports_create on public.koi_daily_reports for insert to authenticated with check((select auth.uid())=user_id);
create policy reports_edit on public.koi_daily_reports for update to authenticated using((select auth.uid())=user_id) with check((select auth.uid())=user_id);
create policy reports_delete on public.koi_daily_reports for delete to authenticated using((select auth.uid())=user_id);
create trigger koi_task_updated before update on public.koi_tasks for each row execute function public.memory_fact_timestamp();
create trigger koi_report_updated before update on public.koi_daily_reports for each row execute function public.memory_fact_timestamp();

-- Lock and compare the observed version: repeated taps cannot complete twice.
-- Invoker honors grants and RLS; this function never bypasses ownership.
create function public.complete_koi_task(task_id uuid, expected_updated_at timestamptz)
 returns boolean language plpgsql security invoker set search_path='' as $$
declare task public.koi_tasks%rowtype;
begin
 select * into task from public.koi_tasks t where t.user_id=(select auth.uid())
   and t.id=task_id and t.updated_at=expected_updated_at and t.completed_at is null for update;
 if not found then return false; end if;
 insert into public.koi_task_completions(user_id,task_id,title,scheduled_date)
   values(task.user_id,task.id,task.title,task.due_date);
 update public.koi_tasks set completed_count=completed_count+1,
   completed_at=case when recurrence='none' then clock_timestamp() else null end,
   due_date=case recurrence when 'daily' then due_date+1 when 'weekly' then due_date+7
     when 'monthly' then (due_date+interval '1 month')::date else due_date end
   where user_id=task.user_id and id=task.id;
 return true;
end; $$;
revoke execute on function public.complete_koi_task(uuid,timestamptz) from public,anon,authenticated;
grant execute on function public.complete_koi_task(uuid,timestamptz) to authenticated;
