-- Personal records and reversible memory/record mutations; owner comes only from Auth.
create table public.koi_personal_records (
 user_id uuid not null references auth.users(id) on delete cascade,
 id uuid not null,
 slot smallint not null check(slot between 1 and 200),
 kind text not null check(kind in ('note','list','goal','workout','expense','income')),
 title text not null check(length(btrim(title)) between 1 and 160),
 content text not null default '' check(length(content)<=8000),
 amount_cents bigint check(amount_cents between 0 and 100000000000),
 progress smallint not null default 0 check(progress between 0 and 100),
 happened_on date,
 archived_at timestamptz,
 created_at timestamptz not null default clock_timestamp(),
 updated_at timestamptz not null default clock_timestamp(),
 primary key(user_id,id), unique(user_id,slot),
 check((kind in ('expense','income') and amount_cents is not null) or (kind not in ('expense','income') and amount_cents is null))
);
alter table public.koi_personal_records enable row level security;
revoke all on public.koi_personal_records from public,anon,authenticated;
grant select,insert,update,delete on public.koi_personal_records to authenticated;
create policy records_owner on public.koi_personal_records for all to authenticated
 using((select auth.uid())=user_id) with check((select auth.uid())=user_id);
create trigger personal_record_updated before update on public.koi_personal_records
 for each row execute function public.memory_fact_timestamp();

create table public.koi_tool_receipts (
 user_id uuid not null references auth.users(id) on delete cascade,
 request_id uuid not null, source_hash text not null check(source_hash ~ '^[a-f0-9]{64}$'),
 result jsonb not null check(octet_length(result::text)<=40000),
 undone boolean not null default false,
 created_at timestamptz not null default clock_timestamp(), primary key(user_id,request_id)
);
alter table public.koi_tool_receipts enable row level security;
revoke all on public.koi_tool_receipts from public,anon,authenticated;
grant select,insert,update on public.koi_tool_receipts to authenticated;
create policy tools_owner on public.koi_tool_receipts for all to authenticated
 using((select auth.uid())=user_id) with check((select auth.uid())=user_id);

create function public.apply_koi_personal_action(request_id uuid, source_hash text, target_kind text,
 action text, record_id uuid, expected_updated_at timestamptz, fields jsonb)
 returns jsonb language plpgsql security invoker set search_path='' as $$
declare owner uuid:=auth.uid(); receipt public.koi_tool_receipts%rowtype;
 old jsonb; current_row jsonb; result jsonb; position smallint;
begin
 if owner is null then raise exception 'Authentication required' using errcode='42501'; end if;
 perform pg_advisory_xact_lock(hashtextextended(owner::text,0));
 select * into receipt from public.koi_tool_receipts r where r.user_id=owner and r.request_id=apply_koi_personal_action.request_id;
 if found then
  if receipt.source_hash<>apply_koi_personal_action.source_hash then raise exception 'Request changed' using errcode='23505'; end if;
  return receipt.result || jsonb_build_object('undone',receipt.undone);
 end if;
 if target_kind not in ('memory','record') or action not in ('create','update','archive','restore','delete')
  or jsonb_typeof(fields)<>'object' then raise exception 'Invalid action'; end if;
 if target_kind='memory' then
  if action not in ('create','update','delete') or exists(select 1 from jsonb_object_keys(fields) k where k not in ('content','category')) then raise exception 'Invalid memory fields'; end if;
  if action='create' then
   select n::smallint into position from generate_series(1,20) n where not exists(select 1 from public.memory_facts m where m.user_id=owner and m.slot=n) order by n limit 1;
   if position is null then raise exception 'Memory capacity reached' using errcode='23505'; end if;
   insert into public.memory_facts(user_id,id,slot,content,category)
    values(owner,record_id,position,fields->>'content',coalesce(fields->>'category','note')) returning to_jsonb(memory_facts.*) into current_row;
  else
   select to_jsonb(m.*) into old from public.memory_facts m where m.user_id=owner and m.id=record_id and m.updated_at=expected_updated_at for update;
   if not found then raise exception 'Memory changed' using errcode='23505'; end if;
   if action='delete' then
    delete from public.memory_facts m where m.user_id=owner and m.id=record_id;
    current_row:=old;
   else
    update public.memory_facts m set content=case when fields?'content' then fields->>'content' else m.content end,
     category=case when fields?'category' then fields->>'category' else m.category end
     where m.user_id=owner and m.id=record_id returning to_jsonb(m.*) into current_row;
   end if;
  end if;
 else
  if action='delete' or exists(select 1 from jsonb_object_keys(fields) k where k not in ('kind','title','content','amount_cents','progress','happened_on')) then raise exception 'Invalid record fields'; end if;
  if action='create' then
   select n::smallint into position from generate_series(1,200) n where not exists(select 1 from public.koi_personal_records r where r.user_id=owner and r.slot=n) order by n limit 1;
   if position is null then raise exception 'Record capacity reached' using errcode='23505'; end if;
   insert into public.koi_personal_records(user_id,id,slot,kind,title,content,amount_cents,progress,happened_on)
    values(owner,record_id,position,fields->>'kind',fields->>'title',coalesce(fields->>'content',''),
     (fields->>'amount_cents')::bigint,coalesce((fields->>'progress')::smallint,0),(fields->>'happened_on')::date)
    returning to_jsonb(koi_personal_records.*) into current_row;
  else
   select to_jsonb(r.*) into old from public.koi_personal_records r where r.user_id=owner and r.id=record_id and r.updated_at=expected_updated_at for update;
   if not found then raise exception 'Record changed' using errcode='23505'; end if;
   update public.koi_personal_records r set
    title=case when fields?'title' then fields->>'title' else r.title end,
    content=case when fields?'content' then fields->>'content' else r.content end,
    amount_cents=case when fields?'amount_cents' then (fields->>'amount_cents')::bigint else r.amount_cents end,
    progress=case when fields?'progress' then (fields->>'progress')::smallint else r.progress end,
    happened_on=case when fields?'happened_on' then (fields->>'happened_on')::date else r.happened_on end,
    archived_at=case action when 'archive' then clock_timestamp() when 'restore' then null else r.archived_at end
    where r.user_id=owner and r.id=record_id returning to_jsonb(r.*) into current_row;
  end if;
 end if;
 result:=jsonb_build_object('request_id',apply_koi_personal_action.request_id,'target_kind',target_kind,
  'action',action,'before',old,'record',current_row);
 insert into public.koi_tool_receipts(user_id,request_id,source_hash,result) values(owner,apply_koi_personal_action.request_id,apply_koi_personal_action.source_hash,result);
 return result || jsonb_build_object('undone',false);
