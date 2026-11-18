-- ReLoop: esquema PostgreSQL
-- Baseado em docs/database/schema-contract.md e nos enums do openapi.yaml.
-- Rode em um banco vazio: psql -d reloop -f database.sql
--
-- Partes do arquivo:
--   1. CREATE TABLE com PK, FK, constraints e índices
--   2. INSERTs de carga inicial
--   3. Consultas de uso (SELECT com JOIN e agregação)

-- Os horários dos dados e das consultas usam o fuso de Campo Grande (MS).
SET TIME ZONE 'America/Campo_Grande';

BEGIN;

-- #####################################################################
-- PARTE 1: CREATE TABLE
-- #####################################################################

-- =====================================================================
-- Identidade e catálogo
-- =====================================================================

CREATE TABLE users (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          varchar(120) NOT NULL CHECK (btrim(name) <> ''),
    email         varchar(254) NOT NULL CHECK (btrim(email) <> ''),
    password_hash text         NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

CREATE TABLE collection_points (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name      text             NOT NULL,
    address   text             NOT NULL,
    latitude  double precision NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude double precision NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    is_active boolean          NOT NULL DEFAULT true
);

CREATE TABLE collection_devices (
    device_id           varchar(64) PRIMARY KEY,
    collection_point_id bigint      NOT NULL REFERENCES collection_points (id) ON DELETE RESTRICT,
    is_active           boolean     NOT NULL DEFAULT true
);

CREATE INDEX ix_collection_devices_point ON collection_devices (collection_point_id);

CREATE TABLE electronic_categories (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          text          NOT NULL UNIQUE,
    base_points   integer       NOT NULL CHECK (base_points >= 0),
    points_per_kg integer       NOT NULL CHECK (points_per_kg >= 0),
    max_points    integer       NOT NULL CHECK (max_points >= 0),
    min_weight_kg numeric(10,3) NOT NULL,
    max_weight_kg numeric(10,3) NOT NULL,
    CHECK (0 < min_weight_kg AND min_weight_kg <= max_weight_kg)
);

CREATE TABLE collection_point_categories (
    collection_point_id bigint NOT NULL REFERENCES collection_points (id) ON DELETE RESTRICT,
    category_id         bigint NOT NULL REFERENCES electronic_categories (id) ON DELETE RESTRICT,
    PRIMARY KEY (collection_point_id, category_id)
);

-- =====================================================================
-- Fotos, descartes e IoT
-- =====================================================================

CREATE TABLE uploaded_files (
    id           uuid        PRIMARY KEY,
    user_id      bigint      NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    storage_key  text        NOT NULL UNIQUE,
    content_type text        NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    size_bytes   bigint      NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, user_id)
);

