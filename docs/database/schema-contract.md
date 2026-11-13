# Proposta de esquema PostgreSQL

Esta proposta orienta o alinhamento entre a equipe de backend e a equipe de
banco. Os tipos e constraints devem ser revisados antes da criação dos
scripts SQL. Com as entidades mapeadas, o perfil `postgres` usará
`ddl-auto=validate` para conferir o mapeamento do Hibernate. As constraints
de negócio precisam ser revisadas nos scripts do banco.

Use nomes em `snake_case`. A proposta usa `bigint` gerado por identidade
para as PKs numéricas, `uuid` para fotos, `integer` para pontos e
`timestamptz` para instantes. Pesos usam `numeric` e são lidos como
`BigDecimal`. Adote a precisão de peso em conjunto com o simulador antes de
fixar escala e arredondamento de armazenamento. Considere todos os campos
obrigatórios, exceto os explicitamente anuláveis. As referências entre
tabelas históricas devem impedir a remoção física de seus registros
referenciados.

## Identidade e catálogo

| Tabela | Campos e tipos sugeridos | Chaves e constraints |
| --- | --- | --- |
| `users` | `id bigint`, `name varchar(120)`, `email varchar(254)`, `password_hash text`, `created_at timestamptz`, `updated_at timestamptz` | PK `id`. E-mail único após normalização sem distinção de caixa. Nome e e-mail não vazios. Senhas são persistidas somente como hash. |
| `collection_points` | `id bigint`, `name text`, `address text`, `latitude double precision`, `longitude double precision`, `is_active boolean` | PK `id`. Latitude entre −90 e 90 e longitude entre −180 e 180. |
| `collection_devices` | `device_id varchar(64)`, `collection_point_id bigint`, `is_active boolean` | PK `device_id`, por exemplo, `COL-001`. FK `collection_point_id → collection_points.id`. A credencial de teste pode ser configurada fora do banco e vinculada ao coletor que autoriza. |
| `electronic_categories` | `id bigint`, `name text`, `base_points integer`, `points_per_kg integer`, `max_points integer`, `min_weight_kg numeric`, `max_weight_kg numeric` | PK `id`. Nome único. Pontos não negativos. Limites com `0 < min_weight_kg <= max_weight_kg`. |
| `collection_point_categories` | `collection_point_id bigint`, `category_id bigint` | PK composta por `collection_point_id` e `category_id`. FKs para `collection_points.id` e `electronic_categories.id`. |

### Campos consumidos pelo catálogo

| Consulta | Campos usados pelo futuro adaptador PostgreSQL |
| --- | --- |
| Lista de pontos | `collection_points.id`, `name`, `address`, `latitude`, `longitude` e `is_active`. |
| Detalhe do ponto | Os campos da lista, `collection_point_categories.category_id` e `collection_devices.device_id` e `is_active`, associados pelo `collection_point_id`. |
| Lista de categorias | `electronic_categories.id`, `name`, `base_points`, `points_per_kg`, `max_points`, `min_weight_kg` e `max_weight_kg`. |

O adaptador preencherá os modelos de leitura de `collection.model`, com
`BigDecimal` para os pesos. Quando um ponto não tiver categorias aceitas
ou coletores, o detalhe retornará arrays vazios. As interfaces de consulta
continuarão a ser usadas pelos serviços do catálogo.

## Fotos, descartes e IoT

| Tabela | Campos e tipos sugeridos | Chaves e constraints |
| --- | --- | --- |
| `uploaded_files` | `id uuid`, `user_id bigint`, `storage_key text`, `content_type text`, `size_bytes bigint`, `created_at timestamptz` | PK `id`. FK `user_id → users.id`. Unicidade de `storage_key` e de `(id, user_id)` para a referência composta de titularidade. JPEG ou PNG com tamanho de 1 a 10.485.760 bytes. A URL autorizada pode ser gerada a partir da referência de armazenamento. |
| `disposals` | `id bigint`, `user_id bigint`, `device_id varchar(64)`, `category_id bigint`, `item_name varchar(120)`, `photo_id uuid`, `status varchar(24)`, `weight_kg numeric` anulável, `estimated_points integer` anulável, `awarded_points integer`, `created_at timestamptz`, `updated_at timestamptz`, `completed_at timestamptz` anulável | PK `id`. FKs para usuário, coletor e categoria. FK `(photo_id, user_id) → uploaded_files(id, user_id)` e unicidade de `photo_id`. Unicidade de `(id, device_id)` para a referência composta do evento. Estado restrito aos valores do OpenAPI. |
| `iot_events` | `event_id varchar(128)`, `device_id varchar(64)`, `disposal_id bigint`, `event_type varchar(24)`, `weight_kg numeric` anulável, `response_payload jsonb`, `processed_at timestamptz` | PK `event_id`, única globalmente. FK `(disposal_id, device_id) → disposals(id, device_id)`. Tipo restrito aos valores do OpenAPI. Peso positivo na pesagem e nulo para eventos de porta. |

