# Auditoria de preparação para PostgreSQL

## Estado da integração

A auditoria usou a `main` em `898b9d6`, que inclui o merge do PR #5 e os
commits `a16dc4f` e `b9cab09` do Bloco 6. A `main` local foi atualizada por
fast-forward e a auditoria foi registrada em `chore/postgres-integration-preparation`.

Foram inspecionados a árvore de trabalho, o histórico alcançável, as árvores
das branches locais e remotas e as referências anunciadas por `origin`
(`Hollow-Knights/ReLoop`) após `git fetch origin`. As referências remotas
contêm a `main` e as branches e PRs dos blocos do backend. A busca por nomes
de arquivos e por conteúdo de DDL, carga de dados e DER encontrou apenas
a proposta de esquema e a configuração do perfil PostgreSQL. A inspeção
abrange o material disponível neste repositório.

| Artefato | Resultado |
| --- | --- |
| DDL e tabelas implementadas | Nenhuma tabela confirmada. Nenhum `schema.sql`, migração ou DDL versionado encontrado. |
| Dados iniciais e consultas | Nenhum `seed.sql`, `queries.sql` ou equivalente encontrado. Os dados do catálogo e das recompensas vêm dos adaptadores em memória. |
| DER e instruções | Nenhum DER ou instrução de instalação do banco do Hiago encontrado. O README orienta como configurar a conexão quando o esquema for disponibilizado. |
| Contratos para comparação | A proposta descreve 12 tabelas. O OpenAPI descreve 17 operações de negócio e a leitura auxiliar de foto. |

Os modelos Java têm correspondência conceitual com a proposta. A
compatibilidade física com PostgreSQL, incluindo nomes, tipos, FKs,
constraints e índices, permanece pendente do DDL executável. Os modelos são
records de domínio e as interfaces delimitam as operações que os
adaptadores JPA devem preservar. O perfil `postgres` ainda carece dos
adaptadores e da ativação da API.

A auditoria tomou como referência a arquitetura e os contratos versionados. [Arquitetura](../architecture.md) ·
[Proposta de esquema](schema-contract.md) ·
[OpenAPI](../../src/main/resources/static/docs/openapi.yaml)

## Matriz por módulo

Os nomes, tipos e chaves abaixo vêm da proposta ou dos requisitos inferidos
do código. A coluna de trabalho JPA indica os pontos que precisam ser
confrontados com o DDL antes do mapeamento.

