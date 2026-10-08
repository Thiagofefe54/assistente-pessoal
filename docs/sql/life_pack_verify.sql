begin;
insert into auth.users(id) values('f24fc19d-df0b-4ec9-a3ee-1d240e02c101'),('f24fc19d-df0b-4ec9-a3ee-1d240e02c102');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"f24fc19d-df0b-4ec9-a3ee-1d240e02c101","role":"authenticated"}',true);
do $$ declare r jsonb; v timestamptz; begin
 r:=public.apply_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c110',repeat('a',64),'record','create','f24fc19d-df0b-4ec9-a3ee-1d240e02c103',null,
  '{"kind":"bill","title":"Conta fictícia","amount_cents":5000,"happened_on":"2027-01-31","details":{"repeat":"monthly"}}');
 v:=(r->'record'->>'updated_at')::timestamptz;
 r:=public.pay_koi_bill('f24fc19d-df0b-4ec9-a3ee-1d240e02c111',repeat('b',64),'f24fc19d-df0b-4ec9-a3ee-1d240e02c103','f24fc19d-df0b-4ec9-a3ee-1d240e02c104',v,'2027-02-28','2027-02-27');
 r:=public.pay_koi_bill('f24fc19d-df0b-4ec9-a3ee-1d240e02c111',repeat('b',64),'f24fc19d-df0b-4ec9-a3ee-1d240e02c103','f24fc19d-df0b-4ec9-a3ee-1d240e02c104',v,'2027-02-28','2027-02-27');
 if (select count(*) from public.koi_bill_payments)<>1 or (select count(*) from public.koi_personal_records where kind='expense')<>1 then raise exception 'Payment duplicated'; end if;
 begin
  perform public.pay_koi_bill('f24fc19d-df0b-4ec9-a3ee-1d240e02c112',repeat('c',64),'f24fc19d-df0b-4ec9-a3ee-1d240e02c103','f24fc19d-df0b-4ec9-a3ee-1d240e02c105',v,'2027-02-28','2027-02-27');
  raise exception 'Duplicate occurrence accepted';
 exception when unique_violation then null; end;
 r:=public.undo_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c111');
 if exists(select 1 from public.koi_bill_payments) or exists(select 1 from public.koi_personal_records where kind='expense') then raise exception 'Payment undo failed'; end if;
 r:=public.apply_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c113',repeat('d',64),'record','update','f24fc19d-df0b-4ec9-a3ee-1d240e02c103',v,'{"details":{"repeat":"weekly"}}');
 perform public.undo_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c113');
 if not exists(select 1 from public.koi_personal_records where details->>'repeat'='monthly') then raise exception 'Details undo failed'; end if;
 perform public.apply_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c114',repeat('e',64),'record','create','f24fc19d-df0b-4ec9-a3ee-1d240e02c106',null,'{"kind":"diary","title":"Sono fictício","happened_on":"2026-10-07","details":{"category":"sleep","started_at":"2026-10-07T23:00:00-03:00","ended_at":"2026-10-08T07:30:00-03:00"}}');
 perform public.apply_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c115',repeat('f',64),'record','create','f24fc19d-df0b-4ec9-a3ee-1d240e02c107',null,'{"kind":"budget","title":"Limite fictício","amount_cents":10000,"happened_on":"2026-10-01"}');
end $$;
select set_config('request.jwt.claims','{"sub":"f24fc19d-df0b-4ec9-a3ee-1d240e02c102","role":"authenticated"}',true);
do $$ begin
 if exists(select 1 from public.koi_personal_records) or exists(select 1 from public.koi_bill_payments) then raise exception 'Owner isolation failed'; end if;
 begin
  perform public.pay_koi_bill('f24fc19d-df0b-4ec9-a3ee-1d240e02c116',repeat('a',64),'f24fc19d-df0b-4ec9-a3ee-1d240e02c103','f24fc19d-df0b-4ec9-a3ee-1d240e02c108',now(),'2027-02-28','2027-02-27');
  raise exception 'Cross owner payment accepted';
 exception when unique_violation then null; end;
end $$;
rollback;
