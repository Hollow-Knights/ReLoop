# Migrações incrementais do ReLoop

O SQL e o DER originais do Hiago permanecem em `../schema/`, sem alterações.
As migrações são arquivos SQL versionados, aplicados explicitamente com `psql`,
em ordem numérica, uma única vez por banco. Não há execução automática pela
aplicação, Flyway ou mudança de `ddl-auto=validate`.

## V001 — CPF de novos usuários

`V001__add_user_document.sql` adiciona `users.document varchar(11)` anulável,
um CHECK para a representação canônica (11 dígitos ASCII, não todos iguais)
e um índice único parcial `ux_users_document` para valores não nulos.
A aplicação valida os dígitos verificadores e exige CPF de todo cadastro novo;
o banco não preenche nem inventa CPF para usuários antigos. Login por e-mail,
identidade por ID/JWT e respostas de perfil não mudam. Atualizar o perfil não
altera CPF. Não existe endpoint de consulta ou atualização de CPF neste bloco.

O arquivo contém BEGIN/COMMIT e um advisory lock transacional que serializa estas
migrações. Qualquer erro reverte a migração inteira quando a conexão psql termina.
A reaplicação falha sem alterar os dados: não usamos IF NOT EXISTS para esconder
um esquema possivelmente divergente. Registre operacionalmente a aplicação da
versão; esta solução mínima não cria um histórico automático de migrações.

## Instalação nova

1. Somente em um banco vazio, aplique o SQL original pelo procedimento de
   `scripts/init-postgres.sh`, conforme o guia de integração.
2. Aplique V001 pelo comando abaixo, antes de iniciar o backend atualizado.
3. Inicie o perfil `postgres`; Hibernate apenas valida o esquema.

## Banco já inicializado

Não execute novamente o inicializador nem o SQL original. Com o PostgreSQL
existente disponível, faça backup e aplique V001 explicitamente em uma janela
coordenada, antes de iniciar esta versão do backend. O comando abaixo escreve
no banco do Compose; execute-o apenas quando tiver autorizado a migração desse
banco. Nesta tarefa, ele foi executado somente em Testcontainers.

```sh
docker compose exec -T postgres sh -c \
  'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < database/migrations/V001__add_user_document.sql
```

Para um PostgreSQL administrado fora do Compose, use `psql -X -v ON_ERROR_STOP=1
-f database/migrations/V001__add_user_document.sql` com a conexão administrada
pelo ambiente. Não exponha senha em argumentos ou logs.

V001 deve ser aplicada somente uma vez. Para conferir a versão sem escrever:

```sql
SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'document';
SELECT indexdef FROM pg_indexes
WHERE schemaname = 'public' AND indexname = 'ux_users_document';
```

O resultado esperado é varchar(11), anulável, e índice UNIQUE com filtro
`document IS NOT NULL`. Múltiplos usuários legados com NULL são permitidos.
Não remova a coluna como forma de rollback: isso apagaria CPFs já cadastrados.
Uma instalação que ainda só tenha o SQL original não satisfaz a validação JPA
desta versão; aplique V001 explicitamente antes de iniciar a aplicação.

## Concorrência, privacidade e verificação

O índice único decide conflitos inclusive entre instâncias. O adaptador usa
INSERT ... ON CONFLICT DO NOTHING RETURNING id para conflitos esperados não
produzirem exceções SQL com CPF nos detalhes de logs. Usuário e carteira continuam
na mesma transação; uma falha da carteira desfaz a inserção e libera o CPF para
nova tentativa. Valores de parâmetros SQL e DTOs com dados pessoais não devem
ser registrados. O cadastro/perfil e o JWT não retornam CPF.

`./mvnw clean verify` cria bancos isolados: aplica original + V001 antes de JPA,
verifica concorrência e reinicialização, além de migrar um banco com os cinco
usuários legados. Verifica também rollback de uma falha no meio da migração e
rejeição segura da reaplicação. Nenhum banco local preenchido é usado nos testes.
Os CPFs de teste são calculados localmente e não vêm de registros de pessoas.