| Módulo e interface existente | Domínio Java | Tabela proposta, campos e tipos | Chaves e invariantes | Diferença e trabalho JPA |
| --- | --- | --- | --- | --- |
| Usuários e autenticação · `UserRepository` | `User(Long id, String name, String email, String passwordHash)` | `users` com `id bigint`, `name varchar(120)`, `email varchar(254)`, `password_hash text` e `created_at`/`updated_at timestamptz`. | PK `id`, unicidade do e-mail normalizado, campos obrigatórios e nome/e-mail não vazios. | O record omite auditoria e o ID hoje é gerado em memória. Mapear geração e timestamps conforme DDL, preservar o hash PBKDF2 e o ID imutável no `sub`, traduzir duplicidade e criar usuário e carteira na mesma transação. |
| Pontos de coleta · `CollectionPointRepository` | `CollectionPoint` com `Long id`, textos, coordenadas `double`, `boolean active`, listas de categorias e dispositivos. | `collection_points` com ID, nome, endereço, latitude e longitude `double precision`, `is_active boolean`. `collection_point_categories` associa IDs `bigint`. | PK do ponto, latitude de −90 a 90 e longitude de −180 a 180. PK composta da associação e FKs para ponto e categoria. | As listas do domínio são relações no banco. Montar o detalhe em projeção ou dentro da transação, com listas vazias quando cabível. Preservar `isActive` no JSON. |
| Categorias · `ElectronicCategoryRepository` | `ElectronicCategory` com ID `Long`, nome, três parâmetros `int` e pesos `BigDecimal`. | `electronic_categories` com ID, nome, `base_points`, `points_per_kg` e `max_points integer`, `min_weight_kg` e `max_weight_kg numeric`. | PK `id`, nome único na proposta, pontos não negativos e intervalo inclusivo com mínimo positivo e máximo ≥ mínimo. | Confirmar precisão e escala dos pesos. A pesagem consulta a categoria e o cálculo usa `HALF_UP`. Um arredondamento silencioso na gravação pode mudar o peso observado e a comparação de eventos. Avaliar busca pontual por ID, pois o serviço hoje percorre o catálogo. |
| Dispositivos · leitura via `CollectionPointRepository` | `CollectionPoint.Device(String deviceId, boolean active)` pertence ao agregado do ponto. | `collection_devices` com `device_id varchar(64)`, `collection_point_id bigint`, `is_active boolean`. | PK textual `device_id` e FK para o ponto. A chave IoT autoriza um dispositivo específico. | Confirmar o código textual usado no QR e no HTTP mesmo se o DDL adotar ID interno. O serviço hoje varre os pontos por código de dispositivo. Usar uma consulta por código para o adaptador, se necessário, e alinhar a origem da chave IoT por ambiente. |
| Fotografias · `PhotoStorage` | `UploadedPhoto(UUID id, Long userId, String contentType)`. O serviço gera a URI. | `uploaded_files` com `id uuid`, `user_id bigint`, `storage_key text`, `content_type text`, `size_bytes bigint`, `created_at timestamptz`. | PK UUID, FK para usuário, chave de armazenamento única e chave candidata `(id, user_id)`. JPEG/PNG e 1 a 10.485.760 bytes. | O modelo público ao adaptador não carrega localização, tamanho ou data. Persistir os metadados internamente e usar armazenamento durável para os bytes. O adaptador atual apaga o diretório temporário no encerramento. Coordenar limpeza de órfãos em falhas entre gravação do arquivo e commit e preservar o download por titular. |
| Descartes · `DisposalRepository` | `Disposal` e `DisposalStatus`. IDs `Long`, código do coletor `String`, foto `UUID`, peso `BigDecimal`, estimativa `Integer`, crédito `int`, criação `Instant`. | `disposals` com IDs correspondentes, `item_name varchar(120)`, `status varchar(24)`, peso `numeric` e estimativa `integer` anuláveis, crédito `integer`, criação/atualização/conclusão `timestamptz`. | PK `id`, FKs para usuário, dispositivo e categoria e FK composta da foto com o titular. Unicidade de `photo_id` e chave candidata `(id, device_id)`. Só uma sessão ativa por coletor. | O record omite `updated_at` e `completed_at`. Mapear os cinco estados por nome. `WAITING_WEIGHT` e `CANCELLED` exigem peso e estimativa nulos. Após a pesagem, exigir peso positivo e estimativa não negativa. Somente `COMPLETED` exige data de conclusão e crédito igual à estimativa. Criar a sessão e vincular a foto atomicamente. Consultar por ID e titular e cancelar apenas em `WAITING_WEIGHT`, mantendo o histórico. |
| IoT · `IotEventRepository.process` | `IotEvent`, `IotEventType` e snapshot do resultado. `eventId` e código do coletor `String`, descarte `Long`, peso `BigDecimal`. | `iot_events` com `event_id varchar(128)`, `device_id varchar(64)`, `disposal_id bigint`, `event_type varchar(24)`, `weight_kg numeric`, `response_payload jsonb`, `processed_at timestamptz`. | PK global `event_id`, FK composta para o descarte e seu coletor. Peso positivo na pesagem e nulo nos eventos de porta. | Hoje o resultado anterior é um `Disposal` imutável. A proposta guarda o snapshot em JSONB. Garantir que o adaptador consiga reconstruir o resultado exigido por `process` ou alinhar um resultado interno específico para o IoT. Repetições devem devolver os valores originais mesmo após novos eventos. |
| Carteiras · `WalletRepository` | `Wallet(Long userId, int totalEarned, int totalRedeemed, List<PointTransaction>)` com saldo derivado. | `wallets` com `id bigint`, `user_id bigint`, `available_points`, `total_earned` e `total_redeemed integer`. | PK `id`, FK única para usuário, totais e saldo não negativos e saldo igual a ganho menos resgatado. | O domínio é identificado por usuário e não contém ID próprio nem saldo persistido. Confirmar a estratégia do DDL e manter agregados e extrato consistentes na escrita e na leitura. Se persistido, atualizar o saldo junto com os totais. |
| Movimentações · operações de crédito e débito de `WalletRepository` | `PointTransaction`, `PointTransactionType` (`CREDIT`/`DEBIT`), ID `long`, magnitude `int`, descrição, `Instant` e IDs anuláveis das origens. | `point_transactions` com `id`, `wallet_id`, `disposal_id` e `redemption_id bigint`, `type varchar(6)`, `points integer`, descrição e `created_at timestamptz`. | PK e FKs para carteira e origens. Valor positivo, exatamente uma origem coerente com o tipo, unicidade de cada origem quando presente e titular da carteira igual ao da origem. | Hoje a carteira contém a lista, os IDs são gerados em memória e um conjunto evita crédito duplicado. Mapear a tabela e preservar unicidade no banco, vincular o lançamento à carteira correta e ordenar o extrato por data e ID decrescentes. |
| Recompensas · `RewardRepository` | `Reward` com ID `Long`, nome, descrição, parceiro, custo e estoque `int`, `boolean active`. | `rewards` com ID, textos, `cost_points` e `available_quantity integer` e `active boolean`. | PK `id`, custo positivo e estoque ≥ 0. A atividade e o estoque disponível condicionam a baixa. | Substituir a carga do construtor por dados aprovados. O catálogo lê o estado persistido. A baixa é parte da transação de resgate. |
| Resgates · `RedemptionRepository` | `Redemption` com IDs `Long`, nome da recompensa, custo, saldo após o resgate, cupom `String` e `Instant`. | `redemptions` com IDs de usuário e recompensa, `reward_name`, `points_spent` e `remaining_points integer`, `coupon_code`, `idempotency_key varchar(128)`, `redeemed_at timestamptz`. | PK `id`, FKs para usuário e recompensa, cupom único, unicidade de `(user_id, idempotency_key)`, custo positivo e saldo histórico não negativo. | O titular e a chave estão na chave do mapa em memória e ficam fora do record. Persistir os dois no adaptador. Guardar nome, custo, saldo e cupom da emissão para reproduzir `201`. Filtrar histórico pelo titular e ordenar por data e ID decrescentes. |

