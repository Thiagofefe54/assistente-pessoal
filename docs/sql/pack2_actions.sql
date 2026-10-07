-- Owner-scoped, reversible chat actions. No service role or personal seed data.
alter table public.koi_tasks add column archived_at timestamptz;
grant insert(archived_at) on public.koi_tasks to authenticated;
grant update(archived_at) on public.koi_tasks to authenticated;
create table public.koi_action_receipts (
 user_id uuid not null references auth.users(id) on delete cascade,
 request_id uuid not null,
 source_hash text not null check(source_hash ~ '^[a-f0-9]{64}$'),
 result jsonb not null check(octet_length(result::text)<=20000),
 undone boolean not null default false,
 created_at timestamptz not null default clock_timestamp(),
 primary key(user_id,request_id)
);
alter table public.koi_action_receipts enable row level security;
revoke all on public.koi_action_receipts from public,anon,authenticated;
grant select,insert,update on public.koi_action_receipts to authenticated;
create policy action_receipts_owner on public.koi_action_receipts for all to authenticated
 using((select auth.uid())=user_id) with check((select auth.uid())=user_id);
grant delete on public.koi_task_completions to authenticated;
create policy completions_undo on public.koi_task_completions for delete to authenticated using((select auth.uid())=user_id);

create function public.apply_koi_action(request_id uuid, source_hash text, action text,
 task_id uuid, expected_updated_at timestamptz, fields jsonb)
 returns jsonb language plpgsql security invoker set search_path='' as $$
declare owner uuid:=auth.uid(); old public.koi_tasks%rowtype; current_task public.koi_tasks%rowtype;
 receipt public.koi_action_receipts%rowtype; completion uuid; result jsonb; position smallint;
begin
 if owner is null then raise exception 'Authentication required' using errcode='42501'; end if;
 perform pg_advisory_xact_lock(hashtextextended(owner::text,0));
 select * into receipt from public.koi_action_receipts r where r.user_id=owner and r.request_id=apply_koi_action.request_id;
 if found then
  if receipt.source_hash<>apply_koi_action.source_hash then raise exception 'Request identity changed' using errcode='23505'; end if;
  return receipt.result || jsonb_build_object('undone',receipt.undone);
 end if;
 if action not in ('create','update','complete','reopen','archive') or jsonb_typeof(fields)<>'object' then raise exception 'Invalid action'; end if;
 if exists(select 1 from jsonb_object_keys(fields) k where k not in ('title','notes','due_date','due_time','timezone','recurrence')) then raise exception 'Invalid fields'; end if;
 if action='create' then
  select n::smallint into position from generate_series(1,500) n where not exists(select 1 from public.koi_tasks t where t.user_id=owner and t.slot=n) order by n limit 1;
  if position is null then raise exception 'Task capacity reached' using errcode='23505'; end if;
  insert into public.koi_tasks(user_id,id,slot,title,notes,due_date,due_time,timezone,recurrence)
   values(owner,apply_koi_action.task_id,position,fields->>'title',coalesce(fields->>'notes',''),
    (fields->>'due_date')::date,(fields->>'due_time')::time,fields->>'timezone',coalesce(fields->>'recurrence','none')) returning * into current_task;
 else
  select * into old from public.koi_tasks t where t.user_id=owner and t.id=apply_koi_action.task_id and t.updated_at=expected_updated_at for update;
  if not found then raise exception 'Task changed' using errcode='23505'; end if;
  if action='complete' then
   if old.completed_at is not null or old.archived_at is not null then raise exception 'Task is not pending' using errcode='23505'; end if;
   insert into public.koi_task_completions(user_id,task_id,title,scheduled_date) values(owner,old.id,old.title,old.due_date) returning id into completion;
   update public.koi_tasks t set completed_count=t.completed_count+1,
    completed_at=case when old.recurrence='none' then clock_timestamp() else null end,
    due_date=case old.recurrence when 'daily' then old.due_date+1 when 'weekly' then old.due_date+7
     when 'monthly' then (old.due_date+interval '1 month')::date else old.due_date end
    where t.user_id=owner and t.id=old.id returning * into current_task;
  elsif action='reopen' then
   update public.koi_tasks t set completed_at=null,archived_at=null where t.user_id=owner and t.id=old.id returning * into current_task;
  elsif action='archive' then
   update public.koi_tasks t set archived_at=clock_timestamp() where t.user_id=owner and t.id=old.id returning * into current_task;
  else
   update public.koi_tasks t set title=case when fields?'title' then fields->>'title' else t.title end,
    notes=case when fields?'notes' then fields->>'notes' else t.notes end,
    due_date=case when fields?'due_date' then (fields->>'due_date')::date else t.due_date end,
    due_time=case when fields?'due_time' then (fields->>'due_time')::time else t.due_time end,
    recurrence=case when fields?'recurrence' then fields->>'recurrence' else t.recurrence end,
    completed_at=case when fields?'recurrence' and fields->>'recurrence'<>'none' then null else t.completed_at end
    where t.user_id=owner and t.id=old.id returning * into current_task;
  end if;
 end if;
 result:=jsonb_build_object('request_id',apply_koi_action.request_id,'action',action,'before',case when action='create' then null else to_jsonb(old) end,
  'task',to_jsonb(current_task),'completion_id',completion);
 insert into public.koi_action_receipts(user_id,request_id,source_hash,result) values(owner,apply_koi_action.request_id,apply_koi_action.source_hash,result);
 return result || jsonb_build_object('undone',false);
