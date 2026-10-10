-- Fix audit_log.created_at timezone issue
-- Background: TIMESTAMP (without timezone) causes 8-hour offset when JVM timezone != UTC
-- Solution: Upgrade to TIMESTAMPTZ and anchor existing data as UTC
-- Related: docs/15-backend-time-governance-plan.md section 3.1
--
-- Operational notes:
--   * ALTER COLUMN ... TYPE rewrites the entire audit_log table and rebuilds
--     idx_audit_log_created_at, idx_audit_log_actor_time, idx_audit_log_action_time
--     under ACCESS EXCLUSIVE lock. Run during a low-traffic window.
--   * Before applying in production, check table size:
--         SELECT pg_size_pretty(pg_total_relation_size('audit_log'));
--     Tables in the multi-GB range may need a maintenance window.
--   * SET LOCAL lock_timeout below makes a contended ALTER fail fast (rather than
--     queueing behind long-running readers); operators may re-run the migration
--     after clearing contention. The DO block guards against re-running on a
--     column that has already been migrated, so retries are safe.

SET LOCAL lock_timeout = '30s';

DO $$
DECLARE
    current_type text;
BEGIN
    SELECT data_type
      INTO current_type
      FROM information_schema.columns
     WHERE table_schema = current_schema()
       AND table_name = 'audit_log'
       AND column_name = 'created_at';

    IF current_type = 'timestamp without time zone' THEN
        ALTER TABLE audit_log
          ALTER COLUMN created_at TYPE TIMESTAMPTZ
            USING created_at AT TIME ZONE 'UTC';
        RAISE NOTICE 'V42: audit_log.created_at -> TIMESTAMPTZ (UTC anchored)';
    ELSE
        RAISE NOTICE 'V42: audit_log.created_at already % (skipped)', current_type;
    END IF;
END $$;
