-- Run only after the opt-in PackTwoLiveTest, never as a general reset.
begin;
delete from public.chat_messages
 where id in ('f24fc19d-df0b-4ec9-a3ee-1d240e02b311','f24fc19d-df0b-4ec9-a3ee-1d240e02b312')
 and role='user' and local_date='1901-02-03' and content like 'Cenário fictício PACK2_REPORT_B311:%';
delete from public.koi_action_receipts
 where request_id in ('f24fc19d-df0b-4ec9-a3ee-1d240e02b301','f24fc19d-df0b-4ec9-a3ee-1d240e02b302')
 and result->'task'->>'title' like '%PACK2_TEST_B301%';
select (select count(*) from public.chat_messages where id in
 ('f24fc19d-df0b-4ec9-a3ee-1d240e02b311','f24fc19d-df0b-4ec9-a3ee-1d240e02b312')) as remaining_messages,
 (select count(*) from public.koi_action_receipts where request_id in
 ('f24fc19d-df0b-4ec9-a3ee-1d240e02b301','f24fc19d-df0b-4ec9-a3ee-1d240e02b302')) as remaining_receipts;
commit;
