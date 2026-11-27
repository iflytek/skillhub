package com.iflytek.skillhub.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Stores the current probe outcome for one immutable revision; the audit log retains history. */
@Repository
public class LoginConnectionProbeRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public LoginConnectionProbeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProbeOutcome> find(String organizationId, String connectionId, String revisionId) {
        return jdbc.query("""
                SELECT status, error_code, completed_at
                FROM identity_operation
                WHERE id = :id
                  AND organization_id = :organizationId
                  AND connection_id = :connectionId
                  AND operation_type = 'LOGIN_CONNECTION_TEST'
                  AND source_type = 'LOGIN_CONNECTION_REVISION'
                  AND source_reference = :revisionId
                  AND status IN ('SUCCEEDED', 'FAILED')
                """, parameters(organizationId, connectionId, revisionId), (row, i) -> new ProbeOutcome(
                        row.getString("status"),
                        row.getString("error_code"),
                        row.getTimestamp("completed_at").toInstant()
                )).stream().findFirst();
    }

    public void record(
            String organizationId, String connectionId, String revisionId, String requestId,
            Instant startedAt, ProbeOutcome outcome
    ) {
        int updated = jdbc.update("""
                INSERT INTO identity_operation (
                    id, organization_id, connection_id, operation_type, status, request_id,
                    source_type, source_reference, error_code, started_at, completed_at, updated_at
                ) VALUES (
                    :id, :organizationId, :connectionId, 'LOGIN_CONNECTION_TEST', :status, :requestId,
                    'LOGIN_CONNECTION_REVISION', :revisionId, :failureReason, :startedAt, :completedAt, :completedAt
                )
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    request_id = EXCLUDED.request_id,
                    error_code = EXCLUDED.error_code,
                    started_at = EXCLUDED.started_at,
                    completed_at = EXCLUDED.completed_at,
                    updated_at = EXCLUDED.updated_at,
                    version = identity_operation.version + 1
                WHERE identity_operation.organization_id = EXCLUDED.organization_id
                  AND identity_operation.connection_id = EXCLUDED.connection_id
                  AND identity_operation.operation_type = EXCLUDED.operation_type
                  AND identity_operation.source_type = EXCLUDED.source_type
                  AND identity_operation.source_reference = EXCLUDED.source_reference
                """, parameters(organizationId, connectionId, revisionId)
                .addValue("status", outcome.status())
                .addValue("failureReason", outcome.failureReason())
                .addValue("requestId", requestId)
                .addValue("startedAt", Timestamp.from(startedAt))
                .addValue("completedAt", Timestamp.from(outcome.checkedAt())));
        if (updated != 1) {
            throw new IllegalStateException("OIDC connection probe record did not match its revision");
        }
    }

    private static MapSqlParameterSource parameters(String organizationId, String connectionId, String revisionId) {
        return new MapSqlParameterSource("id", "oidc-test-" + revisionId)
                .addValue("organizationId", organizationId)
                .addValue("connectionId", connectionId)
                .addValue("revisionId", revisionId);
    }

    public record ProbeOutcome(String status, String failureReason, Instant checkedAt) {
        public boolean succeeded() {
            return "SUCCEEDED".equals(status);
        }
    }
}
