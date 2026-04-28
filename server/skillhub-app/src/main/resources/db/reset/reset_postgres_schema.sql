-- Destructive reset helper for legacy PostgreSQL databases.
-- Use only when you explicitly want to delete all existing application data
-- and rebuild the schema from the consolidated Flyway initialization script.

BEGIN;

DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;

GRANT ALL ON SCHEMA public TO CURRENT_USER;
GRANT ALL ON SCHEMA public TO public;

COMMIT;

-- After executing this file, restart the application with Flyway enabled.
-- Flyway will recreate the schema from:
--   classpath:db/migration/V1__init_schema.sql