CREATE TABLE disposals (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          bigint        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    device_id        varchar(64)   NOT NULL REFERENCES collection_devices (device_id) ON DELETE RESTRICT,
    category_id      bigint        NOT NULL REFERENCES electronic_categories (id) ON DELETE RESTRICT,
    item_name        varchar(120)  NOT NULL,
    photo_id         uuid          NOT NULL UNIQUE,
    status           varchar(24)   NOT NULL DEFAULT 'WAITING_WEIGHT',
    weight_kg        numeric(10,3),
    estimated_points integer,
    awarded_points   integer       NOT NULL DEFAULT 0,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    completed_at     timestamptz,
    UNIQUE (id, device_id),
    FOREIGN KEY (photo_id, user_id) REFERENCES uploaded_files (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_disposals_status CHECK (status IN (
        'WAITING_WEIGHT', 'WAITING_DEPOSIT', 'DEPOSIT_IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_disposals_weight_by_status CHECK (
        (status IN ('WAITING_WEIGHT', 'CANCELLED')
            AND weight_kg IS NULL AND estimated_points IS NULL)
        OR
        (status IN ('WAITING_DEPOSIT', 'DEPOSIT_IN_PROGRESS', 'COMPLETED')
            AND weight_kg IS NOT NULL AND weight_kg > 0
            AND estimated_points IS NOT NULL AND estimated_points >= 0)),
    CONSTRAINT ck_disposals_awarded_points CHECK (
        (status <> 'COMPLETED' AND awarded_points = 0)
        OR (status = 'COMPLETED' AND awarded_points = estimated_points)),
    CONSTRAINT ck_disposals_completed_at CHECK (
        (status = 'COMPLETED') = (completed_at IS NOT NULL))
);

-- Um coletor só pode ter uma sessão ativa por vez.
CREATE UNIQUE INDEX ux_disposals_active_device ON disposals (device_id)
    WHERE status IN ('WAITING_WEIGHT', 'WAITING_DEPOSIT', 'DEPOSIT_IN_PROGRESS');

CREATE INDEX ix_disposals_user_created ON disposals (user_id, created_at DESC);
CREATE INDEX ix_disposals_category ON disposals (category_id);

CREATE TABLE iot_events (
    event_id         varchar(128)  PRIMARY KEY,
    device_id        varchar(64)   NOT NULL,
    disposal_id      bigint        NOT NULL,
    event_type       varchar(24)   NOT NULL CHECK (event_type IN ('WEIGHT_RECORDED', 'DOOR_OPENED', 'DOOR_CLOSED')),
    weight_kg        numeric(10,3),
    response_payload jsonb         NOT NULL,
    processed_at     timestamptz   NOT NULL DEFAULT now(),
    FOREIGN KEY (disposal_id, device_id) REFERENCES disposals (id, device_id) ON DELETE RESTRICT,
    CONSTRAINT ck_iot_events_weight CHECK (
        (event_type = 'WEIGHT_RECORDED' AND weight_kg IS NOT NULL AND weight_kg > 0)
        OR (event_type IN ('DOOR_OPENED', 'DOOR_CLOSED') AND weight_kg IS NULL))
);

CREATE INDEX ix_iot_events_disposal ON iot_events (disposal_id, device_id);

-- =====================================================================
-- Carteira, benefícios e cupons
-- =====================================================================

CREATE TABLE wallets (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          bigint  NOT NULL UNIQUE REFERENCES users (id) ON DELETE RESTRICT,
    available_points integer NOT NULL DEFAULT 0 CHECK (available_points >= 0),
    total_earned     integer NOT NULL DEFAULT 0 CHECK (total_earned >= 0),
    total_redeemed   integer NOT NULL DEFAULT 0 CHECK (total_redeemed >= 0),
    CHECK (available_points = total_earned - total_redeemed)
);

CREATE TABLE rewards (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name               text    NOT NULL,
    description        text    NOT NULL,
    partner_name       text    NOT NULL,
    cost_points        integer NOT NULL CHECK (cost_points > 0),
    available_quantity integer NOT NULL CHECK (available_quantity >= 0),
    active             boolean NOT NULL DEFAULT true
);

CREATE TABLE redemptions (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          bigint       NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    reward_id        bigint       NOT NULL REFERENCES rewards (id) ON DELETE RESTRICT,
    reward_name      text         NOT NULL,
    points_spent     integer      NOT NULL CHECK (points_spent > 0),
    remaining_points integer      NOT NULL CHECK (remaining_points >= 0),
    coupon_code      text         NOT NULL UNIQUE,
    idempotency_key  varchar(128) NOT NULL,
    redeemed_at      timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (user_id, idempotency_key)
);

CREATE INDEX ix_redemptions_user_redeemed ON redemptions (user_id, redeemed_at DESC);
CREATE INDEX ix_redemptions_reward ON redemptions (reward_id);

CREATE TABLE point_transactions (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wallet_id     bigint      NOT NULL REFERENCES wallets (id) ON DELETE RESTRICT,
    type          varchar(6)  NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    points        integer     NOT NULL CHECK (points > 0),
    description   text        NOT NULL,
    disposal_id   bigint      REFERENCES disposals (id) ON DELETE RESTRICT,
    redemption_id bigint      REFERENCES redemptions (id) ON DELETE RESTRICT,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_point_transactions_origin CHECK (
        (type = 'CREDIT' AND disposal_id IS NOT NULL AND redemption_id IS NULL)
        OR (type = 'DEBIT' AND redemption_id IS NOT NULL AND disposal_id IS NULL))
);

CREATE UNIQUE INDEX ux_point_transactions_disposal ON point_transactions (disposal_id)
    WHERE disposal_id IS NOT NULL;
CREATE UNIQUE INDEX ux_point_transactions_redemption ON point_transactions (redemption_id)
    WHERE redemption_id IS NOT NULL;
CREATE INDEX ix_point_transactions_wallet_created ON point_transactions (wallet_id, created_at DESC);

-- #####################################################################
-- PARTE 2: CARGA INICIAL
-- Os ids são gerados em ordem (1, 2, 3...) porque o banco começa vazio.
-- Pontos de um descarte = min(max_points, arredondar(peso * points_per_kg + base_points)).
-- #####################################################################

INSERT INTO users (name, email, password_hash, created_at, updated_at) VALUES
    ('Ana Souza',     'ana.souza@email.com',     '{pbkdf2}5f4dcc3b5aa765d61d8327deb882cf99', '2026-10-01 09:00-04', '2026-10-01 09:00-04'),
    ('Bruno Lima',    'bruno.lima@email.com',    '{pbkdf2}6cb75f652a9b52798eb6cf2201057c73', '2026-10-02 14:30-04', '2026-10-02 14:30-04'),
    ('Carla Mendes',  'carla.mendes@email.com',  '{pbkdf2}819b0643d6b89dc9b579fdfc9094f28e', '2026-10-05 18:10-04', '2026-10-05 18:10-04'),
    ('Diego Rocha',   'diego.rocha@email.com',   '{pbkdf2}34cc93ece0ba9e3f6f235d4af979b16c', '2026-10-10 08:45-04', '2026-10-10 08:45-04'),
    ('Elisa Martins', 'elisa.martins@email.com', '{pbkdf2}db0edd04aaac4506f7edab03ac855d56', '2026-11-01 11:20-04', '2026-11-01 11:20-04');

INSERT INTO collection_points (name, address, latitude, longitude, is_active) VALUES
    ('Ecoponto Centro',       'Rua 14 de Julho, 1200 - Centro',          -20.4628, -54.6152, true),
    ('Ecoponto Bairro Norte', 'Av. Mato Grosso, 3500 - Vila Planalto',   -20.4389, -54.6071, false),
    ('Ecoponto Bairro Sul',   'Av. Gury Marques, 2100 - Universitário',  -20.5012, -54.6198, true),
    ('Ecoponto Shopping',     'Av. Afonso Pena, 4909 - Santa Fé',        -20.4532, -54.5726, true);

INSERT INTO collection_devices (device_id, collection_point_id, is_active) VALUES
    ('COL-001', 1, true),
    ('COL-002', 1, true),
    ('COL-003', 2, false),
    ('COL-004', 3, true),
    ('COL-005', 4, true);

INSERT INTO electronic_categories (name, base_points, points_per_kg, max_points, min_weight_kg, max_weight_kg) VALUES
    ('Celulares',             30,  5,  40, 0.050,  1.000),
    ('Notebooks',             60, 10, 100, 0.800,  5.000),
    ('Baterias e pilhas',     10, 20,  50, 0.050,  3.000),
    ('Monitores',             50,  8, 120, 2.000, 15.000),
    ('Cabos e carregadores',   5, 10,  20, 0.050,  2.000);

INSERT INTO collection_point_categories (collection_point_id, category_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 5),
    (2, 1), (2, 3),
    (3, 1), (3, 2), (3, 4),
    (4, 1), (4, 3), (4, 5);

INSERT INTO uploaded_files (id, user_id, storage_key, content_type, size_bytes, created_at) VALUES
    ('a0000000-0000-4000-8000-000000000001', 1, 'photos/1/moto-g7.jpg',          'image/jpeg', 1843200, '2026-10-03 10:00-04'),
    ('a0000000-0000-4000-8000-000000000002', 1, 'photos/1/dell-inspiron.jpg',    'image/jpeg', 2457600, '2026-10-12 16:20-04'),
    ('a0000000-0000-4000-8000-000000000003', 2, 'photos/2/monitor-lg.png',       'image/png',  3145728, '2026-10-08 09:15-04'),
    ('a0000000-0000-4000-8000-000000000004', 2, 'photos/2/carregador.jpg',       'image/jpeg',  921600, '2026-10-20 19:40-04'),
    ('a0000000-0000-4000-8000-000000000005', 3, 'photos/3/pilhas.jpg',           'image/jpeg', 1228800, '2026-10-15 12:05-04'),
    ('a0000000-0000-4000-8000-000000000006', 3, 'photos/3/macbook.png',          'image/png',  4194304, '2026-10-28 17:30-04'),
    ('a0000000-0000-4000-8000-000000000007', 4, 'photos/4/bateria-notebook.jpg', 'image/jpeg', 1536000, '2026-11-02 08:50-04'),
    ('a0000000-0000-4000-8000-000000000008', 4, 'photos/4/iphone-8.jpg',         'image/jpeg', 2048000, '2026-11-17 10:10-04'),
    ('a0000000-0000-4000-8000-000000000009', 5, 'photos/5/samsung-a10.jpg',      'image/jpeg', 1740800, '2026-11-17 10:25-04'),
    ('a0000000-0000-4000-8000-000000000010', 1, 'photos/1/monitor-samsung.png',  'image/png',  2867200, '2026-11-17 10:40-04');

-- Descartes 1 a 6 terminaram. O 7 foi cancelado. Os 8, 9 e 10 estão em andamento,
-- cada um em um coletor diferente (um coletor só aceita uma sessão ativa).
INSERT INTO disposals (user_id, device_id, category_id, item_name, photo_id, status, weight_kg, estimated_points, awarded_points, created_at, updated_at, completed_at) VALUES
    (1, 'COL-001', 1, 'Motorola Moto G7',       'a0000000-0000-4000-8000-000000000001', 'COMPLETED',           0.180, 31, 31, '2026-10-03 10:01-04', '2026-10-03 10:06-04', '2026-10-03 10:06-04'),
    (1, 'COL-001', 2, 'Notebook Dell Inspiron', 'a0000000-0000-4000-8000-000000000002', 'COMPLETED',           2.300, 83, 83, '2026-10-12 16:21-04', '2026-10-12 16:27-04', '2026-10-12 16:27-04'),
    (2, 'COL-004', 4, 'Monitor LG 24"',         'a0000000-0000-4000-8000-000000000003', 'COMPLETED',           3.500, 78, 78, '2026-10-08 09:16-04', '2026-10-08 09:22-04', '2026-10-08 09:22-04'),
    (2, 'COL-002', 5, 'Carregador USB',         'a0000000-0000-4000-8000-000000000004', 'COMPLETED',           0.250,  8,  8, '2026-10-20 19:41-04', '2026-10-20 19:44-04', '2026-10-20 19:44-04'),
    (3, 'COL-005', 3, 'Pilhas AA (pacote)',     'a0000000-0000-4000-8000-000000000005', 'COMPLETED',           0.400, 18, 18, '2026-10-15 12:06-04', '2026-10-15 12:10-04', '2026-10-15 12:10-04'),
    (3, 'COL-004', 2, 'MacBook Air',            'a0000000-0000-4000-8000-000000000006', 'COMPLETED',           1.300, 73, 73, '2026-10-28 17:31-04', '2026-10-28 17:37-04', '2026-10-28 17:37-04'),
    (4, 'COL-001', 3, 'Bateria de notebook',    'a0000000-0000-4000-8000-000000000007', 'CANCELLED',            NULL, NULL, 0, '2026-11-02 08:51-04', '2026-11-02 08:58-04', NULL),
    (4, 'COL-005', 1, 'iPhone 8',               'a0000000-0000-4000-8000-000000000008', 'DEPOSIT_IN_PROGRESS', 0.150, 31,  0, '2026-11-17 10:11-04', '2026-11-17 10:14-04', NULL),
    (5, 'COL-002', 1, 'Samsung Galaxy A10',     'a0000000-0000-4000-8000-000000000009', 'WAITING_WEIGHT',       NULL, NULL, 0, '2026-11-17 10:26-04', '2026-11-17 10:26-04', NULL),
    (1, 'COL-004', 4, 'Monitor Samsung 19"',    'a0000000-0000-4000-8000-000000000010', 'WAITING_DEPOSIT',     2.800, 72,  0, '2026-11-17 10:41-04', '2026-11-17 10:43-04', NULL);

INSERT INTO iot_events (event_id, device_id, disposal_id, event_type, weight_kg, response_payload, processed_at) VALUES
    ('evt-0001', 'COL-001', 1, 'WEIGHT_RECORDED', 0.180, '{"disposalId": 1, "status": "WAITING_DEPOSIT", "estimatedPoints": 31}', '2026-10-03 10:03-04'),
    ('evt-0002', 'COL-001', 1, 'DOOR_OPENED',     NULL,  '{"disposalId": 1, "status": "DEPOSIT_IN_PROGRESS"}',             '2026-10-03 10:04-04'),
    ('evt-0003', 'COL-001', 1, 'DOOR_CLOSED',     NULL,  '{"disposalId": 1, "status": "COMPLETED", "awardedPoints": 31}',  '2026-10-03 10:06-04'),
    ('evt-0004', 'COL-001', 2, 'WEIGHT_RECORDED', 2.300, '{"disposalId": 2, "status": "WAITING_DEPOSIT", "estimatedPoints": 83}', '2026-10-12 16:23-04'),
    ('evt-0005', 'COL-001', 2, 'DOOR_OPENED',     NULL,  '{"disposalId": 2, "status": "DEPOSIT_IN_PROGRESS"}',             '2026-10-12 16:25-04'),
    ('evt-0006', 'COL-001', 2, 'DOOR_CLOSED',     NULL,  '{"disposalId": 2, "status": "COMPLETED", "awardedPoints": 83}',  '2026-10-12 16:27-04'),
    ('evt-0007', 'COL-004', 3, 'WEIGHT_RECORDED', 3.500, '{"disposalId": 3, "status": "WAITING_DEPOSIT", "estimatedPoints": 78}', '2026-10-08 09:18-04'),
    ('evt-0008', 'COL-004', 3, 'DOOR_OPENED',     NULL,  '{"disposalId": 3, "status": "DEPOSIT_IN_PROGRESS"}',             '2026-10-08 09:20-04'),
    ('evt-0009', 'COL-004', 3, 'DOOR_CLOSED',     NULL,  '{"disposalId": 3, "status": "COMPLETED", "awardedPoints": 78}',  '2026-10-08 09:22-04'),
    ('evt-0010', 'COL-002', 4, 'WEIGHT_RECORDED', 0.250, '{"disposalId": 4, "status": "WAITING_DEPOSIT", "estimatedPoints": 8}',  '2026-10-20 19:42-04'),
    ('evt-0011', 'COL-002', 4, 'DOOR_OPENED',     NULL,  '{"disposalId": 4, "status": "DEPOSIT_IN_PROGRESS"}',             '2026-10-20 19:43-04'),
    ('evt-0012', 'COL-002', 4, 'DOOR_CLOSED',     NULL,  '{"disposalId": 4, "status": "COMPLETED", "awardedPoints": 8}',   '2026-10-20 19:44-04'),
    ('evt-0013', 'COL-005', 5, 'WEIGHT_RECORDED', 0.400, '{"disposalId": 5, "status": "WAITING_DEPOSIT", "estimatedPoints": 18}', '2026-10-15 12:07-04'),
    ('evt-0014', 'COL-005', 5, 'DOOR_OPENED',     NULL,  '{"disposalId": 5, "status": "DEPOSIT_IN_PROGRESS"}',             '2026-10-15 12:08-04'),
    ('evt-0015', 'COL-005', 5, 'DOOR_CLOSED',     NULL,  '{"disposalId": 5, "status": "COMPLETED", "awardedPoints": 18}',  '2026-10-15 12:10-04'),
    ('evt-0016', 'COL-004', 6, 'WEIGHT_RECORDED', 1.300, '{"disposalId": 6, "status": "WAITING_DEPOSIT", "estimatedPoints": 73}', '2026-10-28 17:33-04'),
    ('evt-0017', 'COL-004', 6, 'DOOR_OPENED',     NULL,  '{"disposalId": 6, "status": "DEPOSIT_IN_PROGRESS"}',             '2026-10-28 17:35-04'),
    ('evt-0018', 'COL-004', 6, 'DOOR_CLOSED',     NULL,  '{"disposalId": 6, "status": "COMPLETED", "awardedPoints": 73}',  '2026-10-28 17:37-04'),
    ('evt-0019', 'COL-005', 8, 'WEIGHT_RECORDED', 0.150, '{"disposalId": 8, "status": "WAITING_DEPOSIT", "estimatedPoints": 31}', '2026-11-17 10:12-04'),
    ('evt-0020', 'COL-005', 8, 'DOOR_OPENED',     NULL,  '{"disposalId": 8, "status": "DEPOSIT_IN_PROGRESS"}',             '2026-11-17 10:14-04'),
    ('evt-0021', 'COL-004', 10, 'WEIGHT_RECORDED', 2.800, '{"disposalId": 10, "status": "WAITING_DEPOSIT", "estimatedPoints": 72}', '2026-11-17 10:43-04');

INSERT INTO rewards (name, description, partner_name, cost_points, available_quantity, active) VALUES
    ('Cupom de frete grátis',     'Frete grátis em uma compra na loja parceira',  'Loja Verde',   50, 98,  true),
    ('Desconto de 10%',           '10% de desconto em eletrônicos selecionados',  'TechShop',     80, 29,  true),
    ('Ingresso de cinema',        'Um ingresso 2D de segunda a quinta',           'CineMax',      60,  0,  true),
    ('Muda de árvore nativa',     'Uma muda de ipê para plantar',                 'Viveiro Vida', 20, 199, true),
    ('Cupom de campanha antiga',  'Benefício de uma campanha já encerrada',       'Loja Verde',   40, 10,  false);

INSERT INTO redemptions (user_id, reward_id, reward_name, points_spent, remaining_points, coupon_code, idempotency_key, redeemed_at) VALUES
    (1, 2, 'Desconto de 10%',       80, 34, 'TECH-7F3K-92QA', 'req-ana-0001',   '2026-10-14 20:00-04'),
    (2, 1, 'Cupom de frete grátis', 50, 36, 'VERDE-2M8P-41LD', 'req-bruno-0001', '2026-10-22 13:15-04'),
    (3, 4, 'Muda de árvore nativa', 20, 71, 'VIDA-9X1C-55TR', 'req-carla-0001', '2026-10-29 09:30-04'),
    (3, 1, 'Cupom de frete grátis', 50, 21, 'VERDE-4H6N-08ZB', 'req-carla-0002', '2026-11-05 18:45-04');

-- Saldo = total ganho - total resgatado.
INSERT INTO wallets (user_id, available_points, total_earned, total_redeemed) VALUES
    (1, 34, 114, 80),
    (2, 36,  86, 50),
    (3, 21,  91, 70),
    (4,  0,   0,  0),
    (5,  0,   0,  0);

INSERT INTO point_transactions (wallet_id, type, points, description, disposal_id, redemption_id, created_at) VALUES
    (1, 'CREDIT', 31, 'Descarte: Motorola Moto G7',       1, NULL, '2026-10-03 10:06-04'),
    (1, 'CREDIT', 83, 'Descarte: Notebook Dell Inspiron', 2, NULL, '2026-10-12 16:27-04'),
    (1, 'DEBIT',  80, 'Resgate: Desconto de 10%',         NULL, 1, '2026-10-14 20:00-04'),
    (2, 'CREDIT', 78, 'Descarte: Monitor LG 24"',         3, NULL, '2026-10-08 09:22-04'),
    (2, 'CREDIT',  8, 'Descarte: Carregador USB',         4, NULL, '2026-10-20 19:44-04'),
    (2, 'DEBIT',  50, 'Resgate: Cupom de frete grátis',   NULL, 2, '2026-10-22 13:15-04'),
    (3, 'CREDIT', 18, 'Descarte: Pilhas AA (pacote)',     5, NULL, '2026-10-15 12:10-04'),
    (3, 'CREDIT', 73, 'Descarte: MacBook Air',            6, NULL, '2026-10-28 17:37-04'),
    (3, 'DEBIT',  20, 'Resgate: Muda de árvore nativa',   NULL, 3, '2026-10-29 09:30-04'),
    (3, 'DEBIT',  50, 'Resgate: Cupom de frete grátis',   NULL, 4, '2026-11-05 18:45-04');

COMMIT;

-- #####################################################################
-- PARTE 3: CONSULTAS DE USO
-- #####################################################################

-- 1. Tela "Mapa": pontos de coleta ativos, com as categorias aceitas
--    e quantos coletores ativos cada um tem.
SELECT cp.name                                   AS ponto,
       cp.address                                AS endereco,
       string_agg(DISTINCT ec.name, ', ')        AS categorias_aceitas,
       COUNT(DISTINCT cd.device_id)
           FILTER (WHERE cd.is_active)           AS coletores_ativos
FROM collection_points cp
LEFT JOIN collection_point_categories cpc ON cpc.collection_point_id = cp.id
LEFT JOIN electronic_categories ec        ON ec.id = cpc.category_id
LEFT JOIN collection_devices cd           ON cd.collection_point_id = cp.id
WHERE cp.is_active
GROUP BY cp.id, cp.name, cp.address
ORDER BY cp.name;

-- 2. Ranking de reciclagem: usuários que mais descartaram,
--    com total de itens, peso e pontos ganhos.
SELECT u.name                         AS usuario,
       COUNT(d.id)                    AS itens_descartados,
       SUM(d.weight_kg)               AS total_kg,
       SUM(d.awarded_points)          AS pontos_ganhos
FROM users u
JOIN disposals d ON d.user_id = u.id
WHERE d.status = 'COMPLETED'
GROUP BY u.id, u.name
ORDER BY pontos_ganhos DESC;

-- 3. Tela "Extrato": movimentações da carteira da Ana,
--    com a origem de cada uma e o saldo depois de cada linha.
SELECT pt.created_at                         AS data,
       pt.type                               AS tipo,
       COALESCE(d.item_name, r.reward_name)  AS origem,
       pt.points                             AS pontos,
       SUM(CASE WHEN pt.type = 'CREDIT' THEN pt.points ELSE -pt.points END)
           OVER (ORDER BY pt.created_at)     AS saldo_apos
FROM point_transactions pt
JOIN wallets w           ON w.id = pt.wallet_id
JOIN users u             ON u.id = w.user_id
LEFT JOIN disposals d    ON d.id = pt.disposal_id
LEFT JOIN redemptions r  ON r.id = pt.redemption_id
WHERE u.email = 'ana.souza@email.com'
ORDER BY pt.created_at;

-- 4. Impacto por categoria: quanto foi reciclado de cada tipo de eletrônico.
SELECT ec.name                              AS categoria,
       COUNT(d.id)                          AS descartes,
       COALESCE(SUM(d.weight_kg), 0)        AS total_kg,
       ROUND(AVG(d.awarded_points), 1)      AS media_pontos
FROM electronic_categories ec
LEFT JOIN disposals d ON d.category_id = ec.id AND d.status = 'COMPLETED'
GROUP BY ec.id, ec.name
ORDER BY total_kg DESC;

-- 5. Painel do administrador: movimento de cada ponto de coleta por status.
SELECT cp.name                                                    AS ponto,
       COUNT(d.id)                                                AS total,
       COUNT(d.id) FILTER (WHERE d.status = 'COMPLETED')          AS concluidos,
       COUNT(d.id) FILTER (WHERE d.status = 'CANCELLED')          AS cancelados,
       COUNT(d.id) FILTER (WHERE d.status IN
           ('WAITING_WEIGHT', 'WAITING_DEPOSIT', 'DEPOSIT_IN_PROGRESS')) AS em_andamento
FROM collection_points cp
LEFT JOIN collection_devices cd ON cd.collection_point_id = cp.id
LEFT JOIN disposals d           ON d.device_id = cd.device_id
GROUP BY cp.id, cp.name
ORDER BY total DESC;

-- 6. Benefícios mais resgatados, com estoque e pontos gastos.
SELECT rw.name                              AS beneficio,
       rw.partner_name                      AS parceiro,
       rw.available_quantity                AS estoque,
       COUNT(rd.id)                         AS resgates,
       COALESCE(SUM(rd.points_spent), 0)    AS pontos_gastos
FROM rewards rw
LEFT JOIN redemptions rd ON rd.reward_id = rw.id
WHERE rw.active
GROUP BY rw.id, rw.name, rw.partner_name, rw.available_quantity
ORDER BY resgates DESC, rw.name;

-- 7. Monitoramento: coletores com sessão ativa agora, quem está usando e há quanto tempo.
SELECT cd.device_id                 AS coletor,
       cp.name                      AS ponto,
       u.name                       AS usuario,
       d.item_name                  AS item,
       d.status,
       now() - d.created_at         AS tempo_aberto
FROM disposals d
JOIN collection_devices cd ON cd.device_id = d.device_id
JOIN collection_points cp  ON cp.id = cd.collection_point_id
JOIN users u               ON u.id = d.user_id
WHERE d.status IN ('WAITING_WEIGHT', 'WAITING_DEPOSIT', 'DEPOSIT_IN_PROGRESS')
ORDER BY d.created_at;

-- 8. Auditoria: confere se o saldo de cada carteira bate com o extrato.
SELECT u.name                                                          AS usuario,
       w.available_points                                              AS saldo_carteira,
       COALESCE(SUM(CASE WHEN pt.type = 'CREDIT' THEN pt.points
                         ELSE -pt.points END), 0)                      AS saldo_extrato,
       w.available_points = COALESCE(SUM(CASE WHEN pt.type = 'CREDIT' THEN pt.points
                                              ELSE -pt.points END), 0) AS confere
FROM wallets w
JOIN users u                    ON u.id = w.user_id
LEFT JOIN point_transactions pt ON pt.wallet_id = w.id
GROUP BY u.id, u.name, w.available_points
ORDER BY u.name;
