from pathlib import Path

base=Path(__file__).with_name('personal_tools.sql').read_text(encoding='utf-8')
functions=base[base.index('create function public.apply_koi_personal_action'):]
functions=functions.replace('create function','create or replace function').replace('generate_series(1,200)','generate_series(1,2000)')
functions=functions.replace("'progress','happened_on'","'progress','happened_on','details'")
functions=functions.replace('progress,happened_on)\n    values','progress,happened_on,details)\n    values')
functions=functions.replace("(fields->>'happened_on')::date)\n    returning","(fields->>'happened_on')::date,coalesce(fields->'details','{}'::jsonb))\n    returning")
functions=functions.replace("    archived_at=case action", "    details=case when fields?'details' then fields->'details' else r.details end,\n    archived_at=case action")
functions=functions.replace("progress=(old->>'progress')::smallint,happened_on=", "details=coalesce(old->'details','{}'::jsonb),progress=(old->>'progress')::smallint,happened_on=")
header="""-- Additive life pack; existing rows and IDs remain intact.
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
"""
payment="""
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
"""
Path(__file__).with_name('life_pack.sql').write_text(header+functions+payment,encoding='utf-8')
