# Banco PostgreSQL do ReLoop

O SQL e o DER foram desenvolvidos pelo Hiago, responsável pela modelagem do banco.
A pasta `schema/` preserva os arquivos originais entregues por ele.

- `schema/database.sql`: original entregue pelo Hiago, sem alterações.
- `schema/der.jpg`: diagrama recebido.
- `schema/SHA256SUMS`: checksum do SQL original (`cd database/schema && shasum -a 256 -c SHA256SUMS`).
- `audit/compatibility-checks.sql`: testes SQL para banco descartável, com tentativas de escrita e rollback.

Para criar o banco de desenvolvimento, siga o [guia de integração](../docs/database/postgres-integration.md).
A aplicação apenas valida o esquema; a inicialização é explícita via `scripts/init-postgres.sh`.
Não reaplique o SQL em um banco já usado.

A partir do cadastro com CPF, aplique também as [migrações incrementais](migrations/README.md)
após o esquema original e antes de iniciar o backend atualizado. Para bancos já
preenchidos, aplique somente a migração pendente, mediante autorização, sem
executar novamente o inicializador. Usuários legados mantêm CPF nulo.
