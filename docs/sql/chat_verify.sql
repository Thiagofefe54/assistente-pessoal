-- Testes com dados fictícios inteiramente revertidos ao final.
begin;
insert into auth.users (id)
values ('11111111-1111-4111-8111-111111111111'),
       ('22222222-2222-4222-8222-222222222222');

set local role authenticated;
set local request.jwt.claims = '{"sub":"11111111-1111-4111-8111-111111111111","role":"authenticated"}';
insert into public.chat_messages (user_id,id,role,content,occurred_at,timezone)
values ('11111111-1111-4111-8111-111111111111',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'user', 'Mensagem fictícia',
        '2026-10-04T02:59:59Z', 'America/Sao_Paulo');

insert into public.chat_messages (user_id,id,role,content,occurred_at,timezone)
values ('11111111-1111-4111-8111-111111111111',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'user', 'Mensagem fictícia',
        '2026-10-04T02:59:59Z', 'America/Sao_Paulo')
on conflict (user_id,id) do nothing;

do $$
begin
    if (select count(*) from public.chat_messages) <> 1 then
        raise exception 'Falha: reenvio duplicou a mensagem';
    end if;
    if (select local_date from public.chat_messages limit 1) <> date '2026-10-03' then
        raise exception 'Falha: fuso/data local incorretos';
    end if;
end $$;

set local request.jwt.claims = '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}';
do $$
begin
    if (select count(*) from public.chat_messages) <> 0 then
        raise exception 'Falha: outro usuário conseguiu ler a mensagem';
    end if;
    begin
        insert into public.chat_messages (user_id,id,role,content,occurred_at)
        values ('11111111-1111-4111-8111-111111111111',
                'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'user', 'Teste negado', now());
        raise exception 'Falha: outro usuário conseguiu gravar a mensagem';
    exception when insufficient_privilege then null;
    end;
end $$;

reset role;
set local role anon;
do $$
begin
    begin
        perform * from public.chat_messages;
        raise exception 'Falha: acesso sem login foi permitido';
    exception when insufficient_privilege then null;
    end;
end $$;
reset role;
rollback;

select 'PASSOU: isolamento, acesso anônimo negado, reenvio e data local; dados de teste revertidos' as resultado;
