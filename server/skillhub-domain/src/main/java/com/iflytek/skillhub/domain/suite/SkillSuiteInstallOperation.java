package com.iflytek.skillhub.domain.suite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Durable idempotency marker for one successfully issued Suite install plan. */
@Entity
@Table(name = "skill_suite_install_operation")
public class SkillSuiteInstallOperation {

    @Id
    @Column(name = "operation_id", length = 64)
    private String operationId;

    @Column(name = "client_request_id", nullable = false, length = 64)
    private String clientRequestId;

    @Column(name = "actor_key", nullable = false, length = 160)
    private String actorKey;

    @Column(name = "suite_id", nullable = false)
    private Long suiteId;

    @Column(name = "suite_version_id", nullable = false)
    private Long suiteVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SkillSuiteInstallOperation() {
    }

    public String getOperationId() { return operationId; }
    public String getClientRequestId() { return clientRequestId; }
    public String getActorKey() { return actorKey; }
    public Long getSuiteId() { return suiteId; }
    public Long getSuiteVersionId() { return suiteVersionId; }
    public Instant getCreatedAt() { return createdAt; }
}
