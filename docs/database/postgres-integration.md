# Integração PostgreSQL — SQL do Hiago

Implementação em `feat/postgres-integration`, a partir da `origin/main`
`46317a52531ed479967cdbb19ee03f6d84ff7612`. As branches de alinhamento HTTP e
regressão permaneceram preservadas e não foram incorporadas. A base `main`
não tinha classes de teste; os testes desta entrega foram criados para as
rotas oficiais dessa base.

## SQL recebido e auditoria

O arquivo [database.sql](../../database/schema/database.sql) é uma cópia byte a
byte do arquivo entregue pelo Hiago, incluindo DDL, carga inicial e oito
consultas demonstrativas. O [DER](../../database/schema/der.jpg) foi preservado.
SHA-256 do SQL:

`64e8e7abc711fc8f147eee8f83561858fca1f4b67147648693116a650c7bdabe`

Não há enums nativos PostgreSQL: estados, tipos de evento, MIME e tipos de
transação são `varchar`/`text` limitados por CHECK. O mapeamento usa enums
Java por nome e mantém as constraints físicas do arquivo original.

O script completo executou sem erros no PostgreSQL 16.11, em banco vazio e
descartável, sem portas publicadas ou acesso a volumes existentes. A carga
resultante foi:

| Tabela | Linhas iniciais | Mapeamento Java e correspondência |
| --- | ---: | --- |
| `users` | 5 | `UserRow`: identidade bigint, nome/e-mail, hash e auditoria Instant/timestamptz. `JpaUserAdapter` implementa `UserRepository`. |
| `collection_points` | 4 | `PointRow`: ID, nome, endereço, double/coordenadas e `is_active`. |
| `collection_devices` | 5 | `DeviceRow`: PK textual `device_id`, FK para ponto e atividade. |
| `electronic_categories` | 5 | `CategoryRow`: parâmetros integer, limites BigDecimal/numeric(10,3). |
| `collection_point_categories` | 12 | `PointCategoryRow` + `PointCategoryId`: chave composta e referências escalares. `JpaCatalogAdapter` monta os agregados por `CollectionPointRepository` e `ElectronicCategoryRepository`. |
| `uploaded_files` | 10 | `FileRow`: UUID, titular, storage_key, MIME, tamanho e Instant; `JpaPhotoStorage` implementa `PhotoStorage`. |
| `disposals` | 10 | `DisposalRow`: IDs/FKs, UUID da foto, estado String/enum, peso BigDecimal, pontos e instantes; `JpaDisposalAdapter` implementa `DisposalRepository`. |
| `iot_events` | 21 | `EventRow`: eventId global, dispositivo/descarte, tipo, peso, JsonNode/jsonb e Instant; o mesmo adaptador implementa `IotEventRepository`. |
| `wallets` | 5 | `WalletRow`: ID interno, usuário único e três totais; `JpaWalletAdapter` implementa `WalletRepository` e monta o saldo/extrato do domínio. |
| `point_transactions` | 10 | `TransactionRow`: carteira, tipo, pontos, origem única e Instant, utilizado pelo adaptador da carteira. |
| `rewards` | 5 | `RewardRow`: textos, custo, estoque e atividade. |
| `redemptions` | 4 | `RedemptionRow`: usuário/recompensa, snapshot do nome/custo/saldo/cupom/chave/data; `JpaRewardAdapter` implementa `RewardRepository` e `RedemptionRepository`. |

As 12 entidades internas estão em `persistence.postgres.DatabaseRows` e os
12 stores Spring Data JPA em `JpaStores`. Os records do domínio continuam
independentes de JPA. As associações usam IDs escalares e a chave composta;
os relacionamentos e regras de exclusão física continuam sendo impostos
pelas FKs do SQL, sem cascatas de remoção geradas pelo ORM.

