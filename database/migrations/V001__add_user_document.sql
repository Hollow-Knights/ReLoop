-- Apply once after Hiago's original schema, before starting the updated postgres profile.
-- Existing users intentionally retain NULL. No backfill, DDL generation or seed changes.
-- Use psql -X -v ON_ERROR_STOP=1 -f this-file.sql. A failed/repeated application rolls back.
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('reloop-schema-migrations', 0));
ALTER TABLE users ADD COLUMN document varchar(11);
ALTER TABLE users ADD CONSTRAINT ck_users_document_format
    CHECK (document IS NULL OR (document ~ '^[0-9]{11}$' AND document !~ '^([0-9])\1{10}$'));
CREATE UNIQUE INDEX ux_users_document ON users (document) WHERE document IS NOT NULL;
COMMIT;
