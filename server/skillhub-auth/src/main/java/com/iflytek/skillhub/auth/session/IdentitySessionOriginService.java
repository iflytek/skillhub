package com.iflytek.skillhub.auth.session;

import com.iflytek.skillhub.auth.federation.association.EnterpriseIdentityAssociation;
import com.iflytek.skillhub.domain.organization.Organization;
import com.iflytek.skillhub.domain.organization.OrganizationMembership;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Persists the verified organization context behind a browser session without storing the session id. */
@Service
public class IdentitySessionOriginService {
    private final IdentitySessionOriginRepository origins;
    private final Clock clock;

    public IdentitySessionOriginService(IdentitySessionOriginRepository origins, Clock clock) {
        this.origins = origins;
        this.clock = clock;
    }

    public void record(String sessionId, String userId, Organization organization,
                       OrganizationMembership membership, String connectionId,
                       EnterpriseIdentityAssociation association) {
        Instant now = clock.instant();
        origins.save(IdentitySessionOrigin.create(
                UUID.randomUUID().toString(), hash(sessionId), userId, organization.getId(), membership.getId(),
                connectionId, association.externalIdentityId(), now, "oidc.id_token",
                organization.getAuthorityVersion(), membership.getAuthorityVersion()));
    }

    static String hash(String sessionId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
