-- Isolated synthetic fixture. Everything, including users, is rolled back.
begin;
insert into auth.users(id) values ('f24fc19d-df0b-4ec9-a3ee-1d240e02b001'),('f24fc19d-df0b-4ec9-a3ee-1d240e02b002');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"f24fc19d-df0b-4ec9-a3ee-1d240e02b001","role":"authenticated"}',true);
insert into public.koi_tasks(user_id,id,slot,title,due_date,due_time,recurrence)
 values('f24fc19d-df0b-4ec9-a3ee-1d240e02b001','f24fc19d-df0b-4ec9-a3ee-1d240e02b003',1,'Missão fictícia','2028-01-31','09:00','monthly');
insert into public.koi_daily_reports(user_id,local_date,source_hash,source_count,items)
 values('f24fc19d-df0b-4ec9-a3ee-1d240e02b001','2000-01-01',repeat('a',64),1,'[{"text":"Somente teste","source_ids":[]}]');
do $$ declare old timestamptz; n integer; begin
 select updated_at into old from public.koi_tasks where id='f24fc19d-df0b-4ec9-a3ee-1d240e02b003';
 if not public.complete_koi_task('f24fc19d-df0b-4ec9-a3ee-1d240e02b003',old) then raise exception 'Completion failed'; end if;
 if public.complete_koi_task('f24fc19d-df0b-4ec9-a3ee-1d240e02b003',old) then raise exception 'Duplicate completion'; end if;
 if not exists(select 1 from public.koi_tasks where id='f24fc19d-df0b-4ec9-a3ee-1d240e02b003' and due_date='2028-02-29' and completed_count=1 and completed_at is null and updated_at>old) then raise exception 'Monthly/leap-year recurrence incorrect'; end if;
 if (select count(*) from public.koi_task_completions)<>1 then raise exception 'Completion log incorrect'; end if;
 update public.koi_tasks set title='Stale overwrite' where id='f24fc19d-df0b-4ec9-a3ee-1d240e02b003' and updated_at=old;
 get diagnostics n=row_count; if n<>0 then raise exception 'Stale edit accepted'; end if;
 begin
  insert into public.koi_tasks(user_id,slot,title,recurrence) values('f24fc19d-df0b-4ec9-a3ee-1d240e02b001',2,'Undated','daily');
  raise exception 'Undated repeat accepted';
 exception when check_violation then null; end;
 begin
  insert into public.koi_tasks(user_id,slot,title) values('f24fc19d-df0b-4ec9-a3ee-1d240e02b001',501,'Quota');
  raise exception 'Quota failed';
 exception when check_violation then null; end;
 begin
  update public.koi_tasks set user_id='f24fc19d-df0b-4ec9-a3ee-1d240e02b002';
  raise exception 'Owner reassigned';
 exception when insufficient_privilege then null; end;
 select updated_at into old from public.koi_daily_reports;
 update public.koi_daily_reports set source_count=2;
 update public.koi_daily_reports set source_count=3 where updated_at=old;
 get diagnostics n=row_count; if n<>0 then raise exception 'Stale report accepted'; end if;
end $$;
select set_config('request.jwt.claims','{"sub":"f24fc19d-df0b-4ec9-a3ee-1d240e02b002","role":"authenticated"}',true);
do $$ declare n integer; begin
 if exists(select 1 from public.koi_tasks) or exists(select 1 from public.koi_task_completions) or exists(select 1 from public.koi_daily_reports) then raise exception 'Other owner visible'; end if;
 update public.koi_tasks set title='Foreign edit';get diagnostics n=row_count;if n<>0 then raise exception 'Foreign edit';end if;
 delete from public.koi_daily_reports;get diagnostics n=row_count;if n<>0 then raise exception 'Foreign report deleted';end if;
 begin
  insert into public.koi_tasks(user_id,slot,title) values('f24fc19d-df0b-4ec9-a3ee-1d240e02b001',2,'Foreign');
  raise exception 'Foreign insert';exception when insufficient_privilege then null;end;
 begin
  insert into public.koi_task_completions(user_id,task_id,title) values('f24fc19d-df0b-4ec9-a3ee-1d240e02b002','f24fc19d-df0b-4ec9-a3ee-1d240e02b003','Cross source');
  raise exception 'Cross owner task reference';exception when foreign_key_violation then null;end;
end $$;
set local role anon;
do $$ begin
 begin perform 1 from public.koi_tasks;raise exception 'Anonymous tasks readable';exception when insufficient_privilege then null;end;
 begin perform 1 from public.koi_daily_reports;raise exception 'Anonymous reports readable';exception when insufficient_privilege then null;end;
end $$;
reset role;
select 'Pack 1 ownership, recurrence, leap year, idempotent completion, stale edits, grants: OK' as result;
rollback;