Confirmar também o schema PostgreSQL e o `search_path`, a estratégia de
geração de IDs, a política de exclusão e retenção, os limites de texto e
a precisão temporal. Mapear enums pelo valor acordado. Para instantes,
preservar UTC no HTTP e verificar a precisão após leitura do banco. Usar o mesmo valor temporal na primeira resposta e nas repetições idempotentes. Para
valores `int32` do OpenAPI, validar o limite de pontos acumulados e o
comportamento em overflow antes de mapear qualquer tipo diferente.

## Concorrência, atomicidade e tradução de falhas

Os monitores Java coordenam threads de um único processo `bootstrap`.
No banco, cada operação abaixo precisa de limites transacionais explícitos,
constraints e uma estratégia válida para conexões concorrentes.

| Operação | Garantia a implementar e verificar |
| --- | --- |
| Cadastro e edição de e-mail | Usar uma única normalização consistente entre aplicação, lookup e regra de unicidade. Alinhar collation e capitalização com Hiago, inclusive e-mails já carregados pelo seed. Em cadastros concorrentes, aceitar uma conta e traduzir o conflito para `409 EMAIL_ALREADY_IN_USE`. Abrir uma carteira zerada na mesma transação do cadastro. `UNIQUE(user_id)` impede duplicação e a transação garante a criação junto com a conta. |
| Reserva do coletor e foto | Usar unicidade para a foto e uma garantia no banco para uma sessão por coletor em `WAITING_WEIGHT`, `WAITING_DEPOSIT` ou `DEPOSIT_IN_PROGRESS`. Um índice único parcial por coletor é uma opção a alinhar com Hiago. A validação de atividade e categoria aceita deve ser coerente com a criação da sessão. Mapear os conflitos para `PHOTO_ALREADY_LINKED` ou `DEVICE_UNAVAILABLE`. |
| Cancelamento e transições IoT | Serializar transições do mesmo descarte com bloqueio de linha ou atualização condicionada ao estado esperado. Escopar cancelamento ao usuário e eventos ao dispositivo. Uma corrida entre cancelamento e pesagem só pode confirmar uma transição. `COMPLETED` e `CANCELLED` liberam a sessão. |
| Eventos e fechamento | Reservar ou resolver o `event_id` de forma segura sob concorrência, antes de avançar o estado. Repetição equivalente retorna o snapshot. Um payload divergente retorna `409 EVENT_ID_CONFLICT`. Comparar pesos por valor decimal. Gravar evento, snapshot, estado, crédito positivo único e totais da carteira no mesmo commit. Sem pontos, concluir sem lançamento. Um rollback deve permitir reenviar o mesmo ID. |
| Resgate, estoque e saldo | Resolver a chave por usuário antes das validações de saldo e estoque. Mesma recompensa retorna o cupom original. Uma recompensa diferente retorna `409 IDEMPOTENCY_KEY_CONFLICT`. Serializar ou condicionar as atualizações de carteira e estoque, com ordem consistente de aquisição de locks. Gravar débito, estoque e resgate no mesmo commit. Traduzir saldo insuficiente e benefício inativo ou esgotado para os códigos de conflito do OpenAPI. |
| Créditos, débitos e extrato | Usar unicidade para cada crédito por descarte e para o débito de cada resgate. Validar titularidade cruzada das origens na transação ou via integridade referencial acordada. Em leitura, obter totais e extrato a partir de uma visão coerente para evitar misturar snapshots durante concorrência. |

