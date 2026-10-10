-- Platform-owned identity correlation policy. These controls are immutable revision data so
-- authentication adapters cannot decide account or membership creation.

ALTER TABLE login_connection_revision
    ADD COLUMN verified_email_correlation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN jit_provisioning_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_login_connection_revision_jit_policy CHECK (
        NOT jit_provisioning_enabled OR verified_email_correlation_enabled
    );
