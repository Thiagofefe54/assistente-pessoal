begin;
insert into auth.users(id) values('f24fc19d-df0b-4ec9-a3ee-1d240e02c001'),('f24fc19d-df0b-4ec9-a3ee-1d240e02c002');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"f24fc19d-df0b-4ec9-a3ee-1d240e02c001","role":"authenticated"}',true);
do $$ declare r jsonb; v timestamptz; begin
 r:=public.apply_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c010',repeat('a',64),'record','create','f24fc19d-df0b-4ec9-a3ee-1d240e02c003',null,
  '{"kind":"expense","title":"Fixture financeira","amount_cents":1250}');
 r:=public.apply_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c010',repeat('a',64),'record','create','f24fc19d-df0b-4ec9-a3ee-1d240e02c003',null,'{}');
 if (select count(*) from public.koi_personal_records)<>1 then raise exception 'Duplicated record'; end if;
 v:=(r->'record'->>'updated_at')::timestamptz;
 r:=public.apply_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c011',repeat('b',64),'record','update','f24fc19d-df0b-4ec9-a3ee-1d240e02c003',v,'{"amount_cents":1500}');
 begin
  perform public.apply_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c012',repeat('c',64),'record','archive','f24fc19d-df0b-4ec9-a3ee-1d240e02c003',v,'{}');
  raise exception 'Stale version accepted';
 exception when unique_violation then null; end;
 r:=public.undo_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c011');
 r:=public.undo_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c011');
 if not exists(select 1 from public.koi_personal_records where amount_cents=1250) then raise exception 'Undo failed'; end if;
 r:=public.apply_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c020',repeat('d',64),'memory','create','f24fc19d-df0b-4ec9-a3ee-1d240e02c004',null,
  '{"content":"Somente memória fictícia","category":"note"}');
 v:=(r->'record'->>'updated_at')::timestamptz;
 r:=public.apply_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c021',repeat('e',64),'memory','delete','f24fc19d-df0b-4ec9-a3ee-1d240e02c004',v,'{}');
 if exists(select 1 from public.memory_facts) then raise exception 'Delete failed'; end if;
 r:=public.undo_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c021');
 if not exists(select 1 from public.memory_facts where content='Somente memória fictícia') then raise exception 'Memory restoration failed'; end if;
end $$;
select set_config('request.jwt.claims','{"sub":"f24fc19d-df0b-4ec9-a3ee-1d240e02c002","role":"authenticated"}',true);
do $$ begin
 if exists(select 1 from public.koi_personal_records) or exists(select 1 from public.memory_facts) or exists(select 1 from public.koi_tool_receipts) then raise exception 'Owner isolation failed'; end if;
 begin
  perform public.undo_koi_personal_action('f24fc19d-df0b-4ec9-a3ee-1d240e02c011'); raise exception 'Cross-owner undo allowed';
 exception when unique_violation then null; end;
end $$;
rollback;
