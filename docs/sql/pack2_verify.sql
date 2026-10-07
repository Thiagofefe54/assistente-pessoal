begin;
insert into auth.users(id) values ('f24fc19d-df0b-4ec9-a3ee-1d240e02b201'),('f24fc19d-df0b-4ec9-a3ee-1d240e02b202');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"f24fc19d-df0b-4ec9-a3ee-1d240e02b201","role":"authenticated"}',true);
do $$ declare result jsonb; version timestamptz; before_count integer; begin
 result:=public.apply_koi_action('f24fc19d-df0b-4ec9-a3ee-1d240e02b210',repeat('a',64),'create','f24fc19d-df0b-4ec9-a3ee-1d240e02b203',null,
  '{"title":"Teste fictício","due_date":"2028-01-31","due_time":"09:00","timezone":"UTC","recurrence":"monthly"}');
 result:=public.apply_koi_action('f24fc19d-df0b-4ec9-a3ee-1d240e02b210',repeat('a',64),'create','f24fc19d-df0b-4ec9-a3ee-1d240e02b203',null,'{}');
 if (select count(*) from public.koi_tasks)<>1 then raise exception 'Duplicate creation'; end if;
 select updated_at into version from public.koi_tasks;
 result:=public.apply_koi_action('f24fc19d-df0b-4ec9-a3ee-1d240e02b211',repeat('b',64),'complete','f24fc19d-df0b-4ec9-a3ee-1d240e02b203',version,'{}');
 result:=public.apply_koi_action('f24fc19d-df0b-4ec9-a3ee-1d240e02b211',repeat('b',64),'complete','f24fc19d-df0b-4ec9-a3ee-1d240e02b203',version,'{}');
 if (select count(*) from public.koi_task_completions)<>1 then raise exception 'Duplicate completion'; end if;
 if not exists(select 1 from public.koi_tasks where due_date='2028-02-29' and completed_count=1) then raise exception 'Repeat advance';end if;
 result:=public.undo_koi_action('f24fc19d-df0b-4ec9-a3ee-1d240e02b211');
 result:=public.undo_koi_action('f24fc19d-df0b-4ec9-a3ee-1d240e02b211');
 if exists(select 1 from public.koi_task_completions) or not exists(select 1 from public.koi_tasks where due_date='2028-01-31' and completed_count=0) then raise exception 'Undo failed'; end if;
 begin
  perform public.undo_koi_action('f24fc19d-df0b-4ec9-a3ee-1d240e02b210');raise exception 'Stale undo accepted';
 exception when unique_violation then null;end;
 select updated_at into version from public.koi_tasks;
 result:=public.apply_koi_action('f24fc19d-df0b-4ec9-a3ee-1d240e02b212',repeat('c',64),'archive','f24fc19d-df0b-4ec9-a3ee-1d240e02b203',version,'{}');
 if not exists(select 1 from public.koi_tasks where archived_at is not null) then raise exception 'Archive failed';end if;
 select updated_at into version from public.koi_tasks;
 if public.complete_koi_task('f24fc19d-df0b-4ec9-a3ee-1d240e02b203',version) then raise exception 'Archived completion accepted';end if;
 result:=public.undo_koi_action('f24fc19d-df0b-4ec9-a3ee-1d240e02b212');
 if exists(select 1 from public.koi_tasks where archived_at is not null) then raise exception 'Archive undo failed';end if;
 select updated_at into version from public.koi_tasks;
 result:=public.apply_koi_action('f24fc19d-df0b-4ec9-a3ee-1d240e02b213',repeat('d',64),'archive','f24fc19d-df0b-4ec9-a3ee-1d240e02b203',version,'{}');
 result:=public.undo_koi_action_request('f24fc19d-df0b-4ec9-a3ee-1d240e02b214',repeat('e',64),'f24fc19d-df0b-4ec9-a3ee-1d240e02b213');
 result:=public.undo_koi_action_request('f24fc19d-df0b-4ec9-a3ee-1d240e02b214',repeat('e',64),'f24fc19d-df0b-4ec9-a3ee-1d240e02b212');
 if result->>'target_request_id'<>'f24fc19d-df0b-4ec9-a3ee-1d240e02b213' then raise exception 'Undo retry changed target';end if;
end $$;
insert into public.chat_messages(user_id,id,role,content,occurred_at,timezone)
 values('f24fc19d-df0b-4ec9-a3ee-1d240e02b201','f24fc19d-df0b-4ec9-a3ee-1d240e02b220','user','Somente fixture','2026-09-15T12:00:00Z','UTC');
do $$ declare entries jsonb;begin
 entries:=public.koi_diary_inventory('2026-09-01','2026-10-01');
 if jsonb_array_length(entries)<>1 or entries->0->>'message_count'<>'1' then raise exception 'Inventory incorrect';end if;
end $$;
insert into public.koi_period_reports(user_id,kind,start_date,end_date,source_hash,source_count,items,coverage)
 values('f24fc19d-df0b-4ec9-a3ee-1d240e02b201','month','2026-09-01','2026-10-01',repeat('a',64),1,'[{"text":"Somente fixture","source_keys":["day:2026-09-15"]}]','{}');
select set_config('request.jwt.claims','{"sub":"f24fc19d-df0b-4ec9-a3ee-1d240e02b202","role":"authenticated"}',true);
do $$ begin
 if exists(select 1 from public.koi_action_receipts) or exists(select 1 from public.koi_period_reports) then raise exception 'Foreign records visible';end if;
 if public.koi_diary_inventory('2026-09-01','2026-10-01')<>'[]'::jsonb then raise exception 'Foreign diary visible';end if;
 begin perform public.undo_koi_action('f24fc19d-df0b-4ec9-a3ee-1d240e02b212');raise exception 'Foreign undo allowed';exception when unique_violation then null;end;
end $$;
set local role anon;
do $$ begin
 begin perform public.koi_diary_inventory('2026-09-01','2026-10-01');raise exception 'Anonymous inventory allowed';exception when insufficient_privilege then null;end;
 begin perform 1 from public.koi_action_receipts;raise exception 'Anonymous receipts allowed';exception when insufficient_privilege then null;end;
end $$;
reset role;
select 'Pack 2 receipts, repeat, undo, stale guard, ownership, inventory, reports: OK' as result;
rollback;
