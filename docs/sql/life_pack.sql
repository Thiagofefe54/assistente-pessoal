-- Additive life pack; existing rows and IDs remain intact.
alter table public.koi_personal_records add column details jsonb not null default '{}'::jsonb;
alter table public.koi_personal_records drop constraint koi_personal_records_kind_check;
alter table public.koi_personal_records add constraint koi_personal_records_kind_check check(kind in ('note','list','goal','workout','expense','income','bill','budget','diary'));
alter table public.koi_personal_records drop constraint koi_personal_records_check;
alter table public.koi_personal_records add constraint koi_personal_records_check check((kind in ('expense','income','bill','budget') and amount_cents is not null) or (kind not in ('expense','income','bill','budget') and amount_cents is null));
alter table public.koi_personal_records drop constraint koi_personal_records_slot_check;
alter table public.koi_personal_records add constraint koi_personal_records_slot_check check(slot between 1 and 2000);
alter table public.koi_personal_records add constraint life_date_required check(kind not in ('bill','budget','diary') or happened_on is not null);
alter table public.koi_personal_records add constraint budget_month check(kind<>'budget' or extract(day from happened_on)=1);
alter table public.koi_personal_records add constraint details_object check(jsonb_typeof(details)='object' and octet_length(details::text)<=2000);
create function public.validate_koi_life_details() returns trigger language plpgsql security invoker set search_path='' as $$
declare started timestamptz; ended timestamptz;
begin
 if new.kind='bill' then
  if exists(select 1 from jsonb_object_keys(new.details) k where k<>'repeat') or coalesce(new.details->>'repeat','none') not in ('none','weekly','monthly') then raise exception 'Invalid recurrence'; end if;
 elsif new.kind='diary' then
  if exists(select 1 from jsonb_object_keys(new.details) k where k not in ('category','started_at','ended_at')) or coalesce(new.details->>'category','other') not in ('work','gym','sleep','home','study','other') then raise exception 'Invalid diary'; end if;
  if new.details?'started_at' then
   if not (new.details->>'started_at' ~ '(Z|[+-][0-9]{2}:[0-9]{2})$') then raise exception 'Timezone required'; end if;
   started:=(new.details->>'started_at')::timestamptz;
   if left(new.details->>'started_at',10)::date<>new.happened_on then raise exception 'Event date mismatch'; end if;
  end if;
  if new.details?'ended_at' then
   if not (new.details->>'ended_at' ~ '(Z|[+-][0-9]{2}:[0-9]{2})$') then raise exception 'Timezone required'; end if;
   ended:=(new.details->>'ended_at')::timestamptz;
   if started is null or ended<started or ended-started>interval '48 hours' then raise exception 'Invalid interval'; end if;
  end if;
 elsif new.details<>'{}'::jsonb then raise exception 'Unexpected details'; end if;
 return new;
end; $$;
revoke execute on function public.validate_koi_life_details() from public,anon;
create trigger life_details before insert or update on public.koi_personal_records for each row execute function public.validate_koi_life_details();
create index life_owner_kind_date on public.koi_personal_records(user_id,kind,happened_on) where archived_at is null;

