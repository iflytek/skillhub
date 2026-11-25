package com.iflytek.skillhub.auth.connection.secret;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Immutable;

/** Credential version. Only the encrypted envelope is retained. */
@Entity
@Immutable
@Table(name = "login_connection_secret_version")
public class LoginConnectionSecretVersion {

    public static final String PURPOSE = "oidc-client-secret";

    @EmbeddedId
    private Id id;

    @Column(name = "organization_id", nullable = false, length = 64)
    private String organizationId;

    @Column(nullable = false, length = 128)
    private String purpose;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "key_id", nullable = false, length = 128)
    private String keyId;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] nonce;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] ciphertext;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoginConnectionSecretVersion() {
    }

    LoginConnectionSecretVersion(String connectionId, long version, String organizationId, String keyId,
                                 byte[] nonce, byte[] ciphertext, String createdBy, Instant now) {
        this.id = new Id(connectionId, version);
        this.organizationId = organizationId;
        this.purpose = PURPOSE;
        this.status = "AVAILABLE";
        this.keyId = keyId;
        this.nonce = nonce.clone();
        this.ciphertext = ciphertext.clone();
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "connection_id", length = 64)
        private String connectionId;

        @Column(name = "binding_version")
        private long bindingVersion;

        protected Id() {
        }

        public Id(String connectionId, long bindingVersion) {
            this.connectionId = connectionId;
            this.bindingVersion = bindingVersion;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Id that && bindingVersion == that.bindingVersion
                    && Objects.equals(connectionId, that.connectionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(connectionId, bindingVersion);
        }

        public String getConnectionId() { return connectionId; }
        public long getBindingVersion() { return bindingVersion; }
    }

    public String getOrganizationId() { return organizationId; }
    public String getPurpose() { return purpose; }
    public String getStatus() { return status; }
    public String getKeyId() { return keyId; }
    public byte[] getNonce() { return nonce.clone(); }
    public byte[] getCiphertext() { return ciphertext.clone(); }
    public Id getId() { return id; }
}
