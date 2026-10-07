-- A natural-language undo has its own stable request identity.
create function public.undo_koi_action_request(request_id uuid,source_hash text,target_request_id uuid)
 returns jsonb language plpgsql security invoker set search_path='' as $$
declare owner uuid:=auth.uid();receipt public.koi_action_receipts%rowtype;result jsonb;
begin
 if owner is null then raise exception 'Authentication required' using errcode='42501';end if;
 perform pg_advisory_xact_lock(hashtextextended(owner::text,0));
 select * into receipt from public.koi_action_receipts r where r.user_id=owner and r.request_id=undo_koi_action_request.request_id;
 if found then
  if receipt.source_hash<>undo_koi_action_request.source_hash then raise exception 'Request changed' using errcode='23505';end if;
  return receipt.result || jsonb_build_object('undone',false);
 end if;
 if request_id=target_request_id then raise exception 'Invalid undo target' using errcode='23505';end if;
 if exists(select 1 from public.koi_action_receipts r where r.user_id=owner and r.request_id=target_request_id and r.result->>'action'='undo') then
  raise exception 'Undo is not reversible' using errcode='23505';
 end if;
 result:=public.undo_koi_action(target_request_id);
 result:=jsonb_build_object('request_id',request_id,'action','undo','task',result->'task','target_request_id',target_request_id);
 insert into public.koi_action_receipts(user_id,request_id,source_hash,result) values(owner,request_id,source_hash,result);
 return result || jsonb_build_object('undone',false);
end; $$;
revoke execute on function public.undo_koi_action_request(uuid,text,uuid) from public,anon;
grant execute on function public.undo_koi_action_request(uuid,text,uuid) to authenticated;