end; $$;

create function public.undo_koi_action(request_id uuid) returns jsonb
 language plpgsql security invoker set search_path='' as $$
declare owner uuid:=auth.uid(); receipt public.koi_action_receipts%rowtype;
 current_task public.koi_tasks%rowtype; old jsonb; result jsonb;
begin
 if owner is null then raise exception 'Authentication required' using errcode='42501'; end if;
 perform pg_advisory_xact_lock(hashtextextended(owner::text,0));
 select * into receipt from public.koi_action_receipts r where r.user_id=owner and r.request_id=undo_koi_action.request_id for update;
 if not found then raise exception 'Action unavailable' using errcode='23505'; end if;
 if receipt.undone then return receipt.result || jsonb_build_object('undone',true); end if;
 select * into current_task from public.koi_tasks t where t.user_id=owner and t.id=(receipt.result->'task'->>'id')::uuid
  and t.updated_at=(receipt.result->'task'->>'updated_at')::timestamptz for update;
 if not found then raise exception 'Task changed since action' using errcode='23505'; end if;
 old:=receipt.result->'before';
 if receipt.result->>'action'='create' then
  delete from public.koi_tasks t where t.user_id=owner and t.id=current_task.id;
 else
  update public.koi_tasks t set title=old->>'title',notes=old->>'notes',due_date=(old->>'due_date')::date,
   due_time=(old->>'due_time')::time,recurrence=old->>'recurrence',completed_at=(old->>'completed_at')::timestamptz,
   completed_count=(old->>'completed_count')::integer,archived_at=(old->>'archived_at')::timestamptz
   where t.user_id=owner and t.id=current_task.id;
  if receipt.result->>'completion_id' is not null then
   delete from public.koi_task_completions c where c.user_id=owner and c.id=(receipt.result->>'completion_id')::uuid;
  end if;
 end if;
 update public.koi_action_receipts r set undone=true where r.user_id=owner and r.request_id=undo_koi_action.request_id;
 return receipt.result || jsonb_build_object('undone',true);
end; $$;
revoke execute on function public.apply_koi_action(uuid,text,text,uuid,timestamptz,jsonb),public.undo_koi_action(uuid) from public,anon;
grant execute on function public.apply_koi_action(uuid,text,text,uuid,timestamptz,jsonb),public.undo_koi_action(uuid) to authenticated;