Uma consulta prévia seguida de `save` pode concorrer com outra transação.
Usar bloqueios de linha, atualizações condicionais ou índices únicos nos
pontos de disputa. A decisão sobre `ON CONFLICT` e repetição deve considerar
o isolamento, a visibilidade do registro vencedor e o limite da transação.
Se uma violação deixar a transação abortada, a consulta do vencedor deve
ocorrer após rollback ou em uma estratégia que mantenha a transação válida.
As operações Spring que abrangem múltiplos repositórios precisam compartilhar
o mesmo limite de `@Transactional`. Confirmar também em testes os erros
detectados no flush ou no commit. [Bloqueios do PostgreSQL](https://www.postgresql.org/docs/current/explicit-locking.html) ·
[Isolamento do PostgreSQL](https://www.postgresql.org/docs/current/transaction-iso.html) ·
[Índices parciais do PostgreSQL](https://www.postgresql.org/docs/current/indexes-partial.html) ·
[Transações do Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/transactions.html)

## Configuração e lacunas do perfil postgres

| Área | Verificado | Trabalho para o Bloco 7B |
| --- | --- | --- |
| JDBC e ambiente | O POM inclui Spring Data JPA e o driver PostgreSQL. `application-postgres.yml` lê `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. `.env.example` seleciona `postgres`. | Receber versão, banco, schema e parâmetros de conexão do Hiago. Configurar variáveis fora do Git e aplicar os scripts aprovados em banco de integração antes de iniciar a aplicação. |
| Validação e inicialização | `postgres` usa `ddl-auto=validate`. A configuração comum usa `spring.sql.init.mode=never` e `spring.jpa.open-in-view=false`. A inspeção encontrou zero entidades JPA. | Criar mapeamentos e adaptadores após o DDL. Validar também índices, constraints e comportamento por testes no banco. Um startup sem entidades oferece pouca evidência sobre o esquema. Obter os dados associados dentro da transação ou por projeção. |
| Ativação da API | Controllers e serviços de negócio e adaptadores em memória estão vinculados a `bootstrap`. A configuração atual de segurança só habilita o processamento JWT e o filtro IoT nesse perfil. | Habilitar controllers e serviços compartilháveis com os adaptadores de `postgres`. Manter os beans demonstrativos exclusivos de `bootstrap`. Disponibilizar encoder, decoder, `CurrentUserProvider`, política das rotas públicas e privadas e configuração do IoT em `postgres`, conforme o OpenAPI. Evitar ativar os dois perfis juntos, pois o bootstrap exclui JDBC/JPA. |
| Segredos, fotos e documentação | `JWT_SECRET`, `IOT_DEVICE_ID`, `IOT_DEVICE_KEY`, `PHOTO_BASE_URL` e os limites multipart estão no YAML do bootstrap. O emissor JWT atual é aleatório por instância. Swagger está habilitado no bootstrap. | Definir emissor e segredo adequados ao perfil persistente, autorização dos coletores, base HTTP(S) e armazenamento de fotos e limites de upload equivalentes. Se a documentação for necessária em integração, habilitá-la de forma explícita no ambiente e verificar o carregamento do YAML. |
| Transações reais | Os contratos delimitam cadastro, criação e cancelamento do descarte, processamento IoT e resgate. O código atual usa sincronização e mapas. | Definir as transações nos adaptadores ou serviços de aplicação, com propagação que mantenha as operações dependentes no mesmo commit. Verificar locks, rollback e mapeamento de erros com PostgreSQL. |

A configuração atual pode ser conferida nos arquivos de perfil e na cadeia de segurança. [Spring Boot e inicialização do banco](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html) ·
[Perfil postgres](../../src/main/resources/application-postgres.yml) ·
[Perfil bootstrap](../../src/main/resources/application-bootstrap.yml) ·
[Segurança atual](../../src/main/java/br/com/fiap/reloop/security/SecurityConfiguration.java)

## Dependências do Hiago e ordem recomendada

1. Receber o DDL versionado e a instrução para aplicá-lo do zero, com versão
   do PostgreSQL, extensões se usadas, schema, papéis e ordem dos scripts.
   Usar o DER ou a documentação equivalente para esclarecer relações.
2. Receber os dados de referência ou instruções de seed para pontos,
   categorias, coletores, vínculos de aceitação e benefícios. Conferir
   nomes e tipos físicos, IDs, escalas, enums, timestamps, FKs, índices
   e constraints. Alinhar especialmente a reserva do coletor, a
   normalização do e-mail, a titularidade e as chaves de idempotência.
3. Implementar usuário e carteira inicial, catálogo e dispositivos.
   Configurar segurança e a seleção dos adaptadores por perfil. Testar
   cadastro concorrente e consultas do catálogo.
4. Implementar metadados e armazenamento de fotos e os adaptadores de
   descarte e IoT. Validar a reserva de sessão, o cancelamento, as
   transições e o crédito com rollback. Alinhar a estratégia de
   armazenamento dos bytes com a equipe.
5. Implementar extrato, catálogo de recompensas e resgate. Validar
   concorrência e histórico. Usar `queries.sql`, se disponível, para
   confrontar consultas e índices do esquema aprovado.

Na revisão do DDL, registrar os nomes das constraints de e-mail, foto,
sessão ativa, evento e resgate que o adaptador usará para distinguir
conflitos de negócio. Se o esquema implementar algum efeito por trigger ou
função, alinhar a responsabilidade de escrita antes de adicionar a lógica
no adaptador, especialmente na criação da carteira e no crédito IoT.

## Estratégia de verificação para o Bloco 7B

1. Provisionar PostgreSQL descartável na versão indicada. Aplicar o DDL e o
   seed aprovados a um banco vazio. Registrar o commit dos scripts usados.
   Inspecionar tabelas, tipos, nullability, constraints e índices em
   `information_schema` e `pg_catalog`.
2. Iniciar apenas com `postgres`, `ddl-auto=validate` e credenciais de
   integração. Confirmar que o JPA validou os mapeamentos, que os
   adaptadores demonstrativos estão ausentes e que não houve geração de
   DDL. Testar que um schema incompleto impede a inicialização com os
   mapeamentos e que o health check acusa indisponibilidade do banco.
3. Exercitar as 17 operações de negócio e o download de foto via HTTP.
   Cadastrar e autenticar dois usuários. Validar catálogo, titularidade,
   foto e descarte. Processar os três eventos e conferir crédito e extrato.
   Resgatar e conferir estoque, saldo, histórico e cupom. Com dados de
   referência equivalentes aos do bootstrap, validar o cenário de
   25 pontos de crédito e débito de 20 com saldo final de 5. Comparar
   respostas e erros com o OpenAPI.
4. Disparar requisições paralelas usando transações e conexões distintas,
   preferencialmente com duas instâncias da API. Cobrir e-mail duplicado,
   sessão no mesmo coletor, cancelamento contra pesagem, replay e conflito
   IoT, fechamento concorrente, resgate com a mesma chave, saldo disputado e
   última unidade de estoque. Conferir respostas e contagens de linhas.
5. Forçar uma falha antes do commit no cadastro, no fechamento e no resgate
   e verificar que nenhuma alteração parcial fica visível. Reenviar o
   evento ou resgate após rollback. Reiniciar a aplicação e validar que
   dados, fotos, extrato e respostas de operações idempotentes persistem.

## Resultado da auditoria

`./mvnw clean verify` concluiu com `BUILD SUCCESS` usando Java 21. O build
compilou 85 fontes e gerou o JAR. O Maven informou que não havia testes
automatizados para executar. A auditoria não executou PostgreSQL nem
validou um esquema físico, pois os scripts do banco não estavam disponíveis.

Os contratos Java e a configuração JDBC servem de base para o Bloco 7B.
O mapeamento definitivo e a validação do fluxo real aguardam o DDL do Hiago
e o alinhamento das garantias de integridade e concorrência listadas acima.