end; $$;

create function public.undo_koi_personal_action(request_id uuid) returns jsonb
 language plpgsql security invoker set search_path='' as $$
declare owner uuid:=auth.uid(); receipt public.koi_tool_receipts%rowtype; old jsonb; current_row jsonb; rid uuid;
begin
 if owner is null then raise exception 'Authentication required' using errcode='42501'; end if;
 perform pg_advisory_xact_lock(hashtextextended(owner::text,0));
 select * into receipt from public.koi_tool_receipts r where r.user_id=owner and r.request_id=undo_koi_personal_action.request_id for update;
 if not found then raise exception 'Action unavailable' using errcode='23505'; end if;
 if receipt.undone then return receipt.result || jsonb_build_object('undone',true); end if;
 old:=receipt.result->'before'; rid:=(receipt.result->'record'->>'id')::uuid;
 if receipt.result->>'target_kind'='memory' then
  select to_jsonb(m.*) into current_row from public.memory_facts m where m.user_id=owner and m.id=rid for update;
  if receipt.result->>'action'='delete' then
   if current_row is not null then raise exception 'Memory changed' using errcode='23505'; end if;
   insert into public.memory_facts(user_id,id,slot,content,category,source_message_id)
    values(owner,rid,(old->>'slot')::smallint,old->>'content',old->>'category',(old->>'source_message_id')::uuid);
  else
   if current_row is null or current_row->>'updated_at'<>receipt.result->'record'->>'updated_at' then raise exception 'Memory changed' using errcode='23505'; end if;
   if receipt.result->>'action'='create' then delete from public.memory_facts m where m.user_id=owner and m.id=rid;
   else update public.memory_facts m set content=old->>'content',category=old->>'category' where m.user_id=owner and m.id=rid; end if;
  end if;
 else
  select to_jsonb(r.*) into current_row from public.koi_personal_records r where r.user_id=owner and r.id=rid for update;
  if current_row is null or current_row->>'updated_at'<>receipt.result->'record'->>'updated_at' then raise exception 'Record changed' using errcode='23505'; end if;
  if receipt.result->>'action'='create' then delete from public.koi_personal_records r where r.user_id=owner and r.id=rid;
  else update public.koi_personal_records r set title=old->>'title',content=old->>'content',amount_cents=(old->>'amount_cents')::bigint,
   progress=(old->>'progress')::smallint,happened_on=(old->>'happened_on')::date,archived_at=(old->>'archived_at')::timestamptz
   where r.user_id=owner and r.id=rid; end if;
 end if;
 update public.koi_tool_receipts r set undone=true where r.user_id=owner and r.request_id=undo_koi_personal_action.request_id;
 return receipt.result || jsonb_build_object('undone',true);
end; $$;
revoke execute on function public.apply_koi_personal_action(uuid,text,text,text,uuid,timestamptz,jsonb),public.undo_koi_personal_action(uuid) from public,anon;
grant execute on function public.apply_koi_personal_action(uuid,text,text,text,uuid,timestamptz,jsonb),public.undo_koi_personal_action(uuid) to authenticated;
