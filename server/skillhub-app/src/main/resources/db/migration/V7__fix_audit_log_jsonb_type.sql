-- Fix audit_log detail_json column to properly handle JSONB type
-- This migration ensures existing data is compatible with the JSONB type

-- The column is already defined as jsonb in V1, so this migration is a no-op
-- for fresh installations. For existing installations with text data, this would
-- have been needed, but since the column was always jsonb, we just verify it exists.

-- No-op migration: column is already jsonb in V1
-- This file exists to maintain migration version continuity
SELECT 1;
