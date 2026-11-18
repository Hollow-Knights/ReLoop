-- Run ONLY in an empty, disposable database initialized with Hiago's original script.
-- Checks intentionally attempt invalid writes. Rows are rolled back; identity sequences may advance.
\set ON_ERROR_STOP on
BEGIN;
CREATE FUNCTION pg_temp.must_reject(statement text, expected_state text, label text) RETURNS text LANGUAGE plpgsql AS $check$
BEGIN
    EXECUTE statement;
    RAISE EXCEPTION 'Expected rejection did not occur: %', label;
EXCEPTION WHEN OTHERS THEN
    IF SQLSTATE <> expected_state THEN RAISE; END IF;
    RETURN label || ': OK (' || SQLSTATE || ')';
END
$check$;

SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name;
SELECT 'users' AS tabela,count(*) FROM users UNION ALL SELECT 'collection_points',count(*) FROM collection_points
UNION ALL SELECT 'collection_devices',count(*) FROM collection_devices UNION ALL SELECT 'electronic_categories',count(*) FROM electronic_categories
UNION ALL SELECT 'collection_point_categories',count(*) FROM collection_point_categories UNION ALL SELECT 'uploaded_files',count(*) FROM uploaded_files
UNION ALL SELECT 'disposals',count(*) FROM disposals UNION ALL SELECT 'iot_events',count(*) FROM iot_events
UNION ALL SELECT 'wallets',count(*) FROM wallets UNION ALL SELECT 'point_transactions',count(*) FROM point_transactions
UNION ALL SELECT 'rewards',count(*) FROM rewards UNION ALL SELECT 'redemptions',count(*) FROM redemptions;

DO $check$
BEGIN
    IF (SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE') <> 12 THEN
        RAISE EXCEPTION 'Expected 12 tables';
    END IF;
    IF EXISTS (SELECT 1 FROM wallets w LEFT JOIN point_transactions p ON p.wallet_id=w.id GROUP BY w.id
               HAVING w.available_points <> coalesce(sum(CASE WHEN p.type='CREDIT' THEN p.points ELSE -p.points END),0)) THEN
        RAISE EXCEPTION 'Seed wallet differs from ledger';
    END IF;
    IF EXISTS (SELECT 1 FROM disposals d JOIN electronic_categories c ON c.id=d.category_id
               WHERE d.weight_kg IS NOT NULL AND d.estimated_points <> least(c.max_points,round(c.base_points+d.weight_kg*c.points_per_kg))) THEN
        RAISE EXCEPTION 'Seed points differ from formula';
    END IF;
END
$check$;

SELECT pg_temp.must_reject($q$INSERT INTO users(name,email,password_hash) VALUES('Audit','ANA.SOUZA@EMAIL.COM','not-a-real-password')$q$,'23505','normalized email');
SELECT pg_temp.must_reject($q$INSERT INTO wallets(user_id) VALUES(1)$q$,'23505','one wallet per user');
SELECT pg_temp.must_reject($q$UPDATE wallets SET available_points=-1,total_earned=79,total_redeemed=80 WHERE user_id=1$q$,'23514','no negative balance');
SELECT pg_temp.must_reject($q$UPDATE wallets SET available_points=35 WHERE user_id=1$q$,'23514','wallet total equation');
SELECT pg_temp.must_reject($q$UPDATE rewards SET available_quantity=-1 WHERE id=1$q$,'23514','no negative stock');
SELECT pg_temp.must_reject($q$UPDATE disposals SET status='UNKNOWN' WHERE id=9$q$,'23514','disposal enum');
SELECT pg_temp.must_reject($q$UPDATE disposals SET status='CANCELLED' WHERE id=8$q$,'23514','cancel only before weighing');
SELECT pg_temp.must_reject($q$UPDATE disposals SET completed_at=NULL WHERE id=1$q$,'23514','completion timestamp');
SELECT pg_temp.must_reject($q$INSERT INTO iot_events(event_id,device_id,disposal_id,event_type,weight_kg,response_payload) VALUES('evt-0001','COL-001',1,'WEIGHT_RECORDED',0.180,'{}')$q$,'23505','global event id');
SELECT pg_temp.must_reject($q$INSERT INTO iot_events(event_id,device_id,disposal_id,event_type,response_payload) VALUES('audit-mismatch','COL-004',1,'DOOR_OPENED','{}')$q$,'23503','event disposal and device');
SELECT pg_temp.must_reject($q$INSERT INTO iot_events(event_id,device_id,disposal_id,event_type,response_payload) VALUES('audit-null-weight','COL-001',1,'WEIGHT_RECORDED','{}')$q$,'23514','required weighing weight');
SELECT pg_temp.must_reject($q$INSERT INTO point_transactions(wallet_id,type,points,description,disposal_id) VALUES(1,'CREDIT',31,'audit',1)$q$,'23505','one credit per disposal');
SELECT pg_temp.must_reject($q$INSERT INTO point_transactions(wallet_id,type,points,description,redemption_id) VALUES(1,'DEBIT',80,'audit',1)$q$,'23505','one debit per redemption');
SELECT pg_temp.must_reject($q$INSERT INTO point_transactions(wallet_id,type,points,description) VALUES(1,'CREDIT',31,'audit')$q$,'23514','required transaction origin');
SELECT pg_temp.must_reject($q$INSERT INTO redemptions(user_id,reward_id,reward_name,points_spent,remaining_points,coupon_code,idempotency_key) VALUES(1,2,'audit',80,0,'audit-unique-coupon','req-ana-0001')$q$,'23505','idempotency per user');
SELECT pg_temp.must_reject($q$INSERT INTO redemptions(user_id,reward_id,reward_name,points_spent,remaining_points,coupon_code,idempotency_key) VALUES(1,2,'audit',80,0,'TECH-7F3K-92QA','audit-unique-key')$q$,'23505','unique coupon');

INSERT INTO uploaded_files(id,user_id,storage_key,content_type,size_bytes) VALUES('90000000-0000-4000-8000-000000000001',1,'audit/new-photo','image/png',1);
SELECT pg_temp.must_reject($q$INSERT INTO disposals(user_id,device_id,category_id,item_name,photo_id) VALUES(2,'COL-001',1,'audit','90000000-0000-4000-8000-000000000001')$q$,'23503','photo owner FK');
SELECT pg_temp.must_reject($q$INSERT INTO disposals(user_id,device_id,category_id,item_name,photo_id) VALUES(1,'COL-005',1,'audit','90000000-0000-4000-8000-000000000001')$q$,'23505','one active collector session');

-- Demonstrates storage rounding. The HTTP layer must reject this precision, as approved.
SELECT 1.0499::numeric(10,3) AS postgres_rounds_to,
       round(60+1.0499*10) AS points_before_storage,
       round(60+1.0499::numeric(10,3)*10) AS points_after_storage;
UPDATE disposals SET status='CANCELLED' WHERE id=9;
ROLLBACK;
