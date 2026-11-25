package com.iflytek.skillhub.auth.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable origin record for an enterprise-authenticated browser session. */
@Entity
@Table(name = "identity_session_origin")
public class IdentitySessionOrigin {
    @Id
    @Column(length = 64)
    private String id;
    @Column(name = "session_key_hash", nullable = false, length = 64, unique = true, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String sessionKeyHash;
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;
    @Column(name = "organization_id", nullable = false, length = 64)
    private String organizationId;
    @Column(name = "membership_id", nullable = false, length = 64)
    private String membershipId;
    @Column(name = "login_connection_id", nullable = false, length = 64)
    private String loginConnectionId;
    @Column(name = "external_identity_id", nullable = false, length = 64)
    private String externalIdentityId;
    @Column(name = "authenticated_at", nullable = false)
    private Instant authenticatedAt;
    @Column(nullable = false, length = 64)
    private String assurance;
    @Column(name = "organization_authority_version", nullable = false)
    private long organizationAuthorityVersion;
    @Column(name = "membership_authority_version", nullable = false)
    private long membershipAuthorityVersion;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected IdentitySessionOrigin() {
    }

    public static IdentitySessionOrigin create(String id, String sessionKeyHash, String userId,
                                               String organizationId, String membershipId,
                                               String loginConnectionId, String externalIdentityId,
                                               Instant authenticatedAt, String assurance,
                                               long organizationAuthorityVersion,
                                               long membershipAuthorityVersion) {
        IdentitySessionOrigin origin = new IdentitySessionOrigin();
        origin.id = id;
        origin.sessionKeyHash = sessionKeyHash;
        origin.userId = userId;
        origin.organizationId = organizationId;
        origin.membershipId = membershipId;
        origin.loginConnectionId = loginConnectionId;
        origin.externalIdentityId = externalIdentityId;
        origin.authenticatedAt = authenticatedAt;
        origin.assurance = assurance;
        origin.organizationAuthorityVersion = organizationAuthorityVersion;
        origin.membershipAuthorityVersion = membershipAuthorityVersion;
        origin.createdAt = authenticatedAt;
        origin.updatedAt = authenticatedAt;
        return origin;
    }

    public String getUserId() { return userId; }
    public String getOrganizationId() { return organizationId; }
    public String getMembershipId() { return membershipId; }
    public String getLoginConnectionId() { return loginConnectionId; }
    public String getExternalIdentityId() { return externalIdentityId; }
    public long getOrganizationAuthorityVersion() { return organizationAuthorityVersion; }
    public long getMembershipAuthorityVersion() { return membershipAuthorityVersion; }
}
