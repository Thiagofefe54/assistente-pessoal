-- Synthetic identities and rows, entirely rolled back. No personal data is read.
begin;
insert into auth.users(id) values
 ('f14fc19d-df0b-4ec9-a3ee-1d240e02b001'),
 ('f14fc19d-df0b-4ec9-a3ee-1d240e02b002');
insert into public.chat_messages(user_id,id,role,content,occurred_at,timezone)
 values ('f14fc19d-df0b-4ec9-a3ee-1d240e02b002','f14fc19d-df0b-4ec9-a3ee-1d240e02b003','user','Fonte fictícia',now(),'UTC');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"f14fc19d-df0b-4ec9-a3ee-1d240e02b001","role":"authenticated"}',true);
insert into public.memory_facts(user_id,id,slot,content,category)
 values ('f14fc19d-df0b-4ec9-a3ee-1d240e02b001','f14fc19d-df0b-4ec9-a3ee-1d240e02b004',1,'Somente teste','note');
do $$ declare previous timestamptz; n integer; begin
 select updated_at into previous from public.memory_facts where id='f14fc19d-df0b-4ec9-a3ee-1d240e02b004';
 update public.memory_facts set content='Teste corrigido' where id='f14fc19d-df0b-4ec9-a3ee-1d240e02b004';
 if not exists(select 1 from public.memory_facts where id='f14fc19d-df0b-4ec9-a3ee-1d240e02b004' and updated_at>previous) then raise exception 'Timestamp not updated'; end if;
 update public.memory_facts set content='Edição antiga' where id='f14fc19d-df0b-4ec9-a3ee-1d240e02b004' and updated_at=previous;
 get diagnostics n = row_count;
 if n<>0 then raise exception 'Stale edit succeeded'; end if;
 begin
  insert into public.memory_facts(user_id,slot,content,category) values('f14fc19d-df0b-4ec9-a3ee-1d240e02b001',21,'Too many','note');
  raise exception 'Quota failed';
 exception when check_violation then null; end;
 begin
  insert into public.memory_facts(user_id,slot,content,category,source_message_id) values('f14fc19d-df0b-4ec9-a3ee-1d240e02b001',2,'Cross owner source','note','f14fc19d-df0b-4ec9-a3ee-1d240e02b003');
  raise exception 'Cross owner source accepted';
 exception when foreign_key_violation then null; end;
end $$;
select set_config('request.jwt.claims','{"sub":"f14fc19d-df0b-4ec9-a3ee-1d240e02b002","role":"authenticated"}',true);
do $$ declare n integer; begin
 if exists(select 1 from public.memory_facts where user_id='f14fc19d-df0b-4ec9-a3ee-1d240e02b001') then raise exception 'Other owner visible'; end if;
 update public.memory_facts set content='Other user edit' where id='f14fc19d-df0b-4ec9-a3ee-1d240e02b004';
 get diagnostics n = row_count;
 if n<>0 then raise exception 'Other owner edited'; end if;
 delete from public.memory_facts where id='f14fc19d-df0b-4ec9-a3ee-1d240e02b004';
 get diagnostics n = row_count;
 if n<>0 then raise exception 'Other owner deleted'; end if;
 begin
  insert into public.memory_facts(user_id,slot,content,category) values('f14fc19d-df0b-4ec9-a3ee-1d240e02b001',2,'Wrong owner','note');
  raise exception 'Wrong owner insert accepted';
 exception when insufficient_privilege then null; end;
end $$;
reset role;
select 'owner isolation, source ownership, quota, timestamp and stale edit: ok' as result;
rollback;
