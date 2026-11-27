package com.iflytek.skillhub.auth.connection.persistence;

import com.iflytek.skillhub.auth.connection.core.AdapterCapability;
import com.iflytek.skillhub.auth.connection.oidc.OidcConnectionConfiguration;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An immutable OIDC connection draft or active snapshot, excluding secret material. */
@Entity
@Immutable
@Table(name = "login_connection_revision")
public class LoginConnectionRevision {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "connection_id", nullable = false, length = 64)
    private String connectionId;

    @Column(nullable = false)
    private long revision;

    @Column(name = "adapter_contract_version", nullable = false, length = 32)
    private String adapterContractVersion;

    @Column(name = "config_schema_version", nullable = false)
    private int configSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> capabilities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "typed_config", nullable = false, columnDefinition = "jsonb")
    private OidcConnectionConfiguration configuration;

    @Column(name = "secret_binding_version")
    private Long secretBindingVersion;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoginConnectionRevision() {
    }

    public static LoginConnectionRevision create(String connectionId, long revision,
                                                 OidcConnectionConfiguration configuration,
                                                 Long secretBindingVersion, String actorId, Instant now) {
        LoginConnectionRevision result = new LoginConnectionRevision();
        result.id = UUID.randomUUID().toString();
        result.connectionId = Objects.requireNonNull(connectionId);
        result.revision = revision;
        result.adapterContractVersion = "1.0";
        result.configSchemaVersion = 1;
        result.capabilities = List.of(AdapterCapability.IDENTITY_ASSERTION.name());
        result.configuration = Objects.requireNonNull(configuration);
        result.secretBindingVersion = secretBindingVersion;
        result.createdBy = Objects.requireNonNull(actorId);
        result.createdAt = Objects.requireNonNull(now);
        return result;
    }

    public String getId() {
        return id;
    }

    public long getRevision() {
        return revision;
    }

    public OidcConnectionConfiguration getConfiguration() {
        return configuration;
    }

    public Long getSecretBindingVersion() {
        return secretBindingVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