Estados `WAITING_WEIGHT` e `CANCELLED` exigem peso e estimativa nulos.
`WAITING_DEPOSIT`, `DEPOSIT_IN_PROGRESS` e `COMPLETED` exigem peso positivo
e estimativa não negativa. Pontos creditados começam em zero, permanecem
em zero até `COMPLETED` e, nesse estado, são iguais à estimativa. Apenas
`COMPLETED` exige `completed_at`. O serviço valida as transições, o
vínculo do coletor ao local, as categorias aceitas, a faixa de peso e a
atividade do coletor e do local.

Os campos de entrada armazenados em `iot_events` identificam o conteúdo
aceito. `response_payload` guarda a resposta original para repetição.
A operação registra evento, transição e eventual crédito na mesma
transação. Uma tentativa que sofre rollback pode ser reenviada com o mesmo
`eventId`. O serviço compara pesos pelo valor decimal.

O adaptador de `IotEventRepository` deve aplicar a transição sobre a versão
atual do descarte e guardar o resultado original para futuras repetições.
A mesma transação precisa atualizar o descarte, registrar o evento e,
no fechamento com pontos positivos, incluir o crédito único por descarte.
A unicidade do evento, a reserva do coletor e a transição condicional
devem ser alinhadas com Hiago para preservar o comportamento sob
concorrência. No `bootstrap`, o repositório em memória protege as
operações de evento e cancelamento pela mesma sincronização.

### Integração do cadastro e cancelamento

O adaptador de descartes precisará gravar o identificador do usuário,
coletor, categoria e foto, além de nome do item, estado, criação, peso,
estimativa e pontos creditados. O vínculo composto da foto com o usuário
permite conferir a titularidade também na persistência. O `photoId`
identifica a associação. A URL de leitura pode ser gerada a partir da
referência de armazenamento e da configuração do ambiente.

Cadastro, reserva de sessão ativa do coletor e associação exclusiva da
foto precisam ocorrer atomicamente. O cancelamento precisa ser
condicional ao proprietário e ao estado `WAITING_WEIGHT`, preservando a
linha no histórico. Para substituir o repositório em memória, a equipe
deve alinhar como o banco impedirá sessões ativas concorrentes para o
mesmo coletor.

## Carteira, benefícios e cupons

| Tabela | Campos e tipos sugeridos | Chaves e constraints |
| --- | --- | --- |
| `wallets` | `id bigint`, `user_id bigint`, `available_points integer`, `total_earned integer`, `total_redeemed integer` | PK `id`. FK única `user_id → users.id`. Valores iniciam em zero e são não negativos. Saldo igual a total ganho menos total resgatado. |
| `point_transactions` | `id bigint`, `wallet_id bigint`, `type varchar(6)`, `points integer`, `description text`, `disposal_id bigint` anulável, `redemption_id bigint` anulável, `created_at timestamptz` | PK `id`. FKs para carteira, descarte e resgate. `points > 0`. `CREDIT` exige somente a origem descarte e `DEBIT` exige somente a origem resgate. Cada origem tem índice único quando preenchida. |
| `rewards` | `id bigint`, `name text`, `description text`, `partner_name text`, `cost_points integer`, `available_quantity integer`, `active boolean` | PK `id`. Custo positivo, estoque não negativo. |
| `redemptions` | `id bigint`, `user_id bigint`, `reward_id bigint`, `reward_name text`, `points_spent integer`, `remaining_points integer`, `coupon_code text`, `idempotency_key varchar(128)`, `redeemed_at timestamptz` | PK `id`. FKs para usuário e recompensa. Cupom único e par `(user_id, idempotency_key)` único. Pontos gastos positivos e saldo restante não negativo. |

O lançamento e sua carteira devem pertencer ao mesmo usuário da origem.
O serviço garante essa consistência na transação. Um descarte concluído
com estimativa zero não gera movimentação. No resgate, o nome do benefício
e o saldo restante são registrados no momento da emissão, permitindo
reproduzir a resposta após alterações no catálogo ou na carteira.

## Consultas e concorrência

- Planeje índices para FKs usadas em consultas, incluindo coletores por
  ponto, descartes por coletor e eventos por descarte. Para histórico e
  extrato, considere índices compostos por usuário ou carteira e pela data
  de criação ou resgate.
- Controle a concorrência das transições de um descarte e das atualizações
  da carteira e do estoque. Uma opção é bloquear as linhas relevantes
  durante a transação em uma ordem consistente. As constraints de unicidade
  complementam esse controle e impedem registros duplicados.
- Defina em conjunto os pesos mínimo e máximo por categoria, a precisão do
  simulador e os nomes físicos a mapear nas entidades JPA.
