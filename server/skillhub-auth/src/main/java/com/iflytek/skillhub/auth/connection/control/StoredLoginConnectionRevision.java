package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.ConnectionUnavailableException;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Immutable;
import org.hibernate.type.SqlTypes;

/** Immutable persisted configuration revision; Secret material is referenced, never embedded. */
@Entity
@Table(name = "login_connection_revision")
@Immutable
public class StoredLoginConnectionRevision {

    private static final Pattern CONTRACT_VERSION = Pattern.compile("[1-9][0-9]*\\.[0-9]+");

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "connection_id", nullable = false, length = 64, updatable = false)
    private String connectionId;

    @Column(nullable = false, updatable = false)
    private long revision;

    @Column(name = "adapter_contract_version", nullable = false, length = 32, updatable = false)
    private String adapterContractVersion;

    @Column(name = "config_schema_version", nullable = false, updatable = false)
    private int configSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private String capabilities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "typed_config", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String typedConfig;

    @Column(name = "secret_binding_version", updatable = false)
    private Long secretBindingVersion;

    @Column(name = "created_by", length = 128, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StoredLoginConnectionRevision() {
    }

    private StoredLoginConnectionRevision(
            String id,
            String connectionId,
            long revision,
            String adapterContractVersion,
            int configSchemaVersion,
            String capabilities,
            String typedConfig,
            Long secretBindingVersion,
            String createdBy,
            Instant createdAt
    ) {
        this.id = requireText(id, "id");
        this.connectionId = requireText(connectionId, "connectionId");
        if (revision < 1) {
            throw invalid("revision");
        }
        this.revision = revision;
        this.adapterContractVersion = requireContractVersion(adapterContractVersion);
        if (configSchemaVersion < 1) {
            throw invalid("configSchemaVersion");
        }
        this.configSchemaVersion = configSchemaVersion;
        this.capabilities = requireJsonShape(capabilities, '[', ']', "capabilities");
        this.typedConfig = requireJsonShape(typedConfig, '{', '}', "typedConfig");
        if (secretBindingVersion != null && secretBindingVersion < 0) {
            throw invalid("secretBindingVersion");
        }
        this.secretBindingVersion = secretBindingVersion;
        this.createdBy = createdBy == null ? null : requireText(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static StoredLoginConnectionRevision create(
            String connectionId,
            long revision,
            String adapterContractVersion,
            int configSchemaVersion,
            String capabilities,
            String typedConfig,
            Long secretBindingVersion,
            String createdBy,
            Instant createdAt
    ) {
        return new StoredLoginConnectionRevision(
                UUID.randomUUID().toString(),
                connectionId,
                revision,
                adapterContractVersion,
                configSchemaVersion,
                capabilities,
                typedConfig,
                secretBindingVersion,
                createdBy,
                createdAt
        );
    }

    LoginConnectionRevisionSnapshot snapshot(LoginConnection connection) {
        if (!connectionId.equals(connection.getId())) {
            throw new ConnectionUnavailableException();
        }
        return new LoginConnectionRevisionSnapshot(
                connection.getOrganizationId(),
                connectionId,
                connection.getPublicHandle(),
                id,
                revision,
                connection.getAdapterKey(),
                adapterContractVersion,
                configSchemaVersion,
                capabilities,
                typedConfig,
                Optional.ofNullable(secretBindingVersion)
        );
    }

    private static String requireContractVersion(String value) {
        String normalized = requireText(value, "adapterContractVersion");
        if (!CONTRACT_VERSION.matcher(normalized).matches()) {
            throw invalid("adapterContractVersion");
        }
        return normalized;
    }

    private static String requireJsonShape(String value, char open, char close, String field) {
        String normalized = requireText(value, field);
        if (normalized.charAt(0) != open || normalized.charAt(normalized.length() - 1) != close) {
            throw invalid(field);
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainBadRequestException("error.loginConnection.field.required", field);
        }
        return value.trim();
    }

    private static DomainBadRequestException invalid(String field) {
        return new DomainBadRequestException("error.loginConnection.revision.invalid", field);
    }

    public String getId() {
        return id;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public long getRevision() {
        return revision;
    }

    public String getAdapterContractVersion() {
        return adapterContractVersion;
    }

    public int getConfigSchemaVersion() {
        return configSchemaVersion;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public String getTypedConfig() {
        return typedConfig;
    }

    public Optional<Long> getSecretBindingVersion() {
        return Optional.ofNullable(secretBindingVersion);
    }

    public Optional<String> getCreatedBy() {
        return Optional.ofNullable(createdBy);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "StoredLoginConnectionRevision[id=" + id
                + ", connectionId=" + connectionId
                + ", revision=" + revision
                + ", adapterContractVersion=" + adapterContractVersion
                + ", configSchemaVersion=" + configSchemaVersion
                + ", capabilities=<redacted>, typedConfig=<redacted>, secretBindingVersion="
                + (secretBindingVersion == null ? "none" : "present") + "]";
    }
}