O [roteiro SQL da auditoria](../../database/audit/compatibility-checks.sql)
validou a fórmula das estimativas de todos os descartes pesados e a
igualdade entre os cinco saldos e seus extratos. Verificou 18 rejeições:
e-mail normalizado, carteira única, saldo negativo/inconsistente, estoque
negativo, estado desconhecido, cancelamento após pesagem, conclusão sem
data, eventId repetido, evento em outro coletor, pesagem sem peso, crédito e
débito repetidos, origem ausente, chave de resgate, cupom, titular da foto
e ocupação de coletor. As tentativas ocorreram somente no banco descartável
e as linhas foram revertidas. As sequências podem avançar mesmo com rollback;
esse roteiro não deve ser executado em um banco importante.

## Incompatibilidades e decisões

| Ponto | Evidência e decisão aplicada |
| --- | --- |
| Precisão de peso | O SQL fixa numeric(10,3); o contrato anterior aceitava mais casas. No banco, 1,0499 vira 1,050; para Notebook isso mudaria um recálculo de 70 para 71 pontos. **O usuário decidiu preservar o SQL**, aceitar no máximo três casas e retornar 400 sem arredondar. `@Digits(integer=7,fraction=3)` acompanha numeric(10,3), e o OpenAPI declara passo 0,001, limite representável e a restrição textual. Também se rejeitam quatro casas com zero final, como 1,2300. Pesos com precisão válida mas fora da faixa da categoria continuam retornando 422. |
| Categorias e benefícios | Os catálogos SQL são diferentes dos dados de demonstração em memória. Cada perfil usa seu próprio catálogo. Não se aplica o exemplo da air fryer ao banco. O teste SQL usa Notebook/categoria 2, 2,300 kg: min(100, HALF_UP(60 + 2,300 × 10)) = 83; benefício 2 custa 80; saldo final 3. A fórmula não foi alterada. |
| Hashes dos cinco usuários demonstrativos | Os valores `{pbkdf2}` + 32 hexadecimais não são a saída do Pbkdf2PasswordEncoder v5_8 atual. Foram preservados, sem inventar senhas ou trocar hashes. O login desses registros retorna o mesmo 401 genérico de credenciais inválidas, inclusive se o encoder lançar erro de formato; contas novas armazenam hashes compatíveis. |
| Fotos demonstrativas | O SQL contém somente metadados, por exemplo `photos/1/moto-g7.jpg`. Não foram entregues esses bytes. O download dessas fotos retorna 404 enquanto os arquivos correspondentes não forem fornecidos. Uploads novos gravam bytes reais em diretório durável e metadados no banco. |
| Cancelamento | Compatível: somente WAITING_WEIGHT pode ser cancelado, com peso/estimativa nulos. Não se libera descarte já pesado por alteração silenciosa do CHECK. |
| Snapshots IoT do seed | Os JSON demonstrativos são parciais. O adaptador completa os campos ausentes a partir de valores imutáveis do descarte, com zero concedido antes da conclusão. Eventos novos guardam todos os campos da resposta HTTP. Replays devolvem o estado original mesmo depois de eventos posteriores. |
| Tipos e tempo | UUID mapeia UUID, jsonb mapeia JsonNode, numeric mapeia BigDecimal e timestamptz mapeia Instant. Novos instantes de persistência são produzidos em microssegundos para que a primeira resposta e o replay tenham a mesma precisão do PostgreSQL. |
| Perfis e segurança | Controllers/serviços existentes e autenticação foram habilitados também em postgres. Rotas, nomes de DTO e fluxos da main foram preservados. Apenas a restrição de precisão aprovada alterou o OpenAPI. Não existem `/api/v1/user`, benefícios agregados ou formulário unificado nesta branch. |

## Transações, locks e idempotência

- **Cadastro:** inserção de usuário e carteira zerada na mesma transação.
  O índice `ux_users_email_lower` resolve corridas de cadastro/atualização;
  o adaptador traduz somente essa constraint para o conflito de e-mail.
