ALTER TABLE scan_task_outbox
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
