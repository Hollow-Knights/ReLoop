# Integração PostgreSQL — SQL do Hiago

A integração inicial foi desenvolvida em `feat/postgres-integration`, a partir da
`origin/main` `46317a52531ed479967cdbb19ee03f6d84ff7612`. Este guia contempla também
os três blocos de `feat/mobile-contract-alignment`: perfil mobile, distância
simulada dos ecopontos, cadastro com CPF e formulário de descarte com fotografia.
A especificação OpenAPI versionada define os contratos atuais.

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
| `users` | 5 | `UserRow`: identidade bigint, nome/e-mail, hash e auditoria Instant/timestamptz; V001 acrescenta `document varchar(11)` anulável. `JpaUserAdapter` implementa `UserRepository`. |
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
| Perfis e segurança | Os dois perfis oferecem `GET/PUT /api/v1/user`, mantendo `/api/v1/users/me`; CPF é obrigatório em novos cadastros e não aparece no perfil/JWT. O catálogo inclui `distanceFromUserLocation` simulada em km, estável por ID e igual na lista e no detalhe. `POST /api/v1/disposals` aceita multipart com foto ou JSON com `photoId`; o upload separado permanece disponível. Carteira, históricos e resgates mantêm suas rotas. |

## Transações, locks e idempotência

- **Cadastro:** inserção de usuário e carteira zerada na mesma transação.
  Os índices `ux_users_email_lower` e `ux_users_document` garantem unicidade de
  e-mail normalizado e CPF preenchido. No cadastro, `INSERT ... ON CONFLICT DO
  NOTHING RETURNING id` decide a concorrência sem registrar CPF em exceções SQL
  esperadas. Os conflitos HTTP usam `EMAIL_ALREADY_IN_USE` ou `DOCUMENT_ALREADY_IN_USE`.
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
- **Leitura da carteira:** controller e service delegam ao adaptador sem abrir
  transação externa. `JpaWalletAdapter.findByUserId` abre uma transação somente
  de leitura com `REPEATABLE_READ`: totais e lançamentos são lidos do mesmo
  snapshot, mesmo com resgate confirmado entre as duas consultas. As transações
  de escrita mantêm seu isolamento e seus locks existentes.
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

A sincronização remove o arquivo novo somente em `STATUS_ROLLED_BACK`, isto é,
quando o rollback foi confirmado. Em `STATUS_COMMITTED`, o arquivo permanece.
Em `STATUS_UNKNOWN`, ele também permanece, e um aviso com o UUID registra a
necessidade de reconciliação: não se pode presumir que o banco desfez a operação.
Quando o banco estiver disponível e o resultado tiver sido confirmado, a equipe
pode conferir os metadados/arquivos antes de decidir sobre qualquer limpeza.
Não foi implementado um sistema automático de reconciliação.
A aplicação não apaga as fotos ao encerrar. Banco e
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
set -a
. ./.env
set +a
# Somente na primeira execução, com o banco vazio:
./scripts/init-postgres.sh
# Após criar o esquema original, aplique V001 uma vez; consulte database/migrations/README.md.
docker compose exec -T postgres sh -c \
  'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < database/migrations/V001__add_user_document.sql
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

O inicializador inicia/aguarda o serviço Compose com `--wait` e
`--wait-timeout`. O limite padrão é 60 segundos; exporte
`POSTGRES_INIT_TIMEOUT_SECONDS` como inteiro positivo para alterá-lo. O exemplo
acima exporta o valor do `.env` antes de chamar o script. Valores zero,
negativos ou inválidos são recusados. Falha de subida ou timeout encerra o
script com erro claro **antes de qualquer execução de SQL**.

O healthcheck usa TCP em 127.0.0.1, evitando considerar o servidor temporário
em socket Unix do entrypoint como disponível para a aplicação.
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
carga em memória não é ativada no perfil postgres. O cadastro com CPF adiciona a migração manual versionada
[V001](../../database/migrations/README.md), sem alterar o original. Ela precisa
ser aplicada explicitamente uma vez antes de iniciar esta versão, inclusive em
bancos já preenchidos; mantém CPF NULL para os cinco usuários legados. Se esse
banco já recebeu V001, não a reaplique. DDL futuro deve continuar
versionado e coordenado; não reaplique o arquivo inicial sobre dados existentes.

