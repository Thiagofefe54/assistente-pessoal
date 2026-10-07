# Lembranças confirmadas — primeiro incremento

A consulta diária já funcionava. Esta etapa acrescenta até 20 lembranças de 500
caracteres por conta: preferência, objetivo, rotina ou anotação pessoal. A pessoa
confirma cada informação; a IA não extrai nem salva fatos automaticamente.

## Uso

Em Memória, abra Lembranças confirmadas e escolha Nova lembrança. Também é possível
abrir um dia e usar Guardar lembrança numa mensagem enviada e já sincronizada.
O texto pode ser revisado antes de confirmar. Uma lembrança ligada ao diário
mantém o UUID da mensagem original, consultável pelo botão Fonte.

As operações exigem internet. A lista é consultada ao abrir a área ou ao tocar
Atualizar; ela não tem cache permanente offline nesta primeira etapa. O diário
continua disponível offline. Uma falha ambígua orienta a atualizar antes de tentar
novamente, pois a operação pode ter sido concluída no servidor.

Editar preserva a fonte original. Apagar remove o registro de lembrança, mantendo
a conversa original e as respostas anteriores. Informações ainda presentes no
contexto recente podem continuar sendo citadas; apagar a lembrança não apaga
conversas nem significa esquecimento de tudo que já foi enviado ao provedor.

## Conta e transporte

Tabela memory_facts com RLS de dono para SELECT/INSERT/UPDATE/DELETE. As permissões
de UPDATE permitem apenas conteúdo e categoria. Identidade, fonte e timestamps
não podem ser reassociados pelo cliente. A fonte tem chave estrangeira composta
com user_id, impedindo referência à conversa de outra conta. Vinte posições por
dono limitam o total, inclusive sob requisições concorrentes.

As alterações do app usam updated_at como condição: uma edição ou exclusão
baseada numa versão antiga não sobrescreve silenciosamente outra versão.
O servidor lê até 20 registros com o token da pessoa, depois de verificar Auth,
filtrando também pelo dono verificado. Não usa service_role. O modelo recebe os
fatos como dados abaixo das instruções de identidade, nunca como autorização.
O app informa que as lembranças poderão ser enviadas ao provedor de IA.

## Validação e próximos incrementos

23 testes de backend passaram. Teste SQL com identidades fictícias e rollback
confirmou isolamento, fonte da mesma conta, limite, timestamp e edição antiga.
Teste opt-in MemoryLiveTest cria apenas uma lembrança fictícia, corrige, verifica
recuperação pelo backend sem contexto recente e remove sua própria fixture.
O resultado no Poco e a publicação são registrados em ESTADO_ATUAL.md.

Ainda faltam sugestões revisáveis da IA, relatórios por período, cache offline de
lembranças, atualização automática em múltiplos dispositivos e política de
retenção/exclusão completa. Não foram gerados fatos pessoais de exemplo.

Referência: [RLS do Supabase](https://supabase.com/docs/guides/database/postgres/row-level-security).
