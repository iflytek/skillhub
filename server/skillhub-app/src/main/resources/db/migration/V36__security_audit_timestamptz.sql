-- Align security_audit timestamp columns with project convention (TIMESTAMPTZ)
-- Matches pattern established in V16–V26 for all other tables

ALTER TABLE security_audit
    ALTER COLUMN scanned_at TYPE TIMESTAMPTZ USING scanned_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';