Se o banco já estiver preenchido, **não execute o inicializador**.
Confira se V001 já foi aplicada. Somente se estiver pendente, aplique-a seguindo
o guia de migrações, mediante autorização do responsável pelo banco. Com V001
já aplicada e as variáveis do `.env` exportadas, as próximas subidas usam somente:

```sh
docker compose up -d --wait --wait-timeout "${POSTGRES_INIT_TIMEOUT_SECONDS:-60}" postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

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
| `PostgresIntegrationTest` | 104 | Jornada e regressões anteriores; carteira consistente com resgate entre consultas; JWT ausente/malformado/expirado/assinatura e emissor incorretos/sem assinatura; isolamento; credenciais e payloads IoT; fotos ausentes/vazias/falsas/truncadas/GIF/grandes; PNG/JPEG; inicialização e rejeição da reaplicação em banco Testcontainers. |
| `PostgresRestartHttpTest` | 2 | HTTP real/Tomcat, rejeição 413 de upload grande, encerramento completo, nova aplicação e recuperação de foto, usuário, carteira, eventos e cupom. |
| `BootstrapRegressionTest` | 48 | Health, catálogo, cadastro/login, precisão e jornada completa com crédito/resgate/replays. Cada método tem contexto próprio. |
| `PhotoCompletionTest` | 3 | Callback de commit, rollback e resultado desconhecido, com bytes reais e store simulado; retenção/limpeza e aviso de reconciliação. |
| `PostgresInitializationScriptTest` | 9 | Script real com Docker CLI simulado: espera saudável com limite, timeout configurável, falha sem executar SQL, propagação de erro de banco preenchido e cinco valores inválidos de timeout. |

Além das regressões anteriores, `OpenApiContractTest` executa 26 verificações de
contrato e `UserDocumentPrivacyTest` executa duas de privacidade/rollback em memória.
Os cenários de CPF cobrem ambos os perfis, zeros iniciais, normalização, formatos
inválidos, duplicidade, concorrência, migração de legados e reinicialização.

O formulário multipart reutiliza as validações e serviços existentes. Uma fronteira
transacional de serviço no perfil postgres inclui metadados de foto e descarte;
o callback de `JpaPhotoStorage` continua responsável pelo arquivo em rollback ou
resultado desconhecido. Os testes abrangem JPEG/PNG, campos, credenciais, isolamento,
concorrência, falha de escrita/fechamento e falha na inserção do descarte. O teste
TCP recupera foto e descarte após reiniciar a aplicação e verifica o limite real
do servlet. `TemporaryPhotoScopeTest` adiciona duas verificações de compensação
e preservação de registros/fotos anteriores no bootstrap.

Resultado em Java 21 após o formulário integrado: **196 testes, 196 passaram, zero falhas,
erros ou ignorados; BUILD SUCCESS** (`./mvnw clean verify`). Os relatórios estão
em `target/surefire-reports`. Os 138 testes anteriores ao formulário integrado foram preservados.

O teste da carteira intercala consultas e resgate usando latches, sem sleeps.
Com o comportamento anterior `READ COMMITTED`, ele foi executado e falhou:
o extrato continha dois lançamentos, mas os totais ainda eram anteriores ao
débito. A correção foi restaurada automaticamente e a suíte passou. Não se
reescreveram commits para essa comprovação.

As verificações PostgreSQL, os locks e os rollbacks usam banco real, sem H2.
`STATUS_UNKNOWN` é simulado no callback, sem provocar perda de conexão no
banco de desenvolvimento. O script de inicialização foi exercitado com um CLI
Docker simulado; seu SQL de proteção foi executado em um novo banco exclusivo
do Testcontainers, com primeira execução bem-sucedida e reaplicação recusada.
Nenhuma execução do inicializador foi feita no banco local preenchido.

Os erros `test rollback` dos logs são falhas intencionais injetadas em triggers
do banco descartável e removidas ao final de cada cenário. O aviso de upload
com resultado desconhecido também é esperado no teste correspondente.

Apenas o bootstrap pode ser verificado sem Docker com
`./mvnw -Dtest=BootstrapRegressionTest test`; isso não substitui o verify completo.
Não foi importada a suíte da branch adiada. As rotas mobile foram acrescentadas
pelos blocos de alinhamento descritos no OpenAPI, mantendo as rotas anteriores.

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

A [estabilização após a auditoria](postgres-audit-fixes.md) registra as
correções, o escopo das regressões e as limitações mantidas.

Não houve push ou merge automático.
