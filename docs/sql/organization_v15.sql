-- Additive validation; no data, ownership, grants or RLS changes.
create or replace function public.validate_koi_life_details() returns trigger language plpgsql security invoker set search_path='' as $$
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
 elsif new.kind in ('expense','income','budget') then
  if exists(select 1 from jsonb_object_keys(new.details) k where k<>'finance_category') then raise exception 'Unexpected financial details'; end if;
  if new.details?'finance_category' and (jsonb_typeof(new.details->'finance_category')<>'string' or (new.details->>'finance_category' not in ('food','transport','home','health','study','leisure','work','other') and not (new.kind='budget' and new.details->>'finance_category'='all'))) then raise exception 'Invalid finance category'; end if;
 elsif new.details<>'{}'::jsonb then raise exception 'Unexpected details'; end if;
 return new;
end; $$;
revoke execute on function public.validate_koi_life_details() from public,anon;
