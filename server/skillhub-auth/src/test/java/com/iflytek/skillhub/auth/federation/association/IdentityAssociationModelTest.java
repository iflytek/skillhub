package com.iflytek.skillhub.auth.federation.association;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.auth.federation.core.ExternalIdentityCoordinate;
import com.iflytek.skillhub.auth.federation.core.SubjectRef;
import com.iflytek.skillhub.auth.federation.core.SubjectType;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IdentityAssociationModelTest {

    private static final Instant NOW = Instant.parse("2026-09-08T09:00:00Z");

    @Test
    void persistenceModelsPreserveTheOpaqueCaseSensitiveSubjectExactly() {
        ExternalIdentityCoordinate coordinate = new ExternalIdentityCoordinate(
                Optional.of("organization-1"),
                "connection-1",
                URI.create("https://identity.example.com"),
                new SubjectRef(new SubjectType("oidc-sub"), " Employee-42 ")
        );

        ExternalIdentity identity = ExternalIdentity.bind(coordinate, "user-1", NOW);
        PreProvisionedLoginSubject claim = PreProvisionedLoginSubject.claim(
                "organization-1",
                "membership-1",
                coordinate,
                NOW
        );

        assertThat(identity.coordinate()).isEqualTo(coordinate);
        assertThat(claim.coordinate()).isEqualTo(coordinate);
    }

    @Test
    void revocationIsTerminalAndIdempotent() {
        ExternalIdentityCoordinate coordinate = new ExternalIdentityCoordinate(
                Optional.of("organization-1"),
                "connection-1",
                URI.create("https://identity.example.com"),
                new SubjectRef(new SubjectType("oidc-sub"), "employee-42")
        );
        PreProvisionedLoginSubject claim = PreProvisionedLoginSubject.claim(
                "organization-1",
                "membership-1",
                coordinate,
                NOW
        );

        claim.revoke(NOW.plusSeconds(1));
        claim.revoke(NOW.plusSeconds(2));

        assertThat(claim.isActive()).isFalse();
        assertThat(claim.getRevokedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(claim.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
    }
}