- **Cadastro de descarte:** bloqueia a linha do coletor; testa foto já
  vinculada e sessão ativa; insere o descarte. A unicidade parcial de
  dispositivo e a unicidade da foto complementam o lock, inclusive se
  clientes de banco externos concorrerem com a API.
- **Cancelamento:** bloqueia o descarte e atualiza condicionalmente ao
  proprietário e a WAITING_WEIGHT. A mesma linha protege transições IoT.
- **IoT:** um advisory lock transacional derivado de eventId serializa a
  chave mesmo antes de sua inserção e entre instâncias. A PK e o payload
  completo decidem unicidade/igualdade; colisões de hash apenas serializam
  chaves distintas. Em seguida bloqueia descarte e, ao concluir, carteira.
  Evento + estado + crédito + extrato são confirmados juntos. Rollback não
  consome o eventId. Repetição confere tipo, coletor, descarte e peso decimal.
- **Resgate:** bloqueia carteira **antes** da recompensa e resolve a chave
  salva antes de conferir saldo/estoque. Insere snapshot/cupom, debita e
  diminui estoque na mesma transação. Usuários diferentes disputando a mesma
  recompensa compartilham o lock da linha de estoque. A ordem carteira →
  recompensa é consistente entre resgates; o fluxo IoT não bloqueia recompensa.
- **Movimentações:** o adaptador verifica titularidade, estado/valor da
  origem, magnitude positiva e duplicidade. Atualiza saldo e totais junto
  com o extrato; índices únicos protegem origem de crédito/débito. Os CHECKs
  não implementam sozinhos toda a consistência entre tabelas: ela também
  depende dessas transações da aplicação.
- **Falhas:** conflitos de negócio mantêm os erros existentes. Falhas de
  persistência retornam 500 genérico, sem expor SQL ou credenciais no HTTP.

## Arquivos duráveis

`PHOTO_STORAGE_DIR` deve apontar para um diretório persistente fora do Git.
`storage_key` é relativo a essa raiz, gerado com usuário/UUID; o adaptador
recusa escapes da raiz e links simbólicos. A leitura passa pela autorização
de titularidade já existente. A URL absoluta vem de `PHOTO_BASE_URL` e a
referência de associação continua sendo `photoId`.

Se escrita ou transação dos metadados falhar, uma sincronização de rollback
remove o arquivo novo. A aplicação não apaga as fotos ao encerrar. Banco e
filesystem devem ser mantidos juntos nos backups. Uma queda abrupta entre a
escrita dos bytes e o commit pode deixar um arquivo sem metadados; filesystem
não participa de uma transação distribuída. Não foi adicionada limpeza
automática que pudesse excluir arquivos referenciados.

## Inicialização e execução no Mac

Pré-requisitos: Java 21, Docker Desktop em execução e Maven Wrapper. O
Compose usa projeto/volume próprios e porta 5433 ligada apenas a 127.0.0.1.
Não altera o contêiner `postgres-dev` que já existia na máquina.

