create or replace function public.complete_koi_task(task_id uuid, expected_updated_at timestamptz)
 returns boolean language plpgsql security invoker set search_path='' as $$
declare task public.koi_tasks%rowtype;
begin
 select * into task from public.koi_tasks t where t.user_id=(select auth.uid())
   and t.id=task_id and t.updated_at=expected_updated_at and t.completed_at is null and t.archived_at is null for update;
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
create index koi_receipts_recent on public.koi_action_receipts(user_id,created_at desc);