create table public.koi_bill_payments (
 user_id uuid not null references auth.users(id) on delete cascade,
 bill_id uuid not null, occurrence_on date not null, expense_id uuid not null,
 primary key(user_id,bill_id,occurrence_on), unique(user_id,expense_id),
 foreign key(user_id,bill_id) references public.koi_personal_records(user_id,id) on delete cascade,
 foreign key(user_id,expense_id) references public.koi_personal_records(user_id,id) on delete cascade
);
alter table public.koi_bill_payments enable row level security;
revoke all on public.koi_bill_payments from public,anon,authenticated;
grant select,insert,update,delete on public.koi_bill_payments to authenticated;
create policy payments_owner on public.koi_bill_payments for all to authenticated using((select auth.uid())=user_id) with check((select auth.uid())=user_id);
create or replace function public.apply_koi_personal_action(request_id uuid, source_hash text, target_kind text,
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
  if action='delete' or exists(select 1 from jsonb_object_keys(fields) k where k not in ('kind','title','content','amount_cents','progress','happened_on','details')) then raise exception 'Invalid record fields'; end if;
  if action='create' then
   select n::smallint into position from generate_series(1,2000) n where not exists(select 1 from public.koi_personal_records r where r.user_id=owner and r.slot=n) order by n limit 1;
   if position is null then raise exception 'Record capacity reached' using errcode='23505'; end if;
   insert into public.koi_personal_records(user_id,id,slot,kind,title,content,amount_cents,progress,happened_on,details)
    values(owner,record_id,position,fields->>'kind',fields->>'title',coalesce(fields->>'content',''),
     (fields->>'amount_cents')::bigint,coalesce((fields->>'progress')::smallint,0),(fields->>'happened_on')::date,coalesce(fields->'details','{}'::jsonb))
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
    details=case when fields?'details' then fields->'details' else r.details end,
    archived_at=case action when 'archive' then clock_timestamp() when 'restore' then null else r.archived_at end
    where r.user_id=owner and r.id=record_id returning to_jsonb(r.*) into current_row;
  end if;
 end if;
 result:=jsonb_build_object('request_id',apply_koi_personal_action.request_id,'target_kind',target_kind,
  'action',action,'before',old,'record',current_row);
 insert into public.koi_tool_receipts(user_id,request_id,source_hash,result) values(owner,apply_koi_personal_action.request_id,apply_koi_personal_action.source_hash,result);
 return result || jsonb_build_object('undone',false);
end; $$;

create or replace function public.undo_koi_personal_action(request_id uuid) returns jsonb
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
   details=coalesce(old->'details','{}'::jsonb),progress=(old->>'progress')::smallint,happened_on=(old->>'happened_on')::date,archived_at=(old->>'archived_at')::timestamptz
   where r.user_id=owner and r.id=rid; end if;
 end if;
 update public.koi_tool_receipts r set undone=true where r.user_id=owner and r.request_id=undo_koi_personal_action.request_id;
 return receipt.result || jsonb_build_object('undone',true);
end; $$;
revoke execute on function public.apply_koi_personal_action(uuid,text,text,text,uuid,timestamptz,jsonb),public.undo_koi_personal_action(uuid) from public,anon;
grant execute on function public.apply_koi_personal_action(uuid,text,text,text,uuid,timestamptz,jsonb),public.undo_koi_personal_action(uuid) to authenticated;

-- Record a payment and its expense in one transaction; undoing creation cascades payment.
create function public.pay_koi_bill(request_id uuid,source_hash text,bill_id uuid,record_id uuid,expected_updated_at timestamptz,occurrence_on date,paid_on date)
 returns jsonb language plpgsql security invoker set search_path='' as $$
declare owner uuid:=auth.uid(); bill public.koi_personal_records%rowtype; prior public.koi_tool_receipts%rowtype; delta integer; valid_date date; result jsonb;
begin
 if owner is null then raise exception 'Authentication required' using errcode='42501'; end if;
 perform pg_advisory_xact_lock(hashtextextended(owner::text,0));
 select * into prior from public.koi_tool_receipts r where r.user_id=owner and r.request_id=pay_koi_bill.request_id;
 if found then
  if prior.source_hash<>pay_koi_bill.source_hash then raise exception 'Request changed' using errcode='23505'; end if;
  return prior.result || jsonb_build_object('undone',prior.undone);
 end if;
 select * into bill from public.koi_personal_records r where r.user_id=owner and r.id=pay_koi_bill.bill_id and r.kind='bill' and r.archived_at is null and r.updated_at=expected_updated_at for update;
 if not found then raise exception 'Bill changed' using errcode='23505'; end if;
 if occurrence_on is null or paid_on is null or occurrence_on<bill.happened_on then raise exception 'Invalid date'; end if;
 if coalesce(bill.details->>'repeat','none')='monthly' then
  delta:=(extract(year from occurrence_on)::int-extract(year from bill.happened_on)::int)*12+extract(month from occurrence_on)::int-extract(month from bill.happened_on)::int;
  valid_date:=(bill.happened_on+make_interval(months=>delta))::date;
 elsif bill.details->>'repeat'='weekly' then
  if (occurrence_on-bill.happened_on)%7<>0 then raise exception 'Invalid weekly occurrence'; end if;
  valid_date:=occurrence_on;
 else valid_date:=bill.happened_on; end if;
 if occurrence_on<>valid_date then raise exception 'Invalid occurrence'; end if;
 if exists(select 1 from public.koi_bill_payments p where p.user_id=owner and p.bill_id=pay_koi_bill.bill_id and p.occurrence_on=pay_koi_bill.occurrence_on) then raise exception 'Already paid' using errcode='23505'; end if;
 result:=public.apply_koi_personal_action(request_id,source_hash,'record','create',record_id,null,jsonb_build_object('kind','expense','title','Pagamento: '||left(bill.title,149),'content','Conta: '||bill.title||' · vencimento '||occurrence_on::text,'amount_cents',bill.amount_cents,'happened_on',paid_on));
 insert into public.koi_bill_payments(user_id,bill_id,occurrence_on,expense_id) values(owner,bill.id,occurrence_on,record_id);
 return result;
end; $$;
revoke execute on function public.pay_koi_bill(uuid,text,uuid,uuid,timestamptz,date,date) from public,anon;
grant execute on function public.pay_koi_bill(uuid,text,uuid,uuid,timestamptz,date,date) to authenticated;