```sh
cp .env.example .env
# Preencha POSTGRES_PASSWORD e DB_PASSWORD com a mesma senha local.
# Preencha JWT_SECRET: openssl rand -base64 32
# Preencha IOT_DEVICE_KEY: openssl rand -hex 32
# Confira PHOTO_STORAGE_DIR; a pasta deve permanecer disponível após reinícios.
docker compose up -d postgres
./scripts/init-postgres.sh
set -a
. ./.env
set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

O comando de inicialização é explícito e somente para banco vazio. Ele
executa o arquivo original completo, inclusive as consultas demonstrativas.
Uma segunda execução recusa a operação, retorna erro e não reaplica DDL ou
seed. O corpo desse inicializador foi validado em banco descartável: primeira
execução bem-sucedida; segunda rejeitada com código 3; contagens preservadas.
A sintaxe do Compose também foi validada. Não foi criado um serviço
persistente novo na máquina do usuário durante esta tarefa.

Não existe Flyway/Liquibase na base main. A estratégia desta entrega é o
SQL original aplicado **uma vez**, com `spring.sql.init.mode=never` e
`ddl-auto=validate`. Nenhum DDL é executado pelo backend ao iniciar, e a
carga em memória não é ativada no perfil postgres. Mudanças futuras de DDL
precisarão de migrações versionadas acordadas com Hiago; não se deve reaplicar
o arquivo inicial sobre dados existentes.

Use `docker compose stop` para parar o banco preservando seu volume.
Para usar um PostgreSQL já administrado fora do Compose, aplique o SQL
explicitamente em um banco novo autorizado e configure DB_URL, DB_USERNAME
e DB_PASSWORD. O inicializador incluído atende somente ao serviço Compose;
não usa uma DB_URL arbitrária do shell para aplicar DDL.

## Testes e resultados

```sh
./mvnw clean verify
```

Os testes PostgreSQL requerem o Docker ativo, criam bancos próprios com
Testcontainers 1.21.4 e a imagem `postgres:16.11-alpine3.23`, e carregam o SQL
original. Não usam `.env`, banco de desenvolvimento, senhas de produção ou
servidor iniciado manualmente. Não há testes desabilitados por falta de Docker.
A base original é recarregada entre os métodos nos bancos descartáveis.

| Classe | Execuções | Verificações |
| --- | ---: | --- |
| `PostgresIntegrationTest` | 24 | Mapeamento/seed, cadastro/login, hashes inválidos, perfil/e-mail, upload privado/associação, cancelamento, jornada, limites e precisão, snapshots, concorrência de fechamento/resgate/saldo/estoque/coletor, rollback de crédito/resgate/upload e segunda instância. |
| `PostgresRestartHttpTest` | 1 | HTTP real com Java HttpClient/Tomcat. Cadastra, faz upload, descarta, credita e resgata; encerra aplicação/servidor/pool/JPA completamente, inicia outra e verifica usuário, bytes, carteira, respostas IoT e cupom idempotentes. |
| `BootstrapRegressionTest` | 2 | Health, catálogo original de memória, cadastro/login e rejeição da precisão excedente sem PostgreSQL. |

Resultado em Java 21: **27 testes, 27 passaram, zero falhas, erros ou
ignorados; BUILD SUCCESS**. A execução final está registrada nos relatórios
`target/surefire-reports`. A auditoria SQL, a validação Hibernate, os testes
de locks e os rollbacks usaram PostgreSQL real, sem H2. Os erros `test rollback`
nos logs são falhas deliberadamente injetadas em triggers do banco descartável,
removidas ao final de cada cenário.

Apenas o bootstrap pode ser verificado sem Docker com
`./mvnw -Dtest=BootstrapRegressionTest test`; isso não substitui o verify completo.
Não foi importada a suíte da branch adiada nem foram acrescentados endpoints.

## Pendências e limites

- Hiago/equipe precisam fornecer arquivos binários das fotos históricas caso
  desejem baixá-las, e hashes compatíveis/senhas de demonstração por um fluxo
  autorizado se desejarem login nessas cinco contas. Novos cadastros já funcionam.
- PostgreSQL e diretório de fotos devem ser provisionados e incluídos em
  backup no ambiente definitivo; esta entrega valida desenvolvimento local.
- Não houve teste de carga prolongada, execução em um cluster distribuído
  ou integração com sensores físicos. Os testes concorrentes usam conexões
  reais e os testes de reinício não mantêm estado de aplicação compartilhado.
- JWT mantém a política da main de emissor por instância: depois de um
  reinício o usuário faz login novamente. Persistência/idempotência dos dados
  não depende de manter o JWT antigo válido.
- O SQL não é uma migração idempotente e suas fotos/hashes demonstrativos
  continuam com as limitações originais; nenhuma dessas lacunas foi escondida
  com dados inventados ou alterações silenciosas do esquema.

Não houve push ou merge automático.
