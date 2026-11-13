-- No historical authority can be reconstructed from actor IDs or timestamps.
-- Existing NULL provenance stays unknown and fails closed at PRIVATE confirmation.
ALTER TABLE skill_suite_bundle_operation
    ADD COLUMN initiator_type VARCHAR(16),
    ADD COLUMN token_capabilities JSONB;

ALTER TABLE skill_suite_bundle_operation
    ADD CONSTRAINT ck_suite_bundle_authorization CHECK (
        (initiator_type IS NULL AND token_capabilities IS NULL)
        OR (initiator_type IS NOT NULL AND initiator_type = 'SESSION' AND token_capabilities IS NULL)
        OR (initiator_type IS NOT NULL AND initiator_type = 'TOKEN' AND token_capabilities IS NOT NULL
            AND jsonb_typeof(token_capabilities) = 'array'
            AND jsonb_array_length(token_capabilities) > 0)
    );
