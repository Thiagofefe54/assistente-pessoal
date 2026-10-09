begin;
insert into auth.users(id) values('96285dd6-a23e-49dd-8449-2cc42673af01'),('96285dd6-a23e-49dd-8449-2cc42673af02');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"96285dd6-a23e-49dd-8449-2cc42673af01","role":"authenticated"}',true);
do $$ declare r jsonb; v timestamptz; bad jsonb; rejected boolean; begin
 r:=public.apply_koi_personal_action('96285dd6-a23e-49dd-8449-2cc42673af10',repeat('a',64),'record','create','96285dd6-a23e-49dd-8449-2cc42673af03',null,
 '{"kind":"expense","title":"Teste isolado","amount_cents":1800,"happened_on":"2026-10-08","details":{"finance_category":"food"}}');
 v:=(r->'record'->>'updated_at')::timestamptz;
 r:=public.apply_koi_personal_action('96285dd6-a23e-49dd-8449-2cc42673af11',repeat('b',64),'record','update','96285dd6-a23e-49dd-8449-2cc42673af03',v,'{"details":{"finance_category":"transport"}}');
 perform public.undo_koi_personal_action('96285dd6-a23e-49dd-8449-2cc42673af11');
 if not exists(select 1 from public.koi_personal_records where id='96285dd6-a23e-49dd-8449-2cc42673af03' and details->>'finance_category'='food') then raise exception 'Undo category failed'; end if;
 perform public.apply_koi_personal_action('96285dd6-a23e-49dd-8449-2cc42673af12',repeat('c',64),'record','create','96285dd6-a23e-49dd-8449-2cc42673af04',null,
 '{"kind":"budget","title":"Teste limite","amount_cents":3000,"happened_on":"2026-10-01","details":{"finance_category":"food"}}');
 perform public.apply_koi_personal_action('96285dd6-a23e-49dd-8449-2cc42673af13',repeat('d',64),'record','create','96285dd6-a23e-49dd-8449-2cc42673af05',null,
 '{"kind":"budget","title":"Teste legado","amount_cents":5000,"happened_on":"2026-10-01","details":{}}');
 for bad in select value from jsonb_array_elements('[{"finance_category":"all"},{"finance_category":null},{"finance_category":"unknown"},{"finance_category":4},{"finance_category":"food","unexpected":true}]') loop
  rejected:=false;
  begin update public.koi_personal_records set details=bad where id='96285dd6-a23e-49dd-8449-2cc42673af03'; exception when others then rejected:=true; end;
  if not rejected then raise exception 'Invalid category accepted: %',bad; end if;
 end loop;
end $$;
select set_config('request.jwt.claims','{"sub":"96285dd6-a23e-49dd-8449-2cc42673af02","role":"authenticated"}',true);
do $$ begin
 if exists(select 1 from public.koi_personal_records) then raise exception 'Owner isolation failed'; end if;
 update public.koi_personal_records set details='{"finance_category":"transport"}' where id='96285dd6-a23e-49dd-8449-2cc42673af03';
 if found then raise exception 'Foreign record changed'; end if;
end $$;
select 'Category create/edit/undo, legacy defaults, invalid categories and RLS isolation passed; rollback follows' as verification;
rollback;
