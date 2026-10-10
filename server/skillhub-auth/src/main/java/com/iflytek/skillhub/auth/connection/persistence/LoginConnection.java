package com.iflytek.skillhub.auth.connection.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** Persistent control-plane row for one platform or organization login connection. */
@Entity
@Table(name = "login_connection")
public class LoginConnection {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "public_handle", nullable = false, length = 128)
    private String publicHandle;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private LoginConnectionScopeType scopeType;

    @Column(name = "organization_id", length = 64)
    private String organizationId;

    @Column(name = "system_key", length = 128)
    private String systemKey;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LoginConnectionStatus status;

    @Column(name = "adapter_key", nullable = false, length = 128)
    private String adapterKey;

    @Column(name = "active_revision_id", length = 64)
    private String activeRevisionId;

    @Column(name = "last_tested_revision_id", length = 64)
    private String lastTestedRevisionId;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected LoginConnection() {
    }
}